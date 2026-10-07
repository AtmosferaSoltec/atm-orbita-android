package com.atmosferast.orbita.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.formatDate
import com.atmosferast.orbita.core.formatDayMonth
import com.atmosferast.orbita.core.formatMonthYear
import com.atmosferast.orbita.core.monthShortName
import com.atmosferast.orbita.core.weekdayInitials
import com.atmosferast.orbita.ui.theme.Background
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.MutedLight
import com.atmosferast.orbita.ui.theme.Neutral
import com.atmosferast.orbita.ui.theme.NeutralSoft
import com.atmosferast.orbita.ui.theme.OrbitaShapes
import com.atmosferast.orbita.ui.theme.Primary
import com.atmosferast.orbita.ui.theme.PrimarySoft
import com.atmosferast.orbita.ui.theme.Surface
import java.time.LocalDate

private enum class CalendarView { DAYS, MONTHS }

/** How the soft band of a range crosses a day cell. */
private enum class RangeBand { NONE, START, MIDDLE, END }

private val DayCellHeight = 44.dp
private val DayCircle = 40.dp
private val WeekdayRowHeight = 28.dp

// Six weeks, so the dialog keeps its height from month to month.
private val CalendarBodyHeight = WeekdayRowHeight + DayCellHeight * 6

/**
 * Calendar to pick a start and an end date together. "Aceptar" needs both. Days after [today]
 * cannot be picked unless [allowFuture] (there is no data from the future).
 */
@Composable
fun DateRangeDialog(
    title: String,
    from: LocalDate,
    to: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
    allowFuture: Boolean = false,
) {
    var start by remember { mutableStateOf<LocalDate?>(from) }
    var end by remember { mutableStateOf<LocalDate?>(to) }
    val s = start
    val e = end
    val startText = when {
        s == null -> stringResource(R.string.field_from)
        e != null && s.year == e.year -> formatDayMonth(s)
        else -> formatDate(s)
    }
    CalendarDialog(
        title = title,
        headline = stringResource(
            R.string.reports_range_headline,
            startText,
            e?.let(::formatDate) ?: stringResource(R.string.field_until),
        ),
        initialMonth = to,
        today = today,
        allowFuture = allowFuture,
        start = s,
        end = e,
        onDayClick = { day ->
            when {
                // A complete range starts over.
                s == null || e != null -> {
                    start = day
                    end = null
                }
                day.isBefore(s) -> start = day
                else -> end = day
            }
        },
        canConfirm = s != null && e != null,
        onConfirm = { if (s != null && e != null) onConfirm(s, e) },
        onDismiss = onDismiss,
    )
}

/** Calendar to pick a single date. Days after [today] cannot be picked unless [allowFuture]. */
@Composable
fun DateDialog(
    title: String,
    date: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    allowFuture: Boolean = false,
) {
    var selected by remember { mutableStateOf(date) }
    CalendarDialog(
        title = title,
        headline = formatDate(selected),
        initialMonth = date,
        today = today,
        allowFuture = allowFuture,
        start = selected,
        end = selected,
        onDayClick = { selected = it },
        canConfirm = true,
        onConfirm = { onConfirm(selected) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun CalendarDialog(
    title: String,
    headline: String,
    initialMonth: LocalDate,
    today: LocalDate,
    allowFuture: Boolean,
    start: LocalDate?,
    end: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
    canConfirm: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .clip(OrbitaShapes.HeroCard)
                .background(Surface)
                .padding(20.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = Muted)
            Spacer(Modifier.height(2.dp))
            Text(headline, style = MaterialTheme.typography.titleLarge, color = Ink, maxLines = 1)
            Spacer(Modifier.height(14.dp))
            CalendarPanel(initialMonth, today, allowFuture, start, end, onDayClick)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    stringResource(R.string.action_cancel),
                    onDismiss,
                    Modifier.weight(1f),
                    container = NeutralSoft,
                    content = Neutral,
                )
                PillButton(
                    stringResource(R.string.action_accept),
                    onConfirm,
                    Modifier.weight(1f),
                    container = if (canConfirm) Primary else NeutralSoft,
                    content = if (canConfirm) Color.White else MutedLight,
                    enabled = canConfirm,
                )
            }
        }
    }
}

/**
 * Month grid with its navigation. The arrows step one month; tapping the month-year opens the
 * grid of months, where the arrows step one year.
 */
@Composable
private fun CalendarPanel(
    initialMonth: LocalDate,
    today: LocalDate,
    allowFuture: Boolean,
    start: LocalDate?,
    end: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
) {
    var month by remember { mutableStateOf(initialMonth.withDayOfMonth(1)) }
    var view by remember { mutableStateOf(CalendarView.DAYS) }
    val lastMonth = if (allowFuture) null else today.withDayOfMonth(1)
    val picksMonths = view == CalendarView.MONTHS

    fun goTo(target: LocalDate) {
        month = if (lastMonth != null && target.isAfter(lastMonth)) lastMonth else target
    }

    val canGoNext = when {
        lastMonth == null -> true
        picksMonths -> month.year < lastMonth.year
        else -> month.isBefore(lastMonth)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(
            OrbitaIcons.ChevronLeft,
            stringResource(
                if (picksMonths) R.string.calendar_prev_year else R.string.reports_prev_month,
            ),
            onClick = { goTo(if (picksMonths) month.minusYears(1) else month.minusMonths(1)) },
            container = Background,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .clip(OrbitaShapes.Pill)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.calendar_pick_month),
                    ) { view = if (picksMonths) CalendarView.DAYS else CalendarView.MONTHS }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (picksMonths) month.year.toString() else formatMonthYear(month),
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    OrbitaIcons.ChevronDown,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(if (picksMonths) 180f else 0f),
                )
            }
        }
        CircleIconButton(
            OrbitaIcons.ChevronRight,
            stringResource(
                if (picksMonths) R.string.calendar_next_year else R.string.reports_next_month,
            ),
            onClick = { goTo(if (picksMonths) month.plusYears(1) else month.plusMonths(1)) },
            container = Background,
            enabled = canGoNext,
        )
    }
    Spacer(Modifier.height(10.dp))

    if (picksMonths) {
        MonthGrid(
            month = month,
            lastMonth = lastMonth,
            onPick = {
                month = it
                view = CalendarView.DAYS
            },
        )
    } else {
        DayGrid(
            month = month,
            today = today,
            lastDay = if (allowFuture) null else today,
            start = start,
            end = end,
            onDayClick = onDayClick,
            // Swiping sideways also changes the month.
            modifier = Modifier.pointerInput(month, canGoNext) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        val threshold = 48.dp.toPx()
                        if (dragged > threshold) goTo(month.minusMonths(1))
                        else if (dragged < -threshold && canGoNext) goTo(month.plusMonths(1))
                    },
                ) { _, amount -> dragged += amount }
            },
        )
    }
}

@Composable
private fun DayGrid(
    month: LocalDate,
    today: LocalDate,
    lastDay: LocalDate?,
    start: LocalDate?,
    end: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Weeks start on Sunday.
    val leadingBlanks = month.dayOfWeek.value % 7
    val days = month.lengthOfMonth()
    val isRange = start != null && end != null && start != end

    Column(modifier.height(CalendarBodyHeight)) {
        Row(Modifier.height(WeekdayRowHeight), verticalAlignment = Alignment.CenterVertically) {
            weekdayInitials.forEach { initial ->
                Text(
                    initial,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedLight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        for (week in 0 until 6) {
            Row {
                for (column in 0 until 7) {
                    val number = week * 7 + column - leadingBlanks + 1
                    if (number !in 1..days) {
                        Spacer(
                            Modifier
                                .weight(1f)
                                .height(DayCellHeight),
                        )
                    } else {
                        val day = month.withDayOfMonth(number)
                        DayCell(
                            day = number,
                            selected = day == start || day == end,
                            band = when {
                                !isRange -> RangeBand.NONE
                                day == start -> RangeBand.START
                                day == end -> RangeBand.END
                                day.isAfter(start) && day.isBefore(end) -> RangeBand.MIDDLE
                                else -> RangeBand.NONE
                            },
                            roundLeft = column == 0 || number == 1,
                            roundRight = column == 6 || number == days,
                            isToday = day == today,
                            enabled = lastDay == null || !day.isAfter(lastDay),
                            onClick = { onDayClick(day) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    selected: Boolean,
    band: RangeBand,
    roundLeft: Boolean,
    roundRight: Boolean,
    isToday: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(DayCellHeight)
            .drawBehind {
                if (band == RangeBand.NONE) return@drawBehind
                val bandHeight = DayCircle.toPx()
                // The ends only carry the half of the band that faces the range.
                val left = if (band == RangeBand.START) size.width / 2 else 0f
                val right = if (band == RangeBand.END) size.width / 2 else size.width
                val top = (size.height - bandHeight) / 2
                // Where the band meets the edge of the week or of the month it ends rounded:
                // a pill for the whole cell, squared again on the side that continues.
                val half = size.width / 2
                val squareLeft = if (roundLeft) half else left
                val squareRight = if (roundRight) half else right
                if (roundLeft || roundRight) {
                    drawRoundRect(
                        PrimarySoft,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bandHeight),
                        cornerRadius = CornerRadius(bandHeight / 2),
                    )
                }
                if (squareRight > squareLeft) {
                    drawRect(
                        PrimarySoft,
                        topLeft = Offset(squareLeft, top),
                        size = Size(squareRight - squareLeft, bandHeight),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(DayCircle)
                .clip(CircleShape)
                .background(if (selected) Primary else Color.Transparent)
                .then(
                    if (isToday && !selected) Modifier.border(1.5.dp, Primary, CircleShape)
                    else Modifier,
                )
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.toString(),
                style = if (selected || isToday) MaterialTheme.typography.labelLarge
                else MaterialTheme.typography.bodyMedium,
                color = when {
                    selected -> Color.White
                    !enabled -> MutedLight.copy(alpha = 0.45f)
                    isToday || band != RangeBand.NONE -> Primary
                    else -> Ink
                },
            )
        }
    }
}

/** The twelve months of the year of [month]; the ones after [lastMonth] cannot be picked. */
@Composable
private fun MonthGrid(month: LocalDate, lastMonth: LocalDate?, onPick: (LocalDate) -> Unit) {
    Column(
        modifier = Modifier.height(CalendarBodyHeight),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (row in 0 until 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (column in 0 until 3) {
                    val option = month.withMonth(row * 3 + column + 1)
                    val selected = option == month
                    val enabled = lastMonth == null || !option.isAfter(lastMonth)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(OrbitaShapes.Pill)
                            .background(if (selected) Primary else Background)
                            .clickable(enabled = enabled, role = Role.Button) { onPick(option) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            monthShortName(option.monthValue).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelLarge,
                            color = when {
                                selected -> Color.White
                                !enabled -> MutedLight.copy(alpha = 0.45f)
                                else -> Ink
                            },
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Calendario · rango", widthDp = 390, heightDp = 560)
@Composable
private fun DateRangeDialogPreview() {
    OrbitaPreview {
        DateRangeDialog(
            title = "Elige el rango de fechas",
            from = LocalDate.of(2026, 9, 24),
            to = LocalDate.of(2026, 10, 2),
            today = LocalDate.of(2026, 10, 2),
            onConfirm = { _, _ -> },
            onDismiss = {},
        )
    }
}
