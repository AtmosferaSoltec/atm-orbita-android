package com.atmosferast.orbita.ui.feature.home

import androidx.lifecycle.viewModelScope
import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.CreditPurchase
import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.model.Entry
import com.atmosferast.orbita.domain.model.FxPair
import com.atmosferast.orbita.domain.repository.AccountsRepository
import com.atmosferast.orbita.domain.repository.CreditRepository
import com.atmosferast.orbita.domain.repository.DateProvider
import com.atmosferast.orbita.domain.repository.EntriesRepository
import com.atmosferast.orbita.domain.repository.ReportsRepository
import com.atmosferast.orbita.domain.repository.SettingsRepository
import com.atmosferast.orbita.ui.common.ActionViewModel
import com.atmosferast.orbita.ui.common.Load
import com.atmosferast.orbita.ui.common.MessageBus
import com.atmosferast.orbita.ui.common.WhileScreenVisible
import com.atmosferast.orbita.ui.common.asLoad
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** How many movements Inicio lists under "Últimos movimientos". */
private const val RECENT_ENTRIES = 4

data class HomeUiState(
    val accounts: List<Account>,
    val fx: FxPair,
    val displayCurrency: String,
    /** Totals of the current calendar month in the main currency; transfers do not count. */
    val monthIncome: BigDecimal,
    val monthExpense: BigDecimal,
    val recentEntries: List<Entry>,
    val pendingPurchases: List<CreditPurchase>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    accounts: AccountsRepository,
    private val settings: SettingsRepository,
    entries: EntriesRepository,
    reports: ReportsRepository,
    credit: CreditRepository,
    dates: DateProvider,
    messages: MessageBus,
) : ActionViewModel(messages) {

    private val attempt = MutableStateFlow(0)

    val state: StateFlow<Load<HomeUiState>> = attempt
        .flatMapLatest {
            combine(
                accounts.observeAccounts(),
                settings.observeSettings(),
                entries.observeRecentEntries(RECENT_ENTRIES),
                reports.observeReport(DatePeriod.ofMonth(dates.today())),
                credit.observePendingPurchases(),
            ) { accountList, userSettings, recent, monthReports, purchases ->
                val month = monthReports.firstOrNull { it.currency == userSettings.fx.main }
                HomeUiState(
                    accounts = accountList,
                    fx = userSettings.fx,
                    displayCurrency = userSettings.displayCurrency,
                    monthIncome = month?.incomeTotal ?: BigDecimal.ZERO,
                    monthExpense = month?.expenseTotal ?: BigDecimal.ZERO,
                    recentEntries = recent,
                    pendingPurchases = purchases,
                )
            }.asLoad()
        }
        .stateIn(viewModelScope, WhileScreenVisible, Load.Loading)

    fun retry() = attempt.update { it + 1 }

    fun setDisplayCurrency(currency: String) = act { settings.setDisplayCurrency(currency) }
}
