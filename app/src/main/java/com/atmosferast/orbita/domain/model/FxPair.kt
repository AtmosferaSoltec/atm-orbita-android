package com.atmosferast.orbita.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The user's two currencies (Ajustes) and the manual rate between them:
 * [rate] = units of [main] per 1 unit of [secondary] (1 US$ = S/ 3.20).
 *
 * [rate] is null until the user sets it: a new user has none, and no value is ever made up.
 * Without it there is no conversion and nothing can be in a currency other than [main].
 */
data class FxPair(
    val main: String,
    val secondary: String,
    val rate: BigDecimal?,
) {
    val isConfigured: Boolean get() = rate != null

    /** The other currency of the pair. */
    fun other(currency: String): String = if (currency == main) secondary else main

    /** Same pair the other way round, with the inverse rate. */
    private fun swapped() = FxPair(
        main = secondary,
        secondary = main,
        rate = rate?.let { BigDecimal.ONE.divide(it, 6, RoundingMode.HALF_UP) },
    )

    /**
     * New main currency. The two currencies are never the same: picking the secondary one swaps
     * them. Any other change leaves no known rate, so it is left empty for the user to type.
     */
    fun withMain(code: String): FxPair = when (code) {
        main -> this
        secondary -> swapped()
        else -> copy(main = code, rate = null)
    }

    /** New secondary currency; same rules as [withMain]. */
    fun withSecondary(code: String): FxPair = when (code) {
        secondary -> this
        main -> swapped()
        else -> copy(secondary = code, rate = null)
    }

    /**
     * Unrounded; null when there is no rate for it: [from] or [to] is outside the pair, or the
     * rate is not set yet.
     */
    private fun raw(amount: BigDecimal, from: String, to: String): BigDecimal? = when {
        from == to -> amount
        rate == null -> null
        from == secondary && to == main -> amount.multiply(rate)
        from == main && to == secondary -> amount.divide(rate, 10, RoundingMode.HALF_UP)
        else -> null
    }

    /** Rounds once, at the end. */
    fun convert(amount: BigDecimal, from: String, to: String): BigDecimal? =
        raw(amount, from, to)?.setScale(2, RoundingMode.HALF_UP)

    /** Units of [to] per 1 unit of [from], or null when there is no rate for it. */
    fun rateBetween(from: String, to: String): BigDecimal? =
        raw(BigDecimal.ONE, from, to)?.setScale(6, RoundingMode.HALF_UP)

    /**
     * Savings total of the included accounts, shown in [currency]. Accounts that cannot be
     * converted (a currency outside the pair, or no rate yet) are left out.
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
