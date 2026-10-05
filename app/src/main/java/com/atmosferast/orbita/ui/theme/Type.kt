package com.atmosferast.orbita.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.atmosferast.orbita.R

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: FontWeight) = Font(
    resId = R.font.manrope,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Manrope = FontFamily(
    manrope(FontWeight.Normal),
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold),
)

private fun style(size: Int, weight: FontWeight, lineHeight: Int) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

val Typography = Typography(
    // Highlighted amount (38 sp / 800)
    displaySmall = style(38, FontWeight.ExtraBold, 44),
    // Screen titles (26 sp / 800)
    headlineMedium = style(26, FontWeight.ExtraBold, 32),
    // Card amounts (16–20 sp / 800)
    titleLarge = style(20, FontWeight.ExtraBold, 26),
    titleMedium = style(16, FontWeight.ExtraBold, 22),
    titleSmall = style(15, FontWeight.Bold, 20),
    // Body (14–16 sp)
    bodyLarge = style(16, FontWeight.Medium, 22),
    bodyMedium = style(14, FontWeight.Medium, 20),
    bodySmall = style(13, FontWeight.Medium, 18),
    // Labels (12–13 sp / 600)
    labelLarge = style(14, FontWeight.Bold, 20),
    labelMedium = style(13, FontWeight.SemiBold, 18),
    labelSmall = style(12, FontWeight.SemiBold, 16),
)
