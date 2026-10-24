package com.atmosferast.orbita.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atmosferast.orbita.di.AppConfig
import com.atmosferast.orbita.domain.model.Credentials
import com.atmosferast.orbita.domain.model.SessionState
import com.atmosferast.orbita.domain.model.ValidationError
import com.atmosferast.orbita.domain.model.validateEmail
import com.atmosferast.orbita.domain.model.validateName
import com.atmosferast.orbita.domain.model.validatePasswordChange
import com.atmosferast.orbita.domain.model.validateResetCode
import com.atmosferast.orbita.domain.repository.AuthRepository
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
import com.atmosferast.orbita.domain.repository.DateProvider
import com.atmosferast.orbita.ui.common.MessageBus
import com.atmosferast.orbita.ui.common.UiMessage
import com.atmosferast.orbita.ui.common.toUiMessage
import com.atmosferast.orbita.ui.feature.auth.GoogleResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the whole app hangs from: the session, "today" and the messages for the user. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val config: AppConfig,
    dates: DateProvider,
    private val messageBus: MessageBus,
) : ViewModel() {

    val today: LocalDate = dates.today()

    val messages: Flow<UiMessage> = messageBus.messages

    val session: StateFlow<SessionState> =
        auth.session.stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    /** Problem of the last attempt to sign in or sign up, shown in the form itself. */
    private val _authError = MutableStateFlow<UiMessage?>(null)
    val authError: StateFlow<UiMessage?> = _authError.asStateFlow()

    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

    /** In demo mode no email can arrive: the code screen says any 6 digits work. */
    val isDemo: Boolean get() = !config.usesApi

    fun signIn(email: String, password: String) {
        val credentials = Credentials(email, password)
        // The demo has no server to check anything against: it lets anyone in.
        authAction(if (isDemo) null else credentials.validate()) { auth.signIn(credentials) }
    }

    /** [name]: how the app calls the user. [mainCurrency]: the currency of their accounts. */
    fun signUp(name: String, email: String, password: String, mainCurrency: String) {
        val credentials = Credentials(email, password)
        authAction(if (isDemo) null else validateName(name) ?: credentials.validate()) {
            auth.signUp(name, credentials, mainCurrency)
        }
    }

    // ---------- Continuar con Google ----------

    private val _googleClientId = MutableStateFlow<String?>(null)

    /** The Google client to ask tokens for; null hides the button (demo, or the API has none). */
    val googleClientId: StateFlow<String?> = _googleClientId.asStateFlow()

    /** A Google token whose email already has an account: its password is asked once to link. */
    private data class PendingGoogle(val idToken: String, val nonce: String, val mainCurrency: String)

    private val pendingGoogle = MutableStateFlow<PendingGoogle?>(null)
    val googleLinking: StateFlow<Boolean> =
        pendingGoogle.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        if (!isDemo) {
            viewModelScope.launch {
                // Offline or with an older API there is simply no Google button.
                _googleClientId.value = try {
                    auth.googleClientId()
                } catch (_: DataException) {
                    null
                }
            }
        }
    }

    /**
     * Starts "Continuar con Google": the API's single-use nonce, then [askGoogle] (the screen,
     * which has the Activity) for the token, then the session. [mainCurrency] is only used if
     * the account is new.
     */
    fun continueWithGoogle(mainCurrency: String, askGoogle: suspend (clientId: String, nonce: String) -> GoogleResult) {
        val clientId = _googleClientId.value ?: return
        pendingGoogle.value = null
        authAction(null) {
            val nonce = auth.googleNonce()
            when (val result = askGoogle(clientId, nonce)) {
                is GoogleResult.Token -> signInWithGoogle(PendingGoogle(result.idToken, nonce, mainCurrency), null)
                GoogleResult.Cancelled -> Unit
                GoogleResult.Unavailable -> throw DataException(DataError.GOOGLE_UNAVAILABLE)
            }
        }
    }

    /** Links Google to the account that already has its email, with that account's password. */
    fun linkGoogle(password: String) {
        val pending = pendingGoogle.value ?: return
        if (password.isEmpty()) {
            _authError.value = UiMessage.Invalid(ValidationError.PASSWORD_REQUIRED)
            return
        }
        authAction(null) { signInWithGoogle(pending, password) }
    }

    fun cancelGoogleLink() {
        pendingGoogle.value = null
        _authError.value = null
    }

    private suspend fun signInWithGoogle(pending: PendingGoogle, password: String?) {
        try {
            auth.signInWithGoogle(pending.idToken, pending.nonce, pending.mainCurrency, password)
            pendingGoogle.value = null
        } catch (e: DataException) {
            when (e.error) {
                // Not a failure: the screen asks for the password and explains why.
                DataError.GOOGLE_LINK_REQUIRED -> pendingGoogle.value = pending
                // The token cannot be used again: back to the Google button.
                DataError.GOOGLE_INVALID, DataError.GOOGLE_CHALLENGE_EXPIRED -> {
                    pendingGoogle.value = null
                    throw e
                }
                else -> throw e
            }
        }
    }

    /** Asks for the email with the 6-digit code; [onSent] once it is asked. */
    fun requestPasswordReset(email: String, onSent: () -> Unit) =
        authAction(validateEmail(email), onSent) { auth.requestPasswordReset(email.trim()) }

    /** Checks the code without using it up; [onValid] moves on to the new password. */
    fun verifyResetCode(email: String, code: String, onValid: () -> Unit) =
        authAction(validateResetCode(code), onValid) { auth.verifyResetCode(email, code.trim()) }

    /** Sets the new password with the code; the user signs in again afterwards. */
    fun resetPassword(
        email: String,
        code: String,
        password: String,
        confirmation: String,
        onDone: () -> Unit,
    ) =
        authAction(validatePasswordChange(password, confirmation), onDone) {
            auth.resetPassword(email, code.trim(), password)
        }

    /**
     * One step of signing in, signing up or recovering the password. An [invalid] form is told
     * in the form itself and nothing is sent; so is a failure. [onDone] runs only on success.
     */
    private fun authAction(
        invalid: ValidationError?,
        onDone: () -> Unit = {},
        action: suspend () -> Unit,
    ) {
        if (_authBusy.value) return
        if (invalid != null) {
            _authError.value = UiMessage.Invalid(invalid)
            return
        }
        _authBusy.value = true
        _authError.value = null
        viewModelScope.launch {
            try {
                action()
                onDone()
            } catch (e: DataException) {
                _authError.value = e.toUiMessage()
            } finally {
                _authBusy.value = false
            }
        }
    }

    /**
     * Deletes the account and all its data for good. The session ends by itself; a failure
     * (e.g. a wrong password) is told like any other failed operation.
     */
    fun deleteAccount(password: String) {
        if (password.isEmpty()) {
            messageBus.post(UiMessage.Invalid(ValidationError.PASSWORD_REQUIRED))
            return
        }
        viewModelScope.launch {
            try {
                auth.deleteAccount(password)
            } catch (e: DataException) {
                messageBus.post(e.toUiMessage())
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun signOut() {
        viewModelScope.launch {
            // Leaving must always work, even offline: the library drops the local session.
            runCatching { auth.signOut() }
        }
    }
}
