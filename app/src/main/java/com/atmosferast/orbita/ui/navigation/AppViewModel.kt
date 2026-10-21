package com.atmosferast.orbita.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atmosferast.orbita.di.AppConfig
import com.atmosferast.orbita.domain.model.Credentials
import com.atmosferast.orbita.domain.model.SessionState
import com.atmosferast.orbita.domain.model.ValidationError
import com.atmosferast.orbita.domain.model.validateEmail
import com.atmosferast.orbita.domain.model.validateNewPassword
import com.atmosferast.orbita.domain.repository.AuthRepository
import com.atmosferast.orbita.domain.repository.DataException
import com.atmosferast.orbita.domain.repository.DateProvider
import com.atmosferast.orbita.ui.common.MessageBus
import com.atmosferast.orbita.ui.common.UiMessage
import com.atmosferast.orbita.ui.common.toUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** In demo mode no email can arrive, so the app offers the way to the link's screen itself. */
    val isDemo: Boolean get() = !config.usesApi

    fun signIn(email: String, password: String) {
        val credentials = Credentials(email, password)
        // The demo has no server to check anything against: it lets anyone in.
        authAction(if (isDemo) null else credentials.validate()) { auth.signIn(credentials) }
    }

    /** [mainCurrency]: the currency the user picked for their accounts. */
    fun signUp(email: String, password: String, mainCurrency: String) {
        val credentials = Credentials(email, password)
        authAction(if (isDemo) null else credentials.validate()) {
            auth.signUp(credentials, mainCurrency)
        }
    }

    /** Asks for the email with the link to choose a new password; [onSent] once it is asked. */
    fun requestPasswordReset(email: String, onSent: () -> Unit) =
        authAction(validateEmail(email), onSent) { auth.requestPasswordReset(email.trim()) }

    /** [token] is the one of the link in the email. */
    fun resetPassword(token: String, password: String, onDone: () -> Unit) =
        authAction(validateNewPassword(password), onDone) { auth.resetPassword(token, password) }

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
