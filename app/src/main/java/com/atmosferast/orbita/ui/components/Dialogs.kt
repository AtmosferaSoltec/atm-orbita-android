package com.atmosferast.orbita.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.Surface

/**
 * Deleting the account is the one action that cannot be undone at all, so it asks for the
 * password again. An empty password is told under the field and is not sent.
 */
@Composable
fun DeleteAccountDialog(onConfirm: (password: String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = {
            Text(
                stringResource(R.string.dialog_delete_account_title),
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
            )
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.dialog_delete_account_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                Spacer(Modifier.height(14.dp))
                OrbitaTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        missing = false
                    },
                    placeholder = stringResource(R.string.field_password),
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (missing) {
                    HintText(
                        stringResource(R.string.error_password_required),
                        Modifier.padding(top = 6.dp, start = 4.dp),
                        color = Expense,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (password.isEmpty()) missing = true else onConfirm(password) }) {
                Text(
                    stringResource(R.string.account_delete),
                    style = MaterialTheme.typography.labelLarge,
                    color = Expense,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    color = Muted,
                )
            }
        },
    )
}

/** Confirmation for destructive actions (logical delete, archive). */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = Ink) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = Muted) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, style = MaterialTheme.typography.labelLarge, color = Expense)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    color = Muted,
                )
            }
        },
    )
}
