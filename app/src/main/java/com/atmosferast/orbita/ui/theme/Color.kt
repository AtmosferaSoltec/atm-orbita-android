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
val Primary = Color(0xFF1D4ED8)
val PrimarySoft = Color(0xFFE8EEFD)
val OnHero = Color(0xFFFFFFFF)
val OnHeroMuted = Color(0xFFC7D2E3)

// Gradient of the highlighted cards (Inicio, Cuentas, Crédito): violet -> blue -> teal
val HeroGradientStart = Color(0xFF3B1D8F)
val HeroGradientMid = Color(0xFF1D4ED8)
val HeroGradientEnd = Color(0xFF0E7490)
val HeroGlow = Color(0xFF67E8F9)

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
