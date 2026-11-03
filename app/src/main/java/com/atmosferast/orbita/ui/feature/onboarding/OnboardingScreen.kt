package com.atmosferast.orbita.ui.feature.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.ui.components.IconBadge
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.PrimaryButton
import com.atmosferast.orbita.ui.theme.Income
import com.atmosferast.orbita.ui.theme.IncomeSoft
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.theme.Orange
import com.atmosferast.orbita.ui.theme.OrangeSoft
import com.atmosferast.orbita.ui.theme.Outline
import com.atmosferast.orbita.ui.theme.Primary
import com.atmosferast.orbita.ui.theme.PrimarySoft
import kotlinx.coroutines.launch

private class OnboardingPage(
    val icon: ImageVector,
    val tint: androidx.compose.ui.graphics.Color,
    val container: androidx.compose.ui.graphics.Color,
    val title: Int,
    val text: Int,
)

private val Pages = listOf(
    OnboardingPage(OrbitaIcons.Wallet, Primary, PrimarySoft, R.string.onboarding_1_title, R.string.onboarding_1_text),
    OnboardingPage(OrbitaIcons.CreditCard, Orange, OrangeSoft, R.string.onboarding_2_title, R.string.onboarding_2_text),
    OnboardingPage(OrbitaIcons.Chart, Income, IncomeSoft, R.string.onboarding_3_title, R.string.onboarding_3_text),
)

/** The three screens that introduce Orbita the first time it opens; [onFinish] on "Comenzar" or "Omitir". */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val pager = rememberPagerState { Pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == Pages.lastIndex

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.End) {
            if (!last) {
                Text(
                    stringResource(R.string.onboarding_skip),
                    style = MaterialTheme.typography.labelLarge,
                    color = Muted,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onFinish)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val page = Pages[index]
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconBadge(page.icon, page.tint, page.container, size = 104.dp)
                Spacer(Modifier.height(36.dp))
                Text(
                    stringResource(page.title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(page.text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Muted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(Pages.size) { index ->
                val selected = index == pager.currentPage
                val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dot-width")
                val color by animateColorAsState(if (selected) Primary else Outline, label = "dot-color")
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = width, height = 8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }

        PrimaryButton(
            text = stringResource(if (last) R.string.onboarding_start else R.string.onboarding_next),
            onClick = {
                if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
