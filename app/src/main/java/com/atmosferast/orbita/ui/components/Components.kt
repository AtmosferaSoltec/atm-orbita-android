package com.atmosferast.orbita.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.ui.theme.ChipBorder
import com.atmosferast.orbita.ui.theme.CreditGradientEnd
import com.atmosferast.orbita.ui.theme.CreditGradientMid
import com.atmosferast.orbita.ui.theme.CreditGradientStart
import com.atmosferast.orbita.ui.theme.DividerSoft
import com.atmosferast.orbita.ui.theme.HeroGlow
import com.atmosferast.orbita.ui.theme.HeroGradientEnd
import com.atmosferast.orbita.ui.theme.HeroGradientMid
import com.atmosferast.orbita.ui.theme.HeroGradientStart
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.NeutralSoft
import com.atmosferast.orbita.ui.theme.OnHero
import com.atmosferast.orbita.ui.theme.OnHeroMuted
import com.atmosferast.orbita.ui.theme.OrbitaShapes
import com.atmosferast.orbita.ui.theme.Outline
import com.atmosferast.orbita.ui.theme.Primary
import com.atmosferast.orbita.ui.theme.PrimarySoft
import com.atmosferast.orbita.ui.theme.Surface
import com.atmosferast.orbita.ui.theme.SwitchOff

val ScreenPadding = 20.dp

// ---------- Headers ----------

/** Title block of the bottom-bar screens: optional overline, 26 sp title and a trailing action. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (overline != null) {
                Text(overline, style = MaterialTheme.typography.labelMedium, color = Muted)
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    modifier = Modifier.padding(top = 2.dp, end = 12.dp),
                )
            }
        }
        action?.invoke()
    }
}

/** Top bar of the full-screen flows (no bottom bar): close/back button on the left. */
@Composable
fun ModalTopBar(
    title: String,
    navigationIcon: ImageVector,
    navigationLabel: String,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(navigationIcon, navigationLabel, onNavigate)
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        action?.invoke()
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Surface,
    tint: Color = Ink,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (actionLabel != null) {
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = Primary,
                modifier = Modifier
                    .clip(OrbitaShapes.Pill)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Muted,
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun HintText(text: String, modifier: Modifier = Modifier, color: Color = Muted) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}

// ---------- Containers ----------

@Composable
fun OrbitaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = OrbitaShapes.Card,
    container: Color = Surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Savings cards are violet-blue-teal with white text; credit is yellow-amber with dark text. */
enum class HeroTone(val gradient: List<Color>, val glow: Color, val content: Color) {
    SAVINGS(listOf(HeroGradientStart, HeroGradientMid, HeroGradientEnd), HeroGlow, Color.White),
    CREDIT(listOf(CreditGradientStart, CreditGradientMid, CreditGradientEnd), Color.White, Ink),
}

/**
 * Highlighted card (savings total, credit to pay) over a three-color gradient.
 * [scrim] darkens it from top (0 %) to bottom (80 % black); the screens use it on every tone so
 * the cards look alike, and it is what lets white text sit on the yellow CREDIT tone.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    tone: HeroTone = HeroTone.SAVINGS,
    scrim: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(OrbitaShapes.HeroCard)
            .drawBehind {
                // Diagonal gradient plus two soft glows for depth.
                drawRect(
                    Brush.linearGradient(
                        colors = tone.gradient,
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(tone.glow.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width * 0.95f, 0f),
                        radius = size.width * 0.7f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(tone.gradient.first().copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(0f, size.height),
                        radius = size.width * 0.7f,
                    ),
                )
                if (scrim) {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f)),
                        ),
                    )
                }
            }
            .padding(20.dp),
        content = content,
    )
}

/** Translucent surface used inside a [HeroCard]. */
val HeroGlass = Color.White.copy(alpha = 0.14f)

/** Secondary line of a [HeroCard] inside a translucent pill. */
@Composable
fun HeroPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.9f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(OrbitaShapes.Pill)
            .background(HeroGlass)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun CardDivider(modifier: Modifier = Modifier, color: Color = DividerSoft) {
    HorizontalDivider(modifier = modifier.padding(vertical = 12.dp), thickness = 1.dp, color = color)
}

@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(OrbitaShapes.IconBadge)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

// ---------- Selection controls ----------

/**
 * Pill-shaped segmented control. With [fill] every option takes the same width (tabs);
 * without it the control wraps its content (S/ | US$ selector). [onHero] is the dark variant.
 */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    onHero: Boolean = false,
) {
    val track = if (onHero) Color.White.copy(alpha = 0.12f) else NeutralSoft
    Row(
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .clip(OrbitaShapes.Pill)
            .background(track)
            .padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val textColor = when {
                isSelected -> Ink
                onHero -> OnHeroMuted
                else -> Muted
            }
            Box(
                modifier = Modifier
                    .then(if (fill) Modifier.weight(1f) else Modifier)
                    .heightIn(min = if (fill) 40.dp else 32.dp)
                    .clip(OrbitaShapes.Pill)
                    .background(if (isSelected) Surface else Color.Transparent)
                    .selectable(isSelected, role = Role.Tab) { onSelect(value) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = textColor, maxLines = 1)
            }
        }
    }
}

@Composable
fun OrbitaChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(OrbitaShapes.Pill)
            .background(if (selected) Primary else Surface)
            .border(
                BorderStroke(1.dp, if (selected) Primary else ChipBorder),
                OrbitaShapes.Pill,
            )
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else dotColor),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else Ink,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
fun OrbitaSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Primary,
            checkedBorderColor = Primary,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = SwitchOff,
            uncheckedBorderColor = SwitchOff,
        ),
    )
}

/** Label + switch; the whole row toggles, so TalkBack announces label and state together. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    labelColor: Color = Ink,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = labelStyle, color = labelColor, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        OrbitaSwitch(checked, onCheckedChange = null)
    }
}

// ---------- Buttons ----------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Primary,
    content: Color = Color.White,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(OrbitaShapes.Button)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = content)
    }
}

/** Small pill button with soft background (Transferir, Marcar como pagada, PDF…). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = PrimarySoft,
    content: Color = Primary,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(OrbitaShapes.Pill)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        // Only matters when the caller stretches the pill (e.g. fillMaxWidth).
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

/** Non-interactive status chip (due dates, "próximamente"). */
@Composable
fun StatusChip(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(OrbitaShapes.Pill)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

// ---------- Fields ----------

@Composable
private fun FieldBox(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    role: Role? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(OrbitaShapes.Field)
            .background(Surface)
            .border(BorderStroke(1.dp, Outline), OrbitaShapes.Field)
            .then(
                if (onClick != null) Modifier.clickable(role = role, onClick = onClick)
                else Modifier,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Field that opens a menu to pick one of [options]. [detail] is a secondary text shown on the
 * right (e.g. the account balance) and [leading] an icon or color dot per option.
 */
@Composable
fun <T> DropdownField(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    detail: ((T) -> String)? = null,
    leading: @Composable ((T) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        FieldBox(onClick = { expanded = true }, role = Role.DropdownList) {
            if (leading != null) {
                leading(selected)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                label(selected),
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (detail != null) {
                Spacer(Modifier.width(8.dp))
                Text(detail(selected), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                OrbitaIcons.ChevronDown,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(maxWidth),
            containerColor = Surface,
            shape = OrbitaShapes.Field,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                label(option),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (detail != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    detail(option),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Muted,
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    leadingIcon = if (leading == null) null else {
                        { leading(option) }
                    },
                    trailingIcon = if (option != selected) null else {
                        {
                            Icon(
                                OrbitaIcons.Check,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}

/** Multi-line text box with an optional character counter. */
@Composable
fun OrbitaTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    maxLength: Int? = null,
    minHeight: Dp = 96.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(OrbitaShapes.Field)
            .background(Surface)
            .border(BorderStroke(1.dp, Outline), OrbitaShapes.Field)
            .padding(14.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { if (maxLength == null || it.length <= maxLength) onValueChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
            cursorBrush = SolidColor(Primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.TopStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = SwitchOff)
                    }
                    inner()
                }
            },
        )
        if (maxLength != null) {
            Text(
                "${value.length}/$maxLength",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
fun OrbitaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    prefix: String? = null,
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    FieldBox(modifier) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        if (prefix != null) {
            Text(prefix, style = MaterialTheme.typography.bodyLarge, color = Muted)
            Spacer(Modifier.width(8.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
            cursorBrush = SolidColor(Primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = SwitchOff)
                    }
                    inner()
                }
            },
        )
        trailing?.invoke()
    }
}

/** Read-only field that opens a picker (date, month, account…). */
@Composable
fun PickerField(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = OrbitaIcons.Calendar,
    trailingIcon: ImageVector? = null,
) {
    FieldBox(modifier, onClick = onClick) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Big editable amount with its currency symbol (38 sp / 800). */
@Composable
fun AmountInput(
    symbol: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ink,
    style: TextStyle = MaterialTheme.typography.displaySmall,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, style = style, color = Muted)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = style.copy(color = color),
            cursorBrush = SolidColor(Primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text("0.00", style = style, color = ChipBorder)
                    inner()
                }
            },
        )
    }
}

/** Two-column text row used in summaries. */
@Composable
fun LabelValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelColor: Color = Muted,
    valueColor: Color = Ink,
    valueStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(value, style = valueStyle, color = valueColor)
    }
}

@Composable
fun HeroLabel(text: String, modifier: Modifier = Modifier, color: Color = Color.White) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = color.copy(alpha = 0.85f),
        modifier = modifier,
    )
}

@Composable
fun HeroAmount(text: String, modifier: Modifier = Modifier, color: Color = OnHero) {
    Text(
        text,
        style = MaterialTheme.typography.displaySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
