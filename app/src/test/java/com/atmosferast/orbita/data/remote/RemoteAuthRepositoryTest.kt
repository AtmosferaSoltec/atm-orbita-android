package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.Credentials
import com.atmosferast.orbita.domain.model.SessionState
import com.atmosferast.orbita.domain.model.ValidationError
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// The session against the API (plan 01 of atm-orbita-api), with a fake server.

private class MemoryTokenStore(private var stored: StoredSession? = null) : TokenStore {
    override var accessToken: String? = null
    override fun load() = stored
    override fun save(session: StoredSession) {
        stored = session
    }
    override fun clear() {
        accessToken = null
        stored = null
    }
}

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(body, status, jsonHeaders)

private fun authBody(email: String, access: String = "access-1", refresh: String = "refresh-1") = """
    {"data":{"user":{"id":"0192","email":"$email","created_at":"2026-10-02T15:00:00Z"},
    "tokens":{"access_token":"$access","access_token_expires_in":900,"refresh_token":"$refresh"}}}
""".trimIndent()

private fun errorBody(code: String, details: String = "[]") =
    """{"error":{"code":"$code","message":"texto del API","details":$details}}"""

private suspend fun HttpRequestData.bodyText(): String = String(body.toByteArray())

private class Harness(
    store: MemoryTokenStore = MemoryTokenStore(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val tokens = store
    val engine = MockEngine(handler)
    val session = ApiSession(createApiClient("http://api.test/api/v1", engine, "Orbita-Android/test"), tokens)
    val repository = RemoteAuthRepository(session)
}

private suspend fun expectFailure(block: suspend () -> Unit): DataException {
    try {
        block()
    } catch (e: DataException) {
        return e
    }
    fail("expected a DataException")
    throw AssertionError()
}

class RemoteAuthRepositoryTest {

    @Test
    fun `signing in keeps the session and its tokens`() = runTest {
        val h = Harness { request ->
            assertEquals("/api/v1/auth/login", request.url.encodedPath)
            json(authBody("ana@correo.com"))
        }
        h.repository.signIn(Credentials(" ana@correo.com ", "vidanueva123"))

        assertEquals(SessionState.SignedIn("ana@correo.com"), h.session.session.value)
        assertEquals("access-1", h.tokens.accessToken)
        assertEquals(StoredSession("ana@correo.com", "refresh-1"), h.tokens.load())
    }

    @Test
    fun `signing up sends the currency and the time zone of the phone`() = runTest {
        var sent = ""
        val h = Harness { request ->
            sent = request.bodyText()
            json(authBody("mx@correo.com"), HttpStatusCode.Created)
        }
        h.repository.signUp(Credentials("mx@correo.com", "vidanueva123"), mainCurrency = "MXN")

        assertTrue(sent, sent.contains("\"main_currency\":\"MXN\""))
        assertTrue(sent, sent.contains("\"timezone\":"))
        assertEquals(SessionState.SignedIn("mx@correo.com"), h.session.session.value)
    }

    @Test
    fun `the errors of the API become the errors of the app`() = runTest {
        val responses = mapOf(
            "INVALID_CREDENTIALS" to HttpStatusCode.Unauthorized,
            "EMAIL_ALREADY_REGISTERED" to HttpStatusCode.Conflict,
            "TOO_MANY_REQUESTS" to HttpStatusCode.TooManyRequests,
            "SOMETHING_NEW" to HttpStatusCode.InternalServerError,
        )
        val expected = listOf(
            DataError.INVALID_CREDENTIALS, DataError.EMAIL_ALREADY_REGISTERED,
            DataError.TOO_MANY_REQUESTS, DataError.UNKNOWN,
        )
        responses.entries.zip(expected).forEach { (response, error) ->
            val h = Harness { json(errorBody(response.key), response.value) }
            val failure = expectFailure { h.repository.signIn(Credentials("ana@correo.com", "x")) }
            assertEquals(response.key, error, failure.error)
            assertEquals(SessionState.SignedOut, h.session.session.value)
        }
    }

    @Test
    fun `a rejected form tells its first field problem`() = runTest {
        val details = """[{"field":"email","code":"EMAIL_INVALID"},{"field":"password","code":"PASSWORD_TOO_SHORT"}]"""
        val h = Harness { json(errorBody("VALIDATION_FAILED", details), HttpStatusCode.BadRequest) }
        val failure = expectFailure { h.repository.signUp(Credentials("x", "y"), "PEN") }
        assertEquals(ValidationError.EMAIL_INVALID, failure.invalid)
    }

    @Test
    fun `without a connection the error is NETWORK`() = runTest {
        val h = Harness { throw IOException("no route to host") }
        val failure = expectFailure { h.repository.signIn(Credentials("ana@correo.com", "vidanueva123")) }
        assertEquals(DataError.NETWORK, failure.error)
    }

    @Test
    fun `with a stored session the app opens signed in, also offline`() = runTest {
        val h = Harness(MemoryTokenStore(StoredSession("ana@correo.com", "refresh-1"))) {
            throw IOException("offline")
        }
        assertEquals(SessionState.SignedIn("ana@correo.com"), h.session.session.value)
    }

    @Test
    fun `signing out always works and tells the API`() = runTest {
        var loggedOut = ""
        val h = Harness { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/login" -> json(authBody("ana@correo.com"))
                else -> {
                    loggedOut = request.bodyText()
                    throw IOException("offline")
                }
            }
        }
        h.repository.signIn(Credentials("ana@correo.com", "vidanueva123"))
        h.repository.signOut()

        assertEquals(SessionState.SignedOut, h.session.session.value)
        assertNull(h.tokens.load())
        assertNull(h.tokens.accessToken)
        assertTrue(loggedOut.contains("refresh-1"))
    }

    @Test
    fun `many requests with an expired token renew only once`() = runTest {
        val renewals = AtomicInteger()
        val h = Harness(MemoryTokenStore(StoredSession("ana@correo.com", "refresh-1"))) { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/refresh" -> {
                    renewals.incrementAndGet()
                    json("""{"data":{"tokens":{"access_token":"fresh","access_token_expires_in":900,"refresh_token":"refresh-2"}}}""")
                }
                else -> {
                    if (request.headers[HttpHeaders.Authorization] == "Bearer fresh") json("""{"data":{}}""")
                    else json(errorBody("UNAUTHORIZED"), HttpStatusCode.Unauthorized)
                }
            }
        }
        h.tokens.accessToken = "expired"

        val statuses = List(5) { async { h.session.authorized { url("me") }.status } }.awaitAll()

        assertTrue(statuses.all { it == HttpStatusCode.OK })
        assertEquals(1, renewals.get())
        assertEquals("refresh-2", h.tokens.load()?.refreshToken)
    }

    @Test
    fun `a refused renewal ends the session`() = runTest {
        val h = Harness(MemoryTokenStore(StoredSession("ana@correo.com", "reused"))) { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/refresh" -> json(errorBody("SESSION_EXPIRED"), HttpStatusCode.Unauthorized)
                else -> json(errorBody("UNAUTHORIZED"), HttpStatusCode.Unauthorized)
            }
        }
        val failure = expectFailure { h.session.authorized { url("me") } }

        assertEquals(DataError.SESSION_EXPIRED, failure.error)
        assertEquals(SessionState.SignedOut, h.session.session.value)
        assertNull(h.tokens.load())
    }

    @Test
    fun `recovering the password walks the three steps against the API`() = runTest {
        val calls = mutableListOf<Pair<String, String>>()
        val h = Harness { request ->
            calls += request.url.encodedPath to request.bodyText()
            respond("", HttpStatusCode.NoContent)
        }
        h.repository.requestPasswordReset(" ana@correo.com ")
        h.repository.verifyResetCode("ana@correo.com", "042317")
        h.repository.resetPassword("ana@correo.com", "042317", "otraclave123")

        assertEquals(
            listOf("/api/v1/auth/forgot-password", "/api/v1/auth/verify-reset-code", "/api/v1/auth/reset-password"),
            calls.map { it.first },
        )
        assertEquals("""{"email":"ana@correo.com"}""", calls[0].second)
        assertEquals("""{"email":"ana@correo.com","code":"042317"}""", calls[1].second)
        assertEquals("""{"email":"ana@correo.com","code":"042317","new_password":"otraclave123"}""", calls[2].second)
        assertEquals(SessionState.SignedOut, h.session.session.value)
    }

    @Test
    fun `a wrong or expired code is told as such`() = runTest {
        val h = Harness { json(errorBody("RESET_CODE_INVALID"), HttpStatusCode.UnprocessableEntity) }
        val failure = expectFailure { h.repository.verifyResetCode("ana@correo.com", "000000") }
        assertEquals(DataError.RESET_CODE_INVALID, failure.error)
    }
}
