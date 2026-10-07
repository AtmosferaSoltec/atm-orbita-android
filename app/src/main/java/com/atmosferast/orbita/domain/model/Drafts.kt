package com.atmosferast.orbita.domain.model

import java.math.BigDecimal
import java.time.LocalDate

// What the forms send to be saved. Every draft knows how to check itself: validate() returns
// the first problem found, or null when it can be saved.

enum class ValidationError {
    NAME_REQUIRED,
    NAME_TOO_LONG,
    AMOUNT_REQUIRED,
    ACCOUNT_REQUIRED,
    CATEGORY_REQUIRED,
    CARD_REQUIRED,
    DESCRIPTION_TOO_LONG,
    SAME_ACCOUNT,
    RATE_REQUIRED,
    FUTURE_DATE,
    DUE_DATE_PAST,
    EMAIL_INVALID,
    PASSWORD_TOO_SHORT,
}

const val NAME_MAX_LENGTH = 60
const val TEXT_MAX_LENGTH = 500
const val PASSWORD_MIN_LENGTH = 8

private fun validateName(name: String): ValidationError? = when {
    name.isBlank() -> ValidationError.NAME_REQUIRED
    name.trim().length > NAME_MAX_LENGTH -> ValidationError.NAME_TOO_LONG
    else -> null
}

private fun BigDecimal?.isPositive(): Boolean = this != null && signum() > 0

data class AccountDraft(
    val name: String,
    val type: AccountType,
    /** Fixed once the account exists. */
    val currency: String,
    /** Only used when the account is created. */
    val initialBalance: BigDecimal,
    val includeInSavings: Boolean,
) {
    fun validate(): ValidationError? = validateName(name)
}

data class CategoryDraft(
    val name: String,
    /** Fixed once the category exists. */
    val kind: MovementKind,
    val colorHex: String,
) {
    fun validate(): ValidationError? = validateName(name)
}

data class MovementDraft(
    val kind: MovementKind,
    val amount: BigDecimal?,
    val accountId: String?,
    val categoryId: String?,
    val description: String,
    /** Null on a new movement: it takes the day it is saved. */
    val date: LocalDate? = null,
) {
    fun validate(today: LocalDate): ValidationError? = when {
        !amount.isPositive() -> ValidationError.AMOUNT_REQUIRED
        accountId == null -> ValidationError.ACCOUNT_REQUIRED
        categoryId == null -> ValidationError.CATEGORY_REQUIRED
        description.length > TEXT_MAX_LENGTH -> ValidationError.DESCRIPTION_TOO_LONG
        date != null && date.isAfter(today) -> ValidationError.FUTURE_DATE
        else -> null
    }
}

data class TransferDraft(
    val fromAccountId: String,
    val toAccountId: String,
    val fromAmount: BigDecimal?,
    val toAmount: BigDecimal?,
    /** Null between accounts of the same currency. */
    val exchangeRate: BigDecimal?,
    val date: LocalDate,
    val note: String,
) {
    /** [sameCurrency]: whether both accounts share their currency, so no rate applies. */
    fun validate(today: LocalDate, sameCurrency: Boolean): ValidationError? = when {
        fromAccountId == toAccountId -> ValidationError.SAME_ACCOUNT
        !fromAmount.isPositive() || !toAmount.isPositive() -> ValidationError.AMOUNT_REQUIRED
        !sameCurrency && !exchangeRate.isPositive() -> ValidationError.RATE_REQUIRED
        note.length > TEXT_MAX_LENGTH -> ValidationError.DESCRIPTION_TOO_LONG
        date.isAfter(today) -> ValidationError.FUTURE_DATE
        else -> null
    }
}

data class CreditCardDraft(
    val name: String,
    val currency: String,
) {
    fun validate(): ValidationError? = validateName(name)
}

/** A purchase is registered today and in the currency of its card. */
data class CreditPurchaseDraft(
    val cardId: String?,
    val amount: BigDecimal?,
    val categoryId: String?,
    val description: String,
    /** Unlike every other date, a due date is in the future by nature. */
    val dueDate: LocalDate,
) {
    fun validate(today: LocalDate): ValidationError? = when {
        !amount.isPositive() -> ValidationError.AMOUNT_REQUIRED
        cardId == null -> ValidationError.CARD_REQUIRED
        categoryId == null -> ValidationError.CATEGORY_REQUIRED
        description.length > TEXT_MAX_LENGTH -> ValidationError.DESCRIPTION_TOO_LONG
        dueDate.isBefore(today) -> ValidationError.DUE_DATE_PAST
        else -> null
    }
}

/** [paidAmount] is what the bank really charged, in the currency of the paying account. */
data class CreditPayment(
    val purchaseId: String,
    val accountId: String?,
    val paidAmount: BigDecimal?,
    val paidOn: LocalDate,
) {
    fun validate(today: LocalDate): ValidationError? = when {
        accountId == null -> ValidationError.ACCOUNT_REQUIRED
        !paidAmount.isPositive() -> ValidationError.AMOUNT_REQUIRED
        paidOn.isAfter(today) -> ValidationError.FUTURE_DATE
        else -> null
    }
}

data class Credentials(val email: String, val password: String) {
    fun validate(): ValidationError? = when {
        !EMAIL_PATTERN.matches(email.trim()) -> ValidationError.EMAIL_INVALID
        password.length < PASSWORD_MIN_LENGTH -> ValidationError.PASSWORD_TOO_SHORT
        else -> null
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
