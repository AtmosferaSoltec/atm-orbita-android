package com.atmosferast.orbita.core

import java.math.BigDecimal

// Amount entry "from the right": the user only types digits and the decimal point places
// itself (4, 5, 5, 2 -> 0.04, 0.45, 4.55, 45.52). The amount is kept in minor units (cents).

/** 9,999,999.99 */
const val MAX_AMOUNT_CENTS = 999_999_999L

/** Appends [digits] on the right; digits that would exceed the maximum are ignored. */
fun appendAmountDigits(cents: Long, digits: String): Long {
    var result = cents
    for (digit in digits) {
        val next = result * 10 + (digit - '0')
        if (next > MAX_AMOUNT_CENTS) return result
        result = next
    }
    return result
}

/** Removes the last typed digit. */
fun removeAmountDigit(cents: Long): Long = cents / 10

fun centsToAmount(cents: Long): BigDecimal = BigDecimal.valueOf(cents, 2)

fun amountToCents(amount: BigDecimal): Long = amount.movePointRight(2).toLong()
