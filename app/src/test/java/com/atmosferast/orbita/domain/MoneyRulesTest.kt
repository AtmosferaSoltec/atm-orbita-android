package com.atmosferast.orbita.domain

import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.core.USD
import com.atmosferast.orbita.core.amountToCents
import com.atmosferast.orbita.core.appendAmountDigits
import com.atmosferast.orbita.core.centsToAmount
import com.atmosferast.orbita.core.formatMoney
import com.atmosferast.orbita.data.demo.SampleData
import com.atmosferast.orbita.data.remote.BigDecimalSerializer
import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.model.percentOf
import java.math.BigDecimal
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The cases every platform must give the same result for (docs/06, "Pruebas unitarias obligatorias"). */
class MoneyRulesTest {

    private val fx = SampleData.fx

    @Test
    fun `decimals add up exactly`() {
        assertEquals(BigDecimal("0.3"), BigDecimal("0.1") + BigDecimal("0.2"))
    }

    @Test
    fun `dollars to soles with the manual rate`() {
        assertEquals(BigDecimal("64.00"), fx.convert(BigDecimal("20.00"), USD, PEN))
    }

    @Test
    fun `soles back to dollars with the inverse of the rate`() {
        assertEquals(BigDecimal("20.00"), fx.convert(BigDecimal("64.00"), PEN, USD))
    }

    @Test
    fun `a currency outside the pair has no rate`() {
        assertNull(fx.convert(BigDecimal("10.00"), "EUR", PEN))
    }

    @Test
    fun `savings total of the sample in soles and in dollars`() {
        assertEquals(BigDecimal("7166.30"), fx.savingsTotal(SampleData.accounts, PEN))
        // 7,166.30 / 3.20 = 2,239.46875, rounded once and half up
        assertEquals(BigDecimal("2239.47"), fx.savingsTotal(SampleData.accounts, USD))
    }

    @Test
    fun `an account left out of savings does not add up`() {
        val onlyWallet = SampleData.accounts.filter { it.id == "wallet" }
        assertEquals(BigDecimal("0.00"), fx.savingsTotal(onlyWallet, PEN))
    }

    @Test
    fun `the sample balances come out of its movements and transfers`() {
        val balances = SampleData.accounts.associate { it.name to it.balance }
        assertEquals(BigDecimal("320.50"), balances["Efectivo"])
        assertEquals(BigDecimal("1245.80"), balances["Débito principal"])
        assertEquals(BigDecimal("4800.00"), balances["Ahorros"])
        assertEquals(BigDecimal("250.00"), balances["Cuenta Dólares"])
        assertEquals(BigDecimal("85.00"), balances["Billetera digital"])
    }

    @Test
    fun `september report of the sample`() {
        val report = SampleData.reportFor(DatePeriod.ofMonth(SampleData.reportMonth)).single()
        assertEquals(PEN, report.currency)
        assertEquals(BigDecimal("4200.00"), report.incomeTotal)
        assertEquals(BigDecimal("2148.60"), report.expenseTotal)
        assertEquals(BigDecimal("2051.40"), report.balance)
        // Largest first: Vivienda, Alimentación, Transporte, Ocio, Otros, Salud
        assertEquals(
            listOf("37.2", "28.5", "10.0", "8.8", "8.5", "6.9"),
            report.expenses.map { percentOf(it.total, report.expenseTotal).toPlainString() },
        )
    }

    @Test
    fun `transfers never count in a report`() {
        val october = SampleData.reportFor(DatePeriod.ofMonth(SampleData.currentMonth)).single()
        assertEquals(BigDecimal("3500.00"), october.incomeTotal)
        assertEquals(BigDecimal("30.50"), october.expenseTotal)
    }

    @Test
    fun `money is shown with symbol, thousands separator and two decimals`() {
        assertEquals("S/ 1,245.80", formatMoney(BigDecimal("1245.8"), PEN))
        assertEquals("US$ 250.00", formatMoney(BigDecimal("250"), USD))
    }

    @Test
    fun `amount keypad enters digits from the right`() {
        var cents = 0L
        for (digit in listOf("4", "5", "5", "2")) cents = appendAmountDigits(cents, digit)
        assertEquals(BigDecimal("45.52"), centsToAmount(cents))
        assertEquals(4552L, amountToCents(BigDecimal("45.52")))
    }

    @Serializable
    private data class Row(
        @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    )

    @Test
    fun `numbers from the backend are read as exact decimals`() {
        // 0.1 + 0.2 is where a Double would already be wrong.
        val row = Json.decodeFromString<Row>("""{"amount": 1245.80}""")
        assertEquals(BigDecimal("1245.80"), row.amount)
        assertEquals("""{"amount":"1245.80"}""", Json.encodeToString(Row.serializer(), row))
    }
}
