package com.atmosferast.orbita.ui.theme

import androidx.compose.ui.graphics.Color

// Design tokens from docs/05-pantallas-y-flujos.md, section 1.
val Background = Color(0xFFF3F5F7)
val Surface = Color(0xFFFFFFFF)
val Ink = Color(0xFF0E1A2B)
val Muted = Color(0xFF5B6778)
val MutedLight = Color(0xFF94A0B2)
val Outline =Color(0xFFE3E8EE)
val DividerSoft = Color(0xFFEEF1F5)
// Brand: the two colors of the logo's gradient, on its dark tile (docs/brand/orbita-logo.svg).
val BrandBlue = Color(0xFF6E8CFF)
val BrandCyan = Color(0xFF23D6E0)

// Fills (buttons, selected states, the + button). Text and icons ON them are OnPrimary, never
// white: white is 3.1:1 on BrandBlue (4.5 is needed) and 1.8:1 on BrandCyan, while Ink is 5.7:1
// and 9.8:1.
val Primary = BrandBlue
val OnPrimary = Ink

// The same blue as text or icon over light surfaces (links, the selected tab, soft pills):
// BrandBlue itself is only 3.1:1 on white. This one is 6.1:1 on white and 5.3:1 on PrimarySoft.
val PrimaryDeep = Color(0xFF3B54D6)
val PrimarySoft = Color(0xFFE9EEFF)
val OnHero = Color(0xFFFFFFFF)
val OnHeroMuted = Color(0xFFC7D2E3)

// Gradient of the savings cards (Inicio, Cuentas): the logo's two colors, with Ink text.
val HeroGradientStart = BrandBlue
val HeroGradientEnd = BrandCyan
val HeroGlow = Color.White

// Gradient of the credit card (Crédito): yellow -> amber -> orange, with dark text
val CreditGradientStart = Color(0xFFFDE047)
val CreditGradientMid = Color(0xFFFBBF24)
val CreditGradientEnd = Color(0xFFF59E0B)

val Income = Color(0xFF2EAD5B)
val IncomeSoft = Color(0xFFE6F6EC)
val Expense = Color(0xFFE53935)
val ExpenseSoft = Color(0xFFFDECEA)
val Neutral = Color(0xFF3B4A60)
val NeutralSoft = Color(0xFFEEF1F5)
// Transfers and currency exchanges (sky blue); credit card reminder (orange)
val Transfer = Color(0xFF0EA5E9)
val TransferSoft = Color(0xFFE0F2FE)
val Orange = Color(0xFFF97316)
val OrangeSoft = Color(0xFFFFEDD5)
val ChipBorder =Color(0xFFCBD3DE)
val SwitchOff = Color(0xFF8793A6)

// Category colors
val CategoryHousing = Color(0xFF1D4ED8)
val CategoryFood = Color(0xFF0E7490)
val CategoryTransport = Color(0xFFB45309)
val CategoryLeisure = Color(0xFFBE185D)
val CategoryOther = Color(0xFF64748B)
val CategoryHealth = Color(0xFF7C3AED)
val CategorySalary = Color(0xFF0B7A5A)
val CategoryFreelance = Color(0xFF0E7490)

val CategoryPalette = listOf(
    CategoryHousing, CategoryFood, CategoryTransport, CategoryLeisure,
    CategoryOther, CategoryHealth, CategorySalary,
)
