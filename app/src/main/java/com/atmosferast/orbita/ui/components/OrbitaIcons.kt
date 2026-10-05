package com.atmosferast.orbita.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons (24 dp grid, ~1.9 stroke) as required by docs/05, section 1. */
object OrbitaIcons {
    val Home = lineIcon(
        "Home",
        "M3 10.5 L12 3 l9 7.5 V20 a1 1 0 0 1 -1 1 h-5 v-6 h-6 v6 H4 a1 1 0 0 1 -1 -1 Z",
    )
    val Wallet = lineIcon(
        "Wallet",
        "M17 8 V4 H5 a2 2 0 0 0 0 4 h14 a1 1 0 0 1 1 1 v10 a1 1 0 0 1 -1 1 H5 a2 2 0 0 1 -2 -2 V6",
        "M16 14 h.01",
    )
    val Plus = lineIcon("Plus", "M12 5 v14 M5 12 h14")
    val Chart = lineIcon("Chart", "M6 20 V11 M12 20 V5 M18 20 v-6")
    val CreditCard = lineIcon(
        "CreditCard",
        "M4 5 h16 a2 2 0 0 1 2 2 v10 a2 2 0 0 1 -2 2 H4 a2 2 0 0 1 -2 -2 V7 a2 2 0 0 1 2 -2 Z",
        "M2 10 h20",
    )
    val Settings = lineIcon(
        "Settings",
        "M12.22 2 h-.44 a2 2 0 0 0 -2 2 v.18 a2 2 0 0 1 -1 1.73 l-.43 .25 a2 2 0 0 1 -2 0 " +
            "l-.15 -.08 a2 2 0 0 0 -2.73 .73 l-.22 .38 a2 2 0 0 0 .73 2.73 l.15 .1 " +
            "a2 2 0 0 1 1 1.72 v.51 a2 2 0 0 1 -1 1.74 l-.15 .09 a2 2 0 0 0 -.73 2.73 " +
            "l.22 .38 a2 2 0 0 0 2.73 .73 l.15 -.08 a2 2 0 0 1 2 0 l.43 .25 " +
            "a2 2 0 0 1 1 1.73 V20 a2 2 0 0 0 2 2 h.44 a2 2 0 0 0 2 -2 v-.18 " +
            "a2 2 0 0 1 1 -1.73 l.43 -.25 a2 2 0 0 1 2 0 l.15 .08 a2 2 0 0 0 2.73 -.73 " +
            "l.22 -.39 a2 2 0 0 0 -.73 -2.73 l-.15 -.08 a2 2 0 0 1 -1 -1.74 v-.5 " +
            "a2 2 0 0 1 1 -1.74 l.15 -.09 a2 2 0 0 0 .73 -2.73 l-.22 -.38 " +
            "a2 2 0 0 0 -2.73 -.73 l-.15 .08 a2 2 0 0 1 -2 0 l-.43 -.25 " +
            "a2 2 0 0 1 -1 -1.73 V4 a2 2 0 0 0 -2 -2 Z",
        circle(12f, 12f, 3f),
    )
    val ArrowUpRight = lineIcon("ArrowUpRight", "M7 17 L17 7 M8 7 h9 v9")
    val ArrowDownLeft = lineIcon("ArrowDownLeft", "M17 7 L7 17 M16 17 H7 V8")
    val ArrowDown = lineIcon("ArrowDown", "M12 5 v14 M6 13 l6 6 6 -6")
    val ArrowLeft = lineIcon("ArrowLeft", "M19 12 H5 M11 6 l-6 6 6 6")
    val Swap = lineIcon("Swap", "M4 8 h15 M15 4 l4 4 -4 4 M20 16 H5 M9 12 l-4 4 4 4")
    val ChevronRight = lineIcon("ChevronRight", "M9 6 l6 6 -6 6")
    val ChevronDown = lineIcon("ChevronDown", "M6 9 l6 6 6 -6")
    val ChevronLeft =lineIcon("ChevronLeft", "M15 6 l-6 6 6 6")
    val Close = lineIcon("Close", "M6 6 l12 12 M18 6 L6 18")
    val Check = lineIcon("Check", "M5 12.5 l4.5 4.5 L19 7.5")
    val Calendar = lineIcon(
        "Calendar",
        "M5 5 h14 a2 2 0 0 1 2 2 v12 a2 2 0 0 1 -2 2 H5 a2 2 0 0 1 -2 -2 V7 a2 2 0 0 1 2 -2 Z",
        "M3 10 h18 M8 3 v4 M16 3 v4",
    )
    val Banknote = lineIcon(
        "Banknote",
        "M4 6 h16 a2 2 0 0 1 2 2 v8 a2 2 0 0 1 -2 2 H4 a2 2 0 0 1 -2 -2 V8 a2 2 0 0 1 2 -2 Z",
        circle(12f, 12f, 2.5f),
        "M6 12 h.01 M18 12 h.01",
    )
    val Bank = lineIcon(
        "Bank",
        "M3 10 L12 4 l9 6 Z",
        "M5 10 v8 M9.5 10 v8 M14.5 10 v8 M19 10 v8 M3 20 h18",
    )
    val Dollar = lineIcon(
        "Dollar",
        "M12 2 v20",
        "M17 5 H9.5 a3.5 3.5 0 0 0 0 7 h5 a3.5 3.5 0 0 1 0 7 H6",
    )
    val Search = lineIcon("Search", circle(11f, 11f, 7f), "M20 20 l-3.6 -3.6")
    val Filter = lineIcon("Filter", "M4 6 h16 M7 12 h10 M10 18 h4")
    val Trash = lineIcon(
        "Trash",
        "M4 7 h16 M9 7 V4 h6 v3 M6 7 l1 13 h10 l1 -13 M10 11 v5 M14 11 v5",
    )
    val Eye = lineIcon(
        "Eye",
        "M2 12 C5 6.5 8.5 5 12 5 s7 1.5 10 7 c-3 5.5 -6.5 7 -10 7 s-7 -1.5 -10 -7 Z",
        circle(12f, 12f, 3f),
    )
    val EyeOff = lineIcon(
        "EyeOff",
        "M2 12 C5 6.5 8.5 5 12 5 s7 1.5 10 7 c-3 5.5 -6.5 7 -10 7 s-7 -1.5 -10 -7 Z",
        circle(12f, 12f, 3f),
        "M4 4 l16 16",
    )
    val Edit = lineIcon("Edit", "M4 20 h4 L19 9 l-4 -4 L4 16 Z", "M13.5 6.5 l4 4")
    val Tag = lineIcon("Tag", "M4 4 h7.5 L21 13.5 13.5 21 4 11.5 Z", "M8 8 h.01")
    val Logout = lineIcon("Logout", "M10 4 H5 v16 h5 M15 8 l4 4 -4 4 M19 12 H9")
    val Shield = lineIcon(
        "Shield",
        "M12 3 l8 3 v6 c0 5 -3.5 8 -8 9 c-4.5 -1 -8 -4 -8 -9 V6 Z",
    )
    val Archive = lineIcon("Archive", "M3 5 h18 v4 H3 Z", "M5 9 v10 h14 V9 M10 13 h4")
    val File = lineIcon("File", "M6 3 h8 l4 4 v14 H6 Z", "M14 3 v4 h4")
    val Clock = lineIcon("Clock", circle(12f, 12f, 9f), "M12 7 v5 l3 2")
    val Alert = lineIcon("Alert", circle(12f, 12f, 9f), "M12 7.5 v5 M12 16 h.01")
}

private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0 Z"

private fun lineIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { path ->
            addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
