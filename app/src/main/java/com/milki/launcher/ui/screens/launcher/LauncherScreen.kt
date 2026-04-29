package com.milki.launcher.ui.screens.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.data.widget.WidgetPickerCatalogStore
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.LauncherGestureKind
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.presentation.drawer.AppDrawerUiState
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
    searchUiState: SearchUiState,
    pinnedItems: List<HomeItem>,
    openFolderItem: HomeItem.FolderItem?,
    actions: LauncherActions = LauncherActions(),
    enabledHomeTriggers: Set<LauncherTrigger> = emptySet(),
    isHomescreenMenuOpen: Boolean = false,
    isAppDrawerOpen: Boolean = false,
    appDrawerUiState: AppDrawerUiState = AppDrawerUiState(),
    isWidgetPickerOpen: Boolean = false,
    widgetPickerQuery: String = "",
    widgetHostManager: WidgetHostManager? = null,
    widgetPickerCatalogStore: WidgetPickerCatalogStore? = null,
) {
    val appDrawerSheetState = rememberLauncherSheetState()
    val widgetPickerSheetState = rememberLauncherSheetState()
    var homescreenMenuAnchorPx by remember { mutableStateOf(Offset.Zero) }
    val homeItemBoundsById = remember { mutableStateMapOf<String, Rect>() }
    val shouldDismissTransientSurfaces =
        searchUiState.isSearchVisible || openFolderItem != null

    LaunchedEffect(shouldDismissTransientSurfaces, openFolderItem?.id) {
        if (shouldDismissTransientSurfaces) {
            actions.menu.onHomescreenMenuOpenChange(false)
            actions.drawer.onAppDrawerOpenChange(false)
            actions.widget.onWidgetPickerOpenChange(false)
        }
    }

    val activeHomeTriggers = selectActiveHomeTriggers(
        enabledHomeTriggers = enabledHomeTriggers,
        isHomescreenMenuOpen = isHomescreenMenuOpen,
        isAppDrawerOpen = isAppDrawerOpen,
        isWidgetPickerOpen = isWidgetPickerOpen,
        isSearchVisible = searchUiState.isSearchVisible,
        openFolderItem = openFolderItem
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        HomeSurface(
            pinnedItems = pinnedItems,
            actions = actions,
            enabledHomeTriggers = activeHomeTriggers,
            onMenuAnchorChanged = { homescreenMenuAnchorPx = it },
            onItemBoundsMeasured = { itemId, boundsInWindow ->
                homeItemBoundsById[itemId] = boundsInWindow
            },
            widgetHostManager = widgetHostManager,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        )

        HomescreenMenu(
            expanded = isHomescreenMenuOpen,
            anchorPx = homescreenMenuAnchorPx,
            onDismiss = { actions.menu.onHomescreenMenuOpenChange(false) },
            onOpenWidgets = {
                actions.menu.onHomescreenMenuOpenChange(false)
                actions.widget.onWidgetPickerOpenChange(true)
            },
            onOpenSettings = {
                actions.menu.onHomescreenMenuOpenChange(false)
                actions.menu.onOpenSettings()
            }
        )

        FolderOverlayHost(
            openFolderItem = openFolderItem,
            folderActions = actions.folder,
            anchorBounds = openFolderItem?.let { folder ->
                homeItemBoundsById[folder.id]
            }
        )

        DrawerHost(
            appDrawerSheetState = appDrawerSheetState,
            isAppDrawerOpen = isAppDrawerOpen,
            appDrawerUiState = appDrawerUiState,
            drawerActions = actions.drawer
        )

        WidgetPickerHost(
            widgetPickerSheetState = widgetPickerSheetState,
            isWidgetPickerOpen = isWidgetPickerOpen,
            widgetPickerQuery = widgetPickerQuery,
            widgetPickerCatalogStore = widgetPickerCatalogStore,
            widgetActions = actions.widget
        )
    }

    SearchOverlayHost(
        searchUiState = searchUiState,
        searchActions = actions.search
    )
}

@Composable
private fun HomescreenMenu(
    expanded: Boolean,
    anchorPx: Offset,
    onDismiss: () -> Unit,
    onOpenWidgets: () -> Unit,
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
    actions: LauncherActions,
    enabledHomeTriggers: Set<LauncherTrigger>,
    onMenuAnchorChanged: (Offset) -> Unit,
    onItemBoundsMeasured: (String, Rect) -> Unit,
    widgetHostManager: WidgetHostManager?,
    modifier: Modifier = Modifier
) {
    val backgroundGestures = buildHomeBackgroundGestures(
        enabledHomeTriggers = enabledHomeTriggers,
        onMenuAnchorChanged = onMenuAnchorChanged,
        actions = actions
    )

    DraggablePinnedItemsGrid(
        items = pinnedItems,
        onItemClick = actions.home.onPinnedItemClick,
        onItemLongPress = actions.home.onPinnedItemLongPress,
        onItemMove = actions.home.onPinnedItemMove,
        backgroundGestures = backgroundGestures,
        onItemDroppedToHome = { item, position ->
            actions.home.onItemDroppedToHome(item, position)
            actions.search.onDismissSearch()
        },
        onCreateFolder = actions.folder.onCreateFolder,
        onAddItemToFolder = actions.folder.onAddItemToFolder,
        onMergeFolders = actions.folder.onMergeFolders,
        onFolderItemExtracted = actions.folder.onExtractItemFromFolder,
        onMoveFolderItemToFolder = actions.folder.onMoveFolderItemToFolder,
        onFolderChildDroppedOnItem = actions.folder.onFolderChildDroppedOnItem,
        widgetHostManager = widgetHostManager,
        onRemoveWidget = actions.widget.onRemoveWidget,
        onUpdateWidgetFrame = actions.widget.onUpdateWidgetFrame,
        onWidgetDroppedToHome = actions.widget.onWidgetDroppedToHome,
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
    isAppDrawerOpen: Boolean,
    appDrawerUiState: AppDrawerUiState,
    drawerActions: DrawerActions
) {
    LauncherSurfaceSheetHost(
        isOpen = isAppDrawerOpen,
        sheetState = appDrawerSheetState,
        onDismissRequest = { drawerActions.onAppDrawerOpenChange(false) }
    ) { dragHandleModifier ->
        AppDrawerOverlay(
            uiState = appDrawerUiState,
            onQueryChange = drawerActions.onQueryChange,
            onDismiss = { drawerActions.onAppDrawerOpenChange(false) },
            headerDragHandleModifier = dragHandleModifier,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Hosts widget picker bottom sheet and routes dismissal events through widget contracts.
 */
@Composable
private fun WidgetPickerHost(
    widgetPickerSheetState: LauncherSheetState,
    isWidgetPickerOpen: Boolean,
    widgetPickerQuery: String,
    widgetPickerCatalogStore: WidgetPickerCatalogStore?,
    widgetActions: WidgetActions
) {
    if (widgetPickerCatalogStore == null) return

    LauncherSurfaceSheetHost(
        isOpen = isWidgetPickerOpen,
        sheetState = widgetPickerSheetState,
        onDismissRequest = { widgetActions.onWidgetPickerOpenChange(false) }
    ) { dragHandleModifier ->
        WidgetPickerBottomSheet(
            catalogStore = widgetPickerCatalogStore,
            searchQuery = widgetPickerQuery,
            onSearchQueryChange = widgetActions.onWidgetPickerQueryChange,
            headerDragHandleModifier = dragHandleModifier,
            onExternalDragStarted = {
                widgetActions.onWidgetPickerOpenChange(false)
            }
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
    isAppDrawerOpen: Boolean,
    isWidgetPickerOpen: Boolean,
    isSearchVisible: Boolean,
    openFolderItem: HomeItem.FolderItem?
): Set<LauncherTrigger> {
    val isBackgroundGestureSurfaceBlocked =
        isHomescreenMenuOpen ||
                isAppDrawerOpen ||
                isWidgetPickerOpen ||
                isSearchVisible ||
                openFolderItem != null

    return if (isBackgroundGestureSurfaceBlocked) {
        emptySet()
    } else {
        enabledHomeTriggers
    }
}

private fun buildHomeBackgroundGestures(
    enabledHomeTriggers: Set<LauncherTrigger>,
    onMenuAnchorChanged: (Offset) -> Unit,
    actions: LauncherActions
): HomeBackgroundGestureBindings {
    val hasDirectionalTrigger = enabledHomeTriggers.any { trigger ->
        trigger.metadata.kind == LauncherGestureKind.SWIPE
    }

    return HomeBackgroundGestureBindings(
        configuredTriggers = enabledHomeTriggers,
        onEmptyAreaTap = enabledHomeTriggers.takeIf { LauncherTrigger.HOME_TAP in it }?.let {
            { actions.home.onHomeTrigger(LauncherTrigger.HOME_TAP) }
        },
        onEmptyAreaDoubleTap = enabledHomeTriggers
            .takeIf { LauncherTrigger.HOME_DOUBLE_TAP in it }
            ?.let {
                { actions.home.onHomeTrigger(LauncherTrigger.HOME_DOUBLE_TAP) }
            },
        onEmptyAreaLongPress = { touchOffset ->
            onMenuAnchorChanged(touchOffset)
            actions.menu.onHomescreenMenuOpenChange(true)
        },
        onTrigger = if (hasDirectionalTrigger) actions.home.onHomeTrigger else null
    )
}
