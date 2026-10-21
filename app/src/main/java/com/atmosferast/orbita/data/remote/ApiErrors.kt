package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.ValidationError
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.serialization.Serializable

// The API answers errors as { "error": { "code", "message", "details" } }. The app picks its
// own text from the code (docs/plans/13-android-integration.md in atm-orbita-api); the message
// is only the API's fallback and is never shown.

@Serializable
internal data class ApiErrorEnvelope(val error: ApiErrorBody? = null)

@Serializable
internal data class ApiErrorBody(
    val code: String = "",
    val message: String = "",
    val details: List<ApiFieldError> = emptyList(),
)

@Serializable
internal data class ApiFieldError(val field: String = "", val code: String = "")

/** The domain error for a [code] of the API. */
internal fun dataErrorFor(code: String): DataError = when (code) {
    "INVALID_CREDENTIALS" -> DataError.INVALID_CREDENTIALS
    "EMAIL_ALREADY_REGISTERED" -> DataError.EMAIL_ALREADY_REGISTERED
    "NOT_FOUND" -> DataError.NOT_FOUND
    "CATEGORY_NAME_TAKEN" -> DataError.NAME_TAKEN
    "FX_RATE_NOT_CONFIGURED" -> DataError.FX_NOT_CONFIGURED
    "PURCHASE_ALREADY_PAID" -> DataError.PURCHASE_ALREADY_PAID
    "CARD_HAS_PENDING_PURCHASES" -> DataError.CARD_HAS_PENDING_PURCHASES
    "PAYMENT_EXPENSE_LOCKED" -> DataError.PAYMENT_LOCKED
    "SESSION_EXPIRED", "UNAUTHORIZED" -> DataError.SESSION_EXPIRED
    "TOO_MANY_REQUESTS" -> DataError.TOO_MANY_REQUESTS
    else -> DataError.UNKNOWN
}

/** The first field problem the app knows how to tell; the API sends them in the app's order. */
private fun validationErrorFor(details: List<ApiFieldError>): ValidationError? =
    details.firstNotNullOfOrNull { detail ->
        ValidationError.entries.firstOrNull { it.name == detail.code }
    }

/** Reads an error response of the API into a [DataException]. */
internal suspend fun HttpResponse.toDataException(): DataException {
    val body = runCatching { body<ApiErrorEnvelope>().error }.getOrNull()
    val code = body?.code.orEmpty()
    if (code == "VALIDATION_FAILED") {
        return DataException(DataError.UNKNOWN, invalid = validationErrorFor(body?.details.orEmpty()))
    }
    return DataException(dataErrorFor(code))
}

/** Throws the API's error unless the response succeeded. */
internal suspend fun HttpResponse.requireSuccess(): HttpResponse {
    if (!status.isSuccess()) throw toDataException()
    return this
}

/** No connection, or the server could not be reached: told as [DataError.NETWORK]. */
internal suspend fun <T> networkCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: IOException) {
        throw DataException(DataError.NETWORK, e)
    }
