package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.FxPair
import com.atmosferast.orbita.domain.model.ValidationError
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// The user's currencies and exchange rate against the API (plan 02 of atm-orbita-api).

private class SettingsTokens : TokenStore {
    private var stored: StoredSession? = StoredSession("ana@correo.com", "refresh-1")
    override var accessToken: String? = "access-1"
    override fun load() = stored
    override fun save(session: StoredSession) {
        stored = session
    }
    override fun clear() {
        accessToken = null
        stored = null
    }
}

private fun settingsBody(main: String, secondary: String, display: String, rate: String?) = """
    {"data":{"main_currency":"$main","secondary_currency":"$secondary","display_currency":"$display",
    "timezone":"America/Lima","fx_mode":"manual","fx_configured":${rate != null},
    "rate":${rate?.let { "\"$it\"" } ?: "null"},"rate_updated_at":null}}
""".trimIndent()

private class SettingsHarness(
    handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData,
) {
    val engine = MockEngine(handler)
    val session = ApiSession(createApiClient("http://api.test/api/v1", engine, "test"), SettingsTokens())
    val repository = RemoteSettingsRepository(session)
}

private val json = headersOf(HttpHeaders.ContentType, "application/json")

class RemoteSettingsRepositoryTest {

    @Test
    fun `a new user has no exchange rate`() = runTest {
        val h = SettingsHarness { respond(settingsBody("PEN", "USD", "PEN", null), HttpStatusCode.OK, json) }
        val settings = h.repository.observeSettings().first()

        assertEquals(FxPair("PEN", "USD", rate = null), settings.fx)
        assertEquals("PEN", settings.displayCurrency)
    }

    @Test
    fun `the rate is read as an exact decimal`() = runTest {
        val h = SettingsHarness { respond(settingsBody("PEN", "USD", "USD", "3.200000"), HttpStatusCode.OK, json) }
        val settings = h.repository.observeSettings().first()

        assertEquals(BigDecimal("3.200000"), settings.fx.rate)
        assertEquals("USD", settings.displayCurrency)
    }

    @Test
    fun `saving the pair sends the rate as text and keeps the answer`() = runTest {
        var sent = ""
        var method: HttpMethod? = null
        val h = SettingsHarness { request ->
            method = request.method
            sent = String(request.body.toByteArray())
            respond(settingsBody("PEN", "USD", "PEN", "3.750000"), HttpStatusCode.OK, json)
        }
        h.repository.setFx(FxPair("PEN", "USD", BigDecimal("3.75")))

        assertEquals(HttpMethod.Put, method)
        assertTrue(sent, sent.contains("\"rate\":\"3.750000\""))
        assertEquals(BigDecimal("3.750000"), h.repository.observeSettings().first().fx.rate)
    }

    @Test
    fun `a pair without a rate is never sent`() = runTest {
        var calls = 0
        val h = SettingsHarness {
            calls++
            respond("{}", HttpStatusCode.OK, json)
        }
        try {
            h.repository.setFx(FxPair("PEN", "EUR", rate = null))
            fail("expected a DataException")
        } catch (e: DataException) {
            assertEquals(DataError.FX_NOT_CONFIGURED, e.error)
        }
        assertEquals(0, calls)
    }

    @Test
    fun `showing the secondary without a rate is refused by the API`() = runTest {
        val h = SettingsHarness {
            respond(
                """{"error":{"code":"FX_RATE_NOT_CONFIGURED","message":"x"}}""",
                HttpStatusCode.UnprocessableEntity, json,
            )
        }
        try {
            h.repository.setDisplayCurrency("USD")
            fail("expected a DataException")
        } catch (e: DataException) {
            assertEquals(DataError.FX_NOT_CONFIGURED, e.error)
        }
    }

    @Test
    fun `a rejected rate is told like the app's own validation`() = runTest {
        val h = SettingsHarness {
            respond(
                """{"error":{"code":"VALIDATION_FAILED","message":"x","details":[{"field":"rate","code":"RATE_REQUIRED"}]}}""",
                HttpStatusCode.BadRequest, json,
            )
        }
        try {
            h.repository.setFx(FxPair("PEN", "USD", BigDecimal("3.2")))
            fail("expected a DataException")
        } catch (e: DataException) {
            assertEquals(ValidationError.RATE_REQUIRED, e.invalid)
        }
    }

    @Test
    fun `another session never sees the previous one's settings`() = runTest {
        var main = "PEN"
        val h = SettingsHarness { respond(settingsBody(main, "USD", main, null), HttpStatusCode.OK, json) }
        assertEquals("PEN", h.repository.observeSettings().first().fx.main)

        h.session.end()
        main = "MXN"
        h.session.start("beto@correo.com", TokensDto("access-2", 900, "refresh-2"))

        assertEquals("MXN", h.repository.observeSettings().first().fx.main)
    }

    @Test
    fun `nothing is cached after signing out`() = runTest {
        val h = SettingsHarness { respond(settingsBody("PEN", "USD", "PEN", null), HttpStatusCode.OK, json) }
        h.repository.observeSettings().first()
        h.session.end()
        val cache = RemoteSettingsRepository::class.java.getDeclaredField("cache").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        assertNull((cache.get(h.repository) as kotlinx.coroutines.flow.MutableStateFlow<Any?>).value)
    }
}
