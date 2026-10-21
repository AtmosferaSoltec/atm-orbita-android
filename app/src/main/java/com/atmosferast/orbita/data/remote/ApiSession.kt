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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The session against the API: its tokens and every authorized request.
 *
 * - With a refresh token stored, the app opens signed in, also offline; the access token is
 *   asked for on the first request.
 * - Before a request without an access token, and after a 401, the session is renewed once and
 *   the request repeated. Renewing is serialized: five requests that hit a 401 at the same time
 *   produce a single renewal (the API revokes a session whose refresh token is reused).
 * - If renewing is refused, the session is over: back to the login form.
 */
@Singleton
class ApiSession @Inject constructor(
    private val client: HttpClient,
    private val tokens: TokenStore,
) {
    private val state = MutableStateFlow(
        tokens.load()?.let { SessionState.SignedIn(it.email) } ?: SessionState.SignedOut,
    )
    val session: StateFlow<SessionState> = state.asStateFlow()

    private val refreshing = Mutex()

    internal val httpClient: HttpClient get() = client

    /** After signing in or up: the API's tokens for [email]. */
    internal fun start(email: String, received: TokensDto) {
        tokens.accessToken = received.accessToken
        tokens.save(StoredSession(email, received.refreshToken))
        state.value = SessionState.SignedIn(email)
    }

    /** Forgets the session on this phone. Always works, also offline. */
    internal fun end() {
        tokens.clear()
        state.value = SessionState.SignedOut
    }

    internal fun storedRefreshToken(): String? = tokens.load()?.refreshToken

    /** A request with the access token, renewing it once if needed. */
    internal suspend fun authorized(block: HttpRequestBuilder.() -> Unit): HttpResponse {
        val token = tokens.accessToken ?: renew(stale = null)
        val first = send(token, block)
        if (first.status != HttpStatusCode.Unauthorized) return first
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
            client.post("auth/refresh") { setBody(RefreshRequest(stored.refreshToken)) }
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            end()
            throw DataException(DataError.SESSION_EXPIRED)
        }
        val renewed = response.requireSuccess().body<DataEnvelope<RefreshResponse>>().data.tokens
        tokens.accessToken = renewed.accessToken
        tokens.save(stored.copy(refreshToken = renewed.refreshToken))
        renewed.accessToken
    }
}
