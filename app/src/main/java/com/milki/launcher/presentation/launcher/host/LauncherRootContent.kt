package com.milki.launcher.presentation.launcher.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.SwipeUpAction
import com.milki.launcher.domain.repository.SettingsRepository
import com.milki.launcher.presentation.drawer.AppDrawerViewModel
import com.milki.launcher.presentation.home.HomeViewModel
import com.milki.launcher.presentation.launcher.LocalContextMenuDismissSignal
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchViewModel
import com.milki.launcher.ui.screens.launcher.LauncherScreen
import com.milki.launcher.ui.theme.LauncherTheme

/**
 * Composable root for launcher home.
 *
 * This host keeps UI state collection and CompositionLocal setup together,
 * while MainActivity stays a thin Android lifecycle shell.
 */
@Composable
internal fun LauncherRootContent(
    runtime: LauncherHostRuntime,
    actionFactory: LauncherActionFactory,
    searchViewModel: SearchViewModel,
    homeViewModel: HomeViewModel,
    appDrawerViewModel: AppDrawerViewModel,
    settingsRepository: SettingsRepository,
    widgetHostManager: WidgetHostManager
) {
    val searchUiState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val appDrawerUiState by appDrawerViewModel.uiState.collectAsStateWithLifecycle()
    val launcherSettings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = LauncherSettings()
    )

    val context = LocalContext.current
    val surfaceStateCoordinator = runtime.surfaceStateCoordinator

    CompositionLocalProvider(
        LocalSearchActionHandler provides runtime::dispatchSearchResultAction,
        LocalContextMenuDismissSignal provides surfaceStateCoordinator.contextMenuDismissSignal
    ) {
        SideEffect {
            runtime.updateSearchClosePolicy(
                closeSearchOnLaunch = launcherSettings.closeSearchOnLaunch
            )
            runtime.updateHomeButtonQueryClearPolicy(
                clearDrawerQuery = launcherSettings.homeButtonClearsDrawerQuery,
                clearWidgetPickerQuery = launcherSettings.homeButtonClearsWidgetPickerQuery
            )
        }

        LauncherTheme {
            LauncherScreen(
                searchUiState = searchUiState,
                homeUiState = homeUiState,
                actions = actionFactory.build(
                    context = context,
                    swipeUpAction = launcherSettings.swipeUpAction
                ),
                isHomeSwipeEnabled = launcherSettings.swipeUpAction != SwipeUpAction.DO_NOTHING,
                isHomescreenMenuOpen = surfaceStateCoordinator.isHomescreenMenuOpen,
                isAppDrawerOpen = surfaceStateCoordinator.isAppDrawerOpen,
                appDrawerUiState = appDrawerUiState,
                isWidgetPickerOpen = surfaceStateCoordinator.isWidgetPickerOpen,
                widgetPickerQuery = surfaceStateCoordinator.widgetPickerQuery,
                widgetHostManager = widgetHostManager
            )
        }
    }
}