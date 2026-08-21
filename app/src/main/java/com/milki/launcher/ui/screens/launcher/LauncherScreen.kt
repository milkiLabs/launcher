package com.milki.launcher.ui.screens.launcher

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.milki.launcher.data.widget.WidgetPickerCatalogStore
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.LauncherGestureKind
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.WidgetDisplayMode
import com.milki.launcher.presentation.drawer.AppDrawerUiState
import com.milki.launcher.presentation.launcher.LauncherNavigator
import com.milki.launcher.presentation.launcher.LauncherRoute
import com.milki.launcher.presentation.search.SearchUiState
import com.milki.launcher.ui.components.launcher.AppDrawerOverlay
import com.milki.launcher.ui.components.launcher.DraggablePinnedItemsGrid
import com.milki.launcher.ui.components.launcher.ItemActionMenu
import com.milki.launcher.ui.components.launcher.LauncherSheetState
import com.milki.launcher.ui.components.launcher.MenuAction
import com.milki.launcher.ui.components.launcher.folder.FolderPopupDialog
import com.milki.launcher.ui.components.launcher.rememberLauncherSheetState
import com.milki.launcher.ui.components.launcher.widget.WidgetPickerBottomSheet
import com.milki.launcher.ui.components.search.AppSearchDialog
import com.milki.launcher.ui.interaction.grid.HomeBackgroundGestureBindings
import com.milki.launcher.ui.theme.Spacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Main launcher surface.
 *
 * This file intentionally stays focused on screen composition and layered-surface
 * orchestration. Gesture/action semantics are modeled through [LauncherTrigger]
 * so the screen scales as more homescreen gestures are added.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    navigator: LauncherNavigator,
    searchUiState: SearchUiState,
    pinnedItems: List<HomeItem>,
    openFolderItem: HomeItem.FolderItem?,
    actions: LauncherActions = LauncherActions(),
    enabledHomeTriggers: Set<LauncherTrigger> = emptySet(),
    isHomescreenMenuOpen: Boolean = false,
    appDrawerUiState: AppDrawerUiState = AppDrawerUiState(),
    drawerBenchmarkScrollEvents: Flow<Unit> = emptyFlow(),
    actionShortcuts: List<HomeItem.ActionShortcut> = emptyList(),
    installedApps: List<AppInfo> = emptyList(),
    widgetPickerCatalogStore: WidgetPickerCatalogStore? = null,
) {
    val appDrawerSheetState = rememberLauncherSheetState()
    val widgetPickerSheetState = rememberLauncherSheetState()
    val shortcutManagerSheetState = rememberLauncherSheetState()
    val overlaySceneStrategy = remember {
        LauncherOverlaySceneStrategy<LauncherRoute>()
    }
    val homeSurfaceActions = actions.toHomeSurfaceActions()
    var homescreenMenuAnchorPx by remember { mutableStateOf(Offset.Zero) }
    val homeItemBoundsById = remember { mutableStateMapOf<String, Rect>() }

    val currentSearchUiState by rememberUpdatedState(searchUiState)
    val currentPinnedItems by rememberUpdatedState(pinnedItems)
    val currentOpenFolderItem by rememberUpdatedState(openFolderItem)
    val currentActions by rememberUpdatedState(actions)
    val currentEnabledHomeTriggers by rememberUpdatedState(enabledHomeTriggers)
    val currentHomescreenMenuOpen by rememberUpdatedState(isHomescreenMenuOpen)
    val currentAppDrawerUiState by rememberUpdatedState(appDrawerUiState)
    val currentActionShortcuts by rememberUpdatedState(actionShortcuts)
    val currentInstalledApps by rememberUpdatedState(installedApps)
    val currentWidgetPickerCatalogStore by rememberUpdatedState(widgetPickerCatalogStore)

    BackHandler(enabled = navigator.isAtHome) {
        // The launcher Home root consumes back instead of finishing the activity.
    }

    NavDisplay(
        backStack = navigator.backStack,
        onBack = { navigator.pop() },
        sceneStrategies = listOf(overlaySceneStrategy),
        entryProvider = { route ->
            when (route) {
                LauncherRoute.Home -> NavEntry(route) {
                    val activeHomeTriggers = selectActiveHomeTriggers(
                        enabledHomeTriggers = currentEnabledHomeTriggers,
                        isHomescreenMenuOpen = currentHomescreenMenuOpen,
                        hasNavigationOverlay = !navigator.isAtHome
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        HomeSurface(
                            pinnedItems = currentPinnedItems,
                            actions = homeSurfaceActions,
                            enabledHomeTriggers = activeHomeTriggers,
                            onMenuAnchorChanged = {
                                homescreenMenuAnchorPx = it
                            },
                            onItemBoundsMeasured = { itemId, boundsInWindow ->
                                homeItemBoundsById[itemId] = boundsInWindow
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.Center)
                        )

                        HomescreenMenu(
                            expanded = currentHomescreenMenuOpen,
                            anchorPx = homescreenMenuAnchorPx,
                            onDismiss = {
                                currentActions.menu
                                    .onHomescreenMenuOpenChange(false)
                            },
                            onOpenWidgets = {
                                currentActions.menu
                                    .onHomescreenMenuOpenChange(false)
                                currentActions.widget
                                    .onWidgetPickerOpenChange(true)
                            },
                            onOpenShortcuts = {
                                currentActions.menu
                                    .onHomescreenMenuOpenChange(false)
                                currentActions.menu.onOpenShortcutManager()
                            },
                            onOpenSettings = {
                                currentActions.menu
                                    .onHomescreenMenuOpenChange(false)
                                currentActions.menu.onOpenSettings()
                            }
                        )
                    }
                }

                LauncherRoute.Search -> overlayEntry(route) {
                    SearchOverlayHost(
                        searchUiState = currentSearchUiState,
                        searchActions = currentActions.search
                    )
                }

                LauncherRoute.AppDrawer -> overlayEntry(route) {
                    DrawerHost(
                        appDrawerSheetState = appDrawerSheetState,
                        appDrawerUiState = currentAppDrawerUiState,
                        benchmarkScrollEvents = drawerBenchmarkScrollEvents,
                        drawerActions = currentActions.drawer
                    )
                }

                is LauncherRoute.WidgetPicker -> overlayEntry(route) {
                    WidgetPickerHost(
                        widgetPickerSheetState = widgetPickerSheetState,
                        widgetPickerQuery = route.query,
                        widgetPickerCatalogStore =
                            currentWidgetPickerCatalogStore,
                        widgetActions = currentActions.widget
                    )
                }

                LauncherRoute.ShortcutManager -> overlayEntry(route) {
                    ShortcutManagerHost(
                        shortcutManagerSheetState = shortcutManagerSheetState,
                        shortcuts = currentActionShortcuts,
                        installedApps = currentInstalledApps,
                        shortcutActions = currentActions.shortcuts
                    )
                }

                is LauncherRoute.Folder -> overlayEntry(route) {
                    val folder = currentOpenFolderItem
                        ?.takeIf { it.id == route.folderId }

                    FolderOverlayHost(
                        openFolderItem = folder,
                        folderActions = currentActions.folder,
                        anchorBounds = folder?.let {
                            homeItemBoundsById[it.id]
                        }
                    )
                }
            }
        }
    )
}

private fun overlayEntry(
    route: LauncherRoute,
    content: @Composable () -> Unit
): NavEntry<LauncherRoute> {
    return NavEntry(
        key = route,
        metadata = LauncherOverlaySceneStrategy.overlay()
    ) {
        val lifecycleOwner = rememberLifecycleOwner()
        CompositionLocalProvider(
            LocalLifecycleOwner provides lifecycleOwner
        ) {
            content()
        }
    }
}

@Composable
private fun HomescreenMenu(
    expanded: Boolean,
    anchorPx: Offset,
    onDismiss: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenSettings: () -> Unit
) {
    if (!expanded) return

    val density = LocalDensity.current
    val xOffset = with(density) { anchorPx.x.toDp() }
    val yOffset = with(density) { anchorPx.y.toDp() }
    val actions = listOf(
        MenuAction(
            label = "Widgets",
            icon = Icons.Filled.Widgets,
            onClick = onOpenWidgets
        ),
        MenuAction(
            label = "Shortcuts",
            icon = Icons.Filled.Link,
            onClick = onOpenShortcuts
        ),
        MenuAction(
            label = "Settings",
            icon = Icons.Filled.Settings,
            onClick = onOpenSettings
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .offset(x = xOffset, y = yOffset)
                .size(1.dp)
        ) {
            ItemActionMenu(
                expanded = true,
                onDismiss = onDismiss,
                actions = actions
            )
        }
    }
}

/**
 * Hosts the home grid surface and routes all grid events to grouped action contracts.
 */
@Composable
private fun HomeSurface(
    pinnedItems: List<HomeItem>,
    actions: HomeSurfaceActions,
    enabledHomeTriggers: Set<LauncherTrigger>,
    onMenuAnchorChanged: (Offset) -> Unit,
    onItemBoundsMeasured: (String, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundGestures = buildHomeBackgroundGestures(
        enabledHomeTriggers = enabledHomeTriggers,
        onMenuAnchorChanged = onMenuAnchorChanged,
        actions = actions
    )

    DraggablePinnedItemsGrid(
        items = pinnedItems,
        onItemClick = actions.onPinnedItemClick,
        onItemLongPress = actions.onPinnedItemLongPress,
        onItemMove = actions.onPinnedItemMove,
        backgroundGestures = backgroundGestures,
        onItemDroppedToHome = { item, position ->
            actions.onItemDroppedToHome(item, position)
            actions.onDismissSearch()
        },
        onCreateFolder = actions.onCreateFolder,
        onAddItemToFolder = actions.onAddItemToFolder,
        onMergeFolders = actions.onMergeFolders,
        onFolderItemExtracted = actions.onExtractItemFromFolder,
        onMoveFolderItemToFolder = actions.onMoveFolderItemToFolder,
        onFolderChildDroppedOnItem = actions.onFolderChildDroppedOnItem,
        onRemoveWidget = actions.onRemoveWidget,
        onUpdateWidgetFrame = actions.onUpdateWidgetFrame,
        onUpdateWidgetDisplayMode = actions.onUpdateWidgetDisplayMode,
        onExpandPopupWidget = actions.onExpandPopupWidget,
        onLaunchWidgetApp = actions.onLaunchWidgetApp,
        onWidgetDroppedToHome = actions.onWidgetDroppedToHome,
        onItemBoundsMeasured = onItemBoundsMeasured,
        modifier = modifier.padding(
            horizontal = Spacing.mediumLarge,
            vertical = Spacing.small
        )
    )
}

/**
 * Hosts folder popup lifecycle and delegates actions through folder contracts.
 */
@Composable
private fun FolderOverlayHost(
    openFolderItem: HomeItem.FolderItem?,
    folderActions: FolderActions,
    anchorBounds: Rect?
) {
    openFolderItem?.let { folder ->
        key(folder.id) {
            FolderPopupDialog(
                folder = folder,
                anchorBounds = anchorBounds,
                onClose = folderActions.onFolderClose,
                onRenameFolder = { newName ->
                    folderActions.onFolderRename(folder.id, newName)
                },
                onItemClick = folderActions.onFolderItemClick,
                onReorderFolderItems = { newChildren ->
                    folderActions.onFolderItemReorder(folder.id, newChildren)
                },
                onRemoveItemFromFolder = { itemId ->
                    folderActions.onFolderItemRemove(folder.id, itemId)
                }
            )
        }
    }
}

/**
 * Hosts app drawer bottom sheet and keeps drawer-specific UI isolated.
 */
@Composable
private fun DrawerHost(
    appDrawerSheetState: LauncherSheetState,
    appDrawerUiState: AppDrawerUiState,
    benchmarkScrollEvents: Flow<Unit>,
    drawerActions: DrawerActions
) {
    LauncherSurfaceSheetHost(
        isOpen = true,
        sheetState = appDrawerSheetState,
        onDismissRequest = { drawerActions.onAppDrawerOpenChange(false) }
    ) { dragHandleModifier ->
        AppDrawerOverlay(
            uiState = appDrawerUiState,
            onQueryChange = drawerActions.onQueryChange,
            onDismiss = { drawerActions.onAppDrawerOpenChange(false) },
            headerDragHandleModifier = dragHandleModifier,
            modifier = Modifier.fillMaxSize(),
            benchmarkScrollEvents = benchmarkScrollEvents
        )
    }
}

/**
 * Hosts widget picker bottom sheet and routes dismissal events through widget contracts.
 */
@Composable
private fun WidgetPickerHost(
    widgetPickerSheetState: LauncherSheetState,
    widgetPickerQuery: String,
    widgetPickerCatalogStore: WidgetPickerCatalogStore?,
    widgetActions: WidgetActions
) {
    if (widgetPickerCatalogStore == null) return

    LauncherSurfaceSheetHost(
        isOpen = true,
        sheetState = widgetPickerSheetState,
        onDismissRequest = { widgetActions.onWidgetPickerOpenChange(false) }
    ) { dragHandleModifier ->
        WidgetPickerBottomSheet(
            catalogStore = widgetPickerCatalogStore,
            searchQuery = widgetPickerQuery,
            onSearchQueryChange = widgetActions.onWidgetPickerQueryChange,
            headerDragHandleModifier = dragHandleModifier,
            onExternalDragStarted = widgetActions.onWidgetExternalDragStarted
        )
    }
}

@Composable
private fun ShortcutManagerHost(
    shortcutManagerSheetState: LauncherSheetState,
    shortcuts: List<HomeItem.ActionShortcut>,
    installedApps: List<AppInfo>,
    shortcutActions: ShortcutManagerActions
) {
    LauncherSurfaceSheetHost(
        isOpen = true,
        sheetState = shortcutManagerSheetState,
        onDismissRequest = { shortcutActions.onShortcutManagerOpenChange(false) }
    ) { dragHandleModifier ->
        ActionShortcutManagerSheet(
            shortcuts = shortcuts,
            installedApps = installedApps,
            onSaveShortcut = shortcutActions.onSaveShortcut,
            onDeleteShortcut = shortcutActions.onDeleteShortcut,
            onDismissRequest = {
                shortcutActions.onShortcutManagerOpenChange(false)
            },
            onExternalDragStarted = shortcutActions.onShortcutExternalDragStarted,
            headerDragHandleModifier = dragHandleModifier
        )
    }
}

@Composable
private fun SearchOverlayHost(
    searchUiState: SearchUiState,
    searchActions: SearchActions
) {
    if (!searchUiState.isSearchVisible) return

    AppSearchDialog(
        uiState = searchUiState,
        onQueryChange = searchActions.onQueryChange,
        onDismiss = searchActions.onDismissSearch
    )
}

private fun selectActiveHomeTriggers(
    enabledHomeTriggers: Set<LauncherTrigger>,
    isHomescreenMenuOpen: Boolean,
    hasNavigationOverlay: Boolean
): Set<LauncherTrigger> {
    val isBackgroundGestureSurfaceBlocked =
        isHomescreenMenuOpen ||
                hasNavigationOverlay

    return if (isBackgroundGestureSurfaceBlocked) {
        emptySet()
    } else {
        enabledHomeTriggers
    }
}

private fun buildHomeBackgroundGestures(
    enabledHomeTriggers: Set<LauncherTrigger>,
    onMenuAnchorChanged: (Offset) -> Unit,
    actions: HomeSurfaceActions
): HomeBackgroundGestureBindings {
    val hasDirectionalTrigger = enabledHomeTriggers.any { trigger ->
        trigger.metadata.kind == LauncherGestureKind.SWIPE
    }

    return HomeBackgroundGestureBindings(
        configuredTriggers = enabledHomeTriggers,
        onEmptyAreaTap = enabledHomeTriggers.takeIf { LauncherTrigger.HOME_TAP in it }?.let {
            { actions.onHomeTrigger(LauncherTrigger.HOME_TAP) }
        },
        onEmptyAreaDoubleTap = enabledHomeTriggers
            .takeIf { LauncherTrigger.HOME_DOUBLE_TAP in it }
            ?.let {
                { actions.onHomeTrigger(LauncherTrigger.HOME_DOUBLE_TAP) }
            },
        onEmptyAreaLongPress = { touchOffset ->
            onMenuAnchorChanged(touchOffset)
            actions.onHomescreenMenuOpenChange(true)
        },
        onTrigger = if (hasDirectionalTrigger) actions.onHomeTrigger else null
    )
}

private data class HomeSurfaceActions(
    val onPinnedItemClick: (HomeItem) -> Unit,
    val onPinnedItemLongPress: (HomeItem) -> Unit,
    val onPinnedItemMove: (itemId: String, newPosition: GridPosition) -> Unit,
    val onItemDroppedToHome: (HomeItem, GridPosition) -> Unit,
    val onHomeTrigger: (LauncherTrigger) -> Unit,
    val onCreateFolder: CreateFolderAction,
    val onAddItemToFolder: (folderId: String, item: HomeItem) -> Unit,
    val onMergeFolders: (sourceFolderId: String, targetFolderId: String) -> Unit,
    val onExtractItemFromFolder: ExtractItemFromFolderAction,
    val onMoveFolderItemToFolder: MoveFolderItemToFolderAction,
    val onFolderChildDroppedOnItem: FolderChildDroppedOnItemAction,
    val onRemoveWidget: (widgetId: String, appWidgetId: Int) -> Unit,
    val onUpdateWidgetFrame: (
        widgetId: String,
        newPosition: GridPosition,
        newSpan: GridSpan
    ) -> Unit,
    val onUpdateWidgetDisplayMode: (
        widgetId: String,
        displayMode: WidgetDisplayMode
    ) -> Unit,
    val onExpandPopupWidget: (widgetId: String, visibleRows: Int) -> Unit,
    val onLaunchWidgetApp: (packageName: String) -> Unit,
    val onWidgetDroppedToHome: (
        providerInfo: AppWidgetProviderInfo,
        span: GridSpan,
        dropPosition: GridPosition,
        displayMode: WidgetDisplayMode
    ) -> Unit,
    val onDismissSearch: () -> Unit,
    val onHomescreenMenuOpenChange: (Boolean) -> Unit
)

private fun LauncherActions.toHomeSurfaceActions(): HomeSurfaceActions {
    return HomeSurfaceActions(
        onPinnedItemClick = home.onPinnedItemClick,
        onPinnedItemLongPress = home.onPinnedItemLongPress,
        onPinnedItemMove = home.onPinnedItemMove,
        onItemDroppedToHome = home.onItemDroppedToHome,
        onHomeTrigger = home.onHomeTrigger,
        onCreateFolder = folder.onCreateFolder,
        onAddItemToFolder = folder.onAddItemToFolder,
        onMergeFolders = folder.onMergeFolders,
        onExtractItemFromFolder = folder.onExtractItemFromFolder,
        onMoveFolderItemToFolder = folder.onMoveFolderItemToFolder,
        onFolderChildDroppedOnItem = folder.onFolderChildDroppedOnItem,
        onRemoveWidget = widget.onRemoveWidget,
        onUpdateWidgetFrame = widget.onUpdateWidgetFrame,
        onUpdateWidgetDisplayMode = widget.onUpdateWidgetDisplayMode,
        onExpandPopupWidget = widget.onExpandPopupWidget,
        onLaunchWidgetApp = widget.onLaunchWidgetApp,
        onWidgetDroppedToHome = widget.onWidgetDroppedToHome,
        onDismissSearch = search.onDismissSearch,
        onHomescreenMenuOpenChange = menu.onHomescreenMenuOpenChange
    )
}
