package com.atmosferast.orbita.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atmosferast.orbita.di.AppConfig
import com.atmosferast.orbita.domain.model.Credentials
import com.atmosferast.orbita.domain.model.SessionState
import com.atmosferast.orbita.domain.repository.AuthRepository
import com.atmosferast.orbita.domain.repository.DataException
import com.atmosferast.orbita.domain.repository.DateProvider
import com.atmosferast.orbita.ui.common.MessageBus
import com.atmosferast.orbita.ui.common.UiMessage
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
    messageBus: MessageBus,
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

    fun signIn(email: String, password: String) = authenticate(email, password, register = false)

    fun signUp(email: String, password: String) = authenticate(email, password, register = true)

    private fun authenticate(email: String, password: String, register: Boolean) {
        if (_authBusy.value) return
        val credentials = Credentials(email, password)
        // The demo has no server to check anything against: it lets anyone in.
        val invalid = if (config.usesSupabase) credentials.validate() else null
        if (invalid != null) {
            _authError.value = UiMessage.Invalid(invalid)
            return
        }
        _authBusy.value = true
        _authError.value = null
        viewModelScope.launch {
            try {
                if (register) auth.signUp(credentials) else auth.signIn(credentials)
            } catch (e: DataException) {
                _authError.value = UiMessage.Failed(e.error)
            } finally {
                _authBusy.value = false
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
