package com.atmosferast.orbita.data.demo

import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.CreditPurchase
import com.atmosferast.orbita.domain.model.CreditReminder
import com.atmosferast.orbita.domain.model.CurrencyReport
import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.model.Entry
import com.atmosferast.orbita.domain.model.HomeSummary
import com.atmosferast.orbita.domain.model.UserSettings
import com.atmosferast.orbita.domain.repository.AccountsRepository
import com.atmosferast.orbita.domain.repository.CreditRepository
import com.atmosferast.orbita.domain.repository.DateProvider
import com.atmosferast.orbita.domain.repository.EntriesRepository
import com.atmosferast.orbita.domain.repository.HomeRepository
import com.atmosferast.orbita.domain.repository.ReportsRepository
import com.atmosferast.orbita.domain.repository.SettingsRepository
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** How many entries Inicio lists under "Últimos movimientos". */
const val RECENT_ENTRIES = 4

/**
 * Inicio from the demo repositories: the same pieces the API puts together in GET /home, worked
 * out the same way.
 */
class DemoHomeRepository @Inject constructor(
    private val accounts: AccountsRepository,
    private val settings: SettingsRepository,
    private val entries: EntriesRepository,
    private val reports: ReportsRepository,
    private val credit: CreditRepository,
    private val dates: DateProvider,
) : HomeRepository {

    override fun observeHome(): Flow<HomeSummary> = combine(
        accounts.observeAccounts(),
        settings.observeSettings(),
        entries.observeRecentEntries(RECENT_ENTRIES),
        reports.observeReport(DatePeriod.ofMonth(dates.today())),
        credit.observePendingPurchases(),
    ) { accountList, userSettings, recent, monthReports, purchases ->
        homeOf(accountList, userSettings, recent, monthReports, purchases, dates.today())
    }

    companion object {
        /** Inicio from its pieces, as GET /home composes it. */
        fun homeOf(
            accounts: List<Account>,
            settings: UserSettings,
            recent: List<Entry>,
            monthReports: List<CurrencyReport>,
            purchases: List<CreditPurchase>,
            today: LocalDate,
        ): HomeSummary {
            val fx = settings.fx
            val currencies = if (fx.isConfigured) listOf(fx.main, fx.secondary) else listOf(fx.main)
            val month = monthReports.firstOrNull { it.currency == fx.main }
            return HomeSummary(
                fx = fx,
                displayCurrency = settings.displayCurrency,
                savings = currencies.associateWith { fx.savingsTotal(accounts, it) },
                includedAccounts = accounts.count { it.includeInSavings },
                activeAccounts = accounts.size,
                monthIncome = month?.incomeTotal ?: BigDecimal.ZERO,
                monthExpense = month?.expenseTotal ?: BigDecimal.ZERO,
                recentEntries = recent,
                credit = purchases.takeIf { it.isNotEmpty() }?.let { pending ->
                    CreditReminder(
                        pendingCount = pending.size,
                        nextDueDate = pending.minOf { it.dueDate },
                        hasUrgent = pending.any { it.isUrgent(today) },
                    )
                },
            )
        }
    }
}
