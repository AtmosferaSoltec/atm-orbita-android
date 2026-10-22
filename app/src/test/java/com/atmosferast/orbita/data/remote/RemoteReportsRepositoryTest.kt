package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.model.MovementDraft
import com.atmosferast.orbita.domain.model.MovementKind
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
import org.junit.Assert.assertTrue
import org.junit.Test

// The report of a period against the API (plan 07 of atm-orbita-api).

private class ReportTokens : TokenStore {
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

private fun line(name: String, kind: String, total: String, percent: String) =
    """{"category":{"id":"$name","name":"$name","kind":"$kind","color":"#0E7490"},"total":"$total","percent":"$percent"}"""

private val september = """{"data":{"from":"2026-09-01","to":"2026-09-30","currencies":[
    {"currency":"PEN","income_total":"3500.00","expense_total":"1412.40","balance":"2087.60",
     "expenses":[${line("Vivienda", "expense", "800.00", "37.2")},${line("Alimentación", "expense", "612.40", "28.5")}],
     "incomes":[${line("Sueldo", "income", "3500.00", "83.3")}]},
    {"currency":"USD","income_total":"0.00","expense_total":"12.00","balance":"-12.00",
     "expenses":[${line("Ocio", "expense", "12.00", "100.0")}],"incomes":[]}]}}"""

class RemoteReportsRepositoryTest {

    @Test
    fun `a report per currency, read again after a movement changes`() = runTest {
        var reads = 0
        var query = ""
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("reports/summary") -> {
                    reads++
                    query = request.url.encodedQuery
                    respond(september, HttpStatusCode.OK, json)
                }
                request.method == HttpMethod.Post -> respond("{}", HttpStatusCode.Created, json)
                else -> respond("""{"data":{"accounts":[],"savings":{}}}""", HttpStatusCode.OK, json)
            }
        }
        val session = ApiSession(createApiClient("http://api.test/api/v1", engine, "test"), ReportTokens())
        val entries = RemoteEntriesRepository(session, RemoteAccountsRepository(session))
        val reports = RemoteReportsRepository(session, entries)
        val period = DatePeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))

        val list = reports.observeReport(period).first()
        assertEquals(listOf("PEN", "USD"), list.map { it.currency })
        // The app adds its totals up from the ranking lines, which are always complete.
        assertEquals(BigDecimal("1412.40"), list[0].expenseTotal)
        assertEquals(BigDecimal("2087.60"), list[0].balance)
        assertEquals(listOf("Vivienda", "Alimentación"), list[0].expenses.map { it.category.name })
        assertEquals(MovementKind.INCOME, list[0].incomes.single().category.kind)
        assertTrue(query, query.contains("from=2026-09-01") && query.contains("to=2026-09-30"))

        entries.createMovement(MovementDraft(MovementKind.EXPENSE, BigDecimal("5"), "cash", "food", "Café"))
        reports.observeReport(period).first()
        assertEquals(2, reads)
    }
}
