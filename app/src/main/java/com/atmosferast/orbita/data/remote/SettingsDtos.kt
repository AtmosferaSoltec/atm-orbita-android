package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.FxPair
import com.atmosferast.orbita.domain.model.UserSettings
import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Bodies of /settings (atm-orbita-api, plan 02).

@Serializable
internal data class SettingsDto(
    @SerialName("main_currency") val mainCurrency: String,
    @SerialName("secondary_currency") val secondaryCurrency: String,
    @SerialName("display_currency") val displayCurrency: String,
    val timezone: String,
    @SerialName("fx_mode") val fxMode: String = "manual",
    @SerialName("fx_configured") val fxConfigured: Boolean = false,
    /** Units of the main currency per 1 of the secondary; null until the user sets it. */
    @Serializable(with = BigDecimalSerializer::class) val rate: BigDecimal? = null,
) {
    fun toUserSettings() = UserSettings(
        fx = FxPair(main = mainCurrency, secondary = secondaryCurrency, rate = rate),
        displayCurrency = displayCurrency,
    )
}

@Serializable
internal data class SetFxRequest(
    @SerialName("main_currency") val mainCurrency: String,
    @SerialName("secondary_currency") val secondaryCurrency: String,
    @Serializable(with = BigDecimalSerializer::class) val rate: BigDecimal,
)

@Serializable
internal data class UpdateSettingsRequest(
    @SerialName("display_currency") val displayCurrency: String? = null,
    val timezone: String? = null,
)
