package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.Credentials
import com.atmosferast.orbita.domain.model.SessionState
import com.atmosferast.orbita.domain.repository.AuthRepository
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import java.util.Base64
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Signing in, up and out, recovering the password, Google and deleting the account: all of it
 * against atm-auth-api (the central authentication service). The tokens it issues are the ones
 * ApiSession sends to atm-orbita-api for the data.
 */
@Singleton
class RemoteAuthRepository @Inject constructor(
    private val api: ApiSession,
) : AuthRepository {

    override val session: Flow<SessionState> = api.session

    override suspend fun signIn(credentials: Credentials) {
        val email = credentials.email.trim()
        val response = networkCall {
            api.httpClient.post(api.authUrl("login")) {
                setBody(LoginRequest(ORBITA_SYSTEM, email, credentials.password))
            }
        }
        api.start(email, response.requireSuccess().body<TokensDto>())
    }

    /**
     * atm-auth-api takes no currency (the profile starts on PEN). The time zone of the phone
     * is applied to the profile that atm-orbita-api creates on the first request.
     */
    override suspend fun signUp(name: String, credentials: Credentials) {
        val email = credentials.email.trim()
        val response = networkCall {
            api.httpClient.post(api.authUrl("register")) {
                setBody(
                    RegisterRequest(
                        system = ORBITA_SYSTEM,
                        email = email,
                        fullName = name.trim().ifBlank { email.substringBefore('@') },
                        password = credentials.password,
                    ),
                )
            }
        }
        api.start(email, response.requireSuccess().body<TokensDto>())
        applyTimezone()
    }

    /**
     * Leaving must always work, even offline: the phone forgets the session first, then the
     * auth API is told so the session can no longer be renewed anywhere.
     */
    override suspend fun signOut() {
        val refreshToken = api.storedRefreshToken()
        api.end()
        if (refreshToken != null) {
            runCatching {
                api.httpClient.post(api.authUrl("logout")) { setBody(RefreshRequest(refreshToken)) }
            }
        }
    }

    // Recovering the password: public calls, no session involved.
    override suspend fun requestPasswordReset(email: String) {
        networkCall {
            api.httpClient.post(api.authUrl("password/forgot")) {
                setBody(ForgotPasswordRequest(email.trim(), ORBITA_SYSTEM))
            }
        }.requireSuccess()
    }

    override suspend fun verifyResetCode(email: String, code: String) {
        networkCall {
            api.httpClient.post(api.authUrl("password/verify")) {
                setBody(VerifyResetCodeRequest(email.trim(), code.trim()))
            }
        }.requireSuccess()
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: String) {
        networkCall {
            api.httpClient.post(api.authUrl("password/reset")) {
                setBody(ResetPasswordRequest(email.trim(), code.trim(), newPassword, ORBITA_SYSTEM))
            }
        }.requireSuccess()
    }

    // Signing in with Google, the native way: the screen asks Google (it has the Activity) for
    // an ID token signed with the nonce of the auth API.

    /** The app's own server client ID; null if unset. */
    override suspend fun googleClientId(): String? = api.googleClientId

    override suspend fun googleNonce(): String =
        networkCall { api.httpClient.post(api.authUrl("google/challenge")) { setBody(JsonObject(emptyMap())) } }
            .requireSuccess().body<GoogleChallengeDto>().nonce

    override suspend fun signInWithGoogle(
        idToken: String,
        nonce: String,
        password: String?,
    ) {
        val response = networkCall {
            api.httpClient.post(api.authUrl("google")) {
                setBody(GoogleSignInRequest(ORBITA_SYSTEM, idToken, nonce, password))
            }
        }
        val tokens = response.requireSuccess().body<TokensDto>()
        // The token answer has no user: the email is the one Google signed into the ID token.
        val email = emailOf(idToken) ?: throw DataException(DataError.GOOGLE_INVALID)
        api.start(email, tokens)
        applyTimezone()
    }

    /**
     * Deletes the account and all its data for good. A wrong password keeps the session; once
     * deleted, the phone forgets it and goes back to the login form.
     */
    override suspend fun deleteAccount(password: String) = delete(DeleteAccountRequest(password = password))

    override suspend fun deleteAccountWithGoogle(idToken: String, nonce: String) =
        delete(DeleteAccountRequest(credential = idToken, nonce = nonce))

    private suspend fun delete(body: DeleteAccountRequest) {
        val response = api.authorized {
            method = HttpMethod.Delete
            url(api.authUrl("account"))
            setBody(body)
        }
        // Here the auth API's "invalid credentials" can only be the password (or Google proof).
        if (response.isWrongPassword()) throw DataException(DataError.WRONG_PASSWORD)
        response.requireSuccess()
        api.end()
    }

    /**
     * atm-orbita-api creates the profile (PEN, America/Lima) on the first request; the phone's
     * zone is set right away, as "today" depends on it. Best effort: it can be changed later.
     */
    private suspend fun applyTimezone() {
        runCatching {
            api.authorized {
                method = HttpMethod.Patch
                url("settings")
                setBody(UpdateSettingsRequest(timezone = TimeZone.getDefault().id))
            }
        }
    }

    /** The `email` claim of a JWT, already verified by the auth API. */
    private fun emailOf(jwt: String): String? = runCatching {
        val payload = Base64.getUrlDecoder().decode(jwt.split('.')[1])
        (ApiJson.parseToJsonElement(String(payload)).jsonObject["email"] as? JsonPrimitive)?.contentOrNull
    }.getOrNull()
}
