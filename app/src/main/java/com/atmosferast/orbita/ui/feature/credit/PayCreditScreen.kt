package com.atmosferast.orbita.ui.feature.credit

import com.atmosferast.orbita.ui.components.DateDialog
import com.atmosferast.orbita.ui.components.parseDecimal
import com.atmosferast.orbita.ui.components.LocalToday
import com.atmosferast.orbita.domain.model.CreditPayment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.currencySymbol
import com.atmosferast.orbita.core.formatDate
import com.atmosferast.orbita.core.formatDayMonth
import com.atmosferast.orbita.core.formatMoney
import com.atmosferast.orbita.core.formatSignedMoney
import com.atmosferast.orbita.ui.components.CardDivider
import com.atmosferast.orbita.ui.components.ChipGroup
import com.atmosferast.orbita.ui.components.FieldLabel
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.LabelValueRow
import com.atmosferast.orbita.ui.components.ModalTopBar
import com.atmosferast.orbita.ui.components.OrbitaCard
import com.atmosferast.orbita.ui.components.OrbitaChip
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.OrbitaTextField
import com.atmosferast.orbita.ui.components.PickerField
import com.atmosferast.orbita.ui.components.PrimaryButton
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.CreditPurchase
import com.atmosferast.orbita.data.demo.SampleData
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.components.OrbitaPreview
import java.math.BigDecimal
import java.math.RoundingMode

/** "Marcar como pagada": only now the expense is created in the chosen account. */
@Composable
fun PayCreditScreen(
    purchase: CreditPurchase,
    accounts: List<Account>,
    onBack: () -> Unit,
    onConfirm: (CreditPayment) -> Unit,
    modifier: Modifier = Modifier,
) {
    // If the currencies match, the purchase amount is suggested; otherwise the user must type
    // what the bank really charged.
    fun suggestedAmount(account: Account) =
        if (account.currency == purchase.currency) {
            purchase.amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
        } else ""

    val today = LocalToday.current
    // Starts on an account in the currency of the purchase, where no conversion is needed.
    val initialAccount = remember {
        accounts.firstOrNull { it.currency == purchase.currency } ?: accounts.firstOrNull()
    }
    var selectedId by remember { mutableStateOf(initialAccount?.id) }
    var amount by remember { mutableStateOf(initialAccount?.let(::suggestedAmount).orEmpty()) }
    var paidOn by remember { mutableStateOf(today) }
    var pickingDate by remember { mutableStateOf(false) }
    // The list may hold a newer copy of the account (its balance changes with every movement).
    val account = accounts.firstOrNull { it.id == selectedId }

    val paid = parseDecimal(amount) ?: BigDecimal.ZERO

    ScreenScaffold(
        modifier = modifier,
        header = {
            ModalTopBar(
                title = stringResource(R.string.credit_mark_paid),
                navigationIcon = OrbitaIcons.ChevronLeft,
                navigationLabel = stringResource(R.string.action_back),
                onNavigate = onBack,
            )
        },
        footer = {
            PrimaryButton(
                stringResource(R.string.pay_confirm),
                onClick = {
                    onConfirm(CreditPayment(purchase.id, account?.id, parseDecimal(amount), paidOn))
                },
            )
        },
    ) {
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
                    R.string.pay_purchase_subtitle,
                    purchase.category.name,
                    formatDayMonth(purchase.purchaseDate),
                    formatDayMonth(purchase.dueDate),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }

        FieldLabel(stringResource(R.string.pay_from))
        ChipGroup {
            accounts.forEach { option ->
                OrbitaChip(
                    option.name,
                    option.id == selectedId,
                    {
                        selectedId = option.id
                        amount = suggestedAmount(option)
                    },
                )
            }
        }

        FieldLabel(stringResource(R.string.pay_real_amount))
        OrbitaTextField(
            value = amount,
            onValueChange = { amount = it },
            placeholder = "0.00",
            prefix = currencySymbol(account?.currency ?: purchase.currency),
            keyboardType = KeyboardType.Decimal,
        )
        HintText(stringResource(R.string.pay_real_amount_hint), Modifier.padding(top = 8.dp))

        FieldLabel(stringResource(R.string.pay_date))
        PickerField(formatDate(paidOn), onClick = { pickingDate = true })

        if (account == null) return@ScreenScaffold
        Spacer(Modifier.height(20.dp))
        OrbitaCard {
            Text(
                stringResource(R.string.pay_preview_title),
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
            Spacer(Modifier.height(10.dp))
            LabelValueRow(
                "${purchase.category.name} · ${account.name}",
                formatSignedMoney(paid, account.currency, positive = false),
                labelColor = Ink,
                valueColor = Expense,
            )
            CardDivider()
            LabelValueRow(
                stringResource(R.string.pay_balance_of, account.name),
                formatMoney(account.balance, account.currency) + " → " +
                    formatMoney(account.balance - paid, account.currency),
                valueStyle = MaterialTheme.typography.labelMedium,
            )
        }
    }

    if (pickingDate) {
        // The payment becomes an expense, and an expense cannot be dated in the future.
        DateDialog(
            title = stringResource(R.string.calendar_date_title),
            date = paidOn,
            today = today,
            onConfirm = {
                paidOn = it
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

@Preview(name = "Pagar compra", widthDp = 390, heightDp = 900)
@Composable
private fun PayCreditPreview() {
    OrbitaPreview {
        PayCreditScreen(
            SampleData.creditPurchases.first(),
            SampleData.accounts,
            onBack = {},
            onConfirm = {},
        )
    }
}
