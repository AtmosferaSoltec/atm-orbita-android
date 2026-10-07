package com.atmosferast.orbita.data.demo

import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.core.USD
import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.AccountType
import com.atmosferast.orbita.domain.model.Category
import com.atmosferast.orbita.domain.model.CreditCard
import com.atmosferast.orbita.domain.model.CreditPurchase
import com.atmosferast.orbita.domain.model.CurrencyReport
import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.model.Entry
import com.atmosferast.orbita.domain.model.FxPair
import com.atmosferast.orbita.domain.model.Movement
import com.atmosferast.orbita.domain.model.MovementKind
import com.atmosferast.orbita.domain.model.Transfer
import com.atmosferast.orbita.domain.model.UserSettings
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Sample data (docs/03, section 7). It seeds the in-memory demo repositories and feeds the
 * previews and the tests. Everything shown is computed from the rows below, so the totals of
 * the docs come out of them: balances, savings total and the September and October reports.
 */
object SampleData {
    val today: LocalDate = LocalDate.of(2026, 10, 2)
    val currentMonth: LocalDate = today.withDayOfMonth(1)
    val reportMonth: LocalDate = LocalDate.of(2026, 9, 1)

    const val userEmail = "ana.torres@correo.com"

    /** Manual rate: 1 US$ = S/ 3.20 */
    val fx = FxPair(main = PEN, secondary = USD, rate = BigDecimal("3.20"))

    private fun oct(day: Int) = LocalDate.of(2026, 10, day)
    private fun sep(day: Int) = LocalDate.of(2026, 9, day)

    private fun category(id: String, name: String, kind: MovementKind, colorHex: String) =
        CategoryRecord(Category(id, name, kind, colorHex))

    private val categoryRecords = listOf(
        category("food", "Alimentación", MovementKind.EXPENSE, "#0E7490"),
        category("transport", "Transporte", MovementKind.EXPENSE, "#B45309"),
        category("housing", "Vivienda", MovementKind.EXPENSE, "#1D4ED8"),
        category("health", "Salud", MovementKind.EXPENSE, "#7C3AED"),
        category("leisure", "Ocio", MovementKind.EXPENSE, "#BE185D"),
        category("other", "Otros", MovementKind.EXPENSE, "#64748B"),
        category("salary", "Sueldo", MovementKind.INCOME, "#0B7A5A"),
        category("freelance", "Freelance", MovementKind.INCOME, "#0E7490"),
        category("other-income", "Otros ingresos", MovementKind.INCOME, "#64748B"),
    )

    /** The balance each account must end with; its initial balance is worked out from it. */
    private val accountBalances = listOf(
        AccountRecord("usd", "Cuenta Dólares", AccountType.SAVINGS, USD) to "250.00",
        AccountRecord("cash", "Efectivo", AccountType.CASH, PEN) to "320.50",
        AccountRecord("debit", "Débito principal", AccountType.DEBIT, PEN) to "1245.80",
        AccountRecord("savings", "Ahorros", AccountType.SAVINGS, PEN) to "4800.00",
        AccountRecord(
            "wallet", "Billetera digital", AccountType.OTHER, PEN, includeInSavings = false,
        ) to "85.00",
    )

    private class EntrySeed(
        val id: String,
        val text: String,
        val date: LocalDate,
        val amount: String,
        val categoryId: String? = null,
        val accountId: String? = null,
        val fromId: String? = null,
        val toId: String? = null,
        val toAmount: String? = null,
        val rate: String? = null,
    )

    private fun movement(
        id: String, text: String, categoryId: String, accountId: String, date: LocalDate, amount: String,
    ) = EntrySeed(id, text, date, amount, categoryId = categoryId, accountId = accountId)

    // Newest first, as the lists show them. September adds up to the report of docs/03:
    // incomes 4,200.00 and expenses 2,148.60.
    private val entrySeeds = listOf(
        movement("m-lunch", "Almuerzo", "food", "cash", oct(2), "18.50"),
        movement("m-taxi", "Taxi al trabajo", "transport", "debit", oct(2), "12.00"),
        movement("m-salary-oct", "Sueldo", "salary", "debit", oct(1), "3500.00"),
        EntrySeed(
            "t-dollars", "Cambio de dólares", oct(1), "20.00",
            fromId = "usd", toId = "debit", toAmount = "64.00", rate = "3.20",
        ),
        movement("m-rent", "Alquiler", "housing", "debit", sep(30), "800.00"),
        movement("m-market-3", "Supermercado", "food", "debit", sep(28), "152.40"),
        movement("m-logo", "Diseño de logo", "freelance", "debit", sep(27), "600.00"),
        movement("m-cinema", "Cine", "leisure", "cash", sep(26), "45.00"),
        movement("m-pharmacy", "Farmacia", "health", "cash", sep(24), "38.00"),
        EntrySeed(
            "t-savings", "Ahorro del mes", sep(22), "500.00",
            fromId = "debit", toId = "savings", toAmount = "500.00",
        ),
        movement("m-supplies", "Útiles", "other", "cash", sep(20), "60.00"),
        movement("m-restaurant", "Restaurante", "food", "cash", sep(19), "70.00"),
        movement("m-fuel", "Gasolina", "transport", "debit", sep(17), "120.00"),
        movement("m-gift", "Regalo de cumpleaños", "other", "debit", sep(15), "123.00"),
        movement("m-market-2", "Supermercado", "food", "debit", sep(14), "180.00"),
        movement("m-concert", "Concierto", "leisure", "debit", sep(12), "145.00"),
        movement("m-books", "Venta de libros", "other-income", "cash", sep(12), "100.00"),
        movement("m-doctor", "Consulta médica", "health", "debit", sep(9), "110.00"),
        movement("m-bus", "Pasajes de bus", "transport", "cash", sep(8), "95.20"),
        movement("m-market-1", "Mercado", "food", "debit", sep(6), "210.00"),
        movement("m-salary-sep", "Sueldo", "salary", "debit", sep(1), "3500.00"),
    )

    private val cardRecords = listOf(
        CardRecord(CreditCard("visa", "Visa Clásica", PEN)),
        CardRecord(CreditCard("mastercard", "Mastercard Oro", PEN)),
    )

    private val purchaseRecords = listOf(
        PurchaseRecord("p1", "visa", "Pasajes", "transport", sep(10), oct(5), BigDecimal("240.00"), PEN),
        PurchaseRecord(
            "p2", "visa", "Cena en restaurante", "food", sep(18), oct(15), BigDecimal("96.00"), PEN,
        ),
        PurchaseRecord(
            "p3", "mastercard", "Audífonos", "leisure", sep(20), oct(15), BigDecimal("189.90"), PEN,
        ),
        PurchaseRecord(
            "p4", "mastercard", "Suscripción de software", "other", sep(25), oct(15),
            BigDecimal("12.00"), USD,
        ),
    )

    internal val seed: DemoState = run {
        val size = entrySeeds.size
        val movements = entrySeeds.mapIndexedNotNull { index, seed ->
            if (seed.accountId == null || seed.categoryId == null) return@mapIndexedNotNull null
            MovementRecord(
                seed.id, seed.text, seed.categoryId, seed.accountId, seed.date,
                BigDecimal(seed.amount), order = (size - index).toLong(),
            )
        }
        val transfers = entrySeeds.mapIndexedNotNull { index, seed ->
            if (seed.fromId == null || seed.toId == null || seed.toAmount == null) {
                return@mapIndexedNotNull null
            }
            TransferRecord(
                seed.id, seed.text, seed.fromId, seed.toId, seed.date, BigDecimal(seed.amount),
                BigDecimal(seed.toAmount), seed.rate?.let(::BigDecimal),
                order = (size - index).toLong(),
            )
        }
        val withoutInitial = DemoState(
            accounts = accountBalances.map { it.first },
            categories = categoryRecords,
            movements = movements,
            transfers = transfers,
            cards = cardRecords,
            purchases = purchaseRecords,
            settings = UserSettings(fx, displayCurrency = PEN),
            nextOrder = size + 1L,
        )
        // initial balance = balance it must end with − what its entries add up to
        withoutInitial.copy(
            accounts = accountBalances.map { (record, balance) ->
                record.copy(
                    initialBalance = BigDecimal(balance) - withoutInitial.account(record.id).balance,
                )
            },
        )
    }

    val accounts: List<Account> get() = seed.activeAccounts
    val debitAccount: Account get() = seed.account("debit")
    val dollarAccount: Account get() = seed.account("usd")

    val categories: List<Category> get() = seed.activeCategories

    fun categoriesOf(kind: MovementKind): List<Category> = categories.filter { it.kind == kind }

    val entries: List<Entry> get() = seed.entries
    val recentEntries: List<Entry> get() = entries.take(4)
    val lunch: Movement get() = entries.first { it.id == "m-lunch" } as Movement
    val dollarExchange: Transfer get() = entries.first { it.id == "t-dollars" } as Transfer

    /** Reports of [period], one per currency. */
    fun reportFor(period: DatePeriod): List<CurrencyReport> = seed.report(period)

    val creditCards: List<CreditCard> get() = seed.activeCards
    val creditPurchases: List<CreditPurchase> get() = seed.pendingPurchases
}
