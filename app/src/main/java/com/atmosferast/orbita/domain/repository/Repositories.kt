package com.atmosferast.orbita.domain.repository

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
import com.atmosferast.orbita.domain.model.SessionState
import com.atmosferast.orbita.domain.model.TransferDraft
import com.atmosferast.orbita.domain.model.UserSettings
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

// The screens only know these contracts. Today they are backed by the in-memory demo data
// (data/demo); each one moves to the API (data/remote) without touching ui or domain.
//
// Reads are Flows that emit again whenever the data changes. Writes are suspend functions that
// throw DataException on failure; the drafts they receive are expected to be valid already.

/** Why a read or a write could not be completed, already translated out of the data source. */
enum class DataError {
    NETWORK,
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_REGISTERED,
    EMAIL_NOT_CONFIRMED,
    NOT_FOUND,
    UNKNOWN,
}

class DataException(val error: DataError, cause: Throwable? = null) :
    Exception(cause?.message ?: error.name, cause)

/** "Today" for the app. The demo data pins it; the real one follows the device. */
fun interface DateProvider {
    fun today(): LocalDate
}

interface AuthRepository {
    val session: Flow<SessionState>
    suspend fun signIn(credentials: Credentials)
    suspend fun signUp(credentials: Credentials)
    suspend fun signOut()
}

interface AccountsRepository {
    /** Accounts that are not archived, each with its computed balance. */
    fun observeAccounts(): Flow<List<Account>>
    suspend fun create(draft: AccountDraft)

    /** The currency and the initial balance of [draft] are ignored: they do not change. */
    suspend fun update(id: String, draft: AccountDraft)
    suspend fun setIncludeInSavings(id: String, include: Boolean)
    suspend fun archive(id: String)
}

interface CategoriesRepository {
    /** Categories that are not archived. */
    fun observeCategories(): Flow<List<Category>>
    suspend fun create(draft: CategoryDraft)

    /** The kind of [draft] is ignored: it does not change. */
    suspend fun update(id: String, draft: CategoryDraft)
    suspend fun archive(id: String)
}

interface EntriesRepository {
    /** Movements and transfers of [period] mixed, newest first. */
    fun observeEntries(period: DatePeriod): Flow<List<Entry>>

    /** The newest [limit] movements and transfers. */
    fun observeRecentEntries(limit: Int): Flow<List<Entry>>
    suspend fun createMovement(draft: MovementDraft)
    suspend fun updateMovement(id: String, draft: MovementDraft)
    suspend fun deleteMovement(id: String)
    suspend fun createTransfer(draft: TransferDraft)
    suspend fun updateTransfer(id: String, draft: TransferDraft)
    suspend fun deleteTransfer(id: String)
}

interface ReportsRepository {
    /** One report per currency with movements in [period]; empty when there are none. */
    fun observeReport(period: DatePeriod): Flow<List<CurrencyReport>>
}

interface CreditRepository {
    /** Credit cards that are not archived. */
    fun observeCards(): Flow<List<CreditCard>>

    /** Purchases still to be paid. */
    fun observePendingPurchases(): Flow<List<CreditPurchase>>
    suspend fun createCard(draft: CreditCardDraft)
    suspend fun updateCard(id: String, draft: CreditCardDraft)
    suspend fun archiveCard(id: String)
    suspend fun createPurchase(draft: CreditPurchaseDraft)

    /** Creates the expense in the paying account and takes the purchase out of the pending ones. */
    suspend fun pay(payment: CreditPayment)
}

interface SettingsRepository {
    fun observeSettings(): Flow<UserSettings>
    suspend fun setFx(fx: FxPair)
    suspend fun setDisplayCurrency(currency: String)
}
