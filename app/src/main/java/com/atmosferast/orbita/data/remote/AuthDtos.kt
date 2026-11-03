package com.atmosferast.orbita.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Bodies of /auth/* and /me. The API uses snake_case.

/** Every successful response of the API: { "data": ... }. */
@Serializable
internal data class DataEnvelope<T>(val data: T)

/** The system this app signs into at atm-auth-api. */
internal const val ORBITA_SYSTEM = "orbita"

@Serializable
internal data class RegisterRequest(
    val system: String,
    val email: String,
    @SerialName("full_name") val fullName: String,
    val password: String,
)

@Serializable
internal data class LoginRequest(val system: String, val email: String, val password: String)

@Serializable
internal data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

/** Answer of register, login, refresh and Google: the tokens, with no user. */
@Serializable
internal data class TokensDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int = 0,
)

@Serializable
internal data class ForgotPasswordRequest(val email: String, val system: String)

@Serializable
internal data class VerifyResetCodeRequest(val email: String, val code: String)

@Serializable
internal data class ResetPasswordRequest(
    val email: String,
    val code: String,
    @SerialName("new_password") val newPassword: String,
    val system: String,
)

/** Deleting the account: the password, or for a Google-only account a fresh Google credential. */
@Serializable
internal data class DeleteAccountRequest(
    val password: String? = null,
    val credential: String? = null,
    val nonce: String? = null,
)

@Serializable
internal data class GoogleChallengeDto(val nonce: String)

@Serializable
internal data class GoogleSignInRequest(
    val system: String,
    val credential: String,
    val nonce: String,
    /** Only to link Google to an account that already has this email. */
    val password: String? = null,
)
