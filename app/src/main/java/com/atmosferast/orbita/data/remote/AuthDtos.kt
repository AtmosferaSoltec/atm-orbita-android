package com.atmosferast.orbita.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Bodies of /auth/* and /me. The API uses snake_case.

/** Every successful response of the API: { "data": ... }. */
@Serializable
internal data class DataEnvelope<T>(val data: T)

@Serializable
internal data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    @SerialName("main_currency") val mainCurrency: String,
    /** IANA zone of the phone: the API computes "today" with it. */
    val timezone: String,
)

@Serializable
internal data class LoginRequest(val email: String, val password: String)

@Serializable
internal data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
internal data class UserDto(val id: String, val email: String)

@Serializable
internal data class TokensDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("access_token_expires_in") val accessTokenExpiresIn: Int,
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
internal data class AuthResponse(val user: UserDto, val tokens: TokensDto)

@Serializable
internal data class RefreshResponse(val tokens: TokensDto)

/** Body of the three steps of recovering the password; each sends what it has so far. */
@Serializable
internal data class ResetRequest(
    val email: String,
    val code: String? = null,
    @SerialName("new_password") val newPassword: String? = null,
)

/** Deleting the account asks for the password again (plan 12 of the API). */
@Serializable
internal data class DeleteAccountRequest(val password: String)

/** Sign in with Google (plan 19 of the API): an empty client ID means it is off. */
@Serializable
internal data class GoogleConfigDto(@SerialName("client_id") val clientId: String = "")

@Serializable
internal data class GoogleChallengeDto(val nonce: String)

/** Without the browser's session header the API takes the nonce from the body. */
@Serializable
internal data class GoogleSignInRequest(
    val nonce: String,
    val credential: String,
    @SerialName("main_currency") val mainCurrency: String,
    val timezone: String,
    /** Only to link Google to an account that already has this email. */
    val password: String? = null,
)
