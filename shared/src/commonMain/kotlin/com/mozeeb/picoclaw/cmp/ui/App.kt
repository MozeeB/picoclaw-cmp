package com.mozeeb.picoclaw.cmp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozeeb.picoclaw.cmp.i18n.LocalStrings
import com.mozeeb.picoclaw.cmp.i18n.stringsFor
import com.mozeeb.picoclaw.cmp.mvi.ServiceViewModel
import com.mozeeb.picoclaw.cmp.ui.pages.ConfigPage
import com.mozeeb.picoclaw.cmp.ui.pages.DashboardPage
import com.mozeeb.picoclaw.cmp.ui.pages.LogPage
import com.mozeeb.picoclaw.cmp.ui.pages.WebViewPage
import com.mozeeb.picoclaw.cmp.ui.widgets.AdaptiveNavBar
import org.koin.compose.viewmodel.koinViewModel

data class NavDestination(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/** Config tab index — used for "Go to Config" navigation from the Dashboard banner. */
private const val CONFIG_TAB = 3

/**
 * Root composable. Entry points must call [initKoin] before rendering this.
 *
 * By default the [ServiceViewModel] is resolved via [koinViewModel] (Android/iOS/Web).
 * Desktop passes a shared instance so the system tray and the UI observe the same state.
 */
@Composable
fun App(viewModel: ServiceViewModel = koinViewModel()) {
    AppContent(viewModel)
}

@Composable
private fun AppContent(viewModel: ServiceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var selectedIndex by remember { mutableIntStateOf(0) }

    val strings = stringsFor(state.locale)
    val destinations = listOf(
        NavDestination(strings.navDashboard, Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
        NavDestination(strings.navWeb, Icons.Outlined.Language, Icons.Filled.Language),
        NavDestination(strings.navLogs, Icons.AutoMirrored.Outlined.Article, Icons.AutoMirrored.Filled.Article),
        NavDestination(strings.navConfig, Icons.Outlined.Settings, Icons.Filled.Settings),
    )

    CompositionLocalProvider(LocalStrings provides strings) {
        PicoClawTheme(mode = state.theme) {
            AdaptiveNavBar(
                destinations = destinations,
                selectedIndex = selectedIndex,
                onDestinationSelected = { selectedIndex = it },
            ) {
                AnimatedContent(
                    targetState = selectedIndex,
                    transitionSpec = {
                        slideInVertically { h -> h } togetherWith slideOutVertically { h -> -h }
                    },
                    label = "page_transition",
                ) { index ->
                    when (index) {
                        0 -> DashboardPage(
                            state = state,
                            onIntent = viewModel::onIntent,
                            onNavigateToConfig = { selectedIndex = CONFIG_TAB },
                        )
                        1 -> WebViewPage(state = state, onIntent = viewModel::onIntent)
                        2 -> LogPage(state = state, onIntent = viewModel::onIntent)
                        3 -> ConfigPage(state = state, onIntent = viewModel::onIntent)
                        else -> DashboardPage(
                            state = state,
                            onIntent = viewModel::onIntent,
                            onNavigateToConfig = { selectedIndex = CONFIG_TAB },
                        )
                    }
                }
            }
        }
    }
}
