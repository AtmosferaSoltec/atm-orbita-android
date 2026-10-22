package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.AccountType
import com.atmosferast.orbita.domain.model.Entry
import com.atmosferast.orbita.domain.model.Movement
import com.atmosferast.orbita.domain.model.Transfer
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Bodies of /transactions, /transfers and /entries (atm-orbita-api, plans 05 and 06).

@Serializable
internal data class EntryPageDto(val data: List<EntryDto>, val meta: PageMetaDto)

@Serializable
internal data class PageMetaDto(
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Long,
)

/**
 * A row of the list: a movement (`entry_type` "transaction") or a transfer ("transfer"). Each
 * kind fills its own fields.
 */
@Serializable
internal data class EntryDto(
    @SerialName("entry_type") val entryType: String,
    val id: String,
    @SerialName("occurred_on") val occurredOn: String,
    // A movement.
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal? = null,
    val description: String = "",
    val account: EntryAccountDto? = null,
    val category: CategoryDto? = null,
    @SerialName("credit_purchase_id") val creditPurchaseId: String? = null,
    // A transfer.
    val note: String = "",
    @SerialName("from_account") val fromAccount: EntryAccountDto? = null,
    @SerialName("to_account") val toAccount: EntryAccountDto? = null,
    @SerialName("from_amount") @Serializable(with = BigDecimalSerializer::class) val fromAmount: BigDecimal? = null,
    @SerialName("to_amount") @Serializable(with = BigDecimalSerializer::class) val toAmount: BigDecimal? = null,
    @SerialName("exchange_rate") @Serializable(with = BigDecimalSerializer::class) val exchangeRate: BigDecimal? = null,
) {
    fun toEntry(): Entry = if (entryType == TRANSFER) {
        Transfer(
            id = id,
            note = note,
            from = requireNotNull(fromAccount).toAccount(),
            to = requireNotNull(toAccount).toAccount(),
            date = LocalDate.parse(occurredOn),
            fromAmount = requireNotNull(fromAmount),
            toAmount = requireNotNull(toAmount),
            exchangeRate = exchangeRate,
        )
    } else {
        Movement(
            id = id,
            description = description,
            category = requireNotNull(category).toCategory(),
            account = requireNotNull(account).toAccount(),
            date = LocalDate.parse(occurredOn),
            amount = requireNotNull(amount),
            creditPurchaseId = creditPurchaseId,
        )
    }

    private companion object {
        const val TRANSFER = "transfer"
    }
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

@Serializable
internal data class TransferRequest(
    /** Chosen by the app on create, so a retry never saves the transfer twice. */
    val id: String? = null,
    @SerialName("from_account_id") val fromAccountId: String,
    @SerialName("to_account_id") val toAccountId: String,
    @SerialName("from_amount") @Serializable(with = BigDecimalSerializer::class) val fromAmount: BigDecimal,
    @SerialName("to_amount") @Serializable(with = BigDecimalSerializer::class) val toAmount: BigDecimal,
    /**
     * Always sent, also as JSON null: an edit between accounts of the same currency has to clear
     * the rate, and a left-out field would keep the old one.
     */
    @SerialName("exchange_rate") val exchangeRate: JsonElement,
    @SerialName("occurred_on") val occurredOn: String,
    val note: String,
)
