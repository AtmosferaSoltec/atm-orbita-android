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
import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.core.formatDate
import com.atmosferast.orbita.core.formatDayMonth
import com.atmosferast.orbita.core.formatMoney
import com.atmosferast.orbita.core.formatMonthYear
import com.atmosferast.orbita.core.formatSignedMoney
import com.atmosferast.orbita.ui.components.CardDivider
import com.atmosferast.orbita.ui.components.CircleIconButton
import com.atmosferast.orbita.ui.components.FieldLabel
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.IconBadge
import com.atmosferast.orbita.ui.components.LabelValueRow
import com.atmosferast.orbita.ui.components.OrbitaCard
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.PickerField
import com.atmosferast.orbita.ui.components.ScreenHeader
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.components.SectionTitle
import com.atmosferast.orbita.ui.components.SegmentedControl
import com.atmosferast.orbita.ui.components.StateMessage
import com.atmosferast.orbita.ui.components.StatusChip
import com.atmosferast.orbita.ui.mock.MockCategoryTotal
import com.atmosferast.orbita.ui.mock.SampleData
import com.atmosferast.orbita.ui.mock.percentOf
import com.atmosferast.orbita.ui.mock.total
import com.atmosferast.orbita.ui.theme.Background
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.Income
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.Neutral
import com.atmosferast.orbita.ui.theme.NeutralSoft
import com.atmosferast.orbita.ui.theme.OrbitaTheme
import com.atmosferast.orbita.ui.theme.Primary
import com.atmosferast.orbita.ui.theme.PrimarySoft
import java.time.LocalDate

private enum class ReportMode { MONTH, RANGE }

@Composable
fun ReportsScreen(modifier: Modifier = Modifier) {
    var mode by remember { mutableStateOf(ReportMode.MONTH) }
    var month by remember { mutableStateOf(SampleData.reportMonth) }
    // The sample data only covers September 2026.
    val hasData = mode == ReportMode.RANGE || month == SampleData.reportMonth

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
                ReportMode.RANGE to stringResource(R.string.reports_by_range),
            ),
            selected = mode,
            onSelect = { mode = it },
        )
        Spacer(Modifier.height(12.dp))

        if (mode == ReportMode.MONTH) {
            MonthSelector(
                month = month,
                onPrevious = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
            )
        } else {
            RangeSelector(SampleData.reportMonth, SampleData.reportMonth.withDayOfMonth(30))
        }
        Spacer(Modifier.height(12.dp))

        if (!hasData) {
            StateMessage(OrbitaIcons.Chart, stringResource(R.string.reports_empty))
            return@ScreenScaffold
        }

        val income = SampleData.reportIncomes.total()
        val expense = SampleData.reportExpenses.total()
        val balance = income - expense
        OrbitaCard {
            Row {
                SummaryColumn(
                    stringResource(R.string.reports_income),
                    formatMoney(income, PEN),
                    Income,
                    Modifier.weight(1f),
                )
                SummaryColumn(
                    stringResource(R.string.reports_expense),
                    formatMoney(expense, PEN),
                    Expense,
                    Modifier.weight(1f),
                )
            }
            CardDivider()
            LabelValueRow(
                stringResource(R.string.reports_balance),
                formatSignedMoney(balance, PEN, positive = balance.signum() >= 0),
                valueColor = if (balance.signum() >= 0) Income else Expense,
                valueStyle = MaterialTheme.typography.titleMedium,
            )
        }

        SectionTitle(stringResource(R.string.reports_top_expenses))
        CategoryRanking(SampleData.reportExpenses)

        SectionTitle(stringResource(R.string.reports_income_sources))
        CategoryRanking(SampleData.reportIncomes)

        HintText(
            stringResource(R.string.reports_hint),
            Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun MonthSelector(month: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit) {
    val lastDay = month.withDayOfMonth(month.lengthOfMonth())
    OrbitaCard(contentPadding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                OrbitaIcons.ChevronLeft,
                stringResource(R.string.reports_prev_month),
                onPrevious,
                container = Background,
            )
            Spacer(Modifier.width(10.dp))
            IconBadge(OrbitaIcons.Calendar, Primary, PrimarySoft, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    formatMonthYear(month),
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                    maxLines = 1,
                )
                Text(
                    "1 – ${formatDayMonth(lastDay)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
            CircleIconButton(
                OrbitaIcons.ChevronRight,
                stringResource(R.string.reports_next_month),
                onNext,
                container = Background,
            )
        }
    }
}

@Composable
private fun RangeSelector(from: LocalDate, to: LocalDate) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            FieldLabel(stringResource(R.string.field_from))
            PickerField(formatDate(from), onClick = {})
        }
        Column(Modifier.weight(1f)) {
            FieldLabel(stringResource(R.string.field_until))
            PickerField(formatDate(to), onClick = {})
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
private fun CategoryRanking(items: List<MockCategoryTotal>) {
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
                            "${formatMoney(item.total, PEN)} · ${percent.toPlainString()}%",
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

@Preview(name = "Reportes", widthDp = 390, heightDp = 1200)
@Composable
private fun ReportsPreview() {
    OrbitaTheme { ReportsScreen() }
}
