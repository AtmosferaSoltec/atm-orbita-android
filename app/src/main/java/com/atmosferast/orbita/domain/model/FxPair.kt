package com.atmosferast.orbita.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The user's two currencies (Ajustes) and the manual rate between them:
 * [rate] = units of [main] per 1 unit of [secondary] (1 US$ = S/ 3.20).
 */
data class FxPair(
    val main: String,
    val secondary: String,
    val rate: BigDecimal,
) {
    /** The other currency of the pair. */
    fun other(currency: String): String = if (currency == main) secondary else main

    /** Same pair the other way round, with the inverse rate. */
    private fun swapped() = FxPair(
        main = secondary,
        secondary = main,
        rate = BigDecimal.ONE.divide(rate, 6, RoundingMode.HALF_UP),
    )

    /**
     * New main currency. The two currencies are never the same: picking the secondary one swaps
     * them. Any other change leaves no known rate, so it restarts at 1 for the user to type.
     */
    fun withMain(code: String): FxPair = when (code) {
        main -> this
        secondary -> swapped()
        else -> copy(main = code, rate = BigDecimal.ONE)
    }

    /** New secondary currency; same rules as [withMain]. */
    fun withSecondary(code: String): FxPair = when (code) {
        secondary -> this
        main -> swapped()
        else -> copy(secondary = code, rate = BigDecimal.ONE)
    }

    /** Unrounded; null when [from] or [to] is outside the pair (there is no rate for it). */
    private fun raw(amount: BigDecimal, from: String, to: String): BigDecimal? = when {
        from == to -> amount
        from == secondary && to == main -> amount.multiply(rate)
        from == main && to == secondary -> amount.divide(rate, 10, RoundingMode.HALF_UP)
        else -> null
    }

    /** Rounds once, at the end. */
    fun convert(amount: BigDecimal, from: String, to: String): BigDecimal? =
        raw(amount, from, to)?.setScale(2, RoundingMode.HALF_UP)

    /** Units of [to] per 1 unit of [from], or null outside the pair. */
    fun rateBetween(from: String, to: String): BigDecimal? =
        raw(BigDecimal.ONE, from, to)?.setScale(6, RoundingMode.HALF_UP)

    /**
     * Savings total of the included accounts, shown in [currency]. Accounts in a currency
     * outside the pair are left out: there is no rate to convert them.
     */
    fun savingsTotal(accounts: List<Account>, currency: String): BigDecimal =
        accounts.filter { it.includeInSavings }
            .fold(BigDecimal.ZERO) { acc, account ->
                acc + (raw(account.balance, account.currency, currency) ?: BigDecimal.ZERO)
            }
            .setScale(2, RoundingMode.HALF_UP)
}

/** Share of [part] over [whole] as a percentage with one decimal (HALF_UP). */
fun percentOf(part: BigDecimal, whole: BigDecimal): BigDecimal =
    if (whole.signum() == 0) BigDecimal.ZERO
    else part.multiply(BigDecimal(100)).divide(whole, 1, RoundingMode.HALF_UP)
