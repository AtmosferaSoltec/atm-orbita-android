package com.atmosferast.orbita.ui.feature.movement

import androidx.lifecycle.viewModelScope
import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.Category
import com.atmosferast.orbita.domain.model.CreditCard
import com.atmosferast.orbita.domain.model.CreditPurchase
import com.atmosferast.orbita.domain.model.CreditPurchaseDraft
import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.model.Entry
import com.atmosferast.orbita.domain.model.Movement
import com.atmosferast.orbita.domain.model.MovementDraft
import com.atmosferast.orbita.domain.repository.AccountsRepository
import com.atmosferast.orbita.domain.repository.CategoriesRepository
import com.atmosferast.orbita.domain.repository.CreditRepository
import com.atmosferast.orbita.domain.repository.DateProvider
import com.atmosferast.orbita.domain.repository.EntriesRepository
import com.atmosferast.orbita.ui.common.ActionViewModel
import com.atmosferast.orbita.ui.common.MessageBus
import com.atmosferast.orbita.ui.common.WhileScreenVisible
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The list shows the current month plus the four before it; older activity is in Reportes. */
const val MOVEMENT_MONTHS_SHOWN = 5

data class MovementsUiState(
    val accounts: List<Account>,
    val categories: List<Category>,
    val creditCards: List<CreditCard>,
    /** Movements and transfers of the months the list can show, newest first. */
    val entries: List<Entry>,
) {
    /** A new movement starts on the account of the latest movement, or else on the first one. */
    val defaultAccount: Account?
        get() {
            val lastUsed = entries.firstNotNullOfOrNull { (it as? Movement)?.account?.id }
            return accounts.firstOrNull { it.id == lastUsed } ?: accounts.firstOrNull()
        }
}

/** Movement list and movement form (income, expense or purchase with a credit card). */
@HiltViewModel
class MovementsViewModel @Inject constructor(
    accounts: AccountsRepository,
    categories: CategoriesRepository,
    private val credit: CreditRepository,
    private val entries: EntriesRepository,
    private val dates: DateProvider,
    messages: MessageBus,
) : ActionViewModel(messages) {

    private val shownPeriod = dates.today().let { today ->
        DatePeriod(today.withDayOfMonth(1).minusMonths(MOVEMENT_MONTHS_SHOWN - 1L), today)
    }

    /** Null until the first data arrives. */
    val state: StateFlow<MovementsUiState?> = combine(
        accounts.observeAccounts(),
        categories.observeCategories(),
        credit.observeCards(),
        entries.observeEntries(shownPeriod),
        ::MovementsUiState,
    ).stateIn(viewModelScope, WhileScreenVisible, null)

    /** Creates the movement, or updates [editing] when it is set. */
    fun save(editing: Movement?, draft: MovementDraft, onDone: () -> Unit) =
        act(draft.validate(dates.today()), onDone) {
            if (editing == null) entries.createMovement(draft)
            else entries.updateMovement(editing.id, draft)
        }

    fun delete(movement: Movement, onDone: () -> Unit) =
        act(onDone = onDone) { entries.deleteMovement(movement.id) }

    /**
     * "Compra con tarjeta de crédito": a pending purchase instead of a movement. Creates it, or
     * updates [editing] when it is set; an existing one may already be overdue.
     */
    fun savePurchase(editing: CreditPurchase?, draft: CreditPurchaseDraft, onDone: () -> Unit) =
        if (editing == null) {
            act(draft.validate(dates.today()), onDone) { credit.createPurchase(draft) }
        } else {
            act(draft.validateEdit(editing.purchaseDate), onDone) {
                credit.updatePurchase(editing.id, draft)
            }
        }

    /** Only a purchase that is still pending; it changes no balance nor report. */
    fun deletePurchase(purchase: CreditPurchase, onDone: () -> Unit) =
        act(onDone = onDone) { credit.deletePurchase(purchase.id) }
}
