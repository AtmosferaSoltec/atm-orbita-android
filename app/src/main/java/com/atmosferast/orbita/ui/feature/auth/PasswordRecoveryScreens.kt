package com.atmosferast.orbita.ui.feature.auth

import androidx.compose.foundation.layout.Arrangement
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
import com.atmosferast.orbita.data.demo.SampleData
import com.atmosferast.orbita.ui.components.FieldLabel
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.OrbitaPreview
import com.atmosferast.orbita.ui.components.PrimaryButton
import com.atmosferast.orbita.ui.components.ScreenScaffold

// "¿Olvidaste tu contraseña?": the three screens of recovering a password. They follow the
// login and sign-up ones (same heading, fields and links). Not in the original mockups.

/** Asks for the email the link to choose a new password will be sent to. */
@Composable
fun ForgotPasswordScreen(
    onSubmit: (email: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var email by remember { mutableStateOf("") }

    ScreenScaffold(modifier = modifier, verticalArrangement = Arrangement.Center) {
        AuthHeading(
            title = stringResource(R.string.auth_forgot_title),
            subtitle = stringResource(R.string.auth_forgot_subtitle),
        )

        FieldLabel(stringResource(R.string.field_email))
        EmailField(email, onValueChange = { email = it })

        if (error != null) AuthMessage(error, isError = true)

        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.auth_forgot_action), onClick = { onSubmit(email) })
        AuthLink(link = stringResource(R.string.auth_back_to_login), onClick = onBack)
    }
}

/**
 * Shown after asking for the link, whether [email] has an account or not: the app never tells
 * which emails are registered. [onOpenLink] is only set in demo mode, where no email can
 * arrive: it stands for tapping the link.
 */
@Composable
fun PasswordLinkSentScreen(
    email: String,
    onBack: () -> Unit,
    onResend: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLink: (() -> Unit)? = null,
) {
    ScreenScaffold(modifier = modifier, verticalArrangement = Arrangement.Center) {
        AuthHeading(
            title = stringResource(R.string.auth_forgot_sent_title),
            subtitle = stringResource(R.string.auth_forgot_sent_text, email),
        )
        HintText(stringResource(R.string.auth_forgot_sent_hint), Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.auth_back_to_login), onClick = onBack)
        AuthLink(link = stringResource(R.string.auth_forgot_resend), onClick = onResend)
        if (onOpenLink != null) {
            AuthLink(link = stringResource(R.string.auth_forgot_demo_link), onClick = onOpenLink)
        }
    }
}

/** Where the link of the email leads: choosing the new password. */
@Composable
fun NewPasswordScreen(
    onSubmit: (password: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var password by remember { mutableStateOf("") }

    ScreenScaffold(modifier = modifier, verticalArrangement = Arrangement.Center) {
        AuthHeading(
            title = stringResource(R.string.auth_reset_title),
            subtitle = stringResource(R.string.auth_reset_subtitle),
        )

        FieldLabel(stringResource(R.string.auth_reset_field))
        PasswordField(password, onValueChange = { password = it })
        HintText(stringResource(R.string.auth_password_hint), Modifier.padding(top = 8.dp))

        if (error != null) AuthMessage(error, isError = true)

        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.auth_reset_action), onClick = { onSubmit(password) })
        AuthLink(link = stringResource(R.string.auth_back_to_login), onClick = onBack)
    }
}

@Preview(name = "Recuperar contraseña", widthDp = 390, heightDp = 844)
@Composable
private fun ForgotPasswordPreview() {
    OrbitaPreview { ForgotPasswordScreen(onSubmit = {}, onBack = {}) }
}

@Preview(name = "Recuperar contraseña · enlace enviado", widthDp = 390, heightDp = 844)
@Composable
private fun PasswordLinkSentPreview() {
    OrbitaPreview {
        PasswordLinkSentScreen(SampleData.userEmail, onBack = {}, onResend = {}, onOpenLink = {})
    }
}

@Preview(name = "Contraseña nueva", widthDp = 390, heightDp = 844)
@Composable
private fun NewPasswordPreview() {
    OrbitaPreview {
        NewPasswordScreen(
            onSubmit = {},
            onBack = {},
            error = stringResource(R.string.error_password_too_short),
        )
    }
}
