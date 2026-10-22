package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.CreditCardDraft
import com.atmosferast.orbita.domain.model.CreditPayment
import com.atmosferast.orbita.domain.model.CreditPurchaseDraft
import com.atmosferast.orbita.domain.model.debtIn
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

// Credit cards and pending purchases against the API (plan 09 of atm-orbita-api).

private class CreditTokens : TokenStore {
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

private class CreditHarness(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
    val session = ApiSession(createApiClient("http://api.test/api/v1", MockEngine(handler), "test"), CreditTokens())
    val repository = RemoteCreditRepository(session)
}

private val json = headersOf(HttpHeaders.ContentType, "application/json")

private const val CARDS = """{"data":[
    {"id":"visa","name":"Visa Clásica","currency":"PEN","debt":[{"currency":"PEN","amount":"336.00"}],"pending_count":2,"next_due_date":"2026-10-05"},
    {"id":"mc","name":"Mastercard Oro","currency":"PEN","debt":[],"pending_count":0,"next_due_date":null}]}"""

private fun purchase(id: String, card: String, amount: String, currency: String, due: String) = """
    {"id":"$id","card_id":"$card","card":{"id":"$card","name":"Visa Clásica","currency":"PEN"},
     "description":"Pasajes","amount":"$amount","currency":"$currency","purchase_date":"2026-10-02",
     "due_date":"$due","status":"pending",
     "category":{"id":"transport","name":"Transporte","kind":"expense","color":"#B45309"}}
""".trimIndent()

private val draft = CreditPurchaseDraft("visa", BigDecimal("240"), "transport", "Pasajes", LocalDate.of(2026, 10, 5))

class RemoteCreditRepositoryTest {

    @Test
    fun `cards and pending purchases, with the debt the app adds up`() = runTest {
        val h = CreditHarness { request ->
            val body = if (request.url.encodedPath.endsWith("credit-cards")) CARDS
            else """{"data":[${purchase("p1", "visa", "240.00", "PEN", "2026-10-05")},${purchase("p2", "visa", "96.00", "PEN", "2026-10-15")}]}"""
            respond(body, HttpStatusCode.OK, json)
        }
        val cards = h.repository.observeCards().first()
        val purchases = h.repository.observePendingPurchases().first()

        assertEquals(listOf("Visa Clásica", "Mastercard Oro"), cards.map { it.name })
        assertEquals(LocalDate.of(2026, 10, 5), purchases[0].dueDate)
        assertEquals("Transporte", purchases[0].category.name)
        assertEquals("visa", purchases[0].card.id)
        assertEquals(BigDecimal("336.00"), purchases.debtIn("PEN"))
    }

    @Test
    fun `a new purchase sends an id of the app and two decimals, then the lists read again`() = runTest {
        var sent = ""
        var reads = 0
        val h = CreditHarness { request ->
            if (request.method == HttpMethod.Post) {
                sent = String(request.body.toByteArray())
                respond("{}", HttpStatusCode.Created, json)
            } else {
                reads++
                respond("""{"data":[]}""", HttpStatusCode.OK, json)
            }
        }
        h.repository.observePendingPurchases().first()
        h.repository.createPurchase(draft)
        h.repository.observePendingPurchases().first()

        assertTrue(sent, Regex("\"id\":\"[0-9a-f-]{36}\"").containsMatchIn(sent))
        assertTrue(sent, sent.contains("\"amount\":\"240.00\""))
        assertTrue(sent, sent.contains("\"due_date\":\"2026-10-05\""))
        assertEquals(2, reads)
    }

    @Test
    fun `editing a card or a purchase sends no id`() = runTest {
        val sent = mutableListOf<Pair<String, String>>()
        val h = CreditHarness { request ->
            sent += request.url.encodedPath to String(request.body.toByteArray())
            respond("{}", HttpStatusCode.OK, json)
        }
        h.repository.updateCard("visa", CreditCardDraft("Visa Oro", "USD"))
        h.repository.updatePurchase("p1", draft.copy(amount = BigDecimal("250")))

        assertEquals("/api/v1/credit-cards/visa", sent[0].first)
        assertEquals("""{"name":"Visa Oro","currency":"USD"}""", sent[0].second)
        assertEquals("/api/v1/credit-purchases/p1", sent[1].first)
        assertFalse(sent[1].second, sent[1].second.contains("\"id\""))
    }

    @Test
    fun `a card that still owes is not archived`() = runTest {
        val h = CreditHarness {
            respond("""{"error":{"code":"CARD_HAS_PENDING_PURCHASES","message":"x"}}""", HttpStatusCode.Conflict, json)
        }
        try {
            h.repository.archiveCard("visa")
            fail("expected a DataException")
        } catch (e: DataException) {
            assertEquals(DataError.CARD_HAS_PENDING_PURCHASES, e.error)
        }
    }

    @Test
    fun `paying waits for plan 10`() = runTest {
        var calls = 0
        val h = CreditHarness {
            calls++
            respond("{}", HttpStatusCode.OK, json)
        }
        try {
            h.repository.pay(CreditPayment("p1", "cash", BigDecimal("240"), LocalDate.of(2026, 10, 2)))
            fail("expected a DataException")
        } catch (e: DataException) {
            assertEquals(DataError.UNKNOWN, e.error)
        }
        assertEquals(0, calls)
    }
}
