package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.Credentials
import com.atmosferast.orbita.domain.model.SessionState
import com.atmosferast.orbita.domain.repository.AuthRepository
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Signing in, up and out against the API (atm-orbita-api, plan 01). Recovering the password
 * and deleting the account arrive with plans 11 and 12.
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

    override suspend fun signUp(credentials: Credentials, mainCurrency: String) {
        val response = networkCall {
            api.httpClient.post("auth/register") {
                setBody(
                    RegisterRequest(
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

    // Plan 11 (6-digit code) and plan 12 of the API. Until then, with the API these fail
    // instead of pretending, so nobody believes an email was sent.
    override suspend fun requestPasswordReset(email: String) = notYetAvailable()

    override suspend fun resetPassword(token: String, newPassword: String) = notYetAvailable()

    override suspend fun deleteAccount(password: String) = notYetAvailable()

    private fun notYetAvailable(): Nothing = throw DataException(DataError.UNKNOWN)
}
