package com.milki.launcher.presentation.launcher.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.milki.launcher.domain.model.LauncherInteractionCatalog
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.actionForTrigger
import com.milki.launcher.domain.repository.SettingsRepository
import com.milki.launcher.presentation.drawer.AppDrawerUiState
import com.milki.launcher.presentation.drawer.AppDrawerViewModel
import com.milki.launcher.presentation.home.HomeViewModel
import com.milki.launcher.presentation.launcher.LocalContextMenuDismissSignal
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchUiState
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
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Composable root for launcher home.
 *
 * STARTUP OPTIMIZATION:
 * SearchViewModel and AppDrawerViewModel are accepted as provider functions so
 * their Koin construction is deferred until actual use. On the first frame, only
 * HomeViewModel state (pinnedItems) and SettingsRepository (launcherSettings)
 * are collected. Search and drawer state collection is deferred until the VMs
 * are resolved (which happens after the first frame via deferred startup or
 * user interaction).
 */
@Composable
internal fun LauncherRootContent(
    runtime: LauncherHostRuntime,
    onOpenSettings: () -> Unit,
    showSetDefaultLauncherPrompt: Boolean,
    onSetDefaultLauncher: () -> Unit,
    onDismissSetDefaultLauncherPrompt: () -> Unit,
    searchViewModelProvider: () -> SearchViewModel,
    homeViewModel: HomeViewModel,
    appDrawerViewModelProvider: () -> AppDrawerViewModel,
    settingsRepository: SettingsRepository,
    widgetHostManager: WidgetHostManager,
    obtainWidgetPickerCatalogStore: () -> WidgetPickerCatalogStore
) {
    val pinnedItems by homeViewModel.pinnedItems.collectAsStateWithLifecycle()
    val openFolderItem by homeViewModel.openFolderItem.collectAsStateWithLifecycle()
    val launcherSettings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = LauncherSettings()
    )

    // Lazily resolve VMs that are not needed for the first frame.
    // The remember block ensures VMs are only constructed once per composition root.
    var searchViewModel by remember { mutableStateOf<SearchViewModel?>(null) }
    var appDrawerViewModel by remember { mutableStateOf<AppDrawerViewModel?>(null) }

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
            // Wait for the first frame to be drawn, then kick off deferred work.
            withFrameNanos { }

            // Now resolve the lazy VMs — this is post-first-frame so it won't
            // impact TTID. Once resolved, the UI will recompose to collect their state.
            searchViewModel = searchViewModelProvider()
            appDrawerViewModel = appDrawerViewModelProvider()

            val catalogStore = obtainWidgetPickerCatalogStore()
            widgetPickerCatalogStore = catalogStore
            runtime.completeDeferredStartup(catalogStore)
        }

        // Collect search/drawer state only after VMs are resolved.
        // Before that, use safe defaults (search hidden, drawer empty).
        val resolvedSearchVm = searchViewModel
        val resolvedDrawerVm = appDrawerViewModel

        val searchUiState by (resolvedSearchVm?.uiState
            ?: remember { MutableStateFlow(SearchUiState()) })
            .collectAsStateWithLifecycle()

        val appDrawerUiState by (resolvedDrawerVm?.uiState
            ?: remember { MutableStateFlow(AppDrawerUiState()) })
            .collectAsStateWithLifecycle()

        LaunchedEffect(
            searchUiState.isSearchVisible,
            surfaceStateCoordinator.isAppDrawerOpen,
            surfaceStateCoordinator.isWidgetPickerOpen,
            openFolderItem?.id
        ) {
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

        val enabledHomeTriggers = remember(launcherSettings) {
            LauncherInteractionCatalog.configurableTriggers
                .filter { trigger ->
                    launcherSettings.actionForTrigger(trigger) != LauncherTriggerAction.DO_NOTHING
                }
                .toSet()
        }

        LauncherTheme {
            val launcherActions = remember(
                context,
                launcherSettings,
                onOpenSettings,
                resolvedSearchVm,
                resolvedDrawerVm
            ) {
                LauncherActions(
                    search = SearchActions(
                        onQueryChange = { query ->
                            resolvedSearchVm?.onQueryChange(query)
                        },
                        onDismissSearch = {
                            surfaceStateCoordinator.dismissContextMenus()
                            resolvedSearchVm?.hideSearch()
                        }
                    ),
                    menu = MenuActions(
                        onOpenSettings = onOpenSettings,
                        onHomescreenMenuOpenChange = surfaceStateCoordinator::updateHomescreenMenuOpen
                    ),
                    drawer = DrawerActions(
                        onAppDrawerOpenChange = surfaceStateCoordinator::updateAppDrawerOpen,
                        onQueryChange = { query ->
                            resolvedDrawerVm?.updateQuery(query)
                        }
                    ),
                    home = HomeActions(
                        onHomeTrigger = { trigger ->
                            val action = launcherSettings.actionForTrigger(trigger)
                            surfaceStateCoordinator.handleHomeTriggerAction(action)
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
                enabledHomeTriggers = enabledHomeTriggers,
                isHomescreenMenuOpen = surfaceStateCoordinator.isHomescreenMenuOpen,
                isAppDrawerOpen = surfaceStateCoordinator.isAppDrawerOpen,
                appDrawerUiState = appDrawerUiState,
                isWidgetPickerOpen = surfaceStateCoordinator.isWidgetPickerOpen,
                widgetPickerQuery = surfaceStateCoordinator.widgetPickerQuery,
                widgetHostManager = widgetHostManager,
                widgetPickerCatalogStore = widgetPickerCatalogStore
            )

            if (showSetDefaultLauncherPrompt) {
                AlertDialog(
                    onDismissRequest = onDismissSetDefaultLauncherPrompt,
                    title = { Text("Set as default launcher") },
                    text = {
                        Text(
                            "Milki works best when it is your default Home app. " +
                                    "Set it as default now?"
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = onSetDefaultLauncher) {
                            Text("Set default")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismissSetDefaultLauncherPrompt) {
                            Text(
                                text = "Not now",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
    }
}
