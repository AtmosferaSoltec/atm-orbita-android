package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.SessionState
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Where atm-auth-api is (AUTH_API_BASE_URL, e.g. `https://auth.atmosferast.com/v1`) and the
 * Google server client ID it accepts (blank: no Google sign-in).
 */
data class AuthEndpoint(val baseUrl: String, val googleClientId: String)

/**
 * The session: its tokens (issued and renewed by atm-auth-api) and every authorized request
 * to atm-orbita-api with the access token.
 *
 * - With a refresh token stored, the app opens signed in, also offline; the access token is
 *   asked for on the first request.
 * - Before a request without an access token, and after a 401, the session is renewed once and
 *   the request repeated. Renewing is serialized: five requests that hit a 401 at the same time
 *   produce a single renewal (the refresh token rotates and reusing one ends the session).
 * - If renewing is refused, the session is over: back to the login form.
 * - A 401 for a wrong password (deleting the account) is not about the session: no renewal.
 */
@Singleton
class ApiSession @Inject constructor(
    private val client: HttpClient,
    private val tokens: TokenStore,
    private val authEndpoint: AuthEndpoint,
) {
    private val state = MutableStateFlow(
        tokens.load()?.let { SessionState.SignedIn(it.email) } ?: SessionState.SignedOut,
    )
    val session: StateFlow<SessionState> = state.asStateFlow()

    private val refreshing = Mutex()

    private val changeListeners = CopyOnWriteArrayList<() -> Unit>()

    internal val httpClient: HttpClient get() = client

    internal val googleClientId: String? get() = authEndpoint.googleClientId.ifBlank { null }

    /** The full URL of a path of atm-auth-api; everything else goes to atm-orbita-api. */
    internal fun authUrl(path: String): String = authEndpoint.baseUrl.trimEnd('/') + "/" + path

    /**
     * Called whenever the session starts or ends. Repositories that keep data in memory clear
     * it here, so the next person to sign in never sees the previous one's data.
     */
    internal fun onSessionChange(listener: () -> Unit) {
        changeListeners += listener
    }

    /** After signing in or up: the tokens of the auth API for [email]. */
    internal fun start(email: String, received: TokensDto) {
        tokens.accessToken = received.accessToken
        tokens.save(StoredSession(email, received.refreshToken))
        changeListeners.forEach { it() }
        state.value = SessionState.SignedIn(email)
    }

    /** Forgets the session on this phone. Always works, also offline. */
    internal fun end() {
        tokens.clear()
        changeListeners.forEach { it() }
        state.value = SessionState.SignedOut
    }

    internal fun storedRefreshToken(): String? = tokens.load()?.refreshToken

    /** A request with the access token, renewing it once if needed. */
    internal suspend fun authorized(block: HttpRequestBuilder.() -> Unit): HttpResponse {
        val token = tokens.accessToken ?: renew(stale = null)
        val first = send(token, block)
        if (first.status != HttpStatusCode.Unauthorized || first.isWrongPassword()) return first
        return send(renew(stale = token), block)
    }

    private suspend fun send(token: String, block: HttpRequestBuilder.() -> Unit): HttpResponse =
        networkCall {
            client.request {
                block()
                bearerAuth(token)
            }
        }

    /** A fresh access token. [stale] is the one that failed: if another request already
     * renewed it meanwhile, the new one is used instead of renewing again. */
    private suspend fun renew(stale: String?): String = refreshing.withLock {
        tokens.accessToken?.takeIf { it != stale }?.let { return it }
        val stored = tokens.load() ?: run {
            end()
            throw DataException(DataError.SESSION_EXPIRED)
        }
        val response = networkCall {
            client.post(authUrl("refresh")) { setBody(RefreshRequest(stored.refreshToken)) }
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            end()
            throw DataException(DataError.SESSION_EXPIRED)
        }
        val renewed = response.requireSuccess().body<TokensDto>()
        tokens.accessToken = renewed.accessToken
        tokens.save(stored.copy(refreshToken = renewed.refreshToken))
        renewed.accessToken
    }
}
