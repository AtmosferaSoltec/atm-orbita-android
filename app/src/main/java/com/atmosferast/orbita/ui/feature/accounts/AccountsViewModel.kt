package com.atmosferast.orbita.ui.feature.accounts

import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.AccountDraft
import com.atmosferast.orbita.domain.model.FxPair
import com.atmosferast.orbita.domain.repository.AccountsRepository
import com.atmosferast.orbita.domain.repository.SettingsRepository
import com.atmosferast.orbita.ui.common.ActionViewModel
import com.atmosferast.orbita.ui.common.Load
import com.atmosferast.orbita.ui.common.MessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

data class AccountsUiState(
    val accounts: List<Account>,
    val fx: FxPair,
    val displayCurrency: String,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accounts: AccountsRepository,
    settings: SettingsRepository,
    messages: MessageBus,
) : ActionViewModel(messages) {

    val state: StateFlow<Load<AccountsUiState>> = load {
        combine(accounts.observeAccounts(), settings.observeSettings()) { list, userSettings ->
            AccountsUiState(list, userSettings.fx, userSettings.displayCurrency)
        }
    }

    fun setIncludeInSavings(account: Account, include: Boolean) =
        act { accounts.setIncludeInSavings(account.id, include) }

    /** Creates the account, or updates [editing] when it is set. */
    fun save(editing: Account?, draft: AccountDraft, onDone: () -> Unit) =
        act(draft.validate(), onDone) {
            if (editing == null) accounts.create(draft) else accounts.update(editing.id, draft)
        }

    fun archive(account: Account, onDone: () -> Unit) = act(onDone = onDone) { accounts.archive(account.id) }
}
