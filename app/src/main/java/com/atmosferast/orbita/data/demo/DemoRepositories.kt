package com.atmosferast.orbita.data.demo

import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.AccountDraft
import com.atmosferast.orbita.domain.model.Category
import com.atmosferast.orbita.domain.model.CategoryDraft
import com.atmosferast.orbita.domain.model.Credentials
import com.atmosferast.orbita.domain.model.CreditCard
import com.atmosferast.orbita.domain.model.CreditCardDraft
import com.atmosferast.orbita.domain.model.CreditPayment
import com.atmosferast.orbita.domain.model.CreditPurchase
import com.atmosferast.orbita.domain.model.CreditPurchaseDraft
import com.atmosferast.orbita.domain.model.CurrencyReport
import com.atmosferast.orbita.domain.model.DatePeriod
import com.atmosferast.orbita.domain.model.Entry
import com.atmosferast.orbita.domain.model.FxPair
import com.atmosferast.orbita.domain.model.MovementDraft
import com.atmosferast.orbita.domain.model.MovementKind
import com.atmosferast.orbita.domain.model.SessionState
import com.atmosferast.orbita.domain.model.TransferDraft
import com.atmosferast.orbita.domain.model.UserSettings
import com.atmosferast.orbita.domain.model.cleanName
import com.atmosferast.orbita.domain.model.normalizeName
import com.atmosferast.orbita.domain.repository.AccountsRepository
import com.atmosferast.orbita.domain.repository.AuthRepository
import com.atmosferast.orbita.domain.repository.CategoriesRepository
import com.atmosferast.orbita.domain.repository.CreditRepository
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
import com.atmosferast.orbita.domain.repository.DateProvider
import com.atmosferast.orbita.domain.repository.EntriesRepository
import com.atmosferast.orbita.domain.repository.ReportsRepository
import com.atmosferast.orbita.domain.repository.SettingsRepository
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

// In-memory implementations of the repositories, seeded with SampleData. They behave like the
// real backend is specified to (docs/03) so every screen works end to end before the API is
// connected; nothing survives closing the app.

/** The demo data pins "today" to the day its sample was written for. */
class DemoDateProvider @Inject constructor() : DateProvider {
    override fun today() = SampleData.today
}

/** The whole demo "database", shared by every demo repository. */
@Singleton
class DemoStore @Inject constructor() {
    private val state = MutableStateFlow(SampleData.seed)

    internal fun <T> observe(read: (DemoState) -> T): Flow<T> = state.map(read).distinctUntilChanged()

    internal fun write(change: (DemoState) -> DemoState) = state.update(change)

    internal val current: DemoState get() = state.value
}

private fun newId(): String = UUID.randomUUID().toString()

private fun notFound(): Nothing = throw DataException(DataError.NOT_FOUND)

/** Without an exchange rate, nothing can be in a currency other than the main one. */
private fun DemoState.requireUsable(currency: String) {
    if (!settings.fx.isConfigured && currency != settings.fx.main) {
        throw DataException(DataError.FX_NOT_CONFIGURED)
    }
}

/**
 * A category name is unique per kind among the ones not archived, ignoring case and accents.
 * [exceptId] is the category being renamed, which never clashes with itself.
 */
private fun DemoState.requireFreeName(name: String, kind: MovementKind, exceptId: String? = null) {
    val key = normalizeName(name)
    val taken = categories.any {
        !it.archived && it.category.kind == kind && it.category.id != exceptId &&
            normalizeName(it.category.name) == key
    }
    if (taken) throw DataException(DataError.NAME_TAKEN)
}

/** Accepts any credentials: there is no server to check them against. */
@Singleton
class DemoAuthRepository @Inject constructor() : AuthRepository {
    private val state = MutableStateFlow<SessionState>(SessionState.SignedOut)

    override val session: Flow<SessionState> = state

    override suspend fun signIn(credentials: Credentials) {
        state.value = SessionState.SignedIn(credentials.email.trim().ifBlank { SampleData.userEmail })
    }

    override suspend fun signUp(credentials: Credentials) = signIn(credentials)

    override suspend fun signOut() {
        state.value = SessionState.SignedOut
    }
}

class DemoAccountsRepository @Inject constructor(private val store: DemoStore) : AccountsRepository {
    override fun observeAccounts(): Flow<List<Account>> = store.observe { it.activeAccounts }

    override suspend fun create(draft: AccountDraft) = store.write { state ->
        state.requireUsable(draft.currency)
        state.copy(
            accounts = state.accounts + AccountRecord(
                id = newId(),
                name = draft.name.trim(),
                type = draft.type,
                currency = draft.currency,
                initialBalance = draft.initialBalance.setScale(2, RoundingMode.HALF_UP),
                includeInSavings = draft.includeInSavings,
            ),
        )
    }

    override suspend fun update(id: String, draft: AccountDraft) = change(id) {
        it.copy(name = draft.name.trim(), type = draft.type, includeInSavings = draft.includeInSavings)
    }

    override suspend fun setIncludeInSavings(id: String, include: Boolean) =
        change(id) { it.copy(includeInSavings = include) }

    override suspend fun archive(id: String) = change(id) { it.copy(archived = true) }

    private fun change(id: String, change: (AccountRecord) -> AccountRecord) = store.write { state ->
        if (state.accounts.none { it.id == id }) notFound()
        state.copy(accounts = state.accounts.map { if (it.id == id) change(it) else it })
    }
}

class DemoCategoriesRepository @Inject constructor(private val store: DemoStore) : CategoriesRepository {
    override fun observeCategories(): Flow<List<Category>> = store.observe { it.activeCategories }

    override suspend fun create(draft: CategoryDraft) = store.write { state ->
        state.requireFreeName(draft.name, draft.kind)
        state.copy(
            categories = state.categories +
                CategoryRecord(Category(newId(), cleanName(draft.name), draft.kind, draft.colorHex)),
        )
    }

    // The name is kept as typed; only its comparison ignores case and accents.
    override suspend fun update(id: String, draft: CategoryDraft) = store.write { state ->
        val current = state.categories.firstOrNull { it.category.id == id } ?: notFound()
        state.requireFreeName(draft.name, current.category.kind, exceptId = id)
        state.copy(
            categories = state.categories.map { record ->
                if (record.category.id != id) record
                else record.copy(
                    category = record.category.copy(
                        name = cleanName(draft.name),
                        colorHex = draft.colorHex,
                    ),
                )
            },
        )
    }

    override suspend fun archive(id: String) = change(id) { it.copy(archived = true) }

    private fun change(id: String, change: (CategoryRecord) -> CategoryRecord) = store.write { state ->
        if (state.categories.none { it.category.id == id }) notFound()
        state.copy(categories = state.categories.map { if (it.category.id == id) change(it) else it })
    }
}

class DemoEntriesRepository @Inject constructor(
    private val store: DemoStore,
    private val dates: DateProvider,
) : EntriesRepository {
    override fun observeEntries(period: DatePeriod): Flow<List<Entry>> =
        store.observe { state -> state.entries.filter { it.date in period } }

    override fun observeRecentEntries(limit: Int): Flow<List<Entry>> =
        store.observe { it.entries.take(limit) }

    override suspend fun createMovement(draft: MovementDraft) = store.write { state ->
        state.copy(
            movements = state.movements + MovementRecord(
                id = newId(),
                description = draft.description.trim(),
                categoryId = draft.categoryId ?: notFound(),
                accountId = draft.accountId ?: notFound(),
                date = draft.date ?: dates.today(),
                amount = (draft.amount ?: notFound()).setScale(2, RoundingMode.HALF_UP),
                order = state.nextOrder,
            ),
            nextOrder = state.nextOrder + 1,
        )
    }

    override suspend fun updateMovement(id: String, draft: MovementDraft) = store.write { state ->
        if (state.movements.none { it.id == id }) notFound()
        state.copy(
            movements = state.movements.map { record ->
                if (record.id != id) record
                else record.copy(
                    description = draft.description.trim(),
                    categoryId = draft.categoryId ?: notFound(),
                    accountId = draft.accountId ?: notFound(),
                    date = draft.date ?: record.date,
                    amount = (draft.amount ?: notFound()).setScale(2, RoundingMode.HALF_UP),
                )
            },
        )
    }

    override suspend fun deleteMovement(id: String) = store.write { state ->
        // Deleting the expense of a payment undoes the payment: the purchase is pending again.
        // Its card comes back if it was archived meanwhile, so no debt is ever out of sight.
        val unpaid = state.purchases.firstOrNull { it.paymentMovementId == id }
        state.copy(
            movements = state.movements.filterNot { it.id == id },
            purchases = state.purchases.map {
                if (it.id == unpaid?.id) it.copy(paid = false, paymentMovementId = null) else it
            },
            cards = state.cards.map {
                if (it.card.id == unpaid?.cardId) it.copy(archived = false) else it
            },
        )
    }

    override suspend fun createTransfer(draft: TransferDraft) = store.write { state ->
        state.copy(
            transfers = state.transfers + TransferRecord(
                id = newId(),
                note = draft.note.trim(),
                fromId = draft.fromAccountId,
                toId = draft.toAccountId,
                date = draft.date,
                fromAmount = (draft.fromAmount ?: notFound()).setScale(2, RoundingMode.HALF_UP),
                toAmount = (draft.toAmount ?: notFound()).setScale(2, RoundingMode.HALF_UP),
                exchangeRate = draft.exchangeRate,
                order = state.nextOrder,
            ),
            nextOrder = state.nextOrder + 1,
        )
    }

    override suspend fun updateTransfer(id: String, draft: TransferDraft) = store.write { state ->
        if (state.transfers.none { it.id == id }) notFound()
        state.copy(
            transfers = state.transfers.map { record ->
                if (record.id != id) record
                else record.copy(
                    note = draft.note.trim(),
                    fromId = draft.fromAccountId,
                    toId = draft.toAccountId,
                    date = draft.date,
                    fromAmount = (draft.fromAmount ?: notFound()).setScale(2, RoundingMode.HALF_UP),
                    toAmount = (draft.toAmount ?: notFound()).setScale(2, RoundingMode.HALF_UP),
                    exchangeRate = draft.exchangeRate,
                )
            },
        )
    }

    override suspend fun deleteTransfer(id: String) = store.write { state ->
        state.copy(transfers = state.transfers.filterNot { it.id == id })
    }
}

class DemoReportsRepository @Inject constructor(private val store: DemoStore) : ReportsRepository {
    override fun observeReport(period: DatePeriod): Flow<List<CurrencyReport>> =
        store.observe { it.report(period) }
}

class DemoCreditRepository @Inject constructor(
    private val store: DemoStore,
    private val dates: DateProvider,
) : CreditRepository {
    override fun observeCards(): Flow<List<CreditCard>> = store.observe { it.activeCards }

    override fun observePendingPurchases(): Flow<List<CreditPurchase>> =
        store.observe { it.pendingPurchases }

    override suspend fun createCard(draft: CreditCardDraft) = store.write { state ->
        state.requireUsable(draft.currency)
        state.copy(
            cards = state.cards + CardRecord(CreditCard(newId(), draft.name.trim(), draft.currency)),
        )
    }

    override suspend fun updateCard(id: String, draft: CreditCardDraft) = store.write { state ->
        state.requireUsable(draft.currency)
        state.changeCard(id) { record ->
            record.copy(card = record.card.copy(name = draft.name.trim(), currency = draft.currency))
        }
    }

    override suspend fun archiveCard(id: String) = store.write { state ->
        // A card that still owes something stays in sight: pay or delete its purchases first.
        if (state.purchases.any { it.cardId == id && !it.paid }) {
            throw DataException(DataError.CARD_HAS_PENDING_PURCHASES)
        }
        state.changeCard(id) { it.copy(archived = true) }
    }

    private fun DemoState.changeCard(id: String, change: (CardRecord) -> CardRecord): DemoState {
        if (cards.none { it.card.id == id }) notFound()
        return copy(cards = cards.map { if (it.card.id == id) change(it) else it })
    }

    override suspend fun updatePurchase(id: String, draft: CreditPurchaseDraft) = store.write { state ->
        val current = state.purchases.firstOrNull { it.id == id } ?: notFound()
        if (current.paid) throw DataException(DataError.PURCHASE_ALREADY_PAID)
        val card = state.cards.firstOrNull { it.card.id == draft.cardId && !it.archived }?.card
            ?: notFound()
        state.copy(
            purchases = state.purchases.map { record ->
                if (record.id != id) record
                else record.copy(
                    cardId = card.id,
                    description = draft.description.trim(),
                    categoryId = draft.categoryId ?: notFound(),
                    dueDate = draft.dueDate,
                    amount = (draft.amount ?: notFound()).setScale(2, RoundingMode.HALF_UP),
                    // Moved to another card, it takes that card's currency; otherwise it keeps
                    // the one it was bought in, even if its card changed currency since.
                    currency = if (card.id == record.cardId) record.currency else card.currency,
                )
            },
        )
    }

    override suspend fun deletePurchase(id: String) = store.write { state ->
        // Already gone: deleting twice is not an error.
        val current = state.purchases.firstOrNull { it.id == id } ?: return@write state
        if (current.paid) throw DataException(DataError.PURCHASE_ALREADY_PAID)
        state.copy(purchases = state.purchases.filterNot { it.id == id })
    }

    override suspend fun createPurchase(draft: CreditPurchaseDraft) = store.write { state ->
        val card = state.cards.firstOrNull { it.card.id == draft.cardId && !it.archived }?.card
            ?: notFound()
        state.copy(
            purchases = state.purchases + PurchaseRecord(
                id = newId(),
                cardId = card.id,
                description = draft.description.trim(),
                categoryId = draft.categoryId ?: notFound(),
                purchaseDate = dates.today(),
                dueDate = draft.dueDate,
                amount = (draft.amount ?: notFound()).setScale(2, RoundingMode.HALF_UP),
                currency = card.currency,
            ),
        )
    }

    /** Same as pay_credit_purchase (docs/03): the expense and the paid mark go together. */
    override suspend fun pay(payment: CreditPayment) = store.write { state ->
        val purchase = state.purchases.firstOrNull { it.id == payment.purchaseId } ?: notFound()
        if (purchase.paid) throw DataException(DataError.PURCHASE_ALREADY_PAID)
        val movementId = newId()
        state.copy(
            purchases = state.purchases.map {
                if (it.id == purchase.id) it.copy(paid = true, paymentMovementId = movementId) else it
            },
            movements = state.movements + MovementRecord(
                id = movementId,
                description = purchase.description,
                categoryId = purchase.categoryId,
                accountId = payment.accountId ?: notFound(),
                date = payment.paidOn,
                amount = (payment.paidAmount ?: notFound()).setScale(2, RoundingMode.HALF_UP),
                order = state.nextOrder,
            ),
            nextOrder = state.nextOrder + 1,
        )
    }
}

class DemoSettingsRepository @Inject constructor(private val store: DemoStore) : SettingsRepository {
    override fun observeSettings(): Flow<UserSettings> = store.observe { it.settings }

    override suspend fun setFx(fx: FxPair) = store.write { state ->
        // A pair of currencies is only saved together with its rate, and never with a made-up one.
        if (fx.rate == null || fx.rate.signum() <= 0) throw DataException(DataError.FX_NOT_CONFIGURED)
        // The total is always shown in one of the two currencies of the pair.
        val display = state.settings.displayCurrency
            .takeIf { it == fx.main || it == fx.secondary } ?: fx.main
        state.copy(settings = UserSettings(fx, display))
    }

    override suspend fun setDisplayCurrency(currency: String) = store.write { state ->
        state.requireUsable(currency)
        state.copy(settings = state.settings.copy(displayCurrency = currency))
    }
}
