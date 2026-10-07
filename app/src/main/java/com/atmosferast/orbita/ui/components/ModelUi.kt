package com.atmosferast.orbita.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.atmosferast.orbita.data.demo.SampleData
import com.atmosferast.orbita.domain.model.Category
import com.atmosferast.orbita.ui.theme.CategoryOther
import com.atmosferast.orbita.ui.theme.OrbitaTheme
import java.math.BigDecimal
import java.time.LocalDate

/**
 * "Today" for the screens: date limits, default dates, due dates. The app provides it from its
 * DateProvider, so the demo data and the real one agree with what the repositories use.
 */
val LocalToday = staticCompositionLocalOf<LocalDate> { LocalDate.now() }

/** Theme for previews, on the day the sample data was written for. */
@Composable
fun OrbitaPreview(content: @Composable () -> Unit) {
    OrbitaTheme {
        CompositionLocalProvider(LocalToday provides SampleData.today, content = content)
    }
}

/** `#RRGGBB` -> color; an unreadable value falls back to the neutral category color. */
fun colorFromHex(hex: String): Color =
    hex.removePrefix("#").toLongOrNull(16)?.takeIf { hex.length == 7 }
        ?.let { Color(0xFF000000 or it) }
        ?: CategoryOther

/** Color -> `#RRGGBB`, as categories store it. */
fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

val Category.color: Color get() = colorFromHex(colorHex)

/** Amount typed with the system keyboard: `.` or `,` as decimal separator. Null if unreadable. */
fun parseDecimal(text: String): BigDecimal? = text.trim().replace(',', '.').toBigDecimalOrNull()
