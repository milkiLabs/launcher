package com.milki.launcher.presentation.launcher.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milki.launcher.core.intent.launchApp
import com.milki.launcher.core.intent.launchAppShortcut
import com.milki.launcher.data.widget.WidgetPickerCatalogStore
import com.milki.launcher.domain.widget.WidgetHostPort
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.LauncherInteractionCatalog
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.LauncherTriggerTarget
import com.milki.launcher.domain.model.actionForTrigger
import com.milki.launcher.domain.model.targetForTrigger
import com.milki.launcher.domain.repository.SettingsReader
import com.milki.launcher.domain.repository.ActionShortcutRepository
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.presentation.drawer.AppDrawerUiState
import com.milki.launcher.presentation.drawer.AppDrawerViewModel
import com.milki.launcher.presentation.home.HomeViewModel

import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchUiState
import com.milki.launcher.presentation.search.SearchViewModel
import com.milki.launcher.ui.screens.launcher.DrawerActions
import com.milki.launcher.ui.screens.launcher.FolderActions
import com.milki.launcher.ui.screens.launcher.HomeActions
import com.milki.launcher.ui.screens.launcher.LauncherActions
import com.milki.launcher.ui.screens.launcher.LauncherScreen
import com.milki.launcher.ui.components.launcher.widget.LocalWidgetHost
import com.milki.launcher.ui.screens.launcher.MenuActions
import com.milki.launcher.ui.screens.launcher.SearchActions
import com.milki.launcher.ui.screens.launcher.ShortcutManagerActions
import com.milki.launcher.ui.screens.launcher.WidgetActions
import com.milki.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Composable root for launcher home.
 *
 * STARTUP OPTIMIZATION:
 * SearchViewModel and AppDrawerViewModel are accepted as provider functions so
 * their Koin construction is deferred until actual use. On the first frame, only
 * HomeViewModel state (pinnedItems) and SettingsReader (launcherSettings)
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
    settingsRepository: SettingsReader,
    appRepositoryProvider: () -> AppRepository,
    actionShortcutRepository: ActionShortcutRepository,
    widgetHost: WidgetHostPort,
    obtainWidgetPickerCatalogStore: () -> WidgetPickerCatalogStore
) {
    val pinnedItems by homeViewModel.pinnedItems.collectAsStateWithLifecycle()
    val openFolderItem by homeViewModel.openFolderItem.collectAsStateWithLifecycle()
    val actionShortcuts by actionShortcutRepository.shortcuts.collectAsStateWithLifecycle(
        initialValue = emptyList()
    )
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
    val navigator = runtime.launcherNavigator
    val scope = rememberCoroutineScope()
    val homeController = remember(homeViewModel, runtime.widgetPlacementCoordinator) {
        LauncherHomeController(
            homeViewModel = homeViewModel,
            widgetPlacementCoordinator = runtime.widgetPlacementCoordinator,
            scope = scope
        )
    }
    var widgetPickerCatalogStore by remember { mutableStateOf<WidgetPickerCatalogStore?>(null) }

    CompositionLocalProvider(
        LocalSearchActionHandler provides runtime::dispatchSearchResultAction
    ) {
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
        val installedAppsFlow = remember(navigator.isShortcutManagerOpen) {
            if (navigator.isShortcutManagerOpen) {
                appRepositoryProvider().observeInstalledApps()
            } else {
                kotlinx.coroutines.flow.flowOf(emptyList())
            }
        }

        val searchUiState by (resolvedSearchVm?.uiState
            ?: remember { MutableStateFlow(SearchUiState()) })
            .collectAsStateWithLifecycle()
        val installedApps by installedAppsFlow.collectAsStateWithLifecycle(
            initialValue = emptyList()
        )

        val appDrawerUiState by (resolvedDrawerVm?.uiState
            ?: remember { MutableStateFlow(AppDrawerUiState()) })
            .collectAsStateWithLifecycle()

        LaunchedEffect(navigator.currentRoute) {
            val hasImeOwningSurface = !navigator.isAtHome
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
                resolvedDrawerVm,
                actionShortcutRepository,
                scope
            ) {
                LauncherActions(
                    search = SearchActions(
                        onQueryChange = { query ->
                            resolvedSearchVm?.onQueryChange(query)
                        },
                        onDismissSearch = {
                            navigator.pop()
                        }
                    ),
                    menu = MenuActions(
                        onOpenSettings = {
                            navigator.clearTransientRoutes()
                            onOpenSettings()
                        },
                        onHomescreenMenuOpenChange = navigator::updateHomescreenMenuOpen,
                        onOpenShortcutManager = {
                            navigator.updateShortcutManagerOpen(true)
                        }
                    ),
                    shortcuts = ShortcutManagerActions(
                        onShortcutManagerOpenChange = navigator::updateShortcutManagerOpen,
                        onSaveShortcut = { shortcut, onResult ->
                            scope.launch {
                                val success = actionShortcutRepository.saveShortcut(shortcut)
                                onResult(success)
                            }
                        },
                        onDeleteShortcut = { shortcut ->
                            scope.launch {
                                actionShortcutRepository.deleteShortcut(shortcut.id)
                                homeViewModel.unpinItem(shortcut.id)
                            }
                        },
                        onShortcutExternalDragStarted = {
                            navigator.clearTransientRoutes()
                        }
                    ),
                    drawer = DrawerActions(
                        onAppDrawerOpenChange = navigator::updateAppDrawerOpen,
                        onQueryChange = { query ->
                            resolvedDrawerVm?.updateQuery(query)
                        }
                    ),
                    home = HomeActions(
                        onHomeTrigger = { trigger ->
                            val action = launcherSettings.actionForTrigger(trigger)
                            navigator.handleHomeTriggerAction(
                                action = action,
                                onOpenAppTarget = {
                                    openTriggerLaunchTarget(
                                        context = context,
                                        target = launcherSettings.targetForTrigger(trigger)
                                    )
                                }
                            )
                        },
                        onPinnedItemClick = { item ->
                            if (item is com.milki.launcher.domain.model.HomeItem.FolderItem) {
                                navigator.updateFolderOpen(item.id)
                            } else {
                                navigator.clearTransientRoutes()
                                homeController.onPinnedItemClick(item, context)
                            }
                        },
                        onPinnedItemLongPress = {},
                        onPinnedItemMove = homeController::onPinnedItemMove,
                        onItemDroppedToHome = homeController::onItemDroppedToHome
                    ),
                    folder = FolderActions(
                        onCreateFolder = homeController::onCreateFolder,
                        onAddItemToFolder = homeController::onAddItemToFolder,
                        onMergeFolders = homeController::onMergeFolders,
                        onFolderClose = {
                            navigator.updateFolderOpen(null)
                        },
                        onFolderRename = homeController::onFolderRename,
                        onFolderItemClick = { item ->
                            navigator.clearTransientRoutes()
                            homeController.onFolderItemClick(item, context)
                        },
                        onFolderItemRemove = homeController::onRemoveItemFromFolder,
                        onFolderItemReorder = homeController::onReorderFolderItems,
                        onExtractItemFromFolder = homeController::onExtractItemFromFolder,
                        onMoveFolderItemToFolder = homeController::onMoveFolderItemToFolder,
                        onFolderChildDroppedOnItem = homeController::onFolderChildDroppedOnItem
                    ),
                    widget = WidgetActions(
                        onWidgetPickerOpenChange = navigator::updateWidgetPickerOpen,
                        onWidgetPickerQueryChange = navigator::updateWidgetPickerQuery,
                        onWidgetExternalDragStarted = {
                            navigator.clearTransientRoutes()
                        },
                        onRemoveWidget = { widgetId, _ -> homeController.onRemoveWidget(widgetId) },
                        onUpdateWidgetFrame = homeController::onUpdateWidgetFrame,
                        onUpdateWidgetDisplayMode = homeController::onUpdateWidgetDisplayMode,
                        onExpandPopupWidget = homeController::onExpandPopupWidget,
                        onLaunchWidgetApp = { packageName ->
                            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                            if (intent != null) {
                                navigator.clearTransientRoutes()
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        },
                        onWidgetDroppedToHome = homeController::onWidgetDroppedToHome
                    )
                )
            }

            CompositionLocalProvider(LocalWidgetHost provides widgetHost) {
                LauncherScreen(
                    navigator = navigator,
                    searchUiState = searchUiState,
                    pinnedItems = pinnedItems,
                    openFolderItem = openFolderItem,
                    actions = launcherActions,
                    enabledHomeTriggers = enabledHomeTriggers,
                    isHomescreenMenuOpen = navigator.isHomescreenMenuOpen,
                    appDrawerUiState = appDrawerUiState,
                    actionShortcuts = actionShortcuts,
                    installedApps = installedApps,
                    widgetPickerCatalogStore = widgetPickerCatalogStore
                )
            }

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

private fun openTriggerLaunchTarget(
    context: android.content.Context,
    target: LauncherTriggerTarget?
) {
    when (target) {
        is LauncherTriggerTarget.App -> {
            launchApp(
                context = context,
                appInfo = AppInfo(
                    name = target.displayName,
                    packageName = target.packageName,
                    activityName = target.activityName
                )
            )
        }

        is LauncherTriggerTarget.AppShortcut -> {
            launchAppShortcut(
                context = context,
                appShortcut = target.toHomeShortcut()
            )
        }

        is LauncherTriggerTarget.ActionShortcut -> {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse(target.destinationUri)
                flags =
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER
                target.packageName?.let { setPackage(it) }
            }
            kotlin.runCatching {
                context.startActivity(intent)
            }.onFailure {
                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse(target.destinationUri)
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    target.packageName?.let { setPackage(it) }
                }
                kotlin.runCatching {
                    context.startActivity(fallbackIntent)
                }
            }
        }

        null -> Unit
    }
}
