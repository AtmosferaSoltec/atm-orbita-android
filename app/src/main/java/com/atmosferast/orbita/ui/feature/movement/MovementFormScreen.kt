package com.atmosferast.orbita.ui.feature.movement

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.core.currencySymbol
import com.atmosferast.orbita.core.formatAmount
import com.atmosferast.orbita.core.formatDate
import com.atmosferast.orbita.ui.components.AmountInput
import com.atmosferast.orbita.ui.components.ChipGroup
import com.atmosferast.orbita.ui.components.CircleIconButton
import com.atmosferast.orbita.ui.components.ConfirmDialog
import com.atmosferast.orbita.ui.components.FieldLabel
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.ModalTopBar
import com.atmosferast.orbita.ui.components.OrbitaCard
import com.atmosferast.orbita.ui.components.OrbitaChip
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.OrbitaTextField
import com.atmosferast.orbita.ui.components.PickerField
import com.atmosferast.orbita.ui.components.PrimaryButton
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.components.SegmentedControl
import com.atmosferast.orbita.ui.components.SwitchRow
import com.atmosferast.orbita.ui.mock.MockAccount
import com.atmosferast.orbita.ui.mock.MockMovement
import com.atmosferast.orbita.ui.mock.MovementKind
import com.atmosferast.orbita.ui.mock.SampleData
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.ExpenseSoft
import com.atmosferast.orbita.ui.theme.Income
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.OrbitaTheme

private enum class FormTab { EXPENSE, INCOME, TRANSFER }

/**
 * New movement, or edit/delete when [editing] is set. With "Compra con tarjeta de crédito" on,
 * it saves a pending purchase instead of a movement (v1.1).
 */
@Composable
fun MovementFormScreen(
    accounts: List<MockAccount>,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onOpenTransfer: () -> Unit,
    modifier: Modifier = Modifier,
    editing: MockMovement? = null,
    initialCredit: Boolean = false,
    onDelete: () -> Unit = {},
) {
    var kind by remember { mutableStateOf(editing?.kind ?: MovementKind.EXPENSE) }
    var amount by remember {
        mutableStateOf(editing?.let { formatAmount(it.amount) } ?: "45.52")
    }
    var account by remember { mutableStateOf(editing?.account ?: SampleData.debitAccount) }
    var category by remember {
        mutableStateOf(editing?.category ?: SampleData.categoriesOf(kind).first())
    }
    var description by remember {
        mutableStateOf(editing?.description ?: "Almuerzo con equipo")
    }
    var credit by remember { mutableStateOf(initialCredit) }
    var confirmDelete by remember { mutableStateOf(false) }

    val isExpense = kind == MovementKind.EXPENSE
    val onCredit = credit && isExpense && editing == null
    val symbol = currencySymbol(if (onCredit) PEN else account.currency)

    ScreenScaffold(
        modifier = modifier,
        header = {
            ModalTopBar(
                title = stringResource(
                    if (editing != null) R.string.movement_edit_title
                    else R.string.movement_new_title,
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
                    when {
                        editing != null -> R.string.action_save_changes
                        onCredit -> R.string.movement_save_credit
                        isExpense -> R.string.movement_save_expense
                        else -> R.string.movement_save_income
                    },
                ),
                onSave,
            )
        },
    ) {
        val tabs = buildList {
            add(FormTab.EXPENSE to stringResource(R.string.kind_expense))
            add(FormTab.INCOME to stringResource(R.string.kind_income))
            // An existing movement cannot become a transfer.
            if (editing == null) add(FormTab.TRANSFER to stringResource(R.string.kind_transfer))
        }
        SegmentedControl(
            options = tabs,
            selected = if (isExpense) FormTab.EXPENSE else FormTab.INCOME,
            onSelect = { tab ->
                when (tab) {
                    FormTab.TRANSFER -> onOpenTransfer()
                    else -> {
                        kind = if (tab == FormTab.EXPENSE) MovementKind.EXPENSE else MovementKind.INCOME
                        // Changing the type selects its first category.
                        category = SampleData.categoriesOf(kind).first()
                    }
                }
            },
        )

        Spacer(Modifier.height(14.dp))
        OrbitaCard {
            Text(
                stringResource(R.string.field_amount),
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
            Spacer(Modifier.height(4.dp))
            AmountInput(
                symbol = symbol,
                value = amount,
                onValueChange = { amount = it },
                color = if (isExpense) Ink else Income,
            )
        }

        if (!onCredit) {
            FieldLabel(
                stringResource(
                    if (isExpense) R.string.movement_from_account else R.string.movement_to_account,
                ),
            )
            ChipGroup {
                accounts.forEach { option ->
                    OrbitaChip(option.name, option.id == account.id, { account = option })
                }
            }
        }

        FieldLabel(stringResource(R.string.field_category))
        ChipGroup {
            SampleData.categoriesOf(kind).forEach { option ->
                OrbitaChip(
                    option.name,
                    option == category,
                    { category = option },
                    dotColor = option.color,
                )
            }
        }

        FieldLabel(stringResource(R.string.field_description))
        OrbitaTextField(
            value = description,
            onValueChange = { description = it },
            placeholder = stringResource(R.string.movement_description_placeholder),
        )

        FieldLabel(stringResource(R.string.field_date))
        PickerField(formatDate(editing?.date ?: SampleData.today), onClick = {})

        // Only for new expenses (v1.1)
        if (isExpense && editing == null) {
            Spacer(Modifier.height(18.dp))
            OrbitaCard {
                SwitchRow(
                    label = stringResource(R.string.movement_credit_toggle),
                    checked = credit,
                    onCheckedChange = { credit = it },
                    labelStyle = MaterialTheme.typography.titleSmall,
                )
                HintText(stringResource(R.string.movement_credit_hint))
                if (credit) {
                    FieldLabel(stringResource(R.string.credit_due_date))
                    PickerField(formatDate(SampleData.today.plusDays(13)), onClick = {})
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_movement_title),
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

@Preview(name = "Nuevo movimiento · egreso", widthDp = 390, heightDp = 1000)
@Composable
private fun MovementNewPreview() {
    OrbitaTheme {
        MovementFormScreen(SampleData.accounts, onClose = {}, onSave = {}, onOpenTransfer = {})
    }
}

@Preview(name = "Nuevo movimiento · compra con tarjeta", widthDp = 390, heightDp = 1000)
@Composable
private fun MovementCreditPreview() {
    OrbitaTheme {
        MovementFormScreen(
            SampleData.accounts,
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
    OrbitaTheme {
        MovementFormScreen(
            SampleData.accounts,
            onClose = {},
            onSave = {},
            onOpenTransfer = {},
            editing = SampleData.lunch,
        )
    }
}
