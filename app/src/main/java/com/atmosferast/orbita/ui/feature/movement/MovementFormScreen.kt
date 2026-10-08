package com.atmosferast.orbita.ui.feature.movement

import com.atmosferast.orbita.core.centsToAmount
import com.atmosferast.orbita.ui.components.DateDialog
import com.atmosferast.orbita.ui.components.color
import com.atmosferast.orbita.ui.components.LocalToday
import com.atmosferast.orbita.domain.model.CreditPurchase
import com.atmosferast.orbita.domain.model.CreditPurchaseDraft
import com.atmosferast.orbita.domain.model.MovementDraft
import com.atmosferast.orbita.domain.model.CreditCard
import com.atmosferast.orbita.domain.model.Category
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.core.amountToCents
import com.atmosferast.orbita.core.appendAmountDigits
import com.atmosferast.orbita.core.currencySymbol
import com.atmosferast.orbita.core.removeAmountDigit
import com.atmosferast.orbita.core.formatDate
import com.atmosferast.orbita.core.formatMoney
import com.atmosferast.orbita.ui.components.AmountDisplay
import com.atmosferast.orbita.ui.components.AmountKeypad
import com.atmosferast.orbita.ui.components.CircleIconButton
import com.atmosferast.orbita.ui.components.ConfirmDialog
import com.atmosferast.orbita.ui.components.DropdownField
import com.atmosferast.orbita.ui.components.FieldLabel
import com.atmosferast.orbita.ui.components.FormIntro
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.ModalTopBar
import com.atmosferast.orbita.ui.components.OrbitaCard
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.OrbitaTextArea
import com.atmosferast.orbita.ui.components.PickerField
import com.atmosferast.orbita.ui.components.PrimaryButton
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.components.SegmentedControl
import com.atmosferast.orbita.ui.components.SwitchRow
import com.atmosferast.orbita.ui.components.accountIcon
import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.Movement
import com.atmosferast.orbita.domain.model.MovementKind
import com.atmosferast.orbita.data.demo.SampleData
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.ExpenseSoft
import com.atmosferast.orbita.ui.theme.Income
import com.atmosferast.orbita.ui.theme.IncomeSoft
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.OrbitaShapes
import com.atmosferast.orbita.ui.components.OrbitaPreview
import com.atmosferast.orbita.ui.theme.Primary

private enum class FormTab { EXPENSE, INCOME, TRANSFER }

/** Due date suggested for a new purchase with a credit card. */
private const val DEFAULT_DAYS_TO_PAY = 13L

/**
 * New movement, or edit/delete when [editing] is set. With "Compra con tarjeta de crédito" on,
 * it saves a pending purchase instead of a movement (v1.1); with [editingPurchase] it edits or
 * deletes a purchase that is still pending.
 */
@Composable
fun MovementFormScreen(
    accounts: List<Account>,
    categories: List<Category>,
    creditCards: List<CreditCard>,
    onClose: () -> Unit,
    onSave: (MovementDraft) -> Unit,
    onOpenTransfer: () -> Unit,
    modifier: Modifier = Modifier,
    editing: Movement? = null,
    initialCredit: Boolean = false,
    defaultAccount: Account? = null,
    onSavePurchase: (CreditPurchaseDraft) -> Unit = {},
    onDelete: () -> Unit = {},
    editingPurchase: CreditPurchase? = null,
    onDeletePurchase: () -> Unit = {},
) {
    val today = LocalToday.current
    fun categoriesOf(kind: MovementKind) = categories.filter { it.kind == kind }
    // Nothing exists yet: neither a movement nor a pending purchase is being edited.
    val isNew = editing == null && editingPurchase == null

    var kind by remember { mutableStateOf(editing?.kind ?: MovementKind.EXPENSE) }
    var amountCents by remember {
        mutableStateOf((editing?.amount ?: editingPurchase?.amount)?.let(::amountToCents) ?: 0L)
    }
    // A new movement starts on the amount, with the in-app keypad open.
    var keypadOpen by remember { mutableStateOf(isNew) }
    var accountId by remember {
        mutableStateOf((editing?.account ?: defaultAccount ?: accounts.firstOrNull())?.id)
    }
    var category by remember {
        mutableStateOf(
            editing?.category ?: editingPurchase?.category ?: categoriesOf(kind).firstOrNull(),
        )
    }
    var description by remember {
        mutableStateOf(editing?.description ?: editingPurchase?.description.orEmpty())
    }
    var date by remember { mutableStateOf(editing?.date) }
    var pickingDate by remember { mutableStateOf(false) }
    var credit by remember { mutableStateOf(initialCredit || editingPurchase != null) }
    var creditCard by remember { mutableStateOf(editingPurchase?.card ?: creditCards.firstOrNull()) }
    var dueDate by remember {
        mutableStateOf(editingPurchase?.dueDate ?: today.plusDays(DEFAULT_DAYS_TO_PAY))
    }
    var pickingDueDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // The list holds the newest copy of the account (its balance changes with every movement);
    // an edited movement may sit on an account that was archived since.
    val account = accounts.firstOrNull { it.id == accountId } ?: editing?.account
    val isExpense = kind == MovementKind.EXPENSE
    val onCredit = credit && isExpense && editing == null
    // A credit purchase is registered in the currency of its card. One being edited keeps the
    // currency it was bought in unless it is moved to another card.
    val purchaseCurrency = when {
        editingPurchase != null && creditCard?.id == editingPurchase.card.id -> editingPurchase.currency
        else -> creditCard?.currency
    }
    val symbol = currencySymbol((if (onCredit) purchaseCurrency else account?.currency) ?: PEN)
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = keypadOpen) { keypadOpen = false }

    ScreenScaffold(
        modifier = modifier,
        header = {
            ModalTopBar(
                title = stringResource(
                    when {
                        editingPurchase != null -> R.string.purchase_edit_title
                        editing != null -> R.string.movement_edit_title
                        else -> R.string.movement_new_title
                    },
                ),
                navigationIcon = OrbitaIcons.Close,
                navigationLabel = stringResource(R.string.action_close),
                onNavigate = onClose,
                action = if (isNew) null else {
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
            if (keypadOpen) {
                AmountKeypad(
                    onDigits = { amountCents = appendAmountDigits(amountCents, it) },
                    onBackspace = { amountCents = removeAmountDigit(amountCents) },
                    onClear = { amountCents = 0L },
                    onDone = { keypadOpen = false },
                )
            } else {
                PrimaryButton(
                    stringResource(
                        when {
                            !isNew -> R.string.action_save_changes
                            onCredit -> R.string.movement_save_credit
                            isExpense -> R.string.movement_save_expense
                            else -> R.string.movement_save_income
                        },
                    ),
                    onClick = {
                        val amount = centsToAmount(amountCents)
                        if (onCredit) {
                            onSavePurchase(
                                CreditPurchaseDraft(
                                    creditCard?.id, amount, category?.id, description, dueDate,
                                ),
                            )
                        } else {
                            onSave(
                                MovementDraft(kind, amount, account?.id, category?.id, description, date),
                            )
                        }
                    },
                )
            }
        },
    ) {
        val tabs = buildList {
            add(FormTab.EXPENSE to stringResource(R.string.kind_expense))
            add(FormTab.INCOME to stringResource(R.string.kind_income))
            // An existing movement cannot become a transfer.
            if (editing == null) add(FormTab.TRANSFER to stringResource(R.string.kind_transfer))
        }
        // A purchase with a credit card is always an expense: there is nothing to switch to.
        if (editingPurchase == null) {
            SegmentedControl(
                options = tabs,
                selected = if (isExpense) FormTab.EXPENSE else FormTab.INCOME,
                onSelect = { tab ->
                    when (tab) {
                        FormTab.TRANSFER -> onOpenTransfer()
                        else -> {
                            kind =
                                if (tab == FormTab.EXPENSE) MovementKind.EXPENSE else MovementKind.INCOME
                            // Changing the type selects its first category.
                            category = categoriesOf(kind).firstOrNull()
                        }
                    }
                },
            )
        }

        // Only when creating: the tabs alone do not tell the three forms apart.
        if (isNew) {
            Spacer(Modifier.height(16.dp))
            FormIntro(
                icon = if (isExpense) OrbitaIcons.ArrowDownLeft else OrbitaIcons.ArrowUpRight,
                tint = if (isExpense) Expense else Income,
                container = if (isExpense) ExpenseSoft else IncomeSoft,
                title = stringResource(
                    if (isExpense) R.string.movement_intro_expense_title
                    else R.string.movement_intro_income_title,
                ),
                subtitle = stringResource(
                    if (isExpense) R.string.movement_intro_expense_subtitle
                    else R.string.movement_intro_income_subtitle,
                ),
            )
        }

        Spacer(Modifier.height(14.dp))
        OrbitaCard(
            modifier = if (keypadOpen) Modifier.border(1.5.dp, Primary, OrbitaShapes.Card) else Modifier,
            onClick = {
                // The amount never uses the system keyboard.
                focusManager.clearFocus()
                keypadOpen = true
            },
        ) {
            Text(
                stringResource(R.string.field_amount),
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
            Spacer(Modifier.height(4.dp))
            AmountDisplay(
                symbol = symbol,
                cents = amountCents,
                color = if (isExpense) Ink else Income,
                active = keypadOpen,
            )
        }

        if (!onCredit) {
            FieldLabel(
                stringResource(
                    if (isExpense) R.string.movement_from_account else R.string.movement_to_account,
                ),
            )
            if (account == null) {
                HintText(stringResource(R.string.error_no_accounts_message))
            } else {
                DropdownField(
                    options = accounts,
                    selected = account,
                    onSelect = { accountId = it.id },
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
        }

        FieldLabel(stringResource(R.string.field_category))
        val selectedCategory = category
        if (selectedCategory == null) {
            HintText(stringResource(R.string.error_no_categories))
        } else {
            DropdownField(
                options = categoriesOf(kind),
                selected = selectedCategory,
                onSelect = { category = it },
                label = { it.name },
                leading = { option ->
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(option.color),
                    )
                },
            )
        }

        FieldLabel(stringResource(R.string.field_description))
        OrbitaTextArea(
            value = description,
            onValueChange = { description = it },
            placeholder = stringResource(R.string.movement_description_placeholder),
            maxLength = 500,
            modifier = Modifier.onFocusChanged { if (it.hasFocus) keypadOpen = false },
        )

        // A new movement takes the date and time of the moment it is saved; the date can only
        // be changed later, when editing.
        if (editing != null) {
            FieldLabel(stringResource(R.string.field_date))
            PickerField(formatDate(date ?: editing.date), onClick = { pickingDate = true })
        } else if (isNew) {
            Row(
                modifier = Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    OrbitaIcons.Clock,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                HintText(stringResource(R.string.movement_now_hint))
            }
        }

        // Only for new expenses (v1.1)
        if (isExpense && editing == null) {
            Spacer(Modifier.height(18.dp))
            OrbitaCard {
                // A pending purchase stays one: it cannot be turned into a movement here.
                if (editingPurchase == null) {
                    SwitchRow(
                        label = stringResource(R.string.movement_credit_toggle),
                        checked = credit,
                        onCheckedChange = { credit = it },
                        labelStyle = MaterialTheme.typography.titleSmall,
                    )
                }
                HintText(stringResource(R.string.movement_credit_hint))
                if (credit) {
                    FieldLabel(stringResource(R.string.credit_card_field))
                    val selectedCard = creditCard
                    if (selectedCard == null) {
                        HintText(stringResource(R.string.credit_no_cards))
                    } else {
                        DropdownField(
                            options = creditCards,
                            selected = selectedCard,
                            onSelect = { creditCard = it },
                            label = { it.name },
                            detail = { currencySymbol(it.currency) },
                            leading = {
                                Icon(
                                    OrbitaIcons.CreditCard,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                    }
                    FieldLabel(stringResource(R.string.credit_due_date))
                    PickerField(formatDate(dueDate), onClick = { pickingDueDate = true })
                }
            }
        }
    }

    if (pickingDate && editing != null) {
        // A movement cannot be dated in the future.
        DateDialog(
            title = stringResource(R.string.calendar_date_title),
            date = date ?: editing.date,
            today = today,
            onConfirm = {
                date = it
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }

    if (pickingDueDate) {
        // The one date that is in the future by nature.
        DateDialog(
            title = stringResource(R.string.credit_due_date),
            date = dueDate,
            today = today,
            allowFuture = true,
            onConfirm = {
                dueDate = it
                pickingDueDate = false
            },
            onDismiss = { pickingDueDate = false },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(
                if (editingPurchase != null) R.string.dialog_delete_purchase_title
                else R.string.dialog_delete_movement_title,
            ),
            text = stringResource(
                when {
                    editingPurchase != null -> R.string.dialog_delete_purchase_text
                    // The expense of a payment: deleting it leaves the purchase pending again.
                    editing?.creditPurchaseId != null -> R.string.dialog_delete_payment_text
                    else -> R.string.dialog_delete_text
                },
            ),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                if (editingPurchase != null) onDeletePurchase() else onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Preview(name = "Nuevo movimiento · egreso", widthDp = 390, heightDp = 1000)
@Composable
private fun MovementNewPreview() {
    OrbitaPreview {
        MovementFormScreen(
            SampleData.accounts,
            SampleData.categories,
            SampleData.creditCards,
            onClose = {},
            onSave = {},
            onOpenTransfer = {},
        )
    }
}

@Preview(name = "Nuevo movimiento · compra con tarjeta", widthDp = 390, heightDp = 1000)
@Composable
private fun MovementCreditPreview() {
    OrbitaPreview {
        MovementFormScreen(
            SampleData.accounts,
            SampleData.categories,
            SampleData.creditCards,
            onClose = {},
            onSave = {},
            onOpenTransfer = {},
            initialCredit = true,
        )
    }
}

@Preview(name = "Editar movimiento", widthDp = 390, heightDp = 1000)
@Composable
private fun MovementEditPreview() {
    OrbitaPreview {
        MovementFormScreen(
            SampleData.accounts,
            SampleData.categories,
            SampleData.creditCards,
            onClose = {},
            onSave = {},
            onOpenTransfer = {},
            editing = SampleData.lunch,
        )
    }
}
