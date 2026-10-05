package com.atmosferast.orbita.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// No dark theme in the MVP (docs/01, out of scope).
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimarySoft,
    onPrimaryContainer = Primary,
    secondary = Neutral,
    onSecondary = Color.White,
    secondaryContainer = NeutralSoft,
    onSecondaryContainer = Neutral,
    background = Background,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = NeutralSoft,
    onSurfaceVariant = Muted,
    outline = ChipBorder,
    outlineVariant = Outline,
    error = Expense,
    onError = Color.White,
    errorContainer = ExpenseSoft,
    onErrorContainer = Expense,
)

object OrbitaShapes {
    val Card = RoundedCornerShape(20.dp)
    val HeroCard = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(22.dp)
    val Button = RoundedCornerShape(18.dp)
    val Field = RoundedCornerShape(14.dp)
    val IconBadge = RoundedCornerShape(14.dp)
}

@Composable
fun OrbitaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content,
    )
}
