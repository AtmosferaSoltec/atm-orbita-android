package com.atmosferast.orbita.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class AccountType { CASH, DEBIT, SAVINGS, OTHER }

enum class MovementKind { EXPENSE, INCOME }

/**
 * A place where there is money. [balance] is never stored: the data source computes it
 * (initial balance + incomes − expenses + transfers in − transfers out).
 */
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    val balance: BigDecimal,
    val includeInSavings: Boolean = true,
)

/** [colorHex] is `#RRGGBB`. */
data class Category(
    val id: String,
    val name: String,
    val kind: MovementKind,
    val colorHex: String,
)

/** A row of the movement lists: an income, an expense or a transfer. */
sealed interface Entry {
    val id: String
    val date: LocalDate
}

/** An income or an expense. It takes the currency of its [account]. */
data class Movement(
    override val id: String,
    val description: String,
    val category: Category,
    val account: Account,
    override val date: LocalDate,
    val amount: BigDecimal,
) : Entry {
    val kind: MovementKind get() = category.kind
}

/**
 * Money moved between two own accounts; neither income nor expense. [exchangeRate] is the units
 * of the currency of [to] per 1 unit of the currency of [from], null when both are the same.
 */
data class Transfer(
    override val id: String,
    val note: String,
    val from: Account,
    val to: Account,
    override val date: LocalDate,
    val fromAmount: BigDecimal,
    val toAmount: BigDecimal,
    val exchangeRate: BigDecimal? = null,
) : Entry

/** A credit card: a debt account that groups pending purchases. New purchases use [currency]. */
data class CreditCard(
    val id: String,
    val name: String,
    val currency: String,
)

/** A purchase still to be paid. It is a reminder, not a movement, until it is paid. */
data class CreditPurchase(
    val id: String,
    val card: CreditCard,
    val description: String,
    val category: Category,
    val purchaseDate: LocalDate,
    val dueDate: LocalDate,
    val amount: BigDecimal,
    val currency: String,
) {
    /** Negative once overdue. */
    fun daysUntilDue(today: LocalDate): Long = ChronoUnit.DAYS.between(today, dueDate)

    /** Due in 3 days or less, or overdue. */
    fun isUrgent(today: LocalDate): Boolean = daysUntilDue(today) <= 3
}

data class CategoryTotal(
    val category: Category,
    val total: BigDecimal,
)

/** Inclusive on both ends. */
data class DatePeriod(val from: LocalDate, val to: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)

    companion object {
        fun ofMonth(month: LocalDate) =
            DatePeriod(month.withDayOfMonth(1), month.withDayOfMonth(month.lengthOfMonth()))

        fun ofYear(year: Int) = DatePeriod(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
    }
}

/**
 * Incomes and expenses of a period in one [currency], by category and largest first. Transfers
 * and pending credit purchases never count. Currencies are never mixed: a period with
 * movements in several currencies gives one report per currency.
 */
data class CurrencyReport(
    val currency: String,
    val incomes: List<CategoryTotal>,
    val expenses: List<CategoryTotal>,
) {
    val incomeTotal: BigDecimal get() = incomes.total()
    val expenseTotal: BigDecimal get() = expenses.total()
    val balance: BigDecimal get() = incomeTotal - expenseTotal
}

/** Preferences of the user (Ajustes). */
data class UserSettings(
    val fx: FxPair,
    /** Currency the savings total is shown in: one of the two of [fx]. */
    val displayCurrency: String,
)

sealed interface SessionState {
    /** The stored session is still being restored. */
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val email: String) : SessionState
}

fun List<CategoryTotal>.total(): BigDecimal = fold(BigDecimal.ZERO) { acc, item -> acc + item.total }

/** Currencies owed, [main] first. */
fun List<CreditPurchase>.debtCurrencies(main: String): List<String> =
    map { it.currency }.distinct().sortedBy { it != main }

/** Debt in [currency]; currencies are never mixed. */
fun List<CreditPurchase>.debtIn(currency: String): BigDecimal =
    filter { it.currency == currency }.fold(BigDecimal.ZERO) { acc, purchase -> acc + purchase.amount }
