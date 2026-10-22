package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.AccountType
import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Bodies of /accounts (atm-orbita-api, plan 04). The list also brings the savings total; the
// app works it out itself with FxPair.savingsTotal, so it is not read.

@Serializable
internal data class AccountListDto(val accounts: List<AccountDto>)

@Serializable
internal data class AccountDto(
    val id: String,
    val name: String,
    val type: String,
    val currency: String,
    @Serializable(with = BigDecimalSerializer::class) val balance: BigDecimal,
    @SerialName("include_in_savings") val includeInSavings: Boolean,
) {
    fun toAccount() = Account(
        id = id,
        name = name,
        type = AccountType.entries.firstOrNull { it.toApi() == type } ?: AccountType.OTHER,
        currency = currency,
        balance = balance,
        includeInSavings = includeInSavings,
    )
}

@Serializable
internal data class CreateAccountRequest(
    /** Chosen by the app: retrying with the same id never creates the account twice. */
    val id: String,
    val name: String,
    val type: String,
    val currency: String,
    @SerialName("initial_balance") @Serializable(with = BigDecimalSerializer::class)
    val initialBalance: BigDecimal,
    @SerialName("include_in_savings") val includeInSavings: Boolean,
)

/** Currency and initial balance are never sent: they do not change. */
@Serializable
internal data class UpdateAccountRequest(
    val name: String? = null,
    val type: String? = null,
    @SerialName("include_in_savings") val includeInSavings: Boolean? = null,
)

internal fun AccountType.toApi(): String = when (this) {
    AccountType.CASH -> "cash"
    AccountType.DEBIT -> "debit"
    AccountType.SAVINGS -> "savings"
    AccountType.OTHER -> "other"
}
