package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.Credentials
import com.atmosferast.orbita.domain.model.SessionState
import com.atmosferast.orbita.domain.repository.AuthRepository
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * Signing in, up and out against the API (atm-orbita-api, plan 01), recovering the password
 * (plan 11), deleting the account (plan 12) and signing in with Google (plan 19).
 */
@Singleton
class RemoteAuthRepository @Inject constructor(
    private val api: ApiSession,
) : AuthRepository {

    override val session: Flow<SessionState> = api.session

    override suspend fun signIn(credentials: Credentials) {
        val email = credentials.email.trim()
        val response = networkCall {
            api.httpClient.post("auth/login") { setBody(LoginRequest(email, credentials.password)) }
        }
        val auth = response.requireSuccess().body<DataEnvelope<AuthResponse>>().data
        api.start(auth.user.email, auth.tokens)
    }

    override suspend fun signUp(name: String, credentials: Credentials, mainCurrency: String) {
        val response = networkCall {
            api.httpClient.post("auth/register") {
                setBody(
                    RegisterRequest(
                        name = name.trim(),
                        email = credentials.email.trim(),
                        password = credentials.password,
                        mainCurrency = mainCurrency,
                        timezone = TimeZone.getDefault().id,
                    ),
                )
            }
        }
        val auth = response.requireSuccess().body<DataEnvelope<AuthResponse>>().data
        api.start(auth.user.email, auth.tokens)
    }

    /**
     * Leaving must always work, even offline: the phone forgets the session first, then the
     * API is told so the session can no longer be renewed anywhere.
     */
    override suspend fun signOut() {
        val refreshToken = api.storedRefreshToken()
        api.end()
        if (refreshToken != null) {
            runCatching {
                api.httpClient.post("auth/logout") { setBody(RefreshRequest(refreshToken)) }
            }
        }
    }

    // Recovering the password (plan 11 of the API): public calls, no session involved.
    override suspend fun requestPasswordReset(email: String) {
        networkCall { api.httpClient.post("auth/forgot-password") { setBody(ResetRequest(email.trim())) } }
            .requireSuccess()
    }

    override suspend fun verifyResetCode(email: String, code: String) {
        networkCall {
            api.httpClient.post("auth/verify-reset-code") { setBody(ResetRequest(email.trim(), code.trim())) }
        }.requireSuccess()
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: String) {
        networkCall {
            api.httpClient.post("auth/reset-password") {
                setBody(ResetRequest(email.trim(), code.trim(), newPassword))
            }
        }.requireSuccess()
    }

    // Signing in with Google (plan 19 of the API), the native way: the nonce and the tokens
    // travel in the body. Google itself is asked by the screen, which has the Activity.

    override suspend fun googleClientId(): String? =
        networkCall { api.httpClient.get("auth/google/config") }
            .requireSuccess().body<DataEnvelope<GoogleConfigDto>>().data.clientId.ifBlank { null }

    override suspend fun googleNonce(): String =
        networkCall { api.httpClient.post("auth/google/challenge") { setBody(JsonObject(emptyMap())) } }
            .requireSuccess().body<DataEnvelope<GoogleChallengeDto>>().data.nonce

    override suspend fun signInWithGoogle(
        idToken: String,
        nonce: String,
        mainCurrency: String,
        password: String?,
    ) {
        val response = networkCall {
            api.httpClient.post("auth/google") {
                setBody(
                    GoogleSignInRequest(
                        nonce = nonce,
                        credential = idToken,
                        mainCurrency = mainCurrency,
                        timezone = TimeZone.getDefault().id,
                        password = password,
                    ),
                )
            }
        }
        val auth = response.requireSuccess().body<DataEnvelope<AuthResponse>>().data
        api.start(auth.user.email, auth.tokens)
    }

    /**
     * Deletes the account and all its data for good (plan 12 of the API). A wrong password
     * keeps the session; once deleted, the phone forgets it and goes back to the login form.
     */
    override suspend fun deleteAccount(password: String) {
        val response = api.authorized {
            method = HttpMethod.Delete
            url("me")
            setBody(DeleteAccountRequest(password))
        }
        // Here the API's "wrong email or password" can only be the password.
        if (response.isWrongPassword()) throw DataException(DataError.WRONG_PASSWORD)
        response.requireSuccess()
        api.end()
    }
}
