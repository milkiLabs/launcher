package com.milki.launcher.presentation.launcher.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.data.widget.WidgetPickerCatalogStore
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.SwipeUpAction
import com.milki.launcher.domain.repository.SettingsRepository
import com.milki.launcher.presentation.drawer.AppDrawerViewModel
import com.milki.launcher.presentation.home.HomeViewModel
import com.milki.launcher.presentation.launcher.LocalContextMenuDismissSignal
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchViewModel
import com.milki.launcher.ui.screens.launcher.DrawerActions
import com.milki.launcher.ui.screens.launcher.FolderActions
import com.milki.launcher.ui.screens.launcher.HomeActions
import com.milki.launcher.ui.screens.launcher.LauncherActions
import com.milki.launcher.ui.screens.launcher.LauncherScreen
import com.milki.launcher.ui.screens.launcher.MenuActions
import com.milki.launcher.ui.screens.launcher.SearchActions
import com.milki.launcher.ui.screens.launcher.WidgetActions
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
    onOpenSettings: () -> Unit,
    searchViewModel: SearchViewModel,
    homeViewModel: HomeViewModel,
    appDrawerViewModel: AppDrawerViewModel,
    settingsRepository: SettingsRepository,
    widgetHostManager: WidgetHostManager,
    obtainWidgetPickerCatalogStore: () -> WidgetPickerCatalogStore
) {
    val searchUiState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val pinnedItems by homeViewModel.pinnedItems.collectAsStateWithLifecycle()
    val openFolderItem by homeViewModel.openFolderItem.collectAsStateWithLifecycle()
    val appDrawerUiState by appDrawerViewModel.uiState.collectAsStateWithLifecycle()
    val launcherSettings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = LauncherSettings()
    )

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val surfaceStateCoordinator = runtime.surfaceStateCoordinator
    val homeController = remember(homeViewModel, runtime.widgetPlacementCoordinator, widgetHostManager) {
        LauncherHomeController(
            homeViewModel = homeViewModel,
            widgetPlacementCoordinator = runtime.widgetPlacementCoordinator,
            widgetHostManager = widgetHostManager
        )
    }
    var widgetPickerCatalogStore by remember { mutableStateOf<WidgetPickerCatalogStore?>(null) }

    CompositionLocalProvider(
        LocalSearchActionHandler provides runtime::dispatchSearchResultAction,
        LocalContextMenuDismissSignal provides surfaceStateCoordinator.contextMenuDismissSignal
    ) {
        SideEffect {
            runtime.updateSearchClosePolicy(
                closeSearchOnLaunch = launcherSettings.closeSearchOnLaunch
            )
        }

        LaunchedEffect(runtime) {
            withFrameNanos { }
            val catalogStore = obtainWidgetPickerCatalogStore()
            widgetPickerCatalogStore = catalogStore
            runtime.completeDeferredStartup(catalogStore)
        }

        LaunchedEffect(
            searchUiState.isSearchVisible,
            surfaceStateCoordinator.isAppDrawerOpen,
            surfaceStateCoordinator.isWidgetPickerOpen,
            openFolderItem?.id
        ) {
            // Keep IME cleanup at the launcher host level so overlapping surface
            // transitions do not fight each other by hiding the keyboard too early.
            val hasImeOwningSurface =
                searchUiState.isSearchVisible ||
                    surfaceStateCoordinator.isAppDrawerOpen ||
                    surfaceStateCoordinator.isWidgetPickerOpen ||
                    openFolderItem != null
            if (!hasImeOwningSurface) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        }

        LauncherTheme {
            val launcherActions = remember(
                context,
                launcherSettings.swipeUpAction,
                onOpenSettings
            ) {
                LauncherActions(
                    search = SearchActions(
                        onQueryChange = searchViewModel::onQueryChange,
                        onDismissSearch = {
                            surfaceStateCoordinator.dismissContextMenus()
                            searchViewModel.hideSearch()
                        }
                    ),
                    menu = MenuActions(
                        onOpenSettings = onOpenSettings,
                        onHomescreenMenuOpenChange = surfaceStateCoordinator::updateHomescreenMenuOpen
                    ),
                    drawer = DrawerActions(
                        onAppDrawerOpenChange = surfaceStateCoordinator::updateAppDrawerOpen,
                        onQueryChange = appDrawerViewModel::updateQuery
                    ),
                    home = HomeActions(
                        onHomeSwipeUp = {
                            surfaceStateCoordinator.handleHomeSwipeUp(launcherSettings.swipeUpAction)
                        },
                        onPinnedItemClick = { item -> homeController.onPinnedItemClick(item, context) },
                        onPinnedItemLongPress = {},
                        onPinnedItemMove = homeController::onPinnedItemMove,
                        onItemDroppedToHome = homeController::onItemDroppedToHome
                    ),
                    folder = FolderActions(
                        onCreateFolder = homeController::onCreateFolder,
                        onAddItemToFolder = homeController::onAddItemToFolder,
                        onMergeFolders = homeController::onMergeFolders,
                        onFolderClose = homeController::onFolderClose,
                        onFolderRename = homeController::onFolderRename,
                        onFolderItemClick = { item -> homeController.onFolderItemClick(item, context) },
                        onFolderItemRemove = homeController::onRemoveItemFromFolder,
                        onFolderItemReorder = homeController::onReorderFolderItems,
                        onExtractItemFromFolder = homeController::onExtractItemFromFolder,
                        onMoveFolderItemToFolder = homeController::onMoveFolderItemToFolder,
                        onFolderChildDroppedOnItem = homeController::onFolderChildDroppedOnItem
                    ),
                    widget = WidgetActions(
                        onWidgetPickerOpenChange = surfaceStateCoordinator::updateWidgetPickerOpen,
                        onWidgetPickerQueryChange = surfaceStateCoordinator::updateWidgetPickerQuery,
                        onRemoveWidget = { widgetId, _ -> homeController.onRemoveWidget(widgetId) },
                        onUpdateWidgetFrame = homeController::onUpdateWidgetFrame,
                        onWidgetDroppedToHome = homeController::onWidgetDroppedToHome
                    )
                )
            }

            LauncherScreen(
                searchUiState = searchUiState,
                pinnedItems = pinnedItems,
                openFolderItem = openFolderItem,
                actions = launcherActions,
                isHomeSwipeEnabled = launcherSettings.swipeUpAction != SwipeUpAction.DO_NOTHING,
                isHomescreenMenuOpen = surfaceStateCoordinator.isHomescreenMenuOpen,
                isAppDrawerOpen = surfaceStateCoordinator.isAppDrawerOpen,
                appDrawerUiState = appDrawerUiState,
                isWidgetPickerOpen = surfaceStateCoordinator.isWidgetPickerOpen,
                widgetPickerQuery = surfaceStateCoordinator.widgetPickerQuery,
                widgetHostManager = widgetHostManager,
                widgetPickerCatalogStore = widgetPickerCatalogStore
            )
        }
    }
}
