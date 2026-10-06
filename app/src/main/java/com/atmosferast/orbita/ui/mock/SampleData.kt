package com.atmosferast.orbita.ui.mock

import androidx.compose.ui.graphics.Color
import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.core.USD
import com.atmosferast.orbita.ui.theme.CategoryFood
import com.atmosferast.orbita.ui.theme.CategoryFreelance
import com.atmosferast.orbita.ui.theme.CategoryHealth
import com.atmosferast.orbita.ui.theme.CategoryHousing
import com.atmosferast.orbita.ui.theme.CategoryLeisure
import com.atmosferast.orbita.ui.theme.CategoryOther
import com.atmosferast.orbita.ui.theme.CategorySalary
import com.atmosferast.orbita.ui.theme.CategoryTransport
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Mockup-only models and sample data (docs/03, section 7). They are replaced by the
// domain models and repositories when each phase is implemented.

enum class AccountType { CASH, DEBIT, SAVINGS, OTHER }

enum class MovementKind { EXPENSE, INCOME }

data class MockAccount(
    val id: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    val balance: BigDecimal,
    val includeInSavings: Boolean = true,
)

data class MockCategory(
    val name: String,
    val kind: MovementKind,
    val color: Color,
)

sealed interface MockEntry {
    val date: LocalDate
}

data class MockMovement(
    val description: String,
    val category: MockCategory,
    val account: MockAccount,
    override val date: LocalDate,
    val amount: BigDecimal,
) : MockEntry {
    val kind: MovementKind get() = category.kind
}

data class MockTransfer(
    val note: String,
    val from: MockAccount,
    val to: MockAccount,
    override val date: LocalDate,
    val fromAmount: BigDecimal,
    val toAmount: BigDecimal,
) : MockEntry

/** A credit card: a debt account that groups pending purchases. New purchases use [currency]. */
data class MockCreditCard(
    val id: String,
    val name: String,
    val currency: String = PEN,
)

data class MockCreditPurchase(
    val id: String,
    val card: MockCreditCard,
    val description: String,
    val category: MockCategory,
    val purchaseDate: LocalDate,
    val dueDate: LocalDate,
    val amount: BigDecimal,
    val currency: String,
) {
    val daysUntilDue: Long get() = ChronoUnit.DAYS.between(SampleData.today, dueDate)
    val isUrgent: Boolean get() = daysUntilDue <= 3
}

data class MockCategoryTotal(
    val category: MockCategory,
    val total: BigDecimal,
)

object SampleData {
    val today: LocalDate = LocalDate.of(2026, 10, 2)

    /** Manual rate: 1 US$ = S/ 3.20 */
    val usdToPen = BigDecimal("3.20")

    const val userEmail = "ana.torres@correo.com"

    private val cash = MockAccount("cash", "Efectivo", AccountType.CASH, PEN, BigDecimal("320.50"))
    private val debit =
        MockAccount("debit", "Débito principal", AccountType.DEBIT, PEN, BigDecimal("1245.80"))
    private val savings =
        MockAccount("savings", "Ahorros", AccountType.SAVINGS, PEN, BigDecimal("4800.00"))
    private val dollars =
        MockAccount("usd", "Cuenta Dólares", AccountType.SAVINGS, USD, BigDecimal("250.00"))
    private val wallet = MockAccount(
        "wallet", "Billetera digital", AccountType.OTHER, PEN, BigDecimal("85.00"),
        includeInSavings = false,
    )

    val accounts = listOf(dollars, cash, debit, savings, wallet)

    val debitAccount get() = debit
    val dollarAccount get() = dollars

    private val food = MockCategory("Alimentación", MovementKind.EXPENSE, CategoryFood)
    private val transport = MockCategory("Transporte", MovementKind.EXPENSE, CategoryTransport)
    private val housing = MockCategory("Vivienda", MovementKind.EXPENSE, CategoryHousing)
    private val health = MockCategory("Salud", MovementKind.EXPENSE, CategoryHealth)
    private val leisure = MockCategory("Ocio", MovementKind.EXPENSE, CategoryLeisure)
    private val other = MockCategory("Otros", MovementKind.EXPENSE, CategoryOther)
    private val salary = MockCategory("Sueldo", MovementKind.INCOME, CategorySalary)
    private val freelance = MockCategory("Freelance", MovementKind.INCOME, CategoryFreelance)
    private val otherIncome = MockCategory("Otros ingresos", MovementKind.INCOME, CategoryOther)

    val expenseCategories = listOf(food, transport, housing, health, leisure, other)
    val incomeCategories = listOf(salary, freelance, otherIncome)

    fun categoriesOf(kind: MovementKind) =
        if (kind == MovementKind.EXPENSE) expenseCategories else incomeCategories

    private fun oct(day: Int) = LocalDate.of(2026, 10, day)
    private fun sep(day: Int) = LocalDate.of(2026, 9, day)

    val lunch = MockMovement("Almuerzo", food, cash, oct(2), BigDecimal("18.50"))

    val dollarExchange = MockTransfer(
        "Cambio de dólares", dollars, debit, oct(1), BigDecimal("20.00"), BigDecimal("64.00"),
    )

    /** Movements and transfers mixed, newest first. */
    val entries: List<MockEntry> = listOf(
        lunch,
        MockMovement("Taxi al trabajo", transport, debit, oct(2), BigDecimal("12.00")),
        MockMovement("Sueldo", salary, debit, oct(1), BigDecimal("3500.00")),
        dollarExchange,
        MockMovement("Alquiler", housing, debit, sep(30), BigDecimal("800.00")),
        MockMovement("Supermercado", food, debit, sep(28), BigDecimal("152.40")),
        MockMovement("Diseño de logo", freelance, debit, sep(27), BigDecimal("600.00")),
        MockMovement("Cine", leisure, cash, sep(26), BigDecimal("45.00")),
        MockMovement("Farmacia", health, cash, sep(24), BigDecimal("38.00")),
        MockTransfer(
            "Ahorro del mes", debit, savings, sep(22), BigDecimal("500.00"), BigDecimal("500.00"),
        ),
    )

    val recentEntries get() = entries.take(4)

    // October 2026 (current month)
    val monthIncome = BigDecimal("3500.00")
    val monthExpense = BigDecimal("30.50")

    // September 2026 report
    val reportMonth: LocalDate = LocalDate.of(2026, 9, 1)
    val reportExpenses = listOf(
        MockCategoryTotal(housing, BigDecimal("800.00")),
        MockCategoryTotal(food, BigDecimal("612.40")),
        MockCategoryTotal(transport, BigDecimal("215.20")),
        MockCategoryTotal(leisure, BigDecimal("190.00")),
        MockCategoryTotal(other, BigDecimal("183.00")),
        MockCategoryTotal(health, BigDecimal("148.00")),
    )
    val reportIncomes = listOf(
        MockCategoryTotal(salary, BigDecimal("3500.00")),
        MockCategoryTotal(freelance, BigDecimal("600.00")),
        MockCategoryTotal(otherIncome, BigDecimal("100.00")),
    )

    // October 2026 report (current month), consistent with [entries]
    val currentMonth: LocalDate = today.withDayOfMonth(1)
    private val currentExpenses = listOf(
        MockCategoryTotal(food, BigDecimal("18.50")),
        MockCategoryTotal(transport, BigDecimal("12.00")),
    )
    private val currentIncomes = listOf(MockCategoryTotal(salary, BigDecimal("3500.00")))

    /** Expenses and incomes by category for [month], or null when the sample has no data. */
    fun reportFor(month: LocalDate): Pair<List<MockCategoryTotal>, List<MockCategoryTotal>>? =
        when (month.withDayOfMonth(1)) {
            reportMonth -> reportExpenses to reportIncomes
            currentMonth -> currentExpenses to currentIncomes
            else -> null
        }

    private val visa = MockCreditCard("visa", "Visa Clásica")
    private val mastercard = MockCreditCard("mastercard", "Mastercard Oro")

    val creditCards = listOf(visa, mastercard)

    val creditPurchases = listOf(
        MockCreditPurchase(
            "p1", visa, "Pasajes", transport, sep(10), oct(5), BigDecimal("240.00"), PEN,
        ),
        MockCreditPurchase(
            "p2", visa, "Cena en restaurante", food, sep(18), oct(15), BigDecimal("96.00"), PEN,
        ),
        MockCreditPurchase(
            "p3", mastercard, "Audífonos", leisure, sep(20), oct(15), BigDecimal("189.90"), PEN,
        ),
        MockCreditPurchase(
            "p4", mastercard, "Suscripción de software", other, sep(25), oct(15),
            BigDecimal("12.00"), USD,
        ),
    )
}

/** Currencies owed, [main] first. */
fun List<MockCreditPurchase>.debtCurrencies(main: String): List<String> =
    map { it.currency }.distinct().sortedBy { it != main }

/** Debt in [currency]; currencies are never mixed. */
fun List<MockCreditPurchase>.debtIn(currency: String): BigDecimal =
    filter { it.currency == currency }.fold(BigDecimal.ZERO) { acc, purchase -> acc + purchase.amount }

fun List<MockCategoryTotal>.total(): BigDecimal = fold(BigDecimal.ZERO) { acc, item -> acc + item.total }

/** Share of [part] over [whole] as a percentage with one decimal (HALF_UP). */
fun percentOf(part: BigDecimal, whole: BigDecimal): BigDecimal =
    if (whole.signum() == 0) BigDecimal.ZERO
    else part.multiply(BigDecimal(100)).divide(whole, 1, RoundingMode.HALF_UP)

/**
 * The user's two currencies (Ajustes) and the manual rate between them:
 * [rate] = units of [main] per 1 unit of [secondary] (1 US$ = S/ 3.20).
 */
data class MockFx(
    val main: String = PEN,
    val secondary: String = USD,
    val rate: BigDecimal = SampleData.usdToPen,
) {
    /** The other currency of the pair. */
    fun other(currency: String): String = if (currency == main) secondary else main

    /** Same pair the other way round, with the inverse rate. */
    private fun swapped() = MockFx(
        main = secondary,
        secondary = main,
        rate = BigDecimal.ONE.divide(rate, 6, RoundingMode.HALF_UP),
    )

    /**
     * New main currency. The two currencies are never the same: picking the secondary one swaps
     * them. Any other change leaves no known rate, so it restarts at 1 for the user to type.
     */
    fun withMain(code: String): MockFx = when (code) {
        main -> this
        secondary -> swapped()
        else -> copy(main = code, rate = BigDecimal.ONE)
    }

    /** New secondary currency; same rules as [withMain]. */
    fun withSecondary(code: String): MockFx = when (code) {
        secondary -> this
        main -> swapped()
        else -> copy(secondary = code, rate = BigDecimal.ONE)
    }

    /** Unrounded; null when [from] or [to] is outside the pair (there is no rate for it). */
    private fun raw(amount: BigDecimal, from: String, to: String): BigDecimal? = when {
        from == to -> amount
        from == secondary && to == main -> amount.multiply(rate)
        from == main && to == secondary -> amount.divide(rate, 10, RoundingMode.HALF_UP)
        else -> null
    }

    /** Rounds once, at the end. */
    fun convert(amount: BigDecimal, from: String, to: String): BigDecimal? =
        raw(amount, from, to)?.setScale(2, RoundingMode.HALF_UP)

    /** Units of [to] per 1 unit of [from], or null outside the pair. */
    fun rateBetween(from: String, to: String): BigDecimal? =
        raw(BigDecimal.ONE, from, to)?.setScale(6, RoundingMode.HALF_UP)

    /**
     * Savings total of the included accounts, shown in [currency]. Accounts in a currency
     * outside the pair are left out: there is no rate to convert them.
     */
    fun savingsTotal(accounts: List<MockAccount>, currency: String): BigDecimal =
        accounts.filter { it.includeInSavings }
            .fold(BigDecimal.ZERO) { acc, account ->
                acc + (raw(account.balance, account.currency, currency) ?: BigDecimal.ZERO)
            }
            .setScale(2, RoundingMode.HALF_UP)
}
