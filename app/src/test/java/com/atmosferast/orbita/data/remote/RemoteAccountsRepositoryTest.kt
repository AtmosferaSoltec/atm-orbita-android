package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.AccountDraft
import com.atmosferast.orbita.domain.model.AccountType
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// The user's accounts against the API (plan 04 of atm-orbita-api).

private class AccountTokens : TokenStore {
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

private class AccountsHarness(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val session = ApiSession(createApiClient("http://api.test/api/v1", MockEngine(handler), "test"), AccountTokens())
    val repository = RemoteAccountsRepository(session)
}

private val json = headersOf(HttpHeaders.ContentType, "application/json")

private fun account(id: String, name: String, type: String, currency: String, balance: String, include: Boolean = true) =
    """{"id":"$id","name":"$name","type":"$type","currency":"$currency","balance":"$balance","include_in_savings":$include}"""

private val list = """{"data":{"accounts":[${account("a1", "Efectivo", "cash", "PEN", "0.00")},
    ${account("a2", "Cuenta Dólares", "savings", "USD", "250.00")}],
    "savings":{"included_count":2,"active_count":2,"totals":[{"currency":"PEN","amount":"800.00"}]}}}"""

private val draft = AccountDraft("Ahorros", AccountType.SAVINGS, "PEN", BigDecimal("4800"), includeInSavings = true)

class RemoteAccountsRepositoryTest {

    @Test
    fun `the list maps types and reads balances as exact decimals`() = runTest {
        val h = AccountsHarness { respond(list, HttpStatusCode.OK, json) }
        val accounts = h.repository.observeAccounts().first()

        assertEquals(listOf("Efectivo", "Cuenta Dólares"), accounts.map { it.name })
        assertEquals(listOf(AccountType.CASH, AccountType.SAVINGS), accounts.map { it.type })
        assertEquals(BigDecimal("250.00"), accounts[1].balance)
        assertEquals("USD", accounts[1].currency)
    }

    @Test
    fun `creating sends the initial balance as text with two decimals`() = runTest {
        var sent = ""
        val h = AccountsHarness { request ->
            if (request.method == HttpMethod.Get) return@AccountsHarness respond(list, HttpStatusCode.OK, json)
            sent = String(request.body.toByteArray())
            respond("""{"data":${account("a3", "Ahorros", "savings", "PEN", "4800.00")}}""", HttpStatusCode.Created, json)
        }
        h.repository.observeAccounts().first()
        h.repository.create(draft)

        assertTrue(sent, sent.contains("\"initial_balance\":\"4800.00\""))
        assertTrue(sent, sent.contains("\"type\":\"savings\""))
        assertTrue(sent, Regex("\"id\":\"[0-9a-f-]{36}\"").containsMatchIn(sent))
        assertEquals(3, h.repository.observeAccounts().first().size)
    }

    @Test
    fun `an account in another currency without a rate is refused`() = runTest {
        val h = AccountsHarness {
            respond("""{"error":{"code":"FX_RATE_NOT_CONFIGURED","message":"x"}}""", HttpStatusCode.UnprocessableEntity, json)
        }
        try {
            h.repository.create(draft.copy(currency = "USD"))
            fail("expected a DataException")
        } catch (e: DataException) {
            assertEquals(DataError.FX_NOT_CONFIGURED, e.error)
        }
    }

    @Test
    fun `editing never sends the currency nor the initial balance`() = runTest {
        val sent = mutableListOf<String>()
        val h = AccountsHarness { request ->
            if (request.method == HttpMethod.Get) return@AccountsHarness respond(list, HttpStatusCode.OK, json)
            sent += String(request.body.toByteArray())
            respond("""{"data":${account("a1", "Caja", "other", "PEN", "0.00", include = false)}}""", HttpStatusCode.OK, json)
        }
        h.repository.observeAccounts().first()
        h.repository.update("a1", draft.copy(name = "Caja", type = AccountType.OTHER, currency = "USD"))
        h.repository.setIncludeInSavings("a1", false)

        sent.forEach {
            assertFalse(it, it.contains("currency"))
            assertFalse(it, it.contains("initial_balance"))
        }
        assertEquals("""{"include_in_savings":false}""", sent[1])
        val updated = h.repository.observeAccounts().first().first()
        assertEquals("Caja", updated.name)
        assertFalse(updated.includeInSavings)
    }

    @Test
    fun `archiving removes it from the list`() = runTest {
        val h = AccountsHarness { request ->
            if (request.method == HttpMethod.Get) respond(list, HttpStatusCode.OK, json)
            else respond("", HttpStatusCode.NoContent)
        }
        h.repository.observeAccounts().first()
        h.repository.archive("a2")
        assertEquals(listOf("Efectivo"), h.repository.observeAccounts().first().map { it.name })
    }

    @Test
    fun `another session never sees the previous one's accounts`() = runTest {
        var body = list
        val h = AccountsHarness { respond(body, HttpStatusCode.OK, json) }
        assertEquals(2, h.repository.observeAccounts().first().size)

        h.session.end()
        body = """{"data":{"accounts":[${account("b1", "Efectivo", "cash", "MXN", "0.00")}],"savings":{}}}"""
        h.session.start("beto@correo.com", TokensDto("access-2", 900, "refresh-2"))

        assertEquals(listOf("MXN"), h.repository.observeAccounts().first().map { it.currency })
    }
}
