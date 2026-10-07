package com.atmosferast.orbita.ui.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atmosferast.orbita.domain.model.CurrencyReport
import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.repository.DateProvider
import com.atmosferast.orbita.domain.repository.ReportsRepository
import com.atmosferast.orbita.ui.common.Load
import com.atmosferast.orbita.ui.common.WhileScreenVisible
import com.atmosferast.orbita.ui.common.asLoad
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class ReportMode { MONTH, YEAR, RANGE }

/**
 * What the user picked in Reportes. Only the choice of the active [mode] applies; the others are
 * kept so that switching tabs back and forth does not lose them. There is no data from the
 * future: nothing here goes past [today].
 */
data class ReportSelection(
    val mode: ReportMode,
    /** First day of the month of "Mes". */
    val month: LocalDate,
    val year: Int,
    val rangeFrom: LocalDate,
    val rangeTo: LocalDate,
    val today: LocalDate,
) {
    val canGoToNextMonth: Boolean get() = month.isBefore(today.withDayOfMonth(1))
    val canGoToNextYear: Boolean get() = year < today.year

    /** The dates the report covers; the month and the year in course end today. */
    val period: DatePeriod
        get() = when (mode) {
            ReportMode.MONTH -> DatePeriod.ofMonth(month).untilToday()
            ReportMode.YEAR -> DatePeriod.ofYear(year).untilToday()
            ReportMode.RANGE -> DatePeriod(rangeFrom, rangeTo)
        }

    private fun DatePeriod.untilToday() = if (to.isAfter(today)) copy(to = today) else this
}

data class ReportsUiState(
    val selection: ReportSelection,
    /** One report per currency, the main one first; empty when the period has no movements. */
    val reports: Load<List<CurrencyReport>>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    reports: ReportsRepository,
    dates: DateProvider,
) : ViewModel() {

    private val today = dates.today()
    private val currentMonth = today.withDayOfMonth(1)

    private val selection = MutableStateFlow(
        ReportSelection(
            mode = ReportMode.MONTH,
            month = currentMonth,
            year = today.year,
            rangeFrom = currentMonth,
            rangeTo = today,
            today = today,
        ),
    )

    // Bumped to ask for the same period again after a failure.
    private val attempt = MutableStateFlow(0)

    val state: StateFlow<ReportsUiState> = selection
        .flatMapLatest { picked ->
            attempt.flatMapLatest {
                reports.observeReport(picked.period).asLoad().map { ReportsUiState(picked, it) }
            }
        }
        .stateIn(viewModelScope, WhileScreenVisible, ReportsUiState(selection.value, Load.Loading))

    fun setMode(mode: ReportMode) = selection.update { it.copy(mode = mode) }

    fun previousMonth() = selection.update { it.copy(month = it.month.minusMonths(1)) }

    fun nextMonth() = selection.update {
        if (it.canGoToNextMonth) it.copy(month = it.month.plusMonths(1)) else it
    }

    fun previousYear() = selection.update { it.copy(year = it.year - 1) }

    fun nextYear() = selection.update {
        if (it.canGoToNextYear) it.copy(year = it.year + 1) else it
    }

    fun setRange(from: LocalDate, to: LocalDate) =
        selection.update { it.copy(rangeFrom = from, rangeTo = minOf(to, today)) }

    /** Inicio opens Reportes on the month its cards add up. */
    fun showCurrentMonth() =
        selection.update { it.copy(mode = ReportMode.MONTH, month = currentMonth) }

    fun retry() = attempt.update { it + 1 }
}
