package com.atmosferast.orbita.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.centsToAmount
import com.atmosferast.orbita.core.formatAmount
import com.atmosferast.orbita.ui.theme.ChipBorder
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.NeutralSoft
import com.atmosferast.orbita.ui.theme.OrbitaShapes
import com.atmosferast.orbita.ui.theme.Primary
import com.atmosferast.orbita.ui.theme.Surface

private val KeyShape = RoundedCornerShape(16.dp)

/** Big amount typed with [AmountKeypad]; grey while it is zero, with a blinking cursor when active. */
@Composable
fun AmountDisplay(
    symbol: String,
    cents: Long,
    modifier: Modifier = Modifier,
    color: Color = Ink,
    active: Boolean = false,
    style: TextStyle = MaterialTheme.typography.displaySmall,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, style = style, color = Muted)
        Spacer(Modifier.width(10.dp))
        Text(
            formatAmount(centsToAmount(cents)),
            style = style,
            color = if (cents == 0L) ChipBorder else color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (active) {
            val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
                initialValue = 1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        1f at 0 using LinearEasing
                        1f at 499 using LinearEasing
                        0f at 500 using LinearEasing
                        0f at 999 using LinearEasing
                    },
                    repeatMode = RepeatMode.Restart,
                ),
                label = "cursorAlpha",
            )
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    // As tall as the digits it follows.
                    .size(width = 2.dp, height = (style.fontSize.value * 0.95f).dp)
                    .alpha(blink)
                    .background(Primary),
            )
        }
    }
}

/**
 * In-app numeric keypad for amounts (the system keyboard is not used). There is no decimal key:
 * digits enter from the right and the point places itself. Long-press on backspace clears.
 */
@Composable
fun AmountKeypad(
    onDigits: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(OrbitaShapes.HeroCard)
            .background(Surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("123", "456", "789").forEach { row ->
            KeyRow {
                row.forEach { digit ->
                    DigitKey(digit.toString(), onDigits, Modifier.weight(1f))
                }
            }
        }
        KeyRow {
            Spacer(Modifier.weight(1f))
            DigitKey("0", onDigits, Modifier.weight(1f))
            KeypadKey(
                onClick = onBackspace,
                onLongClick = onClear,
                longClickLabel = stringResource(R.string.keypad_clear),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    OrbitaIcons.Backspace,
                    contentDescription = stringResource(R.string.keypad_backspace),
                    tint = Ink,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        PrimaryButton(stringResource(R.string.keypad_done), onDone, icon = OrbitaIcons.Check)
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun DigitKey(digits: String, onDigits: (String) -> Unit, modifier: Modifier = Modifier) {
    KeypadKey(onClick = { onDigits(digits) }, modifier = modifier) {
        Text(
            digits,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = Ink,
        )
    }
}

@Composable
private fun KeypadKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(KeyShape)
            .background(NeutralSoft)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick?.let {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                },
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Preview(name = "Teclado de monto", widthDp = 390)
@Composable
private fun AmountKeypadPreview() {
    OrbitaPreview {
        Column(Modifier.padding(20.dp)) {
            AmountDisplay("S/", 4552, active = true)
            Spacer(Modifier.height(16.dp))
            AmountKeypad(onDigits = {}, onBackspace = {}, onClear = {}, onDone = {})
        }
    }
}
