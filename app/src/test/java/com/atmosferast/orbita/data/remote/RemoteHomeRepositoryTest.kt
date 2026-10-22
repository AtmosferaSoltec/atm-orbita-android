package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.Movement
import com.atmosferast.orbita.domain.model.Transfer
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Inicio in a single request (plan 08 of atm-orbita-api).

private class HomeTokens : TokenStore {
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

private val json = headersOf(HttpHeaders.ContentType, "application/json")

private const val HOME = """{"data":{
    "today":"2026-10-02",
    "settings":{"main_currency":"PEN","secondary_currency":"USD","display_currency":"PEN","fx_configured":true,"rate":"3.200000","fx_mode":"manual"},
    "savings":{"included_count":4,"active_count":5,"totals":[{"currency":"PEN","amount":"7166.30"},{"currency":"USD","amount":"2239.47"}]},
    "month":{"from":"2026-10-01","to":"2026-10-02","currency":"PEN","income_total":"3500.00","expense_total":"30.50"},
    "recent_entries":[
      {"entry_type":"transaction","id":"m1","kind":"expense","occurred_on":"2026-10-02","amount":"18.50","currency":"PEN","description":"Almuerzo",
       "account":{"id":"cash","name":"Efectivo","type":"cash","currency":"PEN"},
       "category":{"id":"food","name":"Alimentación","kind":"expense","color":"#0E7490"},"credit_purchase_id":null},
      {"entry_type":"transfer","id":"t1","occurred_on":"2026-10-01","note":"Cambio de dólares",
       "from_account":{"id":"usd","name":"Cuenta Dólares","type":"savings","currency":"USD"},
       "to_account":{"id":"debit","name":"Débito principal","type":"debit","currency":"PEN"},
       "from_amount":"20.00","to_amount":"64.00","exchange_rate":"3.200000"}],
    "credit":{"pending_count":4,"next_due_date":"2026-10-05","has_urgent":true}}}"""

private const val NEW_USER = """{"data":{
    "today":"2026-10-02",
    "settings":{"main_currency":"MXN","secondary_currency":"USD","display_currency":"MXN","fx_configured":false,"rate":null,"fx_mode":"manual"},
    "savings":{"included_count":1,"active_count":1,"totals":[{"currency":"MXN","amount":"0.00"}]},
    "month":{"from":"2026-10-01","to":"2026-10-02","currency":"MXN","income_total":"0.00","expense_total":"0.00"},
    "recent_entries":[],"credit":null}}"""

class RemoteHomeRepositoryTest {

    @Test
    fun `Inicio comes in one request`() = runTest {
        var reads = 0
        val engine = MockEngine {
            reads++
            respond(HOME, HttpStatusCode.OK, json)
        }
        val session = ApiSession(createApiClient("http://api.test/api/v1", engine, "test"), HomeTokens())
        val home = RemoteHomeRepository(session, RemoteChanges()).observeHome().first()

        assertEquals(1, reads)
        assertEquals(BigDecimal("3.200000"), home.fx.rate)
        assertEquals(mapOf("PEN" to BigDecimal("7166.30"), "USD" to BigDecimal("2239.47")), home.savings)
        assertEquals(4, home.includedAccounts)
        assertEquals(5, home.activeAccounts)
        assertEquals(BigDecimal("30.50"), home.monthExpense)
        assertEquals("Almuerzo", (home.recentEntries[0] as Movement).description)
        assertEquals("Cambio de dólares", (home.recentEntries[1] as Transfer).note)
        assertEquals(LocalDate.of(2026, 10, 5), home.credit?.nextDueDate)
        assertTrue(home.credit!!.hasUrgent)
    }

    @Test
    fun `a new user has no rate, no entries and no card reminder`() = runTest {
        val engine = MockEngine { respond(NEW_USER, HttpStatusCode.OK, json) }
        val session = ApiSession(createApiClient("http://api.test/api/v1", engine, "test"), HomeTokens())
        val home = RemoteHomeRepository(session, RemoteChanges()).observeHome().first()

        assertNull(home.fx.rate)
        assertEquals(mapOf("MXN" to BigDecimal("0.00")), home.savings)
        assertTrue(home.recentEntries.isEmpty())
        assertNull(home.credit)
    }

    @Test
    fun `a write through another repository reads Inicio again`() = runTest {
        var homeReads = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("home") -> {
                    homeReads++
                    respond(NEW_USER, HttpStatusCode.OK, json)
                }
                request.method == HttpMethod.Post -> respond("{}", HttpStatusCode.Created, json)
                else -> respond("""{"data":[]}""", HttpStatusCode.OK, json)
            }
        }
        val session = ApiSession(createApiClient("http://api.test/api/v1", engine, "test"), HomeTokens())
        val changes = RemoteChanges()
        val home = RemoteHomeRepository(session, changes)
        val credit = RemoteCreditRepository(session, changes)

        home.observeHome().first()
        credit.createCard(com.atmosferast.orbita.domain.model.CreditCardDraft("Visa", "MXN"))
        home.observeHome().first()

        assertEquals(2, homeReads)
    }
}
