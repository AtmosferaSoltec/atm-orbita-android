package com.atmosferast.orbita.ui.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.core.USD
import com.atmosferast.orbita.core.formatDayMonth
import com.atmosferast.orbita.core.formatMoney
import com.atmosferast.orbita.core.formatRate
import com.atmosferast.orbita.core.formatSignedMoney
import com.atmosferast.orbita.core.monthName
import com.atmosferast.orbita.ui.components.CardDivider
import com.atmosferast.orbita.ui.components.CircleIconButton
import com.atmosferast.orbita.ui.components.EntryList
import com.atmosferast.orbita.ui.components.HeroAmount
import com.atmosferast.orbita.ui.components.HeroCard
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
import com.atmosferast.orbita.ui.mock.MockAccount
import com.atmosferast.orbita.ui.mock.MockEntry
import com.atmosferast.orbita.ui.mock.SampleData
import com.atmosferast.orbita.ui.mock.savingsTotal
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.ExpenseSoft
import com.atmosferast.orbita.ui.theme.HeroDivider
import com.atmosferast.orbita.ui.theme.Income
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.OnHero
import com.atmosferast.orbita.ui.theme.OnHeroMuted
import com.atmosferast.orbita.ui.theme.OrbitaTheme
import java.math.BigDecimal

enum class HomeState { CONTENT, LOADING, EMPTY, ERROR }

@Composable
fun HomeScreen(
    accounts: List<MockAccount>,
    displayCurrency: String,
    onDisplayCurrencyChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCredit: () -> Unit,
    onOpenMovements: () -> Unit,
    onEntryClick: (MockEntry) -> Unit,
    modifier: Modifier = Modifier,
    state: HomeState = HomeState.CONTENT,
) {
    ScreenScaffold(
        modifier = modifier,
        header = {
            ScreenHeader(
                title = stringResource(R.string.home_title),
                overline = stringResource(R.string.home_overline),
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
            )

            else -> {
                SavingsHeroCard(accounts, displayCurrency, onDisplayCurrencyChange, onOpenAccounts)
                Spacer(Modifier.height(12.dp))
                val empty = state == HomeState.EMPTY
                MonthCards(
                    income = if (empty) BigDecimal.ZERO else SampleData.monthIncome,
                    expense = if (empty) BigDecimal.ZERO else SampleData.monthExpense,
                )
                HintText(
                    stringResource(R.string.home_month_note),
                    Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
                )
                if (!empty) {
                    Spacer(Modifier.height(14.dp))
                    CreditReminderCard(onOpenCredit)
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
                    EntryList(SampleData.recentEntries, onEntryClick)
                }
            }
        }
    }
}

@Composable
private fun SavingsHeroCard(
    accounts: List<MockAccount>,
    displayCurrency: String,
    onDisplayCurrencyChange: (String) -> Unit,
    onOpenAccounts: () -> Unit,
) {
    val otherCurrency = if (displayCurrency == PEN) USD else PEN
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroLabel(stringResource(R.string.savings_total), Modifier.weight(1f))
            SegmentedControl(
                options = listOf(PEN to "S/", USD to "US$"),
                selected = displayCurrency,
                onSelect = onDisplayCurrencyChange,
                fill = false,
                onHero = true,
            )
        }
        Spacer(Modifier.height(6.dp))
        HeroAmount(formatMoney(accounts.savingsTotal(displayCurrency), displayCurrency))
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(
                R.string.savings_equivalent_manual,
                formatMoney(accounts.savingsTotal(otherCurrency), otherCurrency),
                formatRate(SampleData.usdToPen),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = OnHeroMuted,
        )
        CardDivider(color = HeroDivider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clickable(role = Role.Button, onClick = onOpenAccounts),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(
                    R.string.savings_accounts_included,
                    accounts.count { it.includeInSavings },
                    accounts.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = OnHeroMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.action_choose),
                style = MaterialTheme.typography.labelLarge,
                color = OnHero,
            )
            Icon(
                OrbitaIcons.ChevronRight,
                contentDescription = null,
                tint = OnHero,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MonthCards(income: BigDecimal, expense: BigDecimal) {
    val month = monthName(SampleData.today)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MonthCard(
            label = stringResource(R.string.home_month_income, month),
            amount = formatSignedMoney(income, PEN, positive = true),
            color = Income,
            modifier = Modifier.weight(1f),
        )
        MonthCard(
            label = stringResource(R.string.home_month_expense, month),
            amount = formatSignedMoney(expense, PEN, positive = false),
            color = Expense,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MonthCard(label: String, amount: String, color: Color, modifier: Modifier = Modifier) {
    OrbitaCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(
            amount,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Only in v1.1 and when there are pending purchases. */
@Composable
private fun CreditReminderCard(onClick: () -> Unit) {
    val purchases = SampleData.creditPurchases
    val nextDue = purchases.minOf { it.dueDate }
    OrbitaCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(OrbitaIcons.CreditCard, Expense, ExpenseSoft)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.credit_card),
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                )
                Text(
                    stringResource(
                        R.string.home_credit_pending,
                        purchases.size,
                        formatDayMonth(nextDue),
                    ),
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
    OrbitaTheme {
        HomeScreen(
            accounts = SampleData.accounts,
            displayCurrency = currency,
            onDisplayCurrencyChange = { currency = it },
            onOpenSettings = {},
            onOpenAccounts = {},
            onOpenCredit = {},
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
