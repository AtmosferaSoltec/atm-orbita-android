package com.atmosferast.orbita.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.atmosferast.orbita.R
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.Surface

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
