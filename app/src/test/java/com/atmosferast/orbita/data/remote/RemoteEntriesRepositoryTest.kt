package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.model.Movement
import com.atmosferast.orbita.domain.model.MovementDraft
import com.atmosferast.orbita.domain.model.MovementKind
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
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Incomes, expenses and the list of movements against the API (plan 05 of atm-orbita-api).

private class EntryTokens : TokenStore {
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

private class EntriesHarness(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val session = ApiSession(createApiClient("http://api.test/api/v1", MockEngine(handler), "test"), EntryTokens())
    val accounts = RemoteAccountsRepository(session)
    val repository = RemoteEntriesRepository(session, accounts)
}

private val json = headersOf(HttpHeaders.ContentType, "application/json")

private fun entry(id: String, text: String, date: String, amount: String, kind: String = "expense") = """
    {"entry_type":"transaction","id":"$id","kind":"$kind","occurred_on":"$date","amount":"$amount",
    "currency":"PEN","description":"$text",
    "account":{"id":"cash","name":"Efectivo","type":"cash","currency":"PEN"},
    "category":{"id":"food","name":"Alimentación","kind":"$kind","color":"#0E7490"},
    "credit_purchase_id":null}
""".trimIndent()

private fun page(total: Int, vararg entries: String) =
    """{"data":[${entries.joinToString(",")}],"meta":{"page":1,"page_size":100,"total":$total}}"""

private val september = DatePeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
private val draft = MovementDraft(MovementKind.EXPENSE, BigDecimal("45.5"), "cash", "food", "Almuerzo")

class RemoteEntriesRepositoryTest {

    @Test
    fun `a period is read page by page into movements`() = runTest {
        val pages = mutableListOf<String>()
        val h = EntriesHarness { request ->
            pages += request.url.encodedQuery
            val body = if (request.url.parameters["page"] == "1") page(2, entry("m1", "Alquiler", "2026-09-30", "800.00"))
            else page(2, entry("m2", "Sueldo", "2026-09-01", "3500.00", kind = "income"))
            respond(body, HttpStatusCode.OK, json)
        }
        val list = h.repository.observeEntries(september).first().map { it as Movement }

        assertEquals(listOf("Alquiler", "Sueldo"), list.map { it.description })
        assertEquals(MovementKind.INCOME, list[1].kind)
        assertEquals(LocalDate.of(2026, 9, 30), list[0].date)
        assertEquals(BigDecimal("800.00"), list[0].amount)
        assertEquals("Efectivo", list[0].account.name)
        assertEquals(2, pages.size)
        assertTrue(pages[0], pages[0].contains("from=2026-09-01") && pages[0].contains("to=2026-09-30"))
    }

    @Test
    fun `a new movement sends an id of the app and no date, then the list reads again`() = runTest {
        var sent = ""
        var reads = 0
        val h = EntriesHarness { request ->
            when (request.method) {
                HttpMethod.Post -> {
                    sent = String(request.body.toByteArray())
                    respond("""{"data":${entry("m9", "Almuerzo", "2026-10-02", "45.50")}}""", HttpStatusCode.Created, json)
                }
                else -> {
                    reads++
                    respond(page(0), HttpStatusCode.OK, json)
                }
            }
        }
        h.repository.observeEntries(september).first()
        h.repository.createMovement(draft)
        h.repository.observeEntries(september).first()

        assertTrue(sent, Regex("\"id\":\"[0-9a-f-]{36}\"").containsMatchIn(sent))
        assertTrue(sent, sent.contains("\"amount\":\"45.50\""))
        assertFalse(sent, sent.contains("occurred_on"))
        assertFalse(sent, sent.contains("kind"))
        assertEquals(2, reads)
    }

    @Test
    fun `editing sends the date and no id`() = runTest {
        var sent = ""
        var path = ""
        val h = EntriesHarness { request ->
            sent = String(request.body.toByteArray())
            path = request.url.encodedPath
            respond("""{"data":${entry("m1", "Almuerzo", "2026-09-30", "45.50")}}""", HttpStatusCode.OK, json)
        }
        h.repository.updateMovement("m1", draft.copy(date = LocalDate.of(2026, 9, 30)))

        assertEquals("/api/v1/transactions/m1", path)
        assertTrue(sent, sent.contains("\"occurred_on\":\"2026-09-30\""))
        assertFalse(sent, sent.contains("\"id\""))
    }

    @Test
    fun `a future date is told like the app's own validation`() = runTest {
        val h = EntriesHarness {
            respond(
                """{"error":{"code":"VALIDATION_FAILED","message":"x","details":[{"field":"occurred_on","code":"FUTURE_DATE"}]}}""",
                HttpStatusCode.BadRequest, json,
            )
        }
        try {
            h.repository.createMovement(draft)
            fail("expected a DataException")
        } catch (e: DataException) {
            assertEquals(com.atmosferast.orbita.domain.model.ValidationError.FUTURE_DATE, e.invalid)
        }
    }

    @Test
    fun `deleting reads the account balances again`() = runTest {
        var accountReads = 0
        val h = EntriesHarness { request ->
            when {
                request.method == HttpMethod.Delete -> respond("", HttpStatusCode.NoContent)
                request.url.encodedPath.endsWith("accounts") -> {
                    accountReads++
                    respond("""{"data":{"accounts":[],"savings":{}}}""", HttpStatusCode.OK, json)
                }
                else -> respond(page(0), HttpStatusCode.OK, json)
            }
        }
        h.accounts.observeAccounts().first()
        h.repository.deleteMovement("m1")
        assertEquals(2, accountReads)
    }

    @Test
    fun `transfers are not in the API yet`() = runTest {
        val h = EntriesHarness { respond(page(0), HttpStatusCode.OK, json) }
        try {
            h.repository.deleteTransfer("t1")
            fail("expected a DataException")
        } catch (e: DataException) {
            assertEquals(DataError.UNKNOWN, e.error)
        }
    }
}
