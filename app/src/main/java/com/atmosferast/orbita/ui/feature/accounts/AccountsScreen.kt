package com.atmosferast.orbita.ui.feature.accounts

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.core.USD
import com.atmosferast.orbita.core.formatMoney
import com.atmosferast.orbita.core.formatRate
import com.atmosferast.orbita.ui.components.CardDivider
import com.atmosferast.orbita.ui.components.HeroAmount
import com.atmosferast.orbita.ui.components.HeroCard
import com.atmosferast.orbita.ui.components.HeroLabel
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.IconBadge
import com.atmosferast.orbita.ui.components.OrbitaCard
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.PillButton
import com.atmosferast.orbita.ui.components.ScreenHeader
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.components.SwitchRow
import com.atmosferast.orbita.ui.components.accountIcon
import com.atmosferast.orbita.ui.components.accountTypeLabel
import com.atmosferast.orbita.ui.mock.MockAccount
import com.atmosferast.orbita.ui.mock.SampleData
import com.atmosferast.orbita.ui.mock.convert
import com.atmosferast.orbita.ui.mock.savingsTotal
import com.atmosferast.orbita.ui.theme.ChipBorder
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.OnHeroMuted
import com.atmosferast.orbita.ui.theme.OrbitaShapes
import com.atmosferast.orbita.ui.theme.OrbitaTheme
import com.atmosferast.orbita.ui.theme.Primary
import com.atmosferast.orbita.ui.theme.PrimarySoft

@Composable
fun AccountsScreen(
    accounts: List<MockAccount>,
    displayCurrency: String,
    onToggleSavings: (MockAccount, Boolean) -> Unit,
    onTransfer: () -> Unit,
    onAccountClick: (MockAccount) -> Unit,
    onNewAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        modifier = modifier,
        header = {
            ScreenHeader(
                title = stringResource(R.string.accounts_title),
                action = {
                    PillButton(
                        stringResource(R.string.action_transfer),
                        onTransfer,
                        icon = OrbitaIcons.Swap,
                    )
                },
            )
        },
    ) {
        SavingsSummaryCard(accounts, displayCurrency)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            accounts.forEach { account ->
                AccountCard(
                    account = account,
                    onClick = { onAccountClick(account) },
                    onToggleSavings = { onToggleSavings(account, it) },
                )
            }
            NewAccountButton(onNewAccount)
        }
        HintText(
            stringResource(R.string.accounts_hint),
            Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun SavingsSummaryCard(accounts: List<MockAccount>, displayCurrency: String) {
    val otherCurrency = if (displayCurrency == PEN) USD else PEN
    HeroCard {
        HeroLabel(
            stringResource(
                R.string.accounts_total_label,
                accounts.count { it.includeInSavings },
                accounts.size,
            ),
        )
        Spacer(Modifier.height(6.dp))
        HeroAmount(formatMoney(accounts.savingsTotal(displayCurrency), displayCurrency))
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(
                R.string.savings_equivalent,
                formatMoney(accounts.savingsTotal(otherCurrency), otherCurrency),
                formatRate(SampleData.usdToPen),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = OnHeroMuted,
        )
    }
}

@Composable
private fun AccountCard(
    account: MockAccount,
    onClick: () -> Unit,
    onToggleSavings: (Boolean) -> Unit,
) {
    val currencyName = stringResource(
        if (account.currency == USD) R.string.currency_name_usd else R.string.currency_name_pen,
    )
    OrbitaCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(OrbitaShapes.Field)
                .clickable(role = Role.Button, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(accountIcon(account), Primary, PrimarySoft)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    account.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        R.string.account_subtitle,
                        accountTypeLabel(account.type),
                        currencyName,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(account.balance, account.currency),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                if (account.currency != PEN) {
                    Text(
                        stringResource(
                            R.string.approx_amount,
                            formatMoney(convert(account.balance, account.currency, PEN), PEN),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
        }
        CardDivider(Modifier.padding(top = 2.dp))
        SwitchRow(
            label = stringResource(R.string.account_include_in_savings),
            checked = account.includeInSavings,
            onCheckedChange = onToggleSavings,
            labelColor = Muted,
        )
    }
}

@Composable
private fun NewAccountButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(OrbitaShapes.Card)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = ChipBorder,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(
                        size.width - stroke,
                        size.height - stroke,
                    ),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                        ),
                    ),
                )
            }
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(OrbitaIcons.Plus, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.accounts_new),
            style = MaterialTheme.typography.labelLarge,
            color = Primary,
        )
    }
}

@Preview(name = "Cuentas", widthDp = 390, heightDp = 1100)
@Composable
private fun AccountsPreview() {
    var accounts by remember { mutableStateOf(SampleData.accounts) }
    OrbitaTheme {
        AccountsScreen(
            accounts = accounts,
            displayCurrency = PEN,
            onToggleSavings = { account, include ->
                accounts = accounts.map {
                    if (it.id == account.id) it.copy(includeInSavings = include) else it
                }
            },
            onTransfer = {},
            onAccountClick = {},
            onNewAccount = {},
        )
    }
}
