package com.atmosferast.orbita.data.demo

import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.AccountType
import com.atmosferast.orbita.domain.model.Category
import com.atmosferast.orbita.domain.model.CategoryTotal
import com.atmosferast.orbita.domain.model.CreditCard
import com.atmosferast.orbita.domain.model.CreditPurchase
import com.atmosferast.orbita.domain.model.CurrencyReport
import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.model.Entry
import com.atmosferast.orbita.domain.model.Movement
import com.atmosferast.orbita.domain.model.MovementKind
import com.atmosferast.orbita.domain.model.Transfer
import com.atmosferast.orbita.domain.model.UserSettings
import java.math.BigDecimal
import java.time.LocalDate

// Rows of the in-memory demo "database". They mirror the tables of docs/03: what is stored is
// the initial balance and the ids, and everything shown is computed from them.

internal data class AccountRecord(
    val id: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    val initialBalance: BigDecimal = BigDecimal.ZERO,
    val includeInSavings: Boolean = true,
    val archived: Boolean = false,
)

internal data class CategoryRecord(val category: Category, val archived: Boolean = false)

/** [order] breaks ties between entries of the same day: the one created last goes first. */
internal data class MovementRecord(
    val id: String,
    val description: String,
    val categoryId: String,
    val accountId: String,
    val date: LocalDate,
    val amount: BigDecimal,
    val order: Long,
)

internal data class TransferRecord(
    val id: String,
    val note: String,
    val fromId: String,
    val toId: String,
    val date: LocalDate,
    val fromAmount: BigDecimal,
    val toAmount: BigDecimal,
    val exchangeRate: BigDecimal?,
    val order: Long,
)

internal data class CardRecord(val card: CreditCard, val archived: Boolean = false)

internal data class PurchaseRecord(
    val id: String,
    val cardId: String,
    val description: String,
    val categoryId: String,
    val purchaseDate: LocalDate,
    val dueDate: LocalDate,
    val amount: BigDecimal,
    val currency: String,
    val paid: Boolean = false,
    /** The expense created when it was paid; deleting that expense undoes the payment. */
    val paymentMovementId: String? = null,
)

internal data class DemoState(
    val accounts: List<AccountRecord>,
    val categories: List<CategoryRecord>,
    val movements: List<MovementRecord>,
    val transfers: List<TransferRecord>,
    val cards: List<CardRecord>,
    val purchases: List<PurchaseRecord>,
    val settings: UserSettings,
    val nextOrder: Long,
) {
    private fun category(id: String): Category = categories.first { it.category.id == id }.category

    /** Initial balance + incomes − expenses + transfers in − transfers out. */
    private fun balanceOf(record: AccountRecord): BigDecimal {
        val moved = movements.filter { it.accountId == record.id }.fold(BigDecimal.ZERO) { acc, movement ->
            if (category(movement.categoryId).kind == MovementKind.INCOME) acc + movement.amount
            else acc - movement.amount
        }
        val received = transfers.filter { it.toId == record.id }
            .fold(BigDecimal.ZERO) { acc, transfer -> acc + transfer.toAmount }
        val sent = transfers.filter { it.fromId == record.id }
            .fold(BigDecimal.ZERO) { acc, transfer -> acc + transfer.fromAmount }
        return record.initialBalance + moved + received - sent
    }

    private fun AccountRecord.toAccount() =
        Account(id, name, type, currency, balanceOf(this), includeInSavings)

    /** Any account, archived ones too: old entries still point to them. */
    fun account(id: String): Account = accounts.first { it.id == id }.toAccount()

    val activeAccounts: List<Account>
        get() = accounts.filterNot { it.archived }.map { it.toAccount() }

    val activeCategories: List<Category>
        get() = categories.filterNot { it.archived }.map { it.category }

    val activeCards: List<CreditCard>
        get() = cards.filterNot { it.archived }.map { it.card }

    /** Movements and transfers mixed, newest first. */
    val entries: List<Entry>
        get() {
            val byId = accounts.associate { it.id to it.toAccount() }
            val purchaseByPayment = purchases
                .filter { it.paymentMovementId != null }
                .associate { it.paymentMovementId to it.id }
            val all = movements.map { record ->
                record.order to Movement(
                    id = record.id,
                    description = record.description,
                    category = category(record.categoryId),
                    account = byId.getValue(record.accountId),
                    date = record.date,
                    amount = record.amount,
                    creditPurchaseId = purchaseByPayment[record.id],
                )
            } + transfers.map { record ->
                record.order to Transfer(
                    id = record.id,
                    note = record.note,
                    from = byId.getValue(record.fromId),
                    to = byId.getValue(record.toId),
                    date = record.date,
                    fromAmount = record.fromAmount,
                    toAmount = record.toAmount,
                    exchangeRate = record.exchangeRate,
                )
            }
            return all
                .sortedWith(
                    compareByDescending<Pair<Long, Entry>> { it.second.date }
                        .thenByDescending { it.first },
                )
                .map { it.second }
        }

    val pendingPurchases: List<CreditPurchase>
        get() = purchases.filterNot { it.paid }.map { record ->
            CreditPurchase(
                id = record.id,
                card = cards.first { it.card.id == record.cardId }.card,
                description = record.description,
                category = category(record.categoryId),
                purchaseDate = record.purchaseDate,
                dueDate = record.dueDate,
                amount = record.amount,
                currency = record.currency,
            )
        }

    /**
     * Same result as report_period_summary + report_by_category (docs/03): movements only, by
     * currency of their account, the main currency first.
     */
    fun report(period: DatePeriod): List<CurrencyReport> {
        val currencyOf = accounts.associate { it.id to it.currency }
        return movements
            .filter { it.date in period }
            .groupBy { currencyOf.getValue(it.accountId) }
            .map { (currency, rows) ->
                fun totals(kind: MovementKind) = rows
                    .map { category(it.categoryId) to it.amount }
                    .filter { it.first.kind == kind }
                    .groupBy({ it.first }, { it.second })
                    .map { (category, amounts) ->
                        CategoryTotal(category, amounts.fold(BigDecimal.ZERO, BigDecimal::add))
                    }
                    .sortedByDescending { it.total }
                CurrencyReport(currency, totals(MovementKind.INCOME), totals(MovementKind.EXPENSE))
            }
            .sortedWith(compareBy({ it.currency != settings.fx.main }, { it.currency }))
    }
}
