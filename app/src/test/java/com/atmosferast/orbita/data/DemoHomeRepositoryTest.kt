package com.atmosferast.orbita.data

import com.atmosferast.orbita.data.demo.DemoAccountsRepository
import com.atmosferast.orbita.data.demo.DemoCreditRepository
import com.atmosferast.orbita.data.demo.DemoDateProvider
import com.atmosferast.orbita.data.demo.DemoEntriesRepository
import com.atmosferast.orbita.data.demo.DemoHomeRepository
import com.atmosferast.orbita.data.demo.DemoReportsRepository
import com.atmosferast.orbita.data.demo.DemoSettingsRepository
import com.atmosferast.orbita.data.demo.DemoStore
import com.atmosferast.orbita.domain.model.Movement
import com.atmosferast.orbita.domain.model.Transfer
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Inicio in the demo mode: what GET /home gives for the sample data (plan 08 of atm-orbita-api).
class DemoHomeRepositoryTest {

    @Test
    fun `Inicio of the sample`() = runTest {
        val store = DemoStore()
        val dates = DemoDateProvider()
        val home = DemoHomeRepository(
            DemoAccountsRepository(store),
            DemoSettingsRepository(store),
            DemoEntriesRepository(store, dates),
            DemoReportsRepository(store),
            DemoCreditRepository(store, dates),
            dates,
        ).observeHome().first()

        assertEquals(mapOf("PEN" to BigDecimal("7166.30"), "USD" to BigDecimal("2239.47")), home.savings)
        assertEquals(4, home.includedAccounts)
        assertEquals(5, home.activeAccounts)
        assertEquals(BigDecimal("3500.00"), home.monthIncome)
        assertEquals(BigDecimal("30.50"), home.monthExpense)
        assertEquals(
            listOf("Almuerzo", "Taxi al trabajo", "Sueldo", "Cambio de dólares"),
            home.recentEntries.map { (it as? Movement)?.description ?: (it as Transfer).note },
        )
        val credit = home.credit!!
        assertEquals(4, credit.pendingCount)
        assertEquals(LocalDate.of(2026, 10, 5), credit.nextDueDate)
        assertTrue(credit.hasUrgent)
    }
}
