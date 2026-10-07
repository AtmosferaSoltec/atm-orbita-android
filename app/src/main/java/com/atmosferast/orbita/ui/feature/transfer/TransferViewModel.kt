package com.atmosferast.orbita.ui.feature.transfer

import androidx.lifecycle.viewModelScope
import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.FxPair
import com.atmosferast.orbita.domain.model.Transfer
import com.atmosferast.orbita.domain.model.TransferDraft
import com.atmosferast.orbita.domain.repository.AccountsRepository
import com.atmosferast.orbita.domain.repository.DateProvider
import com.atmosferast.orbita.domain.repository.EntriesRepository
import com.atmosferast.orbita.domain.repository.SettingsRepository
import com.atmosferast.orbita.ui.common.ActionViewModel
import com.atmosferast.orbita.ui.common.MessageBus
import com.atmosferast.orbita.ui.common.WhileScreenVisible
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TransferUiState(
    val accounts: List<Account>,
    val fx: FxPair,
)

@HiltViewModel
class TransferViewModel @Inject constructor(
    accounts: AccountsRepository,
    settings: SettingsRepository,
    private val entries: EntriesRepository,
    private val dates: DateProvider,
    messages: MessageBus,
) : ActionViewModel(messages) {

    /** Null until the first data arrives. */
    val state: StateFlow<TransferUiState?> =
        combine(accounts.observeAccounts(), settings.observeSettings()) { list, userSettings ->
            TransferUiState(list, userSettings.fx)
        }.stateIn(viewModelScope, WhileScreenVisible, null)

    /** Creates the transfer, or updates [editing] when it is set. */
    fun save(editing: Transfer?, draft: TransferDraft, sameCurrency: Boolean, onDone: () -> Unit) =
        act(draft.validate(dates.today(), sameCurrency), onDone) {
            if (editing == null) entries.createTransfer(draft)
            else entries.updateTransfer(editing.id, draft)
        }

    fun delete(transfer: Transfer, onDone: () -> Unit) =
        act(onDone = onDone) { entries.deleteTransfer(transfer.id) }
}
