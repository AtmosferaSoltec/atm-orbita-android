package com.atmosferast.orbita.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.USD
import com.atmosferast.orbita.core.formatDayMonth
import com.atmosferast.orbita.core.formatMoney
import com.atmosferast.orbita.core.formatMonthYear
import com.atmosferast.orbita.core.formatSignedMoney
import com.atmosferast.orbita.domain.model.AccountType
import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.Entry
import com.atmosferast.orbita.domain.model.Movement
import com.atmosferast.orbita.domain.model.Transfer
import com.atmosferast.orbita.domain.model.MovementKind
import com.atmosferast.orbita.ui.theme.Background
import com.atmosferast.orbita.ui.theme.DividerSoft
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.ExpenseSoft
import com.atmosferast.orbita.ui.theme.Income
import com.atmosferast.orbita.ui.theme.IncomeSoft
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.Neutral
import com.atmosferast.orbita.ui.theme.NeutralSoft
import com.atmosferast.orbita.ui.theme.OrbitaShapes
import com.atmosferast.orbita.ui.theme.Outline
import com.atmosferast.orbita.ui.theme.Primary
import com.atmosferast.orbita.ui.theme.Surface
import com.atmosferast.orbita.ui.theme.Transfer
import com.atmosferast.orbita.ui.theme.TransferSoft
import java.time.LocalDate

/** Month stepper: ‹ goes to the previous month, › to the next. An arrow at its limit is dimmed. */
@Composable
fun MonthSelector(
    month: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    canGoPrevious: Boolean = true,
    canGoNext: Boolean = true,
) {
    val lastDay = month.withDayOfMonth(month.lengthOfMonth())
    OrbitaCard(modifier = modifier, contentPadding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                OrbitaIcons.ChevronLeft,
                stringResource(R.string.reports_prev_month),
                onPrevious,
                container = Background,
                enabled = canGoPrevious,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
                enabled = canGoNext,
            )
        }
    }
}

/** Scrollable screen body with the standard horizontal padding and an optional pinned footer. */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    footer: @Composable (() -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
    ) {
        Box(Modifier.padding(horizontal = ScreenPadding)) { header() }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(bottom = 24.dp),
            verticalArrangement = verticalArrangement,
            content = content,
        )
        if (footer != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding)
                    .padding(top = 8.dp, bottom = 16.dp),
            ) { footer() }
        }
    }
}

fun accountIcon(account: Account): ImageVector = when {
    account.currency == USD -> OrbitaIcons.Dollar
    account.type == AccountType.CASH -> OrbitaIcons.Banknote
    account.type == AccountType.OTHER -> OrbitaIcons.Wallet
    else -> OrbitaIcons.Bank
}

@Composable
fun accountTypeLabel(type: AccountType): String = stringResource(
    when (type) {
        AccountType.CASH -> R.string.account_type_cash
        AccountType.DEBIT -> R.string.account_type_debit
        AccountType.SAVINGS -> R.string.account_type_savings
        AccountType.OTHER -> R.string.account_type_other
    },
)

/** Row of the movement lists: income (↗, up), expense (↙, down) or transfer (⇄). */
@Composable
fun EntryRow(entry: Entry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val date = formatDayMonth(entry.date)
    when (entry) {
        is Movement -> {
            val income = entry.kind == MovementKind.INCOME
            val kindLabel = stringResource(if (income) R.string.kind_income else R.string.kind_expense)
            val redundant = entry.description.isBlank() || entry.description == entry.category.name
            EntryRowLayout(
                icon = if (income) OrbitaIcons.ArrowUpRight else OrbitaIcons.ArrowDownLeft,
                tint = if (income) Income else Expense,
                container = if (income) IncomeSoft else ExpenseSoft,
                title = entry.description.ifBlank { entry.category.name },
                subtitle = listOf(
                    if (redundant) kindLabel else entry.category.name,
                    entry.account.name,
                    date,
                ).joinToString(" · "),
                amount = formatSignedMoney(entry.amount, entry.account.currency, income),
                amountColor = if (income) Income else Expense,
                onClick = onClick,
                modifier = modifier,
            )
        }

        is Transfer -> {
            val out = formatMoney(entry.fromAmount, entry.from.currency)
            val amount = if (entry.from.currency == entry.to.currency) out
            else "$out → ${formatMoney(entry.toAmount, entry.to.currency)}"
            EntryRowLayout(
                icon = OrbitaIcons.Swap,
                tint = Transfer,
                container = TransferSoft,
                title = entry.note.ifBlank { stringResource(R.string.kind_transfer) },
                subtitle = "${entry.from.name} → ${entry.to.name} · $date",
                amount = amount,
                amountColor = Neutral,
                onClick = onClick,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun EntryRowLayout(
    icon: ImageVector,
    tint: Color,
    container: Color,
    title: String,
    subtitle: String,
    amount: String,
    amountColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, container, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    amount,
                    style = MaterialTheme.typography.titleSmall,
                    color = amountColor,
                    maxLines = 1,
                )
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Heading under the tabs of the new movement / transfer forms: says what is being registered. */
@Composable
fun FormIntro(
    icon: ImageVector,
    tint: Color,
    container: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, container)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

/** White card that stacks [EntryRow]s separated by soft dividers. */
@Composable
fun EntryList(
    entries: List<Entry>,
    onEntryClick: (Entry) -> Unit,
    modifier: Modifier = Modifier,
) {
    OrbitaCard(modifier = modifier, contentPadding = PaddingValues(vertical = 4.dp)) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 66.dp, end = 14.dp),
                    color = DividerSoft,
                )
            }
            EntryRow(entry, onClick = { onEntryClick(entry) })
        }
    }
}

/** Empty / error placeholder with an optional action. */
@Composable
fun StateMessage(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, Neutral, NeutralSoft, size = 56.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
        }
        if (actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            PillButton(actionLabel, onAction)
        }
    }
}

/** Skeleton block for loading states. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(OrbitaShapes.Card)
            .background(Outline),
    )
}

enum class BottomTab { HOME, ACCOUNTS, REPORTS, CREDIT }

/** Inicio · Cuentas · [ + ] · Reportes · Crédito (v1.1). */
@Composable
fun OrbitaBottomBar(
    current: BottomTab,
    onSelect: (BottomTab) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(Surface)) {
        HorizontalDivider(color = Outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomBarItem(BottomTab.HOME, OrbitaIcons.Home, R.string.tab_home, current, onSelect)
            BottomBarItem(BottomTab.ACCOUNTS, OrbitaIcons.Wallet, R.string.tab_accounts, current, onSelect)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Primary)
                        .clickable(role = Role.Button, onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        OrbitaIcons.Plus,
                        contentDescription = stringResource(R.string.movement_new_title),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            BottomBarItem(BottomTab.REPORTS, OrbitaIcons.Chart, R.string.tab_reports, current, onSelect)
            BottomBarItem(BottomTab.CREDIT, OrbitaIcons.CreditCard, R.string.tab_credit, current, onSelect)
        }
    }
}

@Composable
private fun RowScope.BottomBarItem(
    tab: BottomTab,
    icon: ImageVector,
    labelRes: Int,
    current: BottomTab,
    onSelect: (BottomTab) -> Unit,
) {
    val selected = tab == current
    val color = if (selected) Primary else Muted
    Column(
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .clip(OrbitaShapes.Field)
            .selectable(selected, role = Role.Tab) { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}
