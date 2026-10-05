package com.atmosferast.orbita.ui.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.ui.components.FieldLabel
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.IconBadge
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.OrbitaTextField
import com.atmosferast.orbita.ui.components.PrimaryButton
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.ExpenseSoft
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.OrbitaShapes
import com.atmosferast.orbita.ui.theme.OrbitaTheme
import com.atmosferast.orbita.ui.theme.Primary
import com.atmosferast.orbita.ui.theme.PrimarySoft

/** Login ([register] = false) or sign-up. Not in the original mockups (docs/05, section 9). */
@Composable
fun AuthScreen(
    register: Boolean,
    onSubmit: () -> Unit,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    ScreenScaffold(modifier = modifier) {
        Spacer(Modifier.height(48.dp))
        IconBadge(OrbitaIcons.Wallet, Primary, PrimarySoft, size = 56.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelLarge,
            color = Primary,
        )
        Text(
            stringResource(
                if (register) R.string.auth_register_title else R.string.auth_login_title,
            ),
            style = MaterialTheme.typography.headlineMedium,
            color = Ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(
                if (register) R.string.auth_register_subtitle else R.string.auth_login_subtitle,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )
        Spacer(Modifier.height(10.dp))

        FieldLabel(stringResource(R.string.field_email))
        OrbitaTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = stringResource(R.string.auth_email_placeholder),
            keyboardType = KeyboardType.Email,
        )

        FieldLabel(stringResource(R.string.field_password))
        OrbitaTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "••••••••",
            keyboardType = KeyboardType.Password,
            visualTransformation = if (passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailing = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { passwordVisible = !passwordVisible },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (passwordVisible) OrbitaIcons.EyeOff else OrbitaIcons.Eye,
                        contentDescription = stringResource(
                            if (passwordVisible) R.string.auth_hide_password
                            else R.string.auth_show_password,
                        ),
                        tint = Muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )
        if (register) {
            HintText(stringResource(R.string.auth_password_hint), Modifier.padding(top = 8.dp))
        }

        if (error != null) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(OrbitaIcons.Alert, Expense, ExpenseSoft, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                HintText(error, color = Expense)
            }
        }

        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            stringResource(
                if (register) R.string.auth_register_action else R.string.auth_login_action,
            ),
            onSubmit,
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(
                    if (register) R.string.auth_have_account else R.string.auth_no_account,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
            Text(
                stringResource(if (register) R.string.auth_go_login else R.string.auth_go_register),
                style = MaterialTheme.typography.labelLarge,
                color = Primary,
                modifier = Modifier
                    .clip(OrbitaShapes.Pill)
                    .clickable(role = Role.Button, onClick = onSwitch)
                    .padding(horizontal = 8.dp, vertical = 14.dp),
            )
        }
    }
}

@Preview(name = "Iniciar sesión", widthDp = 390, heightDp = 844)
@Composable
private fun LoginPreview() {
    OrbitaTheme { AuthScreen(register = false, onSubmit = {}, onSwitch = {}) }
}

@Preview(name = "Iniciar sesión · error", widthDp = 390, heightDp = 844)
@Composable
private fun LoginErrorPreview() {
    OrbitaTheme {
        AuthScreen(
            register = false,
            onSubmit = {},
            onSwitch = {},
            error = stringResource(R.string.auth_error_credentials),
        )
    }
}

@Preview(name = "Registro", widthDp = 390, heightDp = 844)
@Composable
private fun RegisterPreview() {
    OrbitaTheme { AuthScreen(register = true, onSubmit = {}, onSwitch = {}) }
}
