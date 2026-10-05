package com.atmosferast.orbita.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.core.USD
import com.atmosferast.orbita.core.formatRate
import com.atmosferast.orbita.ui.components.CardDivider
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.IconBadge
import com.atmosferast.orbita.ui.components.LabelValueRow
import com.atmosferast.orbita.ui.components.ModalTopBar
import com.atmosferast.orbita.ui.components.OrbitaCard
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.OrbitaTextField
import com.atmosferast.orbita.ui.components.PillButton
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.components.SectionTitle
import com.atmosferast.orbita.ui.components.SegmentedControl
import com.atmosferast.orbita.ui.mock.SampleData
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.ExpenseSoft
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.Neutral
import com.atmosferast.orbita.ui.theme.NeutralSoft
import com.atmosferast.orbita.ui.theme.OrbitaTheme
import com.atmosferast.orbita.ui.theme.Primary
import com.atmosferast.orbita.ui.theme.PrimarySoft
import java.math.BigDecimal
import java.math.RoundingMode

private enum class FxMode { MANUAL, AUTO }

@Composable
fun SettingsScreen(
    displayCurrency: String,
    onDisplayCurrencyChange: (String) -> Unit,
    onBack: () -> Unit,
    onManageCategories: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var rate by remember { mutableStateOf(formatRate(SampleData.usdToPen)) }
    // Shown with 4 decimals; the rate must be > 0.
    val inverse = rate.replace(',', '.').toBigDecimalOrNull()
        ?.takeIf { it.signum() > 0 }
        ?.let { BigDecimal.ONE.divide(it, 4, RoundingMode.HALF_UP).toPlainString() }
        ?: "—"

    ScreenScaffold(
        modifier = modifier,
        header = {
            ModalTopBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = OrbitaIcons.ChevronLeft,
                navigationLabel = stringResource(R.string.action_back),
                onNavigate = onBack,
            )
        },
    ) {
        SectionTitle(stringResource(R.string.settings_currencies))
        OrbitaCard {
            LabelValueRow(
                stringResource(R.string.settings_default_currency),
                stringResource(R.string.currency_option_pen),
            )
            CardDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_display_currency),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    modifier = Modifier.weight(1f),
                )
                SegmentedControl(
                    options = listOf(PEN to "S/", USD to "US$"),
                    selected = displayCurrency,
                    onSelect = onDisplayCurrencyChange,
                    fill = false,
                )
            }
        }

        SectionTitle(stringResource(R.string.settings_fx))
        OrbitaCard {
            // "Automático" is announced but not available yet.
            SegmentedControl(
                options = listOf(
                    FxMode.MANUAL to stringResource(R.string.settings_fx_manual),
                    FxMode.AUTO to stringResource(R.string.settings_fx_auto_soon),
                ),
                selected = FxMode.MANUAL,
                onSelect = {},
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_fx_prefix),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                Spacer(Modifier.width(12.dp))
                OrbitaTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    placeholder = "0.00",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.width(120.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.settings_fx_inverse, inverse),
                style = MaterialTheme.typography.labelMedium,
                color = Ink,
            )
            Spacer(Modifier.height(6.dp))
            HintText(stringResource(R.string.settings_fx_hint))
        }

        SectionTitle(stringResource(R.string.categories_title))
        OrbitaCard(onClick = onManageCategories) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(OrbitaIcons.Tag, Primary, PrimarySoft, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_manage_categories),
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                    )
                    HintText(stringResource(R.string.settings_manage_categories_hint))
                }
                Icon(
                    OrbitaIcons.ChevronRight,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        SectionTitle(stringResource(R.string.settings_security))
        OrbitaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(OrbitaIcons.Shield, Neutral, NeutralSoft, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                HintText(stringResource(R.string.settings_security_text), Modifier.weight(1f))
            }
            CardDivider()
            LabelValueRow(stringResource(R.string.settings_session), SampleData.userEmail)
            Spacer(Modifier.height(12.dp))
            PillButton(
                stringResource(R.string.action_logout),
                onLogout,
                icon = OrbitaIcons.Logout,
                container = ExpenseSoft,
                content = Expense,
            )
        }

        SectionTitle(stringResource(R.string.settings_soon))
        OrbitaCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    R.string.soon_pdf,
                    R.string.soon_offline,
                    R.string.soon_auto_fx,
                    R.string.soon_google,
                ).forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 36.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            OrbitaIcons.Clock,
                            contentDescription = null,
                            tint = Muted,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(item),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Ajustes", widthDp = 390, heightDp = 1250)
@Composable
private fun SettingsPreview() {
    var currency by remember { mutableStateOf(PEN) }
    OrbitaTheme {
        SettingsScreen(
            displayCurrency = currency,
            onDisplayCurrencyChange = { currency = it },
            onBack = {},
            onManageCategories = {},
            onLogout = {},
        )
    }
}
