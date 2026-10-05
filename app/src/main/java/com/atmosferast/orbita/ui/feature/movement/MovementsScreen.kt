package com.atmosferast.orbita.ui.feature.movement

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.formatMonthYear
import com.atmosferast.orbita.ui.components.EntryList
import com.atmosferast.orbita.ui.components.ModalTopBar
import com.atmosferast.orbita.ui.components.OrbitaChip
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.OrbitaTextField
import com.atmosferast.orbita.ui.components.PillButton
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.components.StateMessage
import com.atmosferast.orbita.ui.mock.MockAccount
import com.atmosferast.orbita.ui.mock.MockEntry
import com.atmosferast.orbita.ui.mock.MockMovement
import com.atmosferast.orbita.ui.mock.MockTransfer
import com.atmosferast.orbita.ui.mock.SampleData
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.Neutral
import com.atmosferast.orbita.ui.theme.OrbitaTheme
import com.atmosferast.orbita.ui.theme.Surface

/**
 * Full list of movements and transfers with search and filters.
 * Not in the original mockups (docs/05, section 9).
 */
@Composable
fun MovementsScreen(
    accounts: List<MockAccount>,
    onBack: () -> Unit,
    onEntryClick: (MockEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf<String?>(null) }

    val entries = SampleData.entries.filter { entry ->
        val text = when (entry) {
            is MockMovement -> entry.description
            is MockTransfer -> entry.note
        }
        val inAccount = accountId == null || when (entry) {
            is MockMovement -> entry.account.id == accountId
            is MockTransfer -> entry.from.id == accountId || entry.to.id == accountId
        }
        inAccount && text.contains(query.trim(), ignoreCase = true)
    }

    ScreenScaffold(
        modifier = modifier,
        header = {
            ModalTopBar(
                title = stringResource(R.string.movements_title),
                navigationIcon = OrbitaIcons.ChevronLeft,
                navigationLabel = stringResource(R.string.action_back),
                onNavigate = onBack,
            )
        },
    ) {
        OrbitaTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.movements_search),
            leadingIcon = OrbitaIcons.Search,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OrbitaChip(
                stringResource(R.string.filter_all_accounts),
                accountId == null,
                { accountId = null },
            )
            accounts.forEach { account ->
                OrbitaChip(account.name, accountId == account.id, { accountId = account.id })
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                stringResource(R.string.filter_category_all),
                onClick = {},
                icon = OrbitaIcons.Tag,
                container = Surface,
                content = Neutral,
            )
            PillButton(
                stringResource(R.string.filter_dates_all),
                onClick = {},
                icon = OrbitaIcons.Calendar,
                container = Surface,
                content = Neutral,
            )
        }

        if (entries.isEmpty()) {
            StateMessage(OrbitaIcons.Search, stringResource(R.string.movements_empty))
        }
        entries.groupBy { it.date.withDayOfMonth(1) }.forEach { (month, monthEntries) ->
            Text(
                formatMonthYear(month),
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
            )
            EntryList(monthEntries, onEntryClick)
        }
    }
}

@Preview(name = "Movimientos", widthDp = 390, heightDp = 1150)
@Composable
private fun MovementsPreview() {
    OrbitaTheme { MovementsScreen(SampleData.accounts, onBack = {}, onEntryClick = {}) }
}
