package com.atmosferast.orbita.ui.feature.credit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.core.USD
import com.atmosferast.orbita.core.formatDayMonth
import com.atmosferast.orbita.core.formatMoney
import com.atmosferast.orbita.ui.components.HeroAmount
import com.atmosferast.orbita.ui.components.HeroCard
import com.atmosferast.orbita.ui.components.HeroLabel
import com.atmosferast.orbita.ui.components.OrbitaCard
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.PillButton
import com.atmosferast.orbita.ui.components.PrimaryButton
import com.atmosferast.orbita.ui.components.ScreenHeader
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.components.StateMessage
import com.atmosferast.orbita.ui.components.StatusChip
import com.atmosferast.orbita.ui.mock.MockCreditPurchase
import com.atmosferast.orbita.ui.mock.SampleData
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.ExpenseSoft
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.Neutral
import com.atmosferast.orbita.ui.theme.NeutralSoft
import com.atmosferast.orbita.ui.theme.OnHero
import com.atmosferast.orbita.ui.theme.OnHeroMuted
import com.atmosferast.orbita.ui.theme.OrbitaTheme
import com.atmosferast.orbita.ui.theme.Primary
import com.atmosferast.orbita.ui.theme.PrimarySoft
import java.math.BigDecimal

/** Pending credit card purchases (v1.1). They are reminders, not movements. */
@Composable
fun CreditScreen(
    purchases: List<MockCreditPurchase>,
    onRegisterPurchase: () -> Unit,
    onPay: (MockCreditPurchase) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        modifier = modifier,
        header = {
            ScreenHeader(
                title = stringResource(R.string.credit_card),
                overline = stringResource(R.string.credit_overline),
            )
        },
    ) {
        ToPayCard(purchases)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            stringResource(R.string.credit_register),
            onRegisterPurchase,
            container = PrimarySoft,
            content = Primary,
            icon = OrbitaIcons.Plus,
        )

        if (purchases.isEmpty()) {
            StateMessage(OrbitaIcons.Check, stringResource(R.string.credit_empty))
        } else {
            Text(
                stringResource(R.string.credit_sorted),
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                modifier = Modifier.padding(top = 20.dp, bottom = 10.dp, start = 4.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                purchases.sortedBy { it.dueDate }.forEach { purchase ->
                    PurchaseCard(purchase, onPay = { onPay(purchase) })
                }
            }
        }
    }
}

/** Totals per currency of the pending purchases, never mixed. */
@Composable
private fun ToPayCard(purchases: List<MockCreditPurchase>) {
    fun totalOf(currency: String) = purchases.filter { it.currency == currency }
        .fold(BigDecimal.ZERO) { acc, purchase -> acc + purchase.amount }

    val usd = totalOf(USD)
    HeroCard {
        HeroLabel(stringResource(R.string.credit_to_pay))
        Spacer(Modifier.height(6.dp))
        HeroAmount(formatMoney(totalOf(PEN), PEN))
        if (usd.signum() > 0) {
            Text(
                "+ ${formatMoney(usd, USD)}",
                style = MaterialTheme.typography.titleLarge,
                color = OnHero,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.credit_note),
            style = MaterialTheme.typography.bodySmall,
            color = OnHeroMuted,
        )
    }
}

@Composable
fun dueLabel(purchase: MockCreditPurchase): String {
    val days = purchase.daysUntilDue.toInt()
    val date = formatDayMonth(purchase.dueDate)
    return when {
        days < -1 -> stringResource(R.string.credit_overdue_days, -days)
        days == -1 -> stringResource(R.string.credit_overdue_one_day)
        days == 0 -> stringResource(R.string.credit_due_today)
        days == 1 -> stringResource(R.string.credit_due_tomorrow, date)
        else -> stringResource(R.string.credit_due_in_days, date, days)
    }
}

@Composable
private fun PurchaseCard(purchase: MockCreditPurchase, onPay: () -> Unit) {
    OrbitaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                purchase.description,
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                formatMoney(purchase.amount, purchase.currency),
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
        }
        Text(
            stringResource(
                R.string.credit_purchase_subtitle,
                purchase.category.name,
                formatDayMonth(purchase.purchaseDate),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Urgent: due in 3 days or less, or overdue.
            StatusChip(
                dueLabel(purchase),
                container = if (purchase.isUrgent) ExpenseSoft else NeutralSoft,
                content = if (purchase.isUrgent) Expense else Neutral,
            )
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        PillButton(
            stringResource(R.string.credit_mark_paid),
            onPay,
            icon = OrbitaIcons.Check,
        )
    }
}

@Preview(name = "Tarjeta de crédito", widthDp = 390, heightDp = 1250)
@Composable
private fun CreditPreview() {
    OrbitaTheme { CreditScreen(SampleData.creditPurchases, onRegisterPurchase = {}, onPay = {}) }
}

@Preview(name = "Tarjeta de crédito · vacío", widthDp = 390, heightDp = 844)
@Composable
private fun CreditEmptyPreview() {
    OrbitaTheme { CreditScreen(emptyList(), onRegisterPurchase = {}, onPay = {}) }
}
