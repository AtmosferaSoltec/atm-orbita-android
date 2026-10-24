package com.atmosferast.orbita.ui.feature.auth

import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.atmosferast.orbita.R
import com.atmosferast.orbita.ui.components.FieldLabel
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.PrimaryButton
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.OrbitaShapes
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

// "Continuar con Google", like the web: Google only proves who the user is; the API then opens
// an ordinary Orbita session (plan 19 of atm-orbita-api).

/** What the phone's Google account picker gave back. */
sealed interface GoogleResult {
    data class Token(val idToken: String) : GoogleResult

    /** The user closed the picker: nothing to say. */
    data object Cancelled : GoogleResult

    /** No Google account on the phone, or this app is not set up in Google Cloud for it. */
    data object Unavailable : GoogleResult
}

/**
 * Asks the system for the user's Google account and an ID token for [clientId] (the API's web
 * client) that carries [nonce]. Needs the Activity as [context]: the picker is shown over it.
 */
suspend fun requestGoogleIdToken(context: Context, clientId: String, nonce: String): GoogleResult {
    val option = GetSignInWithGoogleOption.Builder(clientId).setNonce(nonce).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val credential = CredentialManager.create(context).getCredential(context, request).credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            GoogleResult.Unavailable
        }
    } catch (_: GetCredentialCancellationException) {
        GoogleResult.Cancelled
    } catch (_: NoCredentialException) {
        // No Google account on the phone, or none it may use for this app.
        Log.w("OrbitaGoogle", "Google sign-in: no credential")
        GoogleResult.Unavailable
    } catch (e: GetCredentialException) {
        // The type says why (no account, or the app's SHA-1 missing in Google Cloud); no token
        // or personal data is in it.
        Log.w("OrbitaGoogle", "Google sign-in unavailable: ${e.type}")
        GoogleResult.Unavailable
    } catch (_: GoogleIdTokenParsingException) {
        GoogleResult.Unavailable
    }
}

/**
 * Under the email form: "o continúa con" and the Google button. When the email already has an
 * account ([linking]), it asks once for that account's password instead.
 */
@Composable
internal fun GoogleSection(
    busy: Boolean,
    linking: Boolean,
    onContinue: () -> Unit,
    onLink: (password: String) -> Unit,
    onCancelLink: () -> Unit,
) {
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            stringResource(R.string.auth_google_divider),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.weight(1f))
    }
    Spacer(Modifier.height(16.dp))

    if (linking) {
        var password by remember { mutableStateOf("") }
        HintText(stringResource(R.string.auth_google_link_text))
        FieldLabel(stringResource(R.string.auth_google_link_field))
        PasswordField(password, onValueChange = { password = it })
        Spacer(Modifier.height(16.dp))
        PrimaryButton(stringResource(R.string.auth_google_link_action), onClick = { onLink(password) })
        AuthLink(link = stringResource(R.string.auth_google_link_cancel), onClick = onCancelLink)
    } else {
        OutlinedButton(
            onClick = onContinue,
            enabled = !busy,
            shape = OrbitaShapes.Pill,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.ic_google), contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(if (busy) R.string.auth_google_busy else R.string.auth_google_action),
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink,
                )
            }
        }
    }
}
