package com.atmosferast.orbita.ui.feature.credit

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.core.currencyInfo
import com.atmosferast.orbita.core.supportedCurrencies
import com.atmosferast.orbita.ui.components.ConfirmDialog
import com.atmosferast.orbita.ui.components.DropdownField
import com.atmosferast.orbita.ui.components.FieldLabel
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.ModalTopBar
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.OrbitaTextField
import com.atmosferast.orbita.ui.components.PillButton
import com.atmosferast.orbita.ui.components.PrimaryButton
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.mock.MockCreditCard
import com.atmosferast.orbita.ui.mock.SampleData
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.ExpenseSoft
import com.atmosferast.orbita.ui.theme.OrbitaTheme

/** New credit card ([card] = null) or edit credit card (v1.1). */
@Composable
fun CreditCardFormScreen(
    card: MockCreditCard?,
    onClose: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    defaultCurrency: String = PEN,
) {
    val editing = card != null
    var name by remember { mutableStateOf(card?.name.orEmpty()) }
    // A new card starts on the user's main currency (Ajustes).
    var currency by remember { mutableStateOf(card?.currency ?: defaultCurrency) }
    var confirmArchive by remember { mutableStateOf(false) }

    ScreenScaffold(
        modifier = modifier,
        header = {
            ModalTopBar(
                title = stringResource(
                    if (editing) R.string.credit_card_edit_title else R.string.credit_card_new_title,
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
                    if (editing) R.string.action_save_changes else R.string.credit_card_save,
                ),
                onSave,
            )
        },
    ) {
        FieldLabel(stringResource(R.string.field_name))
        OrbitaTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.credit_card_name_placeholder),
        )

        FieldLabel(stringResource(R.string.field_currency))
        DropdownField(
            options = supportedCurrencies,
            selected = currencyInfo(currency),
            onSelect = { currency = it.code },
            label = { it.label },
            detail = { it.code },
        )
        HintText(stringResource(R.string.credit_card_currency_hint), Modifier.padding(top = 8.dp))

        if (editing) {
            Spacer(Modifier.height(24.dp))
            PillButton(
                stringResource(R.string.credit_card_archive),
                onClick = { confirmArchive = true },
                icon = OrbitaIcons.Archive,
                container = ExpenseSoft,
                content = Expense,
            )
            HintText(
                stringResource(R.string.credit_card_archive_hint),
                Modifier.padding(top = 8.dp),
            )
        }
    }

    if (confirmArchive) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_archive_card_title),
            text = stringResource(R.string.credit_card_archive_hint),
            confirmLabel = stringResource(R.string.action_archive),
            onConfirm = {
                confirmArchive = false
                onSave()
            },
            onDismiss = { confirmArchive = false },
        )
    }
}

@Preview(name = "Nueva tarjeta", widthDp = 390, heightDp = 844)
@Composable
private fun CreditCardNewPreview() {
    OrbitaTheme { CreditCardFormScreen(card = null, onClose = {}, onSave = {}) }
}

@Preview(name = "Editar tarjeta", widthDp = 390, heightDp = 844)
@Composable
private fun CreditCardEditPreview() {
    OrbitaTheme {
        CreditCardFormScreen(SampleData.creditCards.first(), onClose = {}, onSave = {})
    }
}
