package com.atmosferast.orbita.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.util.Locale

const val PEN = "PEN"
const val USD = "USD"

const val MINUS_SIGN = "−"

private val monthsShort = listOf(
    "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"
)

private val monthsLong = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
)

/** A currency the user can pick: ISO 4217 code, symbol shown next to amounts, Spanish name. */
data class CurrencyInfo(val code: String, val symbol: String, val name: String) {
    /** `Soles (S/)` */
    val label: String get() = "$name ($symbol)"
}

val supportedCurrencies = listOf(
    CurrencyInfo(PEN, "S/", "Soles"),
    CurrencyInfo(USD, "US$", "Dólares"),
    CurrencyInfo("EUR", "€", "Euros"),
    CurrencyInfo("MXN", "MX$", "Pesos mexicanos"),
    CurrencyInfo("COP", "COL$", "Pesos colombianos"),
    CurrencyInfo("CLP", "CLP$", "Pesos chilenos"),
    CurrencyInfo("ARS", "AR$", "Pesos argentinos"),
    CurrencyInfo("BOB", "Bs", "Bolivianos"),
    CurrencyInfo("BRL", "R$", "Reales"),
)

fun currencyInfo(code: String): CurrencyInfo =
    supportedCurrencies.firstOrNull { it.code == code } ?: CurrencyInfo(code, code, code)

fun currencySymbol(currency: String): String = currencyInfo(currency).symbol

private fun decimalFormat(pattern: String) =
    DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).apply {
        roundingMode = RoundingMode.HALF_UP
    }

/** `1245.8` -> `1,245.80` */
fun formatAmount(amount: BigDecimal): String =
    decimalFormat("#,##0.00").format(amount.setScale(2, RoundingMode.HALF_UP))

/** `1245.8`, `PEN` -> `S/ 1,245.80` */
fun formatMoney(amount: BigDecimal, currency: String): String =
    "${currencySymbol(currency)} ${formatAmount(amount)}"

/** Signed amount for incomes (`+`) and expenses (U+2212). */
fun formatSignedMoney(amount: BigDecimal, currency: String, positive: Boolean): String =
    (if (positive) "+" else MINUS_SIGN) + formatMoney(amount.abs(), currency)

/** Exchange rates are shown with 2 to 4 decimals. */
fun formatRate(rate: BigDecimal): String = decimalFormat("0.00##").format(rate)

/** `2 oct 2026` */
fun formatDate(date: LocalDate): String =
    "${date.dayOfMonth} ${monthsShort[date.monthValue - 1]} ${date.year}"

/** `2 oct` */
fun formatDayMonth(date: LocalDate): String =
    "${date.dayOfMonth} ${monthsShort[date.monthValue - 1]}"

/** `oct`, for [month] 1–12. */
fun monthShortName(month: Int): String = monthsShort[month - 1]

/** Initials of the days of the week, Sunday first. */
val weekdayInitials = listOf("D", "L", "M", "M", "J", "V", "S")

/** `octubre` */
fun monthName(date: LocalDate): String = monthsLong[date.monthValue - 1]

/** `Septiembre 2026` */
fun formatMonthYear(date: LocalDate): String =
    "${monthName(date).replaceFirstChar { it.uppercase() }} ${date.year}"
