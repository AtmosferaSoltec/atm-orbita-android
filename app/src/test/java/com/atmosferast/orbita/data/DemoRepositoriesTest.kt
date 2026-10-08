package com.atmosferast.orbita.data

import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.core.USD
import com.atmosferast.orbita.data.demo.DemoAccountsRepository
import com.atmosferast.orbita.data.demo.DemoCategoriesRepository
import com.atmosferast.orbita.data.demo.DemoCreditRepository
import com.atmosferast.orbita.data.demo.DemoSettingsRepository
import com.atmosferast.orbita.domain.model.CategoryDraft
import com.atmosferast.orbita.domain.model.CreditCardDraft
import com.atmosferast.orbita.domain.model.debtIn
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
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
    private val categories = DemoCategoriesRepository(store)
    private val settings = DemoSettingsRepository(store)

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
    fun `deleting the expense of a payment leaves the purchase pending again`() = runTest {
        credit.pay(CreditPayment("p1", "debit", BigDecimal("240.00"), dates.today()))
        val expense = entries.observeRecentEntries(1).first().single() as Movement
        // The movement knows it is the payment of a purchase; an ordinary one does not.
        assertEquals("p1", expense.creditPurchaseId)
        assertNull(SampleData.lunch.creditPurchaseId)

        entries.deleteMovement(expense.id)

        assertEquals(BigDecimal("1245.80"), balanceOf("debit"))
        assertEquals(BigDecimal("30.50"), octoberExpenses())
        val pending = credit.observePendingPurchases().first()
        assertEquals(4, pending.size)
        assertTrue(pending.any { it.id == "p1" })
        // And it can be paid again.
        credit.pay(CreditPayment("p1", "debit", BigDecimal("240.00"), dates.today()))
        assertEquals(BigDecimal("1005.80"), balanceOf("debit"))
    }

    @Test
    fun `a purchase cannot be paid twice`() = runTest {
        credit.pay(CreditPayment("p1", "debit", BigDecimal("240.00"), dates.today()))

        assertEquals(
            DataError.PURCHASE_ALREADY_PAID,
            failure { credit.pay(CreditPayment("p1", "debit", BigDecimal("240.00"), dates.today())) },
        )
        assertEquals(BigDecimal("1005.80"), balanceOf("debit"))
    }

    @Test
    fun `editing or deleting a pending purchase only changes the debt of its card`() = runTest {
        // Pasajes, S/ 240.00 on Visa Clásica, due on 5 Oct: already bought on 10 Sep.
        val draft = CreditPurchaseDraft("visa", BigDecimal("250.00"), "transport", "Pasajes", SampleData.today)
        credit.updatePurchase("p1", draft)

        val visa = credit.observePendingPurchases().first().filter { it.card.id == "visa" }
        assertEquals(BigDecimal("346.00"), visa.debtIn(PEN))
        assertEquals(BigDecimal("1245.80"), balanceOf("debit"))
        assertEquals(BigDecimal("30.50"), octoberExpenses())

        // Cena en restaurante, S/ 96.00
        credit.deletePurchase("p2")
        credit.deletePurchase("p2")

        val pending = credit.observePendingPurchases().first()
        assertEquals(3, pending.size)
        assertEquals(BigDecimal("439.90"), pending.debtIn(PEN))
        assertEquals(BigDecimal("1245.80"), balanceOf("debit"))
    }

    @Test
    fun `a purchase moved to another card takes its currency, and a paid one is not touched`() = runTest {
        credit.createCard(CreditCardDraft("Visa Dólares", USD))
        val dollars = credit.observeCards().first().first { it.name == "Visa Dólares" }
        // Audífonos, S/ 189.90 on Mastercard Oro
        credit.updatePurchase(
            "p3",
            CreditPurchaseDraft(dollars.id, BigDecimal("60.00"), "leisure", "Audífonos", SampleData.today),
        )
        val moved = credit.observePendingPurchases().first().first { it.id == "p3" }
        assertEquals(USD, moved.currency)
        assertEquals(dollars.id, moved.card.id)

        credit.pay(CreditPayment("p1", "debit", BigDecimal("240.00"), dates.today()))
        val draft = CreditPurchaseDraft("visa", BigDecimal("1.00"), "transport", "Pasajes", SampleData.today)
        assertEquals(DataError.PURCHASE_ALREADY_PAID, failure { credit.updatePurchase("p1", draft) })
        assertEquals(DataError.PURCHASE_ALREADY_PAID, failure { credit.deletePurchase("p1") })
        assertEquals(BigDecimal("1005.80"), balanceOf("debit"))
    }

    @Test
    fun `an overdue purchase can be edited, but its due date is never before it was bought`() {
        val bought = SampleData.today.minusDays(40)
        val overdue = CreditPurchaseDraft("visa", BigDecimal("10"), "food", "", SampleData.today.minusDays(5))

        assertEquals(ValidationError.DUE_DATE_PAST, overdue.validate(SampleData.today))
        assertNull(overdue.validateEdit(bought))
        assertEquals(
            ValidationError.DUE_DATE_PAST,
            overdue.copy(dueDate = bought.minusDays(1)).validateEdit(bought),
        )
    }

    @Test
    fun `a card with pending purchases cannot be archived`() = runTest {
        assertEquals(DataError.CARD_HAS_PENDING_PURCHASES, failure { credit.archiveCard("visa") })
        assertTrue(credit.observeCards().first().any { it.id == "visa" })

        // Once nothing is owed on it (one paid, one deleted), it can.
        credit.pay(CreditPayment("p1", "debit", BigDecimal("240.00"), dates.today()))
        credit.deletePurchase("p2")
        credit.archiveCard("visa")
        assertTrue(credit.observeCards().first().none { it.id == "visa" })

        // Undoing the payment brings the card back: no debt is ever out of sight.
        val payment = entries.observeRecentEntries(1).first().single()
        entries.deleteMovement(payment.id)
        assertTrue(credit.observeCards().first().any { it.id == "visa" })
        assertTrue(credit.observePendingPurchases().first().any { it.id == "p1" })
    }

    @Test
    fun `a category name is unique within its kind, whatever its case or accents`() = runTest {
        suspend fun create(name: String, kind: MovementKind = MovementKind.EXPENSE) =
            categories.create(CategoryDraft(name, kind, "#0E7490"))

        for (name in listOf("alimentación", "alimentacion", "ALIMENTACIÓN", " Alimentación ")) {
            assertEquals(name, DataError.NAME_TAKEN, failure { create(name) })
        }
        // "Otros" exists as an expense; as an income it is another category.
        create("Otros", MovementKind.INCOME)
        // It is shown as it was typed.
        create("MASCOTAS  y  más")
        assertTrue(categories.observeCategories().first().any { it.name == "MASCOTAS y más" })

        // Renaming to its own name with other case is not a clash; to another's, it is.
        categories.update("leisure", CategoryDraft("OCIO", MovementKind.EXPENSE, "#BE185D"))
        assertEquals("OCIO", categories.observeCategories().first().first { it.id == "leisure" }.name)
        assertEquals(
            DataError.NAME_TAKEN,
            failure { categories.update("health", CategoryDraft("ocio", MovementKind.EXPENSE, "#7C3AED")) },
        )

        // An archived category frees its name.
        categories.archive("leisure")
        create("Ocio")
    }

    @Test
    fun `without an exchange rate nothing can be in another currency`() = runTest {
        // A new user: the pair of currencies is there, the rate is not.
        store.write { it.copy(settings = it.settings.copy(fx = it.settings.fx.copy(rate = null))) }
        val dollars = AccountDraft("Ahorro USD", AccountType.SAVINGS, USD, BigDecimal.ZERO, true)

        assertEquals(DataError.FX_NOT_CONFIGURED, failure { accounts.create(dollars) })
        assertEquals(DataError.FX_NOT_CONFIGURED, failure { credit.createCard(CreditCardDraft("Visa", USD)) })
        assertEquals(DataError.FX_NOT_CONFIGURED, failure { settings.setDisplayCurrency(USD) })
        // The main currency always works.
        accounts.create(dollars.copy(name = "Ahorro", currency = PEN))

        // A pair is only saved together with a rate above zero.
        val pair = SampleData.fx
        assertEquals(DataError.FX_NOT_CONFIGURED, failure { settings.setFx(pair.copy(rate = null)) })
        assertEquals(DataError.FX_NOT_CONFIGURED, failure { settings.setFx(pair.copy(rate = BigDecimal.ZERO)) })
        settings.setFx(pair)
        accounts.create(dollars)
        settings.setDisplayCurrency(USD)
    }

    /** The reason [action] failed for; fails the test if it did not. */
    private suspend fun failure(action: suspend () -> Unit): DataError? = try {
        action()
        throw AssertionError("Expected the action to fail")
    } catch (e: DataException) {
        e.error
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
