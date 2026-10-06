package com.atmosferast.orbita.ui.feature.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.atmosferast.orbita.R
import com.atmosferast.orbita.ui.components.FieldLabel
import com.atmosferast.orbita.ui.components.HeroCard
import com.atmosferast.orbita.ui.components.HeroTone
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.IconBadge
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.OrbitaTextField
import com.atmosferast.orbita.ui.components.PrimaryButton
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.ExpenseSoft
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.OrbitaShapes
import com.atmosferast.orbita.ui.theme.OrbitaTheme
import com.atmosferast.orbita.ui.theme.Primary
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Login ([register] = false) or sign-up. Not in the original mockups (docs/05, section 9). */
@Composable
fun AuthScreen(
    register: Boolean,
    onSubmit: () -> Unit,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    ScreenScaffold(modifier = modifier, verticalArrangement = Arrangement.Center) {
        Spacer(Modifier.height(24.dp))
        FloatingCards()
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(
                if (register) R.string.auth_register_title else R.string.auth_login_title,
            ),
            style = MaterialTheme.typography.headlineMedium,
            color = Ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(
                if (register) R.string.auth_register_subtitle else R.string.auth_login_subtitle,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )
        Spacer(Modifier.height(10.dp))

        FieldLabel(stringResource(R.string.field_email))
        OrbitaTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = stringResource(R.string.auth_email_placeholder),
            keyboardType = KeyboardType.Email,
        )

        FieldLabel(stringResource(R.string.field_password))
        OrbitaTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "••••••••",
            keyboardType = KeyboardType.Password,
            visualTransformation = if (passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailing = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { passwordVisible = !passwordVisible },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (passwordVisible) OrbitaIcons.EyeOff else OrbitaIcons.Eye,
                        contentDescription = stringResource(
                            if (passwordVisible) R.string.auth_hide_password
                            else R.string.auth_show_password,
                        ),
                        tint = Muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )
        if (register) {
            HintText(stringResource(R.string.auth_password_hint), Modifier.padding(top = 8.dp))
        }

        if (error != null) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(OrbitaIcons.Alert, Expense, ExpenseSoft, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                HintText(error, color = Expense)
            }
        }

        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            stringResource(
                if (register) R.string.auth_register_action else R.string.auth_login_action,
            ),
            onSubmit,
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(
                    if (register) R.string.auth_have_account else R.string.auth_no_account,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
            Text(
                stringResource(if (register) R.string.auth_go_login else R.string.auth_go_register),
                style = MaterialTheme.typography.labelLarge,
                color = Primary,
                modifier = Modifier
                    .clip(OrbitaShapes.Pill)
                    .clickable(role = Role.Button, onClick = onSwitch)
                    .padding(horizontal = 8.dp, vertical = 14.dp),
            )
        }
    }
}

/**
 * Two empty brand cards that float above the form and take turns at the front.
 * They only carry the app name in the top-left corner.
 */
@Composable
private fun FloatingCards(modifier: Modifier = Modifier) {
    val tones = HeroTone.entries
    var front by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_200)
            front = (front + 1) % tones.size
        }
    }
    val drift by rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        tones.forEachIndexed { index, tone ->
            // 0 = front, 1 = back
            val depth by animateFloatAsState(
                targetValue = if (index == front) 0f else 1f,
                animationSpec = tween(900, easing = FastOutSlowInEasing),
                label = "depth",
            )
            val side = if (index % 2 == 0) 1f else -1f
            HeroCard(
                tone = tone,
                modifier = Modifier
                    .zIndex(if (depth < 0.5f) 1f else 0f)
                    .size(width = 264.dp, height = 166.dp)
                    .graphicsLayer {
                        // The cards move apart halfway through, so the swap never clips.
                        val swing = sin(depth * PI.toFloat()) * 64.dp.toPx() * side
                        translationX = lerp(-10.dp.toPx(), 22.dp.toPx(), depth) + swing
                        translationY = lerp(14.dp.toPx(), -18.dp.toPx(), depth) +
                            drift * 6.dp.toPx() * side
                        rotationZ = lerp(-4f, 7f, depth)
                        val scale = lerp(1f, 0.92f, depth)
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(
                        elevation = 16.dp,
                        shape = OrbitaShapes.HeroCard,
                        ambientColor = tone.gradient.last(),
                        spotColor = tone.gradient.last(),
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = tone.content,
                    )
                    CardChip()
                }
            }
        }
    }
}

/** Metallic contact chip, drawn so the empty card still reads as a bank card. */
@Composable
private fun CardChip(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 40.dp, height = 30.dp)) {
        val radius = CornerRadius(6.dp.toPx())
        val line = ChipLine
        val stroke = 1.dp.toPx()
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(ChipLight, ChipMid, ChipDark),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            cornerRadius = radius,
        )
        drawRoundRect(color = line, cornerRadius = radius, style = Stroke(stroke))
        // Contact pads: a center pad with lines running out to both sides.
        val left = size.width * 0.32f
        val right = size.width * 0.68f
        drawLine(line, Offset(left, 0f), Offset(left, size.height), stroke)
        drawLine(line, Offset(right, 0f), Offset(right, size.height), stroke)
        listOf(0.34f, 0.66f).forEach { fraction ->
            val y = size.height * fraction
            drawLine(line, Offset(0f, y), Offset(left, y), stroke)
            drawLine(line, Offset(right, y), Offset(size.width, y), stroke)
        }
    }
}

private val ChipLight = Color(0xFFFFFFFF)
private val ChipMid = Color(0xFFE2E8F0)
private val ChipDark = Color(0xFFB6C0CE)
private val ChipLine = Color(0xFF0E1A2B).copy(alpha = 0.35f)

@Preview(name = "Iniciar sesión", widthDp = 390, heightDp = 844)
@Composable
private fun LoginPreview() {
    OrbitaTheme { AuthScreen(register = false, onSubmit = {}, onSwitch = {}) }
}

@Preview(name = "Iniciar sesión · error", widthDp = 390, heightDp = 844)
@Composable
private fun LoginErrorPreview() {
    OrbitaTheme {
        AuthScreen(
            register = false,
            onSubmit = {},
            onSwitch = {},
            error = stringResource(R.string.auth_error_credentials),
        )
    }
}

@Preview(name = "Registro", widthDp = 390, heightDp = 844)
@Composable
private fun RegisterPreview() {
    OrbitaTheme { AuthScreen(register = true, onSubmit = {}, onSwitch = {}) }
}
