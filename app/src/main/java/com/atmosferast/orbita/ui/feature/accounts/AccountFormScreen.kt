package com.atmosferast.orbita.ui.feature.accounts

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.core.currencyInfo
import com.atmosferast.orbita.core.currencySymbol
import com.atmosferast.orbita.core.supportedCurrencies
import com.atmosferast.orbita.ui.components.ChipGroup
import com.atmosferast.orbita.ui.components.ConfirmDialog
import com.atmosferast.orbita.ui.components.DropdownField
import com.atmosferast.orbita.ui.components.FieldLabel
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.ModalTopBar
import com.atmosferast.orbita.ui.components.OrbitaCard
import com.atmosferast.orbita.ui.components.OrbitaChip
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.OrbitaTextField
import com.atmosferast.orbita.ui.components.PillButton
import com.atmosferast.orbita.ui.components.PrimaryButton
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.components.SwitchRow
import com.atmosferast.orbita.ui.components.accountTypeLabel
import com.atmosferast.orbita.ui.mock.AccountType
import com.atmosferast.orbita.ui.mock.MockAccount
import com.atmosferast.orbita.ui.mock.SampleData
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.ExpenseSoft
import com.atmosferast.orbita.ui.theme.OrbitaTheme

/** New account ([account] = null) or edit account. Not in the original mockups (docs/05, section 9). */
@Composable
fun AccountFormScreen(
    account: MockAccount?,
    onClose: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    defaultCurrency: String = PEN,
) {
    val editing = account != null
    var name by remember { mutableStateOf(account?.name.orEmpty()) }
    var type by remember { mutableStateOf(account?.type ?: AccountType.DEBIT) }
    var currency by remember { mutableStateOf(account?.currency ?: defaultCurrency) }
    var initialBalance by remember { mutableStateOf("") }
    var includeInSavings by remember { mutableStateOf(account?.includeInSavings ?: true) }
    var confirmArchive by remember { mutableStateOf(false) }

    ScreenScaffold(
        modifier = modifier,
        header = {
            ModalTopBar(
                title = stringResource(
                    if (editing) R.string.account_edit_title else R.string.account_new_title,
                ),
                navigationIcon = if (editing) OrbitaIcons.ChevronLeft else OrbitaIcons.Close,
                navigationLabel = stringResource(
                    if (editing) R.string.action_back else R.string.action_close,
                ),
                onNavigate = onClose,
            )
        },
        footer = {
            PrimaryButton(
                stringResource(
                    if (editing) R.string.action_save_changes else R.string.account_save,
                ),
                onSave,
            )
        },
    ) {
        FieldLabel(stringResource(R.string.field_name))
        OrbitaTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.account_name_placeholder),
        )

        FieldLabel(stringResource(R.string.field_type))
        ChipGroup {
            AccountType.entries.forEach { option ->
                OrbitaChip(accountTypeLabel(option), type == option, { type = option })
            }
        }

        FieldLabel(stringResource(R.string.field_currency))
        if (editing) {
            // The currency cannot change once the account has movements.
            ChipGroup {
                OrbitaChip(currencyInfo(currency).label, selected = true, onClick = {})
            }
            HintText(
                stringResource(R.string.account_currency_locked),
                Modifier.padding(top = 8.dp),
            )
        } else {
            // Starts on the user's default currency (Ajustes).
            DropdownField(
                options = supportedCurrencies,
                selected = currencyInfo(currency),
                onSelect = { currency = it.code },
                label = { it.label },
                detail = { it.code },
            )
            FieldLabel(stringResource(R.string.account_initial_balance))
            OrbitaTextField(
                value = initialBalance,
                onValueChange = { initialBalance = it },
                placeholder = "0.00",
                prefix = currencySymbol(currency),
                keyboardType = KeyboardType.Decimal,
            )
        }

        Spacer(Modifier.height(18.dp))
        OrbitaCard {
            SwitchRow(
                label = stringResource(R.string.account_include_in_savings),
                checked = includeInSavings,
                onCheckedChange = { includeInSavings = it },
            )
        }

        if (editing) {
            Spacer(Modifier.height(24.dp))
            PillButton(
                stringResource(R.string.account_archive),
                onClick = { confirmArchive = true },
                icon = OrbitaIcons.Archive,
                container = ExpenseSoft,
                content = Expense,
            )
            HintText(stringResource(R.string.account_archive_hint), Modifier.padding(top = 8.dp))
        }
    }

    if (confirmArchive) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_archive_account_title),
            text = stringResource(R.string.account_archive_hint),
            confirmLabel = stringResource(R.string.action_archive),
            onConfirm = {
                confirmArchive = false
                onSave()
            },
            onDismiss = { confirmArchive = false },
        )
    }
}

@Preview(name = "Nueva cuenta", widthDp = 390, heightDp = 844)
@Composable
private fun AccountNewPreview() {
    OrbitaTheme { AccountFormScreen(account = null, onClose = {}, onSave = {}) }
}

@Preview(name = "Editar cuenta", widthDp = 390, heightDp = 844)
@Composable
private fun AccountEditPreview() {
    OrbitaTheme { AccountFormScreen(SampleData.debitAccount, onClose = {}, onSave = {}) }
}
