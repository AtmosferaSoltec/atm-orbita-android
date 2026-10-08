package com.atmosferast.orbita.ui.feature.home

import com.atmosferast.orbita.ui.components.LocalToday
import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.model.CreditPurchase
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.core.currencySymbol
import com.atmosferast.orbita.core.formatMoney
import com.atmosferast.orbita.core.formatRate
import com.atmosferast.orbita.core.formatSignedMoney
import com.atmosferast.orbita.core.monthName
import com.atmosferast.orbita.ui.components.CircleIconButton
import com.atmosferast.orbita.ui.components.EntryList
import com.atmosferast.orbita.ui.components.HeroCard
import com.atmosferast.orbita.ui.components.HeroGlass
import com.atmosferast.orbita.ui.components.HeroLabel
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.IconBadge
import com.atmosferast.orbita.ui.components.OrbitaCard
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.ScreenHeader
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.components.SectionTitle
import com.atmosferast.orbita.ui.components.SegmentedControl
import com.atmosferast.orbita.ui.components.SkeletonBlock
import com.atmosferast.orbita.ui.components.StateMessage
import com.atmosferast.orbita.ui.feature.credit.pendingPaymentsLabel
import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.Entry
import com.atmosferast.orbita.domain.model.FxPair
import com.atmosferast.orbita.domain.model.MovementKind
import com.atmosferast.orbita.data.demo.SampleData
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.ExpenseSoft
import com.atmosferast.orbita.ui.theme.Income
import com.atmosferast.orbita.ui.theme.IncomeSoft
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.MutedLight
import com.atmosferast.orbita.ui.theme.Orange
import com.atmosferast.orbita.ui.theme.OrangeSoft
import com.atmosferast.orbita.ui.components.OrbitaPreview
import java.math.BigDecimal

enum class HomeState { CONTENT, LOADING, EMPTY, ERROR }

@Composable
fun HomeScreen(
    accounts: List<Account>,
    fx: FxPair,
    displayCurrency: String,
    monthIncome: BigDecimal,
    monthExpense: BigDecimal,
    recentEntries: List<Entry>,
    pendingPurchases: List<CreditPurchase>,
    onDisplayCurrencyChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCredit: () -> Unit,
    onOpenReport: (MovementKind) -> Unit,
    onOpenMovements: () -> Unit,
    onEntryClick: (Entry) -> Unit,
    modifier: Modifier = Modifier,
    state: HomeState = HomeState.CONTENT,
    onRetry: () -> Unit = {},
) {
    ScreenScaffold(
        modifier = modifier,
        header = {
            ScreenHeader(
                title = stringResource(R.string.home_title),
                subtitle = stringResource(R.string.home_subtitle),
                action = {
                    CircleIconButton(
                        OrbitaIcons.Settings,
                        stringResource(R.string.settings_title),
                        onOpenSettings,
                    )
                },
            )
        },
    ) {
        when (state) {
            HomeState.LOADING -> HomeSkeleton()

            HomeState.ERROR -> StateMessage(
                icon = OrbitaIcons.Alert,
                title = stringResource(R.string.error_load_title),
                message = stringResource(R.string.error_load_message),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
            )

            else -> {
                SavingsHeroCard(
                    accounts, fx, displayCurrency, onDisplayCurrencyChange, onOpenAccounts,
                    onConfigureFx = onOpenSettings,
                )
                Spacer(Modifier.height(12.dp))
                val empty = state == HomeState.EMPTY
                MonthCards(
                    income = monthIncome,
                    expense = monthExpense,
                    currency = fx.main,
                    onOpenReport = onOpenReport,
                )
                HintText(
                    stringResource(R.string.home_month_note),
                    Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
                )
                // Only in v1.1 and when there are pending purchases.
                if (pendingPurchases.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    CreditReminderCard(pendingPurchases, onOpenCredit)
                }
                SectionTitle(
                    stringResource(R.string.home_recent),
                    actionLabel = if (empty) null else stringResource(R.string.action_see_all),
                    onAction = onOpenMovements,
                )
                if (empty) {
                    OrbitaCard {
                        StateMessage(
                            icon = OrbitaIcons.Plus,
                            title = stringResource(R.string.home_empty_title),
                            message = stringResource(R.string.home_empty_message),
                        )
                    }
                } else {
                    EntryList(recentEntries, onEntryClick)
                }
            }
        }
    }
}

@Composable
private fun SavingsHeroCard(
    accounts: List<Account>,
    fx: FxPair,
    displayCurrency: String,
    onDisplayCurrencyChange: (String) -> Unit,
    onOpenAccounts: () -> Unit,
    onConfigureFx: () -> Unit,
) {
    val otherCurrency = fx.other(displayCurrency)
    val total = fx.savingsTotal(accounts, displayCurrency)
    val rate = fx.rate
    HeroCard(scrim = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroLabel(stringResource(R.string.savings_total), Modifier.weight(1f))
            SegmentedControl(
                options = listOf(fx.main, fx.secondary).map { it to currencySymbol(it) },
                selected = displayCurrency,
                // Without an exchange rate the total can only be seen in the main currency.
                onSelect = { if (rate != null) onDisplayCurrencyChange(it) },
                fill = false,
                onHero = true,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            formatMoney(total, displayCurrency),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 42.sp, lineHeight = 48.sp),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        if (rate == null) {
            // A new user has no exchange rate yet: the line leads to where it is set.
            Text(
                stringResource(R.string.fx_configure_link),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.clickable(role = Role.Button, onClick = onConfigureFx),
            )
        } else {
            // Plain supporting line: the same total in the other currency.
            Text(
                stringResource(
                    R.string.savings_equivalent_manual,
                    formatMoney(fx.savingsTotal(accounts, otherCurrency), otherCurrency),
                    formatRate(rate),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HeroGlass)
                .clickable(role = Role.Button, onClick = onOpenAccounts)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                OrbitaIcons.Wallet,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(
                    R.string.savings_accounts_included,
                    accounts.count { it.includeInSavings },
                    accounts.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.action_choose),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
            Icon(
                OrbitaIcons.ChevronRight,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MonthCards(
    income: BigDecimal,
    expense: BigDecimal,
    currency: String,
    onOpenReport: (MovementKind) -> Unit,
) {
    val month = monthName(LocalToday.current).replaceFirstChar { it.uppercase() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MonthCard(
            icon = OrbitaIcons.TrendUp,
            label = stringResource(R.string.home_month_income),
            month = month,
            amount = formatSignedMoney(income, currency, positive = true),
            color = Income,
            container = IncomeSoft,
            onClick = { onOpenReport(MovementKind.INCOME) },
        )
        MonthCard(
            icon = OrbitaIcons.TrendDown,
            label = stringResource(R.string.home_month_expense),
            month = month,
            amount = formatSignedMoney(expense, currency, positive = false),
            color = Expense,
            container = ExpenseSoft,
            onClick = { onOpenReport(MovementKind.EXPENSE) },
        )
    }
}

/** Opens the report of the current month; the month reads lighter than the label. */
@Composable
private fun MonthCard(
    icon: ImageVector,
    label: String,
    month: String,
    amount: String,
    color: Color,
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OrbitaCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color, container)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        append(label)
                        withStyle(SpanStyle(color = MutedLight)) { append(" · $month") }
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    amount,
                    style = MaterialTheme.typography.titleLarge,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                OrbitaIcons.ChevronRight,
                contentDescription = null,
                tint = MutedLight,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CreditReminderCard(purchases: List<CreditPurchase>, onClick: () -> Unit) {
    OrbitaCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(OrbitaIcons.CreditCard, Orange, OrangeSoft)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.credit_card),
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                )
                Text(
                    pendingPaymentsLabel(purchases),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
            Icon(
                OrbitaIcons.ChevronRight,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBlock(
            Modifier
                .fillMaxWidth()
                .height(190.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBlock(
                Modifier
                    .weight(1f)
                    .height(78.dp),
            )
            SkeletonBlock(
                Modifier
                    .weight(1f)
                    .height(78.dp),
            )
        }
        SkeletonBlock(
            Modifier
                .fillMaxWidth()
                .height(260.dp),
        )
    }
}

@Composable
private fun HomePreviewContent(state: HomeState) {
    var currency by remember { mutableStateOf(PEN) }
    val empty = state == HomeState.EMPTY
    val october = SampleData.reportFor(DatePeriod.ofMonth(SampleData.today)).first()
    OrbitaPreview {
        HomeScreen(
            accounts = SampleData.accounts,
            fx = SampleData.fx,
            displayCurrency = currency,
            monthIncome = if (empty) BigDecimal.ZERO else october.incomeTotal,
            monthExpense = if (empty) BigDecimal.ZERO else october.expenseTotal,
            recentEntries = if (empty) emptyList() else SampleData.recentEntries,
            pendingPurchases = if (empty) emptyList() else SampleData.creditPurchases,
            onDisplayCurrencyChange = { currency = it },
            onOpenSettings = {},
            onOpenAccounts = {},
            onOpenCredit = {},
            onOpenReport = {},
            onOpenMovements = {},
            onEntryClick = {},
            state = state,
        )
    }
}

@Preview(name = "Inicio", widthDp = 390, heightDp = 844)
@Composable
private fun HomePreview() = HomePreviewContent(HomeState.CONTENT)

@Preview(name = "Inicio · sin movimientos", widthDp = 390, heightDp = 844)
@Composable
private fun HomeEmptyPreview() = HomePreviewContent(HomeState.EMPTY)

@Preview(name = "Inicio · cargando", widthDp = 390, heightDp = 844)
@Composable
private fun HomeLoadingPreview() = HomePreviewContent(HomeState.LOADING)

@Preview(name = "Inicio · error", widthDp = 390, heightDp = 844)
@Composable
private fun HomeErrorPreview() = HomePreviewContent(HomeState.ERROR)
