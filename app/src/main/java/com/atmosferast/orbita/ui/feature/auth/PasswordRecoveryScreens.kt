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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.data.demo.SampleData
import com.atmosferast.orbita.ui.components.FieldLabel
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.OrbitaPreview
import com.atmosferast.orbita.ui.components.OrbitaTextField
import com.atmosferast.orbita.ui.components.PrimaryButton
import com.atmosferast.orbita.ui.components.ScreenScaffold

// "¿Olvidaste tu contraseña?": the three screens of recovering a password. They follow the
// login and sign-up ones (same heading, fields and links). Not in the original mockups.

/** Asks for the email the 6-digit code to choose a new password will be sent to. */
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
 * The code that arrived by email, shown whether [email] has an account or not: the app never
 * tells which emails are registered. In [demo] mode no email arrives and any 6 digits work.
 */
@Composable
fun ResetCodeScreen(
    email: String,
    onSubmit: (code: String) -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    demo: Boolean = false,
) {
    var code by remember { mutableStateOf("") }

    ScreenScaffold(modifier = modifier, verticalArrangement = Arrangement.Center) {
        AuthHeading(
            title = stringResource(R.string.auth_code_title),
            subtitle = stringResource(R.string.auth_code_text, email),
        )

        FieldLabel(stringResource(R.string.auth_code_field))
        OrbitaTextField(
            value = code,
            // Only digits, at most 6: what a code can be.
            onValueChange = { typed -> code = typed.filter(Char::isDigit).take(RESET_CODE_LENGTH) },
            placeholder = "000000",
            keyboardType = KeyboardType.NumberPassword,
        )
        HintText(
            stringResource(if (demo) R.string.auth_code_demo else R.string.auth_code_hint),
            Modifier.padding(top = 8.dp),
        )

        if (error != null) AuthMessage(error, isError = true)

        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.auth_code_action), onClick = { onSubmit(code) })
        AuthLink(link = stringResource(R.string.auth_code_resend), onClick = onResend)
        AuthLink(link = stringResource(R.string.auth_back_to_login), onClick = onBack)
    }
}

private const val RESET_CODE_LENGTH = 6

/** After a valid code: choosing the new password. */
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

@Preview(name = "Recuperar contraseña · código", widthDp = 390, heightDp = 844)
@Composable
private fun ResetCodePreview() {
    OrbitaPreview {
        ResetCodeScreen(SampleData.userEmail, onSubmit = {}, onResend = {}, onBack = {})
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
