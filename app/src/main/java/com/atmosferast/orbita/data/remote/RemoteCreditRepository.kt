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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Credit cards and their pending purchases (atm-orbita-api, plan 09). The lists are read again
 * after every change made here and when the session changes. A pending purchase moves no
 * balance, so nothing else has to be read again.
 *
 * Paying a purchase is plan 10: until then it fails with [DataError.UNKNOWN].
 */
@Singleton
class RemoteCreditRepository @Inject constructor(
    private val api: ApiSession,
    private val changes: RemoteChanges = RemoteChanges(),
) : CreditRepository {

    /** Bumped after every write, so open lists read again. */
    private val version = MutableStateFlow(0)

    init {
        api.onSessionChange { version.update { it + 1 } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCards(): Flow<List<CreditCard>> =
        version.flatMapLatest {
            flow {
                val cards = api.authorized { url("credit-cards") }.requireSuccess()
                    .body<DataEnvelope<List<CardDto>>>().data
                emit(cards.map { it.toCard() })
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePendingPurchases(): Flow<List<CreditPurchase>> =
        version.flatMapLatest {
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
        version.update { it + 1 }
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
        version.update { it + 1 }
        changes.bump()
    }

    override suspend fun pay(payment: CreditPayment) = throw DataException(DataError.UNKNOWN)

    private suspend inline fun <reified T : Any> send(method: HttpMethod, path: String, payload: T) {
        api.authorized {
            this.method = method
            url(path)
            setBody(payload)
        }.requireSuccess()
        version.update { it + 1 }
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
