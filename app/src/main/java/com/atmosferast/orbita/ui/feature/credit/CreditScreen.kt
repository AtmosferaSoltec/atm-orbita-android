package com.atmosferast.orbita.ui.feature.credit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.core.formatDayMonth
import com.atmosferast.orbita.core.formatMoney
import com.atmosferast.orbita.ui.components.CardDivider
import com.atmosferast.orbita.ui.components.HeroAmount
import com.atmosferast.orbita.ui.components.HeroCard
import com.atmosferast.orbita.ui.components.HeroLabel
import com.atmosferast.orbita.ui.components.HeroTone
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.IconBadge
import com.atmosferast.orbita.ui.components.OrbitaCard
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.PillButton
import com.atmosferast.orbita.ui.components.PrimaryButton
import com.atmosferast.orbita.ui.components.ScreenHeader
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.components.StateMessage
import com.atmosferast.orbita.ui.components.StatusChip
import com.atmosferast.orbita.ui.feature.accounts.DashedAddButton
import com.atmosferast.orbita.ui.mock.MockCreditCard
import com.atmosferast.orbita.ui.mock.MockCreditPurchase
import com.atmosferast.orbita.ui.mock.SampleData
import com.atmosferast.orbita.ui.mock.debtCurrencies
import com.atmosferast.orbita.ui.mock.debtIn
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.ExpenseSoft
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.Neutral
import com.atmosferast.orbita.ui.theme.NeutralSoft
import com.atmosferast.orbita.ui.theme.OnHero
import com.atmosferast.orbita.ui.theme.OrbitaShapes
import com.atmosferast.orbita.ui.theme.OrbitaTheme
import com.atmosferast.orbita.ui.theme.Primary
import com.atmosferast.orbita.ui.theme.PrimarySoft
import java.time.LocalDate

/**
 * Credit card debt (v1.1): the total, then one card per credit card with its pending purchases.
 * Purchases are reminders, not movements.
 */
@Composable
fun CreditScreen(
    cards: List<MockCreditCard>,
    purchases: List<MockCreditPurchase>,
    onRegisterPurchase: () -> Unit,
    onPay: (MockCreditPurchase) -> Unit,
    onCardClick: (MockCreditCard) -> Unit,
    onNewCard: () -> Unit,
    mainCurrency: String = PEN,
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
        TotalDebtCard(cards.size, purchases, mainCurrency)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            stringResource(R.string.credit_register),
            onRegisterPurchase,
            container = PrimarySoft,
            content = Primary,
            icon = OrbitaIcons.Plus,
        )
        Spacer(Modifier.height(12.dp))

        if (cards.isEmpty()) {
            StateMessage(OrbitaIcons.CreditCard, stringResource(R.string.credit_no_cards))
        }
        val byCard = purchases.groupBy { it.card.id }
        // The card with the closest due date goes first; cards without debt go last.
        val sorted = cards.sortedBy { card ->
            byCard[card.id]?.minOf { it.dueDate } ?: LocalDate.MAX
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sorted.forEach { card ->
                CreditCardCard(
                    card = card,
                    purchases = byCard[card.id].orEmpty().sortedBy { it.dueDate },
                    onClick = { onCardClick(card) },
                    onPay = onPay,
                )
            }
            DashedAddButton(stringResource(R.string.credit_new_card), onNewCard)
        }
        HintText(
            stringResource(R.string.credit_hint),
            Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp),
        )
    }
}

/** Debt of every card, per currency and never mixed. */
@Composable
private fun TotalDebtCard(
    cardCount: Int,
    purchases: List<MockCreditPurchase>,
    mainCurrency: String,
) {
    // The main currency leads (even at zero); every other currency owed gets its own line.
    val extras = purchases.debtCurrencies(mainCurrency) - mainCurrency
    // Yellow tones tell credit (money owed) apart from the blue savings cards. The dark scrim
    // on top of the yellow is what lets the text be white here.
    HeroCard(tone = HeroTone.CREDIT, scrim = true) {
        HeroLabel(
            stringResource(
                R.string.credit_total_label,
                pluralStringResource(R.plurals.credit_cards_count, cardCount, cardCount),
            ),
        )
        Spacer(Modifier.height(6.dp))
        HeroAmount(formatMoney(purchases.debtIn(mainCurrency), mainCurrency))
        extras.forEach { currency ->
            Text(
                stringResource(
                    R.string.credit_extra_amount,
                    formatMoney(purchases.debtIn(currency), currency),
                ),
                style = MaterialTheme.typography.titleLarge,
                color = OnHero,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.credit_note),
            style = MaterialTheme.typography.bodySmall,
            color = OnHero.copy(alpha = 0.9f),
        )
    }
}

/** "2 pagos pendientes · vence 5 oct" for the closest due date of [purchases]. */
@Composable
fun pendingPaymentsLabel(purchases: List<MockCreditPurchase>): String = stringResource(
    R.string.credit_pending_due,
    pluralStringResource(R.plurals.credit_pending_payments, purchases.size, purchases.size),
    formatDayMonth(purchases.minOf { it.dueDate }),
)

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

/**
 * One credit card. Collapsed it shows only the card and its debt; expanded it also lists the
 * purchases still to be paid and the link to edit the card.
 */
@Composable
private fun CreditCardCard(
    card: MockCreditCard,
    purchases: List<MockCreditPurchase>,
    onClick: () -> Unit,
    onPay: (MockCreditPurchase) -> Unit,
) {
    // The card's own currency leads; a card that only owes another one shows that one instead.
    val currencies = purchases.debtCurrencies(card.currency).ifEmpty { listOf(card.currency) }
    val urgent = purchases.any { it.isUrgent }
    // Cards with pending payments start open.
    var expanded by rememberSaveable(card.id) { mutableStateOf(purchases.isNotEmpty()) }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    OrbitaCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(OrbitaShapes.Field)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(
                        if (expanded) R.string.credit_collapse else R.string.credit_expand,
                    ),
                ) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Red when a payment is due in 3 days or less, or overdue; blue otherwise.
            IconBadge(
                OrbitaIcons.CreditCard,
                tint = if (urgent) Expense else Primary,
                container = if (urgent) ExpenseSoft else PrimarySoft,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    card.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (purchases.isEmpty()) stringResource(R.string.credit_card_no_pending)
                    else pluralStringResource(
                        R.plurals.credit_pending_payments, purchases.size, purchases.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                if (purchases.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.credit_next_due,
                            formatDayMonth(purchases.minOf { it.dueDate }),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (urgent) Expense else Muted,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(purchases.debtIn(currencies.first()), currencies.first()),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                currencies.drop(1).forEach { currency ->
                    Text(
                        stringResource(
                            R.string.credit_extra_amount,
                            formatMoney(purchases.debtIn(currency), currency),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                OrbitaIcons.ChevronDown,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                purchases.forEach { purchase ->
                    CardDivider()
                    PurchaseItem(purchase, onPay = { onPay(purchase) })
                }
                CardDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(OrbitaShapes.Pill)
                        .clickable(role = Role.Button, onClick = onClick),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        OrbitaIcons.Edit,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.credit_card_edit),
                        style = MaterialTheme.typography.labelLarge,
                        color = Primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PurchaseItem(purchase: MockCreditPurchase, onPay: () -> Unit) {
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
            style = MaterialTheme.typography.titleSmall,
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
    Spacer(Modifier.height(10.dp))
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
        modifier = Modifier.fillMaxWidth(),
        icon = OrbitaIcons.Check,
    )
}

@Preview(name = "Tarjeta de crédito", widthDp = 390, heightDp = 1400)
@Composable
private fun CreditPreview() {
    OrbitaTheme {
        CreditScreen(
            SampleData.creditCards,
            SampleData.creditPurchases,
            onRegisterPurchase = {},
            onPay = {},
            onCardClick = {},
            onNewCard = {},
        )
    }
}

@Preview(name = "Tarjeta de crédito · sin deudas", widthDp = 390, heightDp = 844)
@Composable
private fun CreditEmptyPreview() {
    OrbitaTheme {
        CreditScreen(
            SampleData.creditCards,
            emptyList(),
            onRegisterPurchase = {},
            onPay = {},
            onCardClick = {},
            onNewCard = {},
        )
    }
}
