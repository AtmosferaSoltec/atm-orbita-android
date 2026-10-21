package com.atmosferast.orbita.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Bodies of /auth/* and /me. The API uses snake_case.

/** Every successful response of the API: { "data": ... }. */
@Serializable
internal data class DataEnvelope<T>(val data: T)

@Serializable
internal data class RegisterRequest(
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
