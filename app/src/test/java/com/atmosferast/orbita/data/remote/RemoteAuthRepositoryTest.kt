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

internal val TestAuthEndpoint = AuthEndpoint("https://auth.test/v1", "web.apps.googleusercontent.com")

private fun tokensBody(access: String = "access-1", refresh: String = "refresh-1") = """
    {"access_token":"$access","refresh_token":"$refresh","token_type":"Bearer","expires_in":900,
    "refresh_expires_in":2592000,"session_mode":"native"}
""".trimIndent()

/** A Google ID token whose payload says [email]. */
private fun googleIdToken(email: String): String {
    val b64 = java.util.Base64.getUrlEncoder().withoutPadding()
    return listOf("""{"alg":"RS256"}""", """{"email":"$email"}""").joinToString(".") {
        b64.encodeToString(it.toByteArray())
    } + ".sig"
}

private fun errorBody(code: String, details: String = "[]") =
    """{"error":{"code":"$code","message":"texto del API","details":$details}}"""

private suspend fun HttpRequestData.bodyText(): String = String(body.toByteArray())

private class Harness(
    store: MemoryTokenStore = MemoryTokenStore(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val tokens = store
    val engine = MockEngine(handler)
    val session = ApiSession(createApiClient("http://api.test/api/v1", engine, "Orbita-Android/test"), tokens, TestAuthEndpoint)
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
            assertEquals("https://auth.test/v1/login", request.url.toString())
            assertEquals("""{"system":"orbita","email":"ana@correo.com","password":"vidanueva123"}""", request.bodyText())
            json(tokensBody())
        }
        h.repository.signIn(Credentials(" ana@correo.com ", "vidanueva123"))

        assertEquals(SessionState.SignedIn("ana@correo.com"), h.session.session.value)
        assertEquals("access-1", h.tokens.accessToken)
        assertEquals(StoredSession("ana@correo.com", "refresh-1"), h.tokens.load())
    }

    @Test
    fun `signing up goes to the auth API and then sets the time zone of the phone in the data API`() = runTest {
        val calls = mutableListOf<Triple<String, String, String>>()
        val h = Harness { request ->
            calls += Triple("${request.method.value} ${request.url}", request.bodyText(), request.headers[HttpHeaders.Authorization].orEmpty())
            if (request.url.host == "auth.test") json(tokensBody(), HttpStatusCode.Created)
            else json("""{"data":{}}""")
        }
        h.repository.signUp(" Ana Torres ", Credentials("mx@correo.com", "vidanueva123"))

        assertEquals("POST https://auth.test/v1/register", calls[0].first)
        assertEquals(
            """{"system":"orbita","email":"mx@correo.com","full_name":"Ana Torres","password":"vidanueva123"}""",
            calls[0].second,
        )
        assertEquals("PATCH http://api.test/api/v1/settings", calls[1].first)
        assertTrue(calls[1].second, calls[1].second.contains("\"timezone\":"))
        assertEquals("Bearer access-1", calls[1].third)
        assertEquals(SessionState.SignedIn("mx@correo.com"), h.session.session.value)
    }

    @Test
    fun `signing up without a name uses the part of the email before the at`() = runTest {
        var sent = ""
        val h = Harness { request ->
            if (request.url.host == "auth.test") sent = request.bodyText()
            json(tokensBody(), HttpStatusCode.Created)
        }
        h.repository.signUp(" ", Credentials("ana.t@correo.com", "vidanueva123"))
        assertTrue(sent, sent.contains("\"full_name\":\"ana.t\""))
    }

    @Test
    fun `a failing profile call does not undo a sign up`() = runTest {
        val h = Harness { request ->
            if (request.url.host == "auth.test") json(tokensBody(), HttpStatusCode.Created)
            else throw IOException("offline")
        }
        h.repository.signUp("Ana", Credentials("ana@correo.com", "vidanueva123"))
        assertEquals(SessionState.SignedIn("ana@correo.com"), h.session.session.value)
    }

    @Test
    fun `the errors of the API become the errors of the app`() = runTest {
        val responses = mapOf(
            "invalid_credentials" to HttpStatusCode.Unauthorized,
            "email_taken" to HttpStatusCode.Conflict,
            "too_many_attempts" to HttpStatusCode.TooManyRequests,
            "signup_closed" to HttpStatusCode.Forbidden,
            "no_access" to HttpStatusCode.Forbidden,
            "invalid_refresh_token" to HttpStatusCode.Unauthorized,
            "password_required_to_link" to HttpStatusCode.Conflict,
            "google_challenge_expired" to HttpStatusCode.BadRequest,
            "google_unavailable" to HttpStatusCode.ServiceUnavailable,
            "google_not_linked" to HttpStatusCode.Conflict,
            "account_cleanup_failed" to HttpStatusCode.BadGateway,
            "something_new" to HttpStatusCode.InternalServerError,
        )
        val expected = listOf(
            DataError.INVALID_CREDENTIALS, DataError.EMAIL_ALREADY_REGISTERED,
            DataError.TOO_MANY_REQUESTS, DataError.SIGNUP_CLOSED, DataError.NO_ACCESS,
            DataError.SESSION_EXPIRED, DataError.GOOGLE_LINK_REQUIRED, DataError.GOOGLE_CHALLENGE_EXPIRED,
            DataError.GOOGLE_UNAVAILABLE, DataError.GOOGLE_NOT_LINKED, DataError.ACCOUNT_CLEANUP_FAILED,
            DataError.UNKNOWN,
        )
        responses.entries.zip(expected).forEach { (response, error) ->
            val h = Harness { json(errorBody(response.key), response.value) }
            val failure = expectFailure { h.repository.signIn(Credentials("ana@correo.com", "x")) }
            assertEquals(response.key, error, failure.error)
            assertEquals(SessionState.SignedOut, h.session.session.value)
        }
    }

    @Test
    fun `a rejected form tells its field problem`() = runTest {
        val expected = mapOf(
            "invalid_email" to ValidationError.EMAIL_INVALID,
            "invalid_full_name" to ValidationError.NAME_REQUIRED,
            "weak_password" to ValidationError.PASSWORD_TOO_SHORT,
            "invalid_code_format" to ValidationError.CODE_INVALID,
        )
        expected.forEach { (code, invalid) ->
            val h = Harness { json(errorBody(code), HttpStatusCode.BadRequest) }
            val failure = expectFailure { h.repository.signUp("Ana", Credentials("x", "y")) }
            assertEquals(code, invalid, failure.invalid)
        }
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
                "/v1/login" -> json(tokensBody())
                else -> {
                    assertEquals("https://auth.test/v1/logout", request.url.toString())
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
                "/v1/refresh" -> {
                    renewals.incrementAndGet()
                    json(tokensBody("fresh", "refresh-2"))
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
                "/v1/refresh" -> json(errorBody("invalid_refresh_token"), HttpStatusCode.Unauthorized)
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
            listOf("/v1/password/forgot", "/v1/password/verify", "/v1/password/reset"),
            calls.map { it.first },
        )
        assertEquals("""{"email":"ana@correo.com","system":"orbita"}""", calls[0].second)
        assertEquals("""{"email":"ana@correo.com","code":"042317"}""", calls[1].second)
        assertEquals("""{"email":"ana@correo.com","code":"042317","new_password":"otraclave123","system":"orbita"}""", calls[2].second)
        assertEquals(SessionState.SignedOut, h.session.session.value)
    }

    @Test
    fun `a wrong or expired code is told as such`() = runTest {
        val h = Harness { json(errorBody("reset_code_invalid"), HttpStatusCode.UnprocessableEntity) }
        val failure = expectFailure { h.repository.verifyResetCode("ana@correo.com", "000000") }
        assertEquals(DataError.RESET_CODE_INVALID, failure.error)
    }

    @Test
    fun `deleting the account sends the password and forgets the session`() = runTest {
        val calls = mutableListOf<Pair<String, String>>()
        val h = Harness { request ->
            when (request.url.encodedPath) {
                "/v1/login" -> json(tokensBody())
                else -> {
                    calls += "${request.method.value} ${request.url.encodedPath}" to request.bodyText()
                    respond("", HttpStatusCode.NoContent)
                }
            }
        }
        h.repository.signIn(Credentials("ana@correo.com", "vidanueva123"))
        h.repository.deleteAccount("vidanueva123")

        assertEquals(listOf("DELETE /v1/account" to """{"password":"vidanueva123"}"""), calls)
        assertEquals(SessionState.SignedOut, h.session.session.value)
        assertNull(h.tokens.load())
    }

    @Test
    fun `a wrong password when deleting keeps the session and is not repeated`() = runTest {
        val renewals = AtomicInteger()
        val deletes = AtomicInteger()
        val h = Harness { request ->
            when (request.url.encodedPath) {
                "/v1/login" -> json(tokensBody())
                "/v1/refresh" -> {
                    renewals.incrementAndGet()
                    json(tokensBody("fresh", "refresh-2"))
                }
                else -> {
                    deletes.incrementAndGet()
                    json(errorBody("invalid_credentials"), HttpStatusCode.Unauthorized)
                }
            }
        }
        h.repository.signIn(Credentials("ana@correo.com", "vidanueva123"))
        val failure = expectFailure { h.repository.deleteAccount("otraclave123") }

        assertEquals(DataError.WRONG_PASSWORD, failure.error)
        assertEquals(1, deletes.get())
        assertEquals(0, renewals.get())
        assertEquals(SessionState.SignedIn("ana@correo.com"), h.session.session.value)
        assertEquals(StoredSession("ana@correo.com", "refresh-1"), h.tokens.load())
    }

    @Test
    fun `Google is off without a configured client`() = runTest {
        val h = Harness { json("{}") }
        assertEquals("web.apps.googleusercontent.com", h.repository.googleClientId())
        val off = ApiSession(
            createApiClient("http://api.test/api/v1", MockEngine { json("{}") }, "t"),
            MemoryTokenStore(), AuthEndpoint("https://auth.test/v1", " "),
        )
        assertNull(RemoteAuthRepository(off).googleClientId())
    }

    @Test
    fun `signing in with Google sends the credential and nonce and keeps the session`() = runTest {
        val calls = mutableListOf<Pair<String, String>>()
        val idToken = googleIdToken("ana@gmail.com")
        val h = Harness { request ->
            calls += request.url.encodedPath to request.bodyText()
            when (request.url.encodedPath) {
                "/v1/google/challenge" -> json("""{"nonce":"n-1","expires_in":300}""")
                "/v1/google" -> json(tokensBody())
                else -> json("""{"data":{}}""")
            }
        }
        val nonce = h.repository.googleNonce()
        h.repository.signInWithGoogle(idToken, nonce)

        assertEquals("n-1", nonce)
        assertEquals(
            """{"system":"orbita","credential":"$idToken","nonce":"n-1"}""",
            calls[1].second,
        )
        assertEquals(SessionState.SignedIn("ana@gmail.com"), h.session.session.value)
        assertEquals(StoredSession("ana@gmail.com", "refresh-1"), h.tokens.load())
    }

    @Test
    fun `an email that already has an account asks for its password to link Google`() = runTest {
        var sent = ""
        val token = googleIdToken("ana@gmail.com")
        val h = Harness { request ->
            if (request.url.host == "auth.test") sent = request.bodyText()
            if (request.url.host != "auth.test") json("""{"data":{}}""")
            else if (sent.contains("password")) json(tokensBody())
            else json(errorBody("password_required_to_link"), HttpStatusCode.Conflict)
        }
        val failure = expectFailure { h.repository.signInWithGoogle(token, "n-1") }
        assertEquals(DataError.GOOGLE_LINK_REQUIRED, failure.error)

        h.repository.signInWithGoogle(token, "n-1", password = "vidanueva123")
        assertTrue(sent, sent.contains("\"password\":\"vidanueva123\""))
        assertEquals(SessionState.SignedIn("ana@gmail.com"), h.session.session.value)
    }

    @Test
    fun `a Google-only account deletes itself with a fresh credential and nonce`() = runTest {
        val calls = mutableListOf<Pair<String, String>>()
        val h = Harness { request ->
            when (request.url.encodedPath) {
                "/v1/login" -> json(tokensBody())
                else -> {
                    calls += "${request.method.value} ${request.url.encodedPath}" to request.bodyText()
                    respond("", HttpStatusCode.NoContent)
                }
            }
        }
        h.repository.signIn(Credentials("ana@gmail.com", "vidanueva123"))
        h.repository.deleteAccountWithGoogle("cred", "n-2")

        assertEquals(listOf("DELETE /v1/account" to """{"credential":"cred","nonce":"n-2"}"""), calls)
        assertEquals(SessionState.SignedOut, h.session.session.value)
    }
}
