package com.atmosferast.orbita.ui.feature.home

import com.atmosferast.orbita.domain.model.HomeSummary
import com.atmosferast.orbita.domain.repository.HomeRepository
import com.atmosferast.orbita.domain.repository.SettingsRepository
import com.atmosferast.orbita.ui.common.ActionViewModel
import com.atmosferast.orbita.ui.common.Load
import com.atmosferast.orbita.ui.common.MessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Inicio: everything it shows comes in one piece from [HomeRepository]. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    home: HomeRepository,
    private val settings: SettingsRepository,
    messages: MessageBus,
) : ActionViewModel(messages) {

    val state: StateFlow<Load<HomeSummary>> = load { home.observeHome() }

    fun setDisplayCurrency(currency: String) = act { settings.setDisplayCurrency(currency) }
}
