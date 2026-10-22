package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.AccountType
import com.atmosferast.orbita.domain.model.Movement
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Bodies of /transactions and /entries (atm-orbita-api, plan 05).

@Serializable
internal data class EntryPageDto(val data: List<EntryDto>, val meta: PageMetaDto)

@Serializable
internal data class PageMetaDto(
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Long,
)

/** A row of the list. Only `transaction` rows exist until transfers move to the API (plan 06). */
@Serializable
internal data class EntryDto(
    @SerialName("entry_type") val entryType: String,
    val id: String,
    @SerialName("occurred_on") val occurredOn: String,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    val description: String = "",
    val account: EntryAccountDto,
    val category: CategoryDto,
    @SerialName("credit_purchase_id") val creditPurchaseId: String? = null,
) {
    fun toMovement() = Movement(
        id = id,
        description = description,
        category = category.toCategory(),
        account = account.toAccount(),
        date = LocalDate.parse(occurredOn),
        amount = amount,
        creditPurchaseId = creditPurchaseId,
    )
}

/** The account as a row shows it: its balance is not sent, the accounts list has it. */
@Serializable
internal data class EntryAccountDto(
    val id: String,
    val name: String,
    val type: String,
    val currency: String,
) {
    fun toAccount() = Account(
        id = id,
        name = name,
        type = AccountType.entries.firstOrNull { it.toApi() == type } ?: AccountType.OTHER,
        currency = currency,
        balance = BigDecimal.ZERO,
    )
}

@Serializable
internal data class TransactionRequest(
    /** Chosen by the app on create, so a retry never saves the movement twice. */
    val id: String? = null,
    @SerialName("account_id") val accountId: String,
    @SerialName("category_id") val categoryId: String,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    val description: String,
    /** Left out on a new movement: the API takes today in the user's time zone. */
    @SerialName("occurred_on") val occurredOn: String? = null,
)
