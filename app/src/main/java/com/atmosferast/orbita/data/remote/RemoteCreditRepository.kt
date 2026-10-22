package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.CreditCard
import com.atmosferast.orbita.domain.model.CreditCardDraft
import com.atmosferast.orbita.domain.model.CreditPayment
import com.atmosferast.orbita.domain.model.CreditPurchase
import com.atmosferast.orbita.domain.model.CreditPurchaseDraft
import com.atmosferast.orbita.domain.repository.CreditRepository
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Credit cards and their pending purchases (atm-orbita-api, plans 09 and 10). A pending purchase
 * moves no balance; paying it creates its expense in the same request.
 */
@Singleton
class RemoteCreditRepository @Inject constructor(
    private val api: ApiSession,
    private val changes: RemoteChanges = RemoteChanges(),
    private val accounts: RemoteAccountsRepository = RemoteAccountsRepository(api, changes),
) : CreditRepository {

    /** Bumped when the session changes. */
    private val version = MutableStateFlow(0)

    /**
     * The lists read again after the session changes and after any write through the remote
     * repositories: deleting the expense of a payment in Movimientos brings its purchase back.
     */
    private val reads = combine(version, changes.changes, ::Pair)

    init {
        api.onSessionChange { version.update { it + 1 } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCards(): Flow<List<CreditCard>> =
        reads.flatMapLatest {
            flow {
                val cards = api.authorized { url("credit-cards") }.requireSuccess()
                    .body<DataEnvelope<List<CardDto>>>().data
                emit(cards.map { it.toCard() })
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePendingPurchases(): Flow<List<CreditPurchase>> =
        reads.flatMapLatest {
            flow {
                val purchases = api.authorized {
                    url("credit-purchases")
                    parameter("status", "pending")
                }.requireSuccess().body<DataEnvelope<List<PurchaseDto>>>().data
                emit(purchases.map { it.toPurchase() })
            }
        }

    override suspend fun createCard(draft: CreditCardDraft) {
        send(HttpMethod.Post, "credit-cards", CardRequest(UUID.randomUUID().toString(), draft.name, draft.currency))
    }

    override suspend fun updateCard(id: String, draft: CreditCardDraft) {
        send(HttpMethod.Patch, "credit-cards/$id", CardRequest(name = draft.name, currency = draft.currency))
    }

    override suspend fun archiveCard(id: String) {
        api.authorized {
            method = HttpMethod.Post
            url("credit-cards/$id/archive")
        }.requireSuccess()
        changes.bump()
    }

    override suspend fun createPurchase(draft: CreditPurchaseDraft) {
        send(HttpMethod.Post, "credit-purchases", draft.toRequest(UUID.randomUUID().toString()))
    }

    override suspend fun updatePurchase(id: String, draft: CreditPurchaseDraft) {
        send(HttpMethod.Patch, "credit-purchases/$id", draft.toRequest(id = null))
    }

    override suspend fun deletePurchase(id: String) {
        api.authorized {
            method = HttpMethod.Delete
            url("credit-purchases/$id")
        }.requireSuccess()
        changes.bump()
    }

    /**
     * Creates the expense in the paying account and takes the purchase out of the pending ones,
     * in one step on the API. The balances change, so the accounts are read again.
     */
    override suspend fun pay(payment: CreditPayment) {
        api.authorized {
            method = HttpMethod.Post
            url("credit-purchases/${payment.purchaseId}/pay")
            setBody(
                PayRequest(
                    accountId = payment.accountId ?: throw DataException(DataError.NOT_FOUND),
                    paidAmount = (payment.paidAmount ?: throw DataException(DataError.UNKNOWN))
                        .setScale(2, RoundingMode.HALF_UP),
                    paidOn = payment.paidOn.toString(),
                ),
            )
        }.requireSuccess()
        accounts.reload()
        changes.bump()
    }

    private suspend inline fun <reified T : Any> send(method: HttpMethod, path: String, payload: T) {
        api.authorized {
            this.method = method
            url(path)
            setBody(payload)
        }.requireSuccess()
        changes.bump()
    }

    private fun CreditPurchaseDraft.toRequest(id: String?) = PurchaseRequest(
        id = id,
        cardId = cardId ?: throw DataException(DataError.NOT_FOUND),
        categoryId = categoryId ?: throw DataException(DataError.NOT_FOUND),
        amount = (amount ?: throw DataException(DataError.UNKNOWN)).setScale(2, RoundingMode.HALF_UP),
        description = description,
        dueDate = dueDate.toString(),
    )
}

// Bodies of /credit-cards and /credit-purchases. The debt of each card is also sent; the app
// adds it up from the pending purchases, as before.

@Serializable
internal data class CardDto(val id: String, val name: String, val currency: String) {
    fun toCard() = CreditCard(id = id, name = name, currency = currency)
}

@Serializable
internal data class PurchaseDto(
    val id: String,
    val card: CardDto,
    val description: String = "",
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    val currency: String,
    @SerialName("purchase_date") val purchaseDate: String,
    @SerialName("due_date") val dueDate: String,
    val category: CategoryDto,
) {
    fun toPurchase() = CreditPurchase(
        id = id,
        card = card.toCard(),
        description = description,
        category = category.toCategory(),
        purchaseDate = LocalDate.parse(purchaseDate),
        dueDate = LocalDate.parse(dueDate),
        amount = amount,
        currency = currency,
    )
}

@Serializable
internal data class CardRequest(
    /** Chosen by the app on create, so a retry never saves the card twice. */
    val id: String? = null,
    val name: String,
    val currency: String,
)

@Serializable
internal data class PurchaseRequest(
    val id: String? = null,
    @SerialName("card_id") val cardId: String,
    @SerialName("category_id") val categoryId: String,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    val description: String,
    @SerialName("due_date") val dueDate: String,
)

@Serializable
internal data class PayRequest(
    @SerialName("account_id") val accountId: String,
    @SerialName("paid_amount") @Serializable(with = BigDecimalSerializer::class) val paidAmount: BigDecimal,
    @SerialName("paid_on") val paidOn: String,
)
