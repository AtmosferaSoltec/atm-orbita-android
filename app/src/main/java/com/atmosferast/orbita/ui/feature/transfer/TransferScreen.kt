package com.atmosferast.orbita.ui.feature.transfer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.USD
import com.atmosferast.orbita.core.currencySymbol
import com.atmosferast.orbita.core.formatDate
import com.atmosferast.orbita.core.formatMoney
import com.atmosferast.orbita.core.formatRate
import com.atmosferast.orbita.ui.components.AmountInput
import com.atmosferast.orbita.ui.components.CardDivider
import com.atmosferast.orbita.ui.components.CircleIconButton
import com.atmosferast.orbita.ui.components.ConfirmDialog
import com.atmosferast.orbita.ui.components.FieldLabel
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.IconBadge
import com.atmosferast.orbita.ui.components.LabelValueRow
import com.atmosferast.orbita.ui.components.ModalTopBar
import com.atmosferast.orbita.ui.components.OrbitaCard
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.OrbitaTextField
import com.atmosferast.orbita.ui.components.PickerField
import com.atmosferast.orbita.ui.components.PrimaryButton
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.components.SectionTitle
import com.atmosferast.orbita.ui.components.SegmentedControl
import com.atmosferast.orbita.ui.components.accountIcon
import com.atmosferast.orbita.ui.mock.MockAccount
import com.atmosferast.orbita.ui.mock.MockTransfer
import com.atmosferast.orbita.ui.mock.SampleData
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.ExpenseSoft
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.Neutral
import com.atmosferast.orbita.ui.theme.NeutralSoft
import com.atmosferast.orbita.ui.theme.OrbitaShapes
import com.atmosferast.orbita.ui.theme.OrbitaTheme
import com.atmosferast.orbita.ui.theme.Outline
import com.atmosferast.orbita.ui.theme.Primary
import com.atmosferast.orbita.ui.theme.PrimarySoft
import com.atmosferast.orbita.ui.theme.Surface
import java.math.BigDecimal
import java.math.RoundingMode

private enum class TransferTab { EXPENSE, INCOME, TRANSFER }

private fun parseAmount(text: String): BigDecimal? =
    text.replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

private fun plain(amount: BigDecimal): String =
    amount.setScale(2, RoundingMode.HALF_UP).toPlainString()

/** Transfer between two own accounts; with different currencies out, rate and in are linked. */
@Composable
fun TransferScreen(
    onClose: () -> Unit,
    onSave: () -> Unit,
    onOpenMovement: () -> Unit,
    modifier: Modifier = Modifier,
    from: MockAccount = SampleData.dollarAccount,
    to: MockAccount = SampleData.debitAccount,
    editing: MockTransfer? = null,
    onDelete: () -> Unit = {},
) {
    val sameCurrency = from.currency == to.currency
    val referenceRate = remember(from, to) {
        if (from.currency == to.currency) BigDecimal.ONE
        else if (from.currency == USD) SampleData.usdToPen
        else BigDecimal.ONE.divide(SampleData.usdToPen, 6, RoundingMode.HALF_UP)
    }
    var out by remember { mutableStateOf(plain(editing?.fromAmount ?: BigDecimal("20.00"))) }
    var rate by remember { mutableStateOf(formatRate(referenceRate)) }
    var incoming by remember {
        mutableStateOf(
            plain(editing?.toAmount ?: BigDecimal("20.00").multiply(referenceRate)),
        )
    }
    var note by remember { mutableStateOf(editing?.note.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }

    // in = round(out × rate, 2)
    fun recalculateIncoming(newOut: String, newRate: String) {
        val o = parseAmount(newOut)
        val r = parseAmount(newRate)
        if (o != null && r != null) {
            incoming = plain(o.multiply(r))
        }
    }

    val outAmount = parseAmount(out) ?: BigDecimal.ZERO
    val inAmount = if (sameCurrency) outAmount else parseAmount(incoming) ?: BigDecimal.ZERO
    val fromAfter = from.balance - outAmount
    val toAfter = to.balance + inAmount

    ScreenScaffold(
        modifier = modifier,
        header = {
            ModalTopBar(
                title = stringResource(
                    if (editing != null) R.string.transfer_edit_title else R.string.transfer_title,
                ),
                navigationIcon = OrbitaIcons.Close,
                navigationLabel = stringResource(R.string.action_close),
                onNavigate = onClose,
                action = if (editing == null) null else {
                    {
                        CircleIconButton(
                            OrbitaIcons.Trash,
                            stringResource(R.string.action_delete),
                            onClick = { confirmDelete = true },
                            container = ExpenseSoft,
                            tint = Expense,
                        )
                    }
                },
            )
        },
        footer = {
            PrimaryButton(
                stringResource(
                    if (editing != null) R.string.action_save_changes else R.string.transfer_save,
                ),
                onSave,
            )
        },
    ) {
        if (editing == null) {
            SegmentedControl(
                options = listOf(
                    TransferTab.EXPENSE to stringResource(R.string.kind_expense),
                    TransferTab.INCOME to stringResource(R.string.kind_income),
                    TransferTab.TRANSFER to stringResource(R.string.kind_transfer),
                ),
                selected = TransferTab.TRANSFER,
                onSelect = { if (it != TransferTab.TRANSFER) onOpenMovement() },
            )
            Spacer(Modifier.height(14.dp))
        }

        Box(contentAlignment = Alignment.Center) {
            Column {
                AccountSlot(stringResource(R.string.transfer_from), from)
                Spacer(Modifier.height(10.dp))
                AccountSlot(stringResource(R.string.transfer_to), to)
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .border(BorderStroke(1.dp, Outline), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    OrbitaIcons.ArrowDown,
                    contentDescription = null,
                    tint = Neutral,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OrbitaCard {
            CardLabel(stringResource(R.string.transfer_amount_out))
            AmountInput(
                symbol = currencySymbol(from.currency),
                value = out,
                onValueChange = {
                    out = it
                    recalculateIncoming(it, rate)
                },
            )
            // Same currency: no exchange rate, in = out.
            if (!sameCurrency) {
                CardDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardLabel(stringResource(R.string.transfer_rate_label), Modifier.weight(1f))
                    CardLabel(stringResource(R.string.transfer_reference))
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            R.string.transfer_rate_prefix,
                            currencySymbol(from.currency),
                            currencySymbol(to.currency),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                    )
                    Spacer(Modifier.width(8.dp))
                    RateInput(
                        value = rate,
                        onValueChange = {
                            rate = it
                            recalculateIncoming(out, it)
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(
                            R.string.transfer_reference_manual,
                            formatRate(referenceRate),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
                CardDivider()
                CardLabel(stringResource(R.string.transfer_amount_in))
                AmountInput(
                    symbol = currencySymbol(to.currency),
                    value = incoming,
                    onValueChange = { text ->
                        incoming = text
                        // rate = in ÷ out
                        val i = parseAmount(text)
                        val o = parseAmount(out)
                        if (i != null && o != null) {
                            rate = formatRate(i.divide(o, 6, RoundingMode.HALF_UP))
                        }
                    },
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }
        if (!sameCurrency) {
            HintText(
                stringResource(R.string.transfer_hint),
                Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
            )
        }

        SectionTitle(stringResource(R.string.transfer_balances_after))
        OrbitaCard {
            LabelValueRow(
                from.name,
                formatMoney(fromAfter, from.currency),
                valueColor = if (fromAfter.signum() < 0) Expense else Ink,
            )
            Spacer(Modifier.height(10.dp))
            LabelValueRow(to.name, formatMoney(toAfter, to.currency))
        }
        // Warn, but do not block.
        if (fromAfter.signum() < 0) {
            HintText(
                stringResource(R.string.transfer_negative_warning, from.name),
                Modifier.padding(top = 8.dp, start = 4.dp),
                color = Expense,
            )
        }

        FieldLabel(stringResource(R.string.field_date))
        PickerField(formatDate(editing?.date ?: SampleData.today), onClick = {})

        FieldLabel(stringResource(R.string.field_note))
        OrbitaTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = stringResource(R.string.transfer_note_placeholder),
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_transfer_title),
            text = stringResource(R.string.dialog_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun CardLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Muted, modifier = modifier)
}

/** "Desde" / "Hacia" card; tapping it opens the account selector. */
@Composable
private fun AccountSlot(label: String, account: MockAccount) {
    OrbitaCard(onClick = {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(accountIcon(account), Primary, PrimarySoft, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                CardLabel(label)
                Text(
                    account.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(
                    R.string.transfer_balance,
                    formatMoney(account.balance, account.currency),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
    }
}

@Composable
private fun RateInput(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = Ink),
        cursorBrush = SolidColor(Primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
            .width(88.dp)
            .clip(OrbitaShapes.Field)
            .background(NeutralSoft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Preview(name = "Transferencia · entre monedas", widthDp = 390, heightDp = 1100)
@Composable
private fun TransferPreview() {
    OrbitaTheme { TransferScreen(onClose = {}, onSave = {}, onOpenMovement = {}) }
}

@Preview(name = "Transferencia · misma moneda", widthDp = 390, heightDp = 1000)
@Composable
private fun TransferSameCurrencyPreview() {
    OrbitaTheme {
        TransferScreen(
            onClose = {},
            onSave = {},
            onOpenMovement = {},
            from = SampleData.debitAccount,
            to = SampleData.accounts.first { it.id == "savings" },
        )
    }
}

@Preview(name = "Editar transferencia", widthDp = 390, heightDp = 1100)
@Composable
private fun TransferEditPreview() {
    OrbitaTheme {
        TransferScreen(
            onClose = {},
            onSave = {},
            onOpenMovement = {},
            editing = SampleData.dollarExchange,
        )
    }
}
