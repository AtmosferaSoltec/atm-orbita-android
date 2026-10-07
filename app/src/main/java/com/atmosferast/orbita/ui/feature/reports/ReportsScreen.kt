package com.atmosferast.orbita.ui.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.currencyInfo
import com.atmosferast.orbita.core.formatDate
import com.atmosferast.orbita.core.formatDayMonth
import com.atmosferast.orbita.core.formatMoney
import com.atmosferast.orbita.core.formatSignedMoney
import com.atmosferast.orbita.data.demo.SampleData
import com.atmosferast.orbita.domain.model.CategoryTotal
import com.atmosferast.orbita.domain.model.CurrencyReport
import com.atmosferast.orbita.domain.model.MovementKind
import com.atmosferast.orbita.domain.model.percentOf
import com.atmosferast.orbita.domain.model.total
import com.atmosferast.orbita.ui.common.Load
import com.atmosferast.orbita.ui.components.CardDivider
import com.atmosferast.orbita.ui.components.CircleIconButton
import com.atmosferast.orbita.ui.components.DateRangeDialog
import com.atmosferast.orbita.ui.components.FieldLabel
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.LabelValueRow
import com.atmosferast.orbita.ui.components.MonthSelector
import com.atmosferast.orbita.ui.components.OrbitaCard
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.OrbitaPreview
import com.atmosferast.orbita.ui.components.PickerField
import com.atmosferast.orbita.ui.components.ScreenHeader
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.components.SectionTitle
import com.atmosferast.orbita.ui.components.SegmentedControl
import com.atmosferast.orbita.ui.components.SkeletonBlock
import com.atmosferast.orbita.ui.components.StateMessage
import com.atmosferast.orbita.ui.components.StatusChip
import com.atmosferast.orbita.ui.components.color
import com.atmosferast.orbita.ui.theme.Background
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.Income
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.Neutral
import com.atmosferast.orbita.ui.theme.NeutralSoft
import java.time.LocalDate

/**
 * Incomes and expenses of a month, a year or a range of dates. [focus] is set when coming from a
 * month card of Inicio: the ranking of that kind goes first.
 */
@Composable
fun ReportsScreen(
    state: ReportsUiState,
    onModeChange: (ReportMode) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onRangeChange: (LocalDate, LocalDate) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    focus: MovementKind? = null,
) {
    val selection = state.selection
    var pickingRange by remember { mutableStateOf(false) }

    ScreenScaffold(
        modifier = modifier,
        header = {
            ScreenHeader(
                title = stringResource(R.string.reports_title),
                action = {
                    // Visible but inactive: PDF export comes later.
                    StatusChip(
                        stringResource(R.string.reports_pdf_soon),
                        container = NeutralSoft,
                        content = Neutral,
                        icon = OrbitaIcons.File,
                    )
                },
            )
        },
    ) {
        SegmentedControl(
            options = listOf(
                ReportMode.MONTH to stringResource(R.string.reports_by_month),
                ReportMode.YEAR to stringResource(R.string.reports_by_year),
                ReportMode.RANGE to stringResource(R.string.reports_by_range),
            ),
            selected = selection.mode,
            onSelect = onModeChange,
        )
        Spacer(Modifier.height(12.dp))

        when (selection.mode) {
            ReportMode.MONTH -> MonthSelector(
                month = selection.month,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                // There is no data from the future: the current month is the last one.
                canGoNext = selection.canGoToNextMonth,
            )

            ReportMode.YEAR -> YearSelector(
                year = selection.year,
                today = selection.today,
                onPrevious = onPreviousYear,
                onNext = onNextYear,
                canGoNext = selection.canGoToNextYear,
            )

            ReportMode.RANGE -> RangeSelector(
                selection.rangeFrom,
                selection.rangeTo,
                onClick = { pickingRange = true },
            )
        }
        if (pickingRange) {
            DateRangeDialog(
                title = stringResource(R.string.reports_range_title),
                from = selection.rangeFrom,
                to = selection.rangeTo,
                today = selection.today,
                onConfirm = { from, to ->
                    onRangeChange(from, to)
                    pickingRange = false
                },
                onDismiss = { pickingRange = false },
            )
        }
        Spacer(Modifier.height(12.dp))

        when (val reports = state.reports) {
            Load.Loading -> ReportSkeleton()

            Load.Failed -> StateMessage(
                icon = OrbitaIcons.Alert,
                title = stringResource(R.string.error_load_title),
                message = stringResource(R.string.error_load_message),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
            )

            is Load.Ready -> if (reports.value.isEmpty()) {
                StateMessage(OrbitaIcons.Chart, stringResource(R.string.reports_empty))
            } else {
                // Currencies are never mixed: one block per currency, the main one first.
                val severalCurrencies = reports.value.size > 1
                reports.value.forEach { report ->
                    if (severalCurrencies) SectionTitle(currencyInfo(report.currency).label)
                    CurrencyReportBlock(report, focus)
                }
                HintText(
                    stringResource(R.string.reports_hint),
                    Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CurrencyReportBlock(report: CurrencyReport, focus: MovementKind?) {
    val balance = report.balance
    OrbitaCard {
        Row {
            SummaryColumn(
                stringResource(R.string.reports_income),
                formatMoney(report.incomeTotal, report.currency),
                Income,
                Modifier.weight(1f),
            )
            SummaryColumn(
                stringResource(R.string.reports_expense),
                formatMoney(report.expenseTotal, report.currency),
                Expense,
                Modifier.weight(1f),
            )
        }
        CardDivider()
        LabelValueRow(
            stringResource(R.string.reports_balance),
            formatSignedMoney(balance, report.currency, positive = balance.signum() >= 0),
            valueColor = if (balance.signum() >= 0) Income else Expense,
            valueStyle = MaterialTheme.typography.titleMedium,
        )
    }

    // A period can have only incomes or only expenses: the empty ranking is left out.
    val expenseRanking = @Composable {
        if (report.expenses.isNotEmpty()) {
            SectionTitle(stringResource(R.string.reports_top_expenses))
            CategoryRanking(report.expenses, report.currency)
        }
    }
    val incomeRanking = @Composable {
        if (report.incomes.isNotEmpty()) {
            SectionTitle(stringResource(R.string.reports_income_sources))
            CategoryRanking(report.incomes, report.currency)
        }
    }
    if (focus == MovementKind.INCOME) {
        incomeRanking()
        expenseRanking()
    } else {
        expenseRanking()
        incomeRanking()
    }
}

/** Year stepper, the twin of [MonthSelector]. The year in course ends today. */
@Composable
private fun YearSelector(
    year: Int,
    today: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canGoNext: Boolean,
) {
    val firstDay = LocalDate.of(year, 1, 1)
    val lastDay = LocalDate.of(year, 12, 31)
    OrbitaCard(contentPadding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                OrbitaIcons.ChevronLeft,
                stringResource(R.string.calendar_prev_year),
                onPrevious,
                container = Background,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    year.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                    maxLines = 1,
                )
                Text(
                    if (year == today.year) {
                        stringResource(R.string.reports_year_until_today, formatDayMonth(firstDay))
                    } else {
                        "${formatDayMonth(firstDay)} – ${formatDayMonth(lastDay)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
            CircleIconButton(
                OrbitaIcons.ChevronRight,
                stringResource(R.string.calendar_next_year),
                onNext,
                container = Background,
                enabled = canGoNext,
            )
        }
    }
}

@Composable
private fun ReportSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBlock(
            Modifier
                .fillMaxWidth()
                .height(120.dp),
        )
        SkeletonBlock(
            Modifier
                .fillMaxWidth()
                .height(260.dp),
        )
    }
}

@Composable
private fun RangeSelector(from: LocalDate, to: LocalDate, onClick: () -> Unit) {
    // Both fields open the same calendar, where the start and the end are picked together.
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            FieldLabel(stringResource(R.string.field_from))
            PickerField(formatDate(from), onClick = onClick)
        }
        Column(Modifier.weight(1f)) {
            FieldLabel(stringResource(R.string.field_until))
            PickerField(formatDate(to), onClick = onClick)
        }
    }
}

@Composable
private fun SummaryColumn(label: String, amount: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
        Spacer(Modifier.height(4.dp))
        Text(
            amount,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Horizontal bars: width = share of the currency total, largest first, category color. */
@Composable
private fun CategoryRanking(items: List<CategoryTotal>, currency: String) {
    val total = items.total()
    OrbitaCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items.sortedByDescending { it.total }.forEach { item ->
                val percent = percentOf(item.total, total)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(item.category.color),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            item.category.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${formatMoney(item.total, currency)} · ${percent.toPlainString()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Muted,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(NeutralSoft),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(percent.toFloat() / 100f)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(item.category.color),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportsPreviewContent(mode: ReportMode) {
    val selection = ReportSelection(
        mode = mode,
        month = SampleData.reportMonth,
        year = SampleData.today.year,
        rangeFrom = SampleData.reportMonth,
        rangeTo = SampleData.today,
        today = SampleData.today,
    )
    OrbitaPreview {
        ReportsScreen(
            state = ReportsUiState(selection, Load.Ready(SampleData.reportFor(selection.period))),
            onModeChange = {},
            onPreviousMonth = {},
            onNextMonth = {},
            onPreviousYear = {},
            onNextYear = {},
            onRangeChange = { _, _ -> },
            onRetry = {},
        )
    }
}

@Preview(name = "Reportes · mes", widthDp = 390, heightDp = 1200)
@Composable
private fun ReportsPreview() = ReportsPreviewContent(ReportMode.MONTH)

@Preview(name = "Reportes · año", widthDp = 390, heightDp = 1200)
@Composable
private fun ReportsYearPreview() = ReportsPreviewContent(ReportMode.YEAR)
