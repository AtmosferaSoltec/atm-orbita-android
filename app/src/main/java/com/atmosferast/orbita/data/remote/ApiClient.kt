package com.atmosferast.orbita.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID
import kotlinx.serialization.json.Json

/**
 * Fields the API adds later are ignored, so an older app keeps working (ORBITA_SPEC §10).
 * Nulls are not sent: an absent optional field and a null one mean the same to the API.
 */
val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * The HTTP client of the Orbita API. [baseUrl] is API_BASE_URL, e.g.
 * `http://192.168.1.10:3001/api/v1`; requests use paths relative to it (`auth/login`).
 * Errors are not thrown by Ktor: [ApiErrors] reads them from the response.
 */
fun createApiClient(baseUrl: String, engine: HttpClientEngine): HttpClient =
    HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(ApiJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
        defaultRequest {
            url(baseUrl.trimEnd('/') + "/")
            contentType(ContentType.Application.Json)
            // The same id is in every log line of the API for this request.
            headers.append("X-Request-Id", UUID.randomUUID().toString())
        }
    }
