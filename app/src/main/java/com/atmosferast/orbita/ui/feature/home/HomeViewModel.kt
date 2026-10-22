package com.atmosferast.orbita.ui.feature.home

import androidx.lifecycle.viewModelScope
import com.atmosferast.orbita.domain.model.HomeSummary
import com.atmosferast.orbita.domain.repository.HomeRepository
import com.atmosferast.orbita.domain.repository.SettingsRepository
import com.atmosferast.orbita.ui.common.ActionViewModel
import com.atmosferast.orbita.ui.common.Load
import com.atmosferast.orbita.ui.common.MessageBus
import com.atmosferast.orbita.ui.common.WhileScreenVisible
import com.atmosferast.orbita.ui.common.asLoad
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Inicio: everything it shows comes in one piece from [HomeRepository]. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    home: HomeRepository,
    private val settings: SettingsRepository,
    messages: MessageBus,
) : ActionViewModel(messages) {

    private val attempt = MutableStateFlow(0)

    val state: StateFlow<Load<HomeSummary>> = attempt
        .flatMapLatest { home.observeHome().asLoad() }
        .stateIn(viewModelScope, WhileScreenVisible, Load.Loading)

    fun retry() = attempt.update { it + 1 }

    fun setDisplayCurrency(currency: String) = act { settings.setDisplayCurrency(currency) }
}
