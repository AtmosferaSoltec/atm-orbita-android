package com.atmosferast.orbita.ui.feature.settings

import androidx.lifecycle.viewModelScope
import com.atmosferast.orbita.domain.model.FxPair
import com.atmosferast.orbita.domain.model.UserSettings
import com.atmosferast.orbita.domain.model.ValidationError
import com.atmosferast.orbita.domain.repository.SettingsRepository
import com.atmosferast.orbita.ui.common.ActionViewModel
import com.atmosferast.orbita.ui.common.MessageBus
import com.atmosferast.orbita.ui.common.WhileScreenVisible
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    messages: MessageBus,
) : ActionViewModel(messages) {

    /** Null until the first data arrives. */
    val settings: StateFlow<UserSettings?> =
        repository.observeSettings().stateIn(viewModelScope, WhileScreenVisible, null)

    /** A pair of currencies is only saved together with its rate, which must be above zero. */
    fun setFx(fx: FxPair) = act(
        invalid = if ((fx.rate?.signum() ?: 0) > 0) null else ValidationError.RATE_REQUIRED,
    ) { repository.setFx(fx) }

    fun setDisplayCurrency(currency: String) = act { repository.setDisplayCurrency(currency) }
}
