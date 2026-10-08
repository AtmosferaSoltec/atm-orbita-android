package com.atmosferast.orbita.data

import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.data.demo.DemoAccountsRepository
import com.atmosferast.orbita.data.demo.DemoCreditRepository
import com.atmosferast.orbita.data.demo.DemoDateProvider
import com.atmosferast.orbita.data.demo.DemoEntriesRepository
import com.atmosferast.orbita.data.demo.DemoReportsRepository
import com.atmosferast.orbita.data.demo.DemoStore
import com.atmosferast.orbita.data.demo.SampleData
import com.atmosferast.orbita.domain.model.AccountDraft
import com.atmosferast.orbita.domain.model.AccountType
import com.atmosferast.orbita.domain.model.CreditPayment
import com.atmosferast.orbita.domain.model.CreditPurchaseDraft
import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.model.Movement
import com.atmosferast.orbita.domain.model.MovementDraft
import com.atmosferast.orbita.domain.model.MovementKind
import com.atmosferast.orbita.domain.model.TransferDraft
import com.atmosferast.orbita.domain.model.ValidationError
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The demo repositories must behave as the backend is specified to (docs/03 and docs/06), so
 * the screens built on them keep working when the API takes their place.
 */
class DemoRepositoriesTest {

    private val store = DemoStore()
    private val dates = DemoDateProvider()
    private val accounts = DemoAccountsRepository(store)
    private val entries = DemoEntriesRepository(store, dates)
    private val reports = DemoReportsRepository(store)
    private val credit = DemoCreditRepository(store, dates)

    private suspend fun balanceOf(id: String) = accounts.observeAccounts().first().first { it.id == id }.balance

    private suspend fun octoberExpenses() =
        reports.observeReport(DatePeriod.ofMonth(dates.today())).first()
            .first { it.currency == PEN }.expenseTotal

    @Test
    fun `an expense takes exactly its amount out of the account`() = runTest {
        entries.createMovement(
            MovementDraft(MovementKind.EXPENSE, BigDecimal("45.52"), "debit", "food", "Almuerzo"),
        )

        assertEquals(BigDecimal("1200.28"), balanceOf("debit"))
        assertEquals(BigDecimal("76.02"), octoberExpenses())
        // A new movement takes the day it is saved and goes first in the lists.
        val newest = entries.observeRecentEntries(1).first().single() as Movement
        assertEquals("Almuerzo", newest.description)
        assertEquals(dates.today(), newest.date)
    }

    @Test
    fun `deleting a movement gives its amount back`() = runTest {
        entries.deleteMovement("m-lunch")

        assertEquals(BigDecimal("339.00"), balanceOf("cash"))
        assertEquals(BigDecimal("12.00"), octoberExpenses())
    }

    @Test
    fun `a transfer between currencies moves each amount and is neither income nor expense`() = runTest {
        entries.createTransfer(
            TransferDraft(
                fromAccountId = "usd",
                toAccountId = "debit",
                fromAmount = BigDecimal("20.00"),
                toAmount = BigDecimal("63.40"),
                exchangeRate = BigDecimal("3.17"),
                date = dates.today(),
                note = "",
            ),
        )

        assertEquals(BigDecimal("230.00"), balanceOf("usd"))
        assertEquals(BigDecimal("1309.20"), balanceOf("debit"))
        assertEquals(BigDecimal("30.50"), octoberExpenses())
    }

    @Test
    fun `a pending credit purchase changes nothing until it is paid`() = runTest {
        credit.createPurchase(
            CreditPurchaseDraft("visa", BigDecimal("80.00"), "leisure", "Zapatillas", dates.today().plusDays(20)),
        )

        assertEquals(5, credit.observePendingPurchases().first().size)
        assertEquals(BigDecimal("1245.80"), balanceOf("debit"))
        assertEquals(BigDecimal("30.50"), octoberExpenses())
    }

    @Test
    fun `paying a purchase creates the expense with its category and takes it off the pending ones`() = runTest {
        // Pasajes, S/ 240.00, from Débito principal (docs/03, section 7)
        credit.pay(CreditPayment("p1", "debit", BigDecimal("240.00"), dates.today()))

        assertEquals(BigDecimal("1005.80"), balanceOf("debit"))
        assertEquals(BigDecimal("270.50"), octoberExpenses())
        assertTrue(credit.observePendingPurchases().first().none { it.id == "p1" })
        val expense = entries.observeRecentEntries(1).first().single() as Movement
        assertEquals("Pasajes", expense.description)
        assertEquals("Transporte", expense.category.name)
    }

    @Test
    fun `a new account starts on its initial balance and an archived one leaves the list`() = runTest {
        accounts.create(AccountDraft("Yape", AccountType.OTHER, PEN, BigDecimal("50"), includeInSavings = true))
        val created = accounts.observeAccounts().first().first { it.name == "Yape" }
        assertEquals(BigDecimal("50.00"), created.balance)

        accounts.archive(created.id)
        assertTrue(accounts.observeAccounts().first().none { it.name == "Yape" })
    }

    @Test
    fun `drafts say why they cannot be saved`() {
        val today = SampleData.today
        val movement = MovementDraft(MovementKind.EXPENSE, BigDecimal("10"), "cash", "food", "")

        assertNull(movement.validate(today))
        assertEquals(ValidationError.AMOUNT_REQUIRED, movement.copy(amount = BigDecimal.ZERO).validate(today))
        assertEquals(ValidationError.ACCOUNT_REQUIRED, movement.copy(accountId = null).validate(today))
        // There is no data from the future.
        assertEquals(ValidationError.FUTURE_DATE, movement.copy(date = today.plusDays(1)).validate(today))

        val transfer = TransferDraft("usd", "debit", BigDecimal("20"), BigDecimal("64"), null, today, "")
        assertEquals(ValidationError.RATE_REQUIRED, transfer.validate(today, sameCurrency = false))
        assertNull(transfer.validate(today, sameCurrency = true))
        assertEquals(
            ValidationError.SAME_ACCOUNT,
            transfer.copy(toAccountId = "usd").validate(today, sameCurrency = true),
        )
    }
}
