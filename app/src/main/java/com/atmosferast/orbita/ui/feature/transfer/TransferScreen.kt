package com.atmosferast.orbita.ui.feature.transfer

import com.atmosferast.orbita.ui.components.LocalToday
import com.atmosferast.orbita.domain.model.TransferDraft
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import com.atmosferast.orbita.core.MAX_AMOUNT_CENTS
import com.atmosferast.orbita.core.amountToCents
import com.atmosferast.orbita.core.appendAmountDigits
import com.atmosferast.orbita.core.centsToAmount
import com.atmosferast.orbita.core.removeAmountDigit
import com.atmosferast.orbita.ui.components.AmountDisplay
import com.atmosferast.orbita.ui.components.AmountKeypad
import kotlinx.coroutines.delay
import com.atmosferast.orbita.core.currencySymbol
import com.atmosferast.orbita.core.formatDate
import com.atmosferast.orbita.core.formatMoney
import com.atmosferast.orbita.core.formatRate
import com.atmosferast.orbita.ui.components.CardDivider
import com.atmosferast.orbita.ui.components.CircleIconButton
import com.atmosferast.orbita.ui.components.ConfirmDialog
import com.atmosferast.orbita.ui.components.DateDialog
import com.atmosferast.orbita.ui.components.DropdownField
import com.atmosferast.orbita.ui.components.FieldLabel
import com.atmosferast.orbita.ui.components.FormIntro
import com.atmosferast.orbita.ui.components.HintText
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
import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.FxPair
import com.atmosferast.orbita.domain.model.Transfer
import com.atmosferast.orbita.data.demo.SampleData
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.ExpenseSoft
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.NeutralSoft
import com.atmosferast.orbita.ui.theme.OrbitaShapes
import com.atmosferast.orbita.ui.components.OrbitaPreview
import com.atmosferast.orbita.ui.theme.Outline
import com.atmosferast.orbita.ui.theme.Primary
import com.atmosferast.orbita.ui.theme.Surface
import com.atmosferast.orbita.ui.theme.Transfer
import com.atmosferast.orbita.ui.theme.TransferSoft
import java.math.BigDecimal
import java.math.RoundingMode

private enum class TransferTab { EXPENSE, INCOME, TRANSFER }

private fun parseAmount(text: String): BigDecimal? =
    text.replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

/** The two amounts of a transfer; the in-app keypad types into one of them at a time. */
private enum class AmountField { OUT, IN }

/**
 * Manual rate: units of [to]'s currency per 1 unit of [from]'s. Outside the user's pair of
 * currencies there is no rate: it is left empty for the user to type, never made up.
 */
private fun referenceRate(from: Account, to: Account, fx: FxPair): BigDecimal? =
    fx.rateBetween(from.currency, to.currency)

private fun rateText(rate: BigDecimal?): String = rate?.let(::formatRate).orEmpty()

/**
 * Transfer between two own accounts, both chosen by the user. With different currencies the
 * amount out, the (manual) rate and the amount in are linked; with the same currency there is
 * no exchange.
 */
@Composable
fun TransferScreen(
    accounts: List<Account>,
    initialFrom: Account,
    initialTo: Account,
    fx: FxPair,
    onClose: () -> Unit,
    onSave: (draft: TransferDraft, sameCurrency: Boolean) -> Unit,
    onOpenMovement: () -> Unit,
    modifier: Modifier = Modifier,
    editing: Transfer? = null,
    onDelete: () -> Unit = {},
) {
    val today = LocalToday.current
    var from by remember { mutableStateOf(initialFrom) }
    var to by remember { mutableStateOf(initialTo) }
    val sameCurrency = from.currency == to.currency
    val referenceRate = referenceRate(from, to, fx)
    // A new transfer starts empty; an edited one, with what was saved. Both amounts are typed
    // with the in-app keypad, so they are kept in cents like the amount of a movement.
    var outCents by remember { mutableStateOf(editing?.let { amountToCents(it.fromAmount) } ?: 0L) }
    var rate by remember { mutableStateOf(rateText(editing?.exchangeRate ?: referenceRate)) }
    var inCents by remember { mutableStateOf(editing?.let { amountToCents(it.toAmount) } ?: 0L) }
    // The amount the keypad is typing into; null while it is closed.
    var keypadField by remember { mutableStateOf<AmountField?>(null) }
    var date by remember { mutableStateOf(editing?.date ?: today) }
    var pickingDate by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf(editing?.note.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val amountsCard = remember { BringIntoViewRequester() }

    // in = round(out × rate, 2)
    fun recalculateIncoming(newOutCents: Long, newRate: String) {
        val r = parseAmount(newRate) ?: return
        val converted = centsToAmount(newOutCents).multiply(r).setScale(2, RoundingMode.HALF_UP)
        inCents = amountToCents(converted).coerceAtMost(MAX_AMOUNT_CENTS)
    }

    // Changing an account resets the rate to the manual one of the new pair of currencies.
    fun setAccounts(newFrom: Account, newTo: Account) {
        from = newFrom
        to = newTo
        rate = rateText(referenceRate(newFrom, newTo, fx))
        // Without a rate for the new pair, what comes in is unknown until the user types it.
        if (rate.isEmpty() && newFrom.currency != newTo.currency) inCents = 0L
        recalculateIncoming(outCents, rate)
    }

    // What the keypad types goes to the active amount; the other values follow.
    fun typeAmount(change: (Long) -> Long) {
        when (keypadField) {
            AmountField.OUT -> {
                outCents = change(outCents)
                recalculateIncoming(outCents, rate)
            }

            AmountField.IN -> {
                inCents = change(inCents)
                // rate = in ÷ out
                if (inCents > 0 && outCents > 0) {
                    rate = formatRate(
                        centsToAmount(inCents).divide(centsToAmount(outCents), 6, RoundingMode.HALF_UP),
                    )
                }
            }

            null -> Unit
        }
    }

    // The amounts never use the system keyboard.
    fun openKeypad(field: AmountField) {
        focusManager.clearFocus()
        keypadField = field
    }

    BackHandler(enabled = keypadField != null) { keypadField = null }
    // The keypad takes the foot of the screen: keep the amounts in sight above it.
    LaunchedEffect(keypadField) {
        if (keypadField != null) {
            delay(150)
            amountsCard.bringIntoView()
        }
    }

    val outAmount = centsToAmount(outCents)
    val inAmount = if (sameCurrency) outAmount else centsToAmount(inCents)
    // When editing, the balances already count this transfer: it is taken back first.
    fun withoutEdited(account: Account): BigDecimal =
        account.balance +
            (editing?.takeIf { it.from.id == account.id }?.fromAmount ?: BigDecimal.ZERO) -
            (editing?.takeIf { it.to.id == account.id }?.toAmount ?: BigDecimal.ZERO)
    val fromAfter = withoutEdited(from) - outAmount
    val toAfter = withoutEdited(to) + inAmount

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
            if (keypadField != null) {
                AmountKeypad(
                    onDigits = { digits -> typeAmount { appendAmountDigits(it, digits) } },
                    onBackspace = { typeAmount(::removeAmountDigit) },
                    onClear = { typeAmount { 0L } },
                    onDone = { keypadField = null },
                )
            } else {
                PrimaryButton(
                    stringResource(
                        if (editing != null) R.string.action_save_changes else R.string.transfer_save,
                    ),
                    onClick = {
                        onSave(
                            TransferDraft(
                                fromAccountId = from.id,
                                toAccountId = to.id,
                                fromAmount = outAmount,
                                // Same currency: no exchange, what goes out is what comes in.
                                toAmount = inAmount,
                                exchangeRate = if (sameCurrency) null else parseAmount(rate),
                                date = date,
                                note = note,
                            ),
                            sameCurrency,
                        )
                    },
                )
            }
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
            Spacer(Modifier.height(16.dp))
            FormIntro(
                icon = OrbitaIcons.Swap,
                tint = Transfer,
                container = TransferSoft,
                title = stringResource(R.string.transfer_intro_title),
                subtitle = stringResource(R.string.transfer_intro_subtitle),
            )
            Spacer(Modifier.height(4.dp))
        }

        // The same account cannot be on both sides: picking the other side's account swaps them.
        FieldLabel(stringResource(R.string.transfer_from))
        AccountDropdown(
            accounts = accounts,
            selected = from,
            onSelect = { if (it.id == to.id) setAccounts(to, from) else setAccounts(it, to) },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .border(BorderStroke(1.dp, Outline), CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.transfer_swap),
                    ) { setAccounts(to, from) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    OrbitaIcons.SwapVertical,
                    contentDescription = stringResource(R.string.transfer_swap),
                    tint = Primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        FieldLabel(stringResource(R.string.transfer_to))
        AccountDropdown(
            accounts = accounts,
            selected = to,
            onSelect = { if (it.id == from.id) setAccounts(to, from) else setAccounts(from, it) },
        )

        Spacer(Modifier.height(12.dp))
        OrbitaCard(
            modifier = Modifier
                .bringIntoViewRequester(amountsCard)
                .then(
                    if (keypadField != null) Modifier.border(1.5.dp, Primary, OrbitaShapes.Card)
                    else Modifier,
                ),
        ) {
            CardLabel(stringResource(R.string.transfer_amount_out))
            AmountDisplay(
                symbol = currencySymbol(from.currency),
                cents = outCents,
                active = keypadField == AmountField.OUT,
                modifier = Modifier
                    .clip(OrbitaShapes.Field)
                    .clickable(role = Role.Button) { openKeypad(AmountField.OUT) },
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
                    // The rate has up to 4 decimals: it keeps the decimal keyboard of the system.
                    RateInput(
                        value = rate,
                        onValueChange = {
                            rate = it
                            recalculateIncoming(outCents, it)
                        },
                        modifier = Modifier.onFocusChanged { if (it.hasFocus) keypadField = null },
                    )
                    Spacer(Modifier.weight(1f))
                    // Only when there is a manual rate for this pair of currencies.
                    if (referenceRate != null) {
                        Text(
                            stringResource(
                                R.string.transfer_reference_manual,
                                formatRate(referenceRate),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }
                CardDivider()
                CardLabel(stringResource(R.string.transfer_amount_in))
                AmountDisplay(
                    symbol = currencySymbol(to.currency),
                    cents = inCents,
                    active = keypadField == AmountField.IN,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .clip(OrbitaShapes.Field)
                        .clickable(role = Role.Button) { openKeypad(AmountField.IN) },
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
        PickerField(formatDate(date), onClick = { pickingDate = true })

        FieldLabel(stringResource(R.string.field_note))
        OrbitaTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = stringResource(R.string.transfer_note_placeholder),
            modifier = Modifier.onFocusChanged { if (it.hasFocus) keypadField = null },
        )
    }

    if (pickingDate) {
        // A transfer cannot be dated in the future.
        DateDialog(
            title = stringResource(R.string.calendar_date_title),
            date = date,
            today = today,
            onConfirm = {
                date = it
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
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

/** "Desde" / "Hacia" selector: every account with its icon and balance. */
@Composable
private fun AccountDropdown(
    accounts: List<Account>,
    selected: Account,
    onSelect: (Account) -> Unit,
) {
    DropdownField(
        options = accounts,
        // The list may hold a newer copy of the account (e.g. savings switch changed).
        selected = accounts.firstOrNull { it.id == selected.id } ?: selected,
        onSelect = onSelect,
        label = { it.name },
        detail = { formatMoney(it.balance, it.currency) },
        leading = { option ->
            Icon(
                accountIcon(option),
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(20.dp),
            )
        },
    )
}

@Composable
private fun RateInput(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = Ink),
        cursorBrush = SolidColor(Primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
            .width(88.dp)
            .clip(OrbitaShapes.Field)
            .background(NeutralSoft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Preview(name = "Transferencia · entre monedas", widthDp = 390, heightDp = 1100)
@Composable
private fun TransferPreview() {
    OrbitaPreview {
        TransferScreen(
            SampleData.accounts,
            initialFrom = SampleData.dollarAccount,
            initialTo = SampleData.debitAccount,
            fx = SampleData.fx,
            onClose = {},
            onSave = { _, _ -> },
            onOpenMovement = {},
        )
    }
}

@Preview(name = "Transferencia · misma moneda", widthDp = 390, heightDp = 1000)
@Composable
private fun TransferSameCurrencyPreview() {
    OrbitaPreview {
        TransferScreen(
            SampleData.accounts,
            initialFrom = SampleData.debitAccount,
            initialTo = SampleData.accounts.first { it.id == "savings" },
            fx = SampleData.fx,
            onClose = {},
            onSave = { _, _ -> },
            onOpenMovement = {},
        )
    }
}

@Preview(name = "Editar transferencia", widthDp = 390, heightDp = 1100)
@Composable
private fun TransferEditPreview() {
    OrbitaPreview {
        TransferScreen(
            SampleData.accounts,
            initialFrom = SampleData.dollarExchange.from,
            initialTo = SampleData.dollarExchange.to,
            fx = SampleData.fx,
            onClose = {},
            onSave = { _, _ -> },
            onOpenMovement = {},
            editing = SampleData.dollarExchange,
        )
    }
}
