package com.atmosferast.orbita.ui.common

import androidx.annotation.StringRes
import com.atmosferast.orbita.R
import com.atmosferast.orbita.domain.model.ValidationError
import com.atmosferast.orbita.domain.repository.DataError

/** Text of [UiMessage] in the language of the interface. */
@get:StringRes
val UiMessage.textRes: Int
    get() = when (this) {
        is UiMessage.Invalid -> when (error) {
            ValidationError.NAME_REQUIRED -> R.string.error_name_required
            ValidationError.NAME_TOO_LONG -> R.string.error_name_too_long
            ValidationError.AMOUNT_REQUIRED -> R.string.error_amount_required
            ValidationError.ACCOUNT_REQUIRED -> R.string.error_account_required
            ValidationError.CATEGORY_REQUIRED -> R.string.error_category_required
            ValidationError.CARD_REQUIRED -> R.string.error_card_required
            ValidationError.DESCRIPTION_TOO_LONG -> R.string.error_description_too_long
            ValidationError.SAME_ACCOUNT -> R.string.error_same_account
            ValidationError.RATE_REQUIRED -> R.string.error_rate_required
            ValidationError.FUTURE_DATE -> R.string.error_future_date
            ValidationError.DUE_DATE_PAST -> R.string.error_due_date_past
            ValidationError.EMAIL_INVALID -> R.string.error_email_invalid
            ValidationError.PASSWORD_TOO_SHORT -> R.string.error_password_too_short
        }

        is UiMessage.Failed -> when (error) {
            DataError.NETWORK -> R.string.error_network
            DataError.INVALID_CREDENTIALS -> R.string.error_invalid_credentials
            DataError.EMAIL_ALREADY_REGISTERED -> R.string.error_email_registered
            DataError.EMAIL_NOT_CONFIRMED -> R.string.error_email_not_confirmed
            DataError.NOT_FOUND -> R.string.error_not_found
            DataError.NAME_TAKEN -> R.string.error_name_taken
            DataError.FX_NOT_CONFIGURED -> R.string.error_fx_not_configured
            DataError.PURCHASE_ALREADY_PAID -> R.string.error_purchase_paid
            DataError.CARD_HAS_PENDING_PURCHASES -> R.string.error_card_has_pending
            DataError.UNKNOWN -> R.string.error_unknown
        }
    }
