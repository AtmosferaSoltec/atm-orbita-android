package com.atmosferast.orbita.ui.feature.credit

import androidx.lifecycle.viewModelScope
import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.CreditCard
import com.atmosferast.orbita.domain.model.CreditCardDraft
import com.atmosferast.orbita.domain.model.CreditPayment
import com.atmosferast.orbita.domain.model.CreditPurchase
import com.atmosferast.orbita.domain.repository.AccountsRepository
import com.atmosferast.orbita.domain.repository.CreditRepository
import com.atmosferast.orbita.domain.repository.DateProvider
import com.atmosferast.orbita.domain.repository.SettingsRepository
import com.atmosferast.orbita.ui.common.ActionViewModel
import com.atmosferast.orbita.ui.common.MessageBus
import com.atmosferast.orbita.ui.common.WhileScreenVisible
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CreditUiState(
    val cards: List<CreditCard>,
    val pendingPurchases: List<CreditPurchase>,
    /** To pay a purchase from. */
    val accounts: List<Account>,
    val mainCurrency: String,
    /** Without an exchange rate, a card can only be in the main currency. */
    val fxConfigured: Boolean,
)

/** Credit cards, their pending purchases and paying them (v1.1). */
@HiltViewModel
class CreditViewModel @Inject constructor(
    private val credit: CreditRepository,
    accounts: AccountsRepository,
    settings: SettingsRepository,
    private val dates: DateProvider,
    messages: MessageBus,
) : ActionViewModel(messages) {

    /** Null until the first data arrives. */
    val state: StateFlow<CreditUiState?> = combine(
        credit.observeCards(),
        credit.observePendingPurchases(),
        accounts.observeAccounts(),
        settings.observeSettings(),
    ) { cards, purchases, accountList, userSettings ->
        CreditUiState(
            cards, purchases, accountList, userSettings.fx.main, userSettings.fx.isConfigured,
        )
    }.stateIn(viewModelScope, WhileScreenVisible, null)

    /** Creates the card, or updates [editing] when it is set. */
    fun saveCard(editing: CreditCard?, draft: CreditCardDraft, onDone: () -> Unit) =
        act(draft.validate(), onDone) {
            if (editing == null) credit.createCard(draft) else credit.updateCard(editing.id, draft)
        }

    fun archiveCard(card: CreditCard, onDone: () -> Unit) =
        act(onDone = onDone) { credit.archiveCard(card.id) }

    fun pay(payment: CreditPayment, onDone: () -> Unit) =
        act(payment.validate(dates.today()), onDone) { credit.pay(payment) }
}
