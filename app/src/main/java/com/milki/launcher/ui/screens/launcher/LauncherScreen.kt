package com.milki.launcher.ui.screens.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.presentation.drawer.AppDrawerUiState
import com.milki.launcher.presentation.home.HomeUiState
import com.milki.launcher.presentation.main.LocalContextMenuDismissSignal
import com.milki.launcher.presentation.search.SearchUiState
import com.milki.launcher.ui.components.AppDrawerOverlay
import com.milki.launcher.ui.components.AppSearchDialog
import com.milki.launcher.ui.components.DraggablePinnedItemsGrid
import com.milki.launcher.ui.components.folder.FolderPopupDialog
import com.milki.launcher.ui.components.grid.HomeBackgroundGestureBindings
import com.milki.launcher.ui.components.widget.WidgetPickerBottomSheet
import com.milki.launcher.ui.components.LauncherSheet
import com.milki.launcher.ui.components.rememberLauncherSheetState
import com.milki.launcher.ui.theme.Spacing

/**
 * Main launcher surface.
 *
 * This file intentionally stays focused on screen composition and layered-surface
 * orchestration. Action contracts and pinned-item opening behavior live in
 * dedicated files within the same package.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    searchUiState: SearchUiState,
    homeUiState: HomeUiState,
    actions: LauncherActions = LauncherActions(),
    isHomeSwipeEnabled: Boolean = true,
    isHomescreenMenuOpen: Boolean = false,
    isAppDrawerOpen: Boolean = false,
    appDrawerUiState: AppDrawerUiState = AppDrawerUiState(),
    isWidgetPickerOpen: Boolean = false,
    widgetHostManager: WidgetHostManager? = null,
) {
    val appDrawerSheetState = rememberLauncherSheetState()
    val widgetPickerSheetState = rememberLauncherSheetState()
    var homescreenMenuAnchorPx by remember { mutableStateOf(Offset.Zero) }
    val homeItemBoundsById = remember { mutableStateMapOf<String, Rect>() }

    LaunchedEffect(searchUiState.isSearchVisible) {
        if (searchUiState.isSearchVisible) {
            actions.menu.onHomescreenMenuOpenChange(false)
            actions.drawer.onAppDrawerOpenChange(false)
            actions.widget.onWidgetPickerOpenChange(false)
        }
    }

    LaunchedEffect(homeUiState.openFolderItem?.id) {
        if (homeUiState.openFolderItem != null) {
            actions.menu.onHomescreenMenuOpenChange(false)
            actions.drawer.onAppDrawerOpenChange(false)
            actions.widget.onWidgetPickerOpenChange(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        HomescreenMenuScrim(
            isVisible = isHomescreenMenuOpen,
            onDismiss = { actions.menu.onHomescreenMenuOpenChange(false) }
        )

        HomeSurface(
            homeUiState = homeUiState,
            actions = actions,
            canOpenDrawerFromSwipe =
                isHomeSwipeEnabled &&
                !isHomescreenMenuOpen &&
                    !isAppDrawerOpen &&
                    !isWidgetPickerOpen &&
                    !searchUiState.isSearchVisible &&
                    homeUiState.openFolderItem == null,
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
            homeUiState = homeUiState,
            actions = actions,
            anchorBounds = homeUiState.openFolderItem?.let { folder ->
                homeItemBoundsById[folder.id]
            }
        )

        DrawerHost(
            appDrawerSheetState = appDrawerSheetState,
            isAppDrawerOpen = isAppDrawerOpen,
            appDrawerUiState = appDrawerUiState,
            actions = actions
        )

        WidgetPickerHost(
            widgetPickerSheetState = widgetPickerSheetState,
            isWidgetPickerOpen = isWidgetPickerOpen,
            widgetHostManager = widgetHostManager,
            actions = actions
        )
    }

    SearchOverlayHost(
        searchUiState = searchUiState,
        actions = actions
    )
}

@Composable
private fun HomescreenMenuScrim(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
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
    val dismissSignal = LocalContextMenuDismissSignal.current
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    var lastHandledDismissSignal by remember { mutableIntStateOf(dismissSignal) }

    LaunchedEffect(dismissSignal) {
        if (dismissSignal == lastHandledDismissSignal) return@LaunchedEffect
        lastHandledDismissSignal = dismissSignal
        latestOnDismiss()
    }

    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        offset = with(density) {
            DpOffset(
                x = anchorPx.x.toDp(),
                y = anchorPx.y.toDp()
            )
        }
    ) {
        DropdownMenuItem(
            text = { Text("Widgets") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Widgets,
                    contentDescription = null
                )
            },
            onClick = onOpenWidgets
        )

        DropdownMenuItem(
            text = { Text("Settings") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null
                )
            },
            onClick = onOpenSettings
        )
    }
}

/**
 * Hosts the home grid surface and routes all grid events to grouped action contracts.
 */
@Composable
private fun HomeSurface(
    homeUiState: HomeUiState,
    actions: LauncherActions,
    canOpenDrawerFromSwipe: Boolean,
    onMenuAnchorChanged: (Offset) -> Unit,
    onItemBoundsMeasured: (String, Rect) -> Unit,
    widgetHostManager: WidgetHostManager?,
    modifier: Modifier = Modifier
) {
    DraggablePinnedItemsGrid(
        items = homeUiState.pinnedItems,
        onItemClick = actions.home.onPinnedItemClick,
        onItemLongPress = actions.home.onPinnedItemLongPress,
        onItemMove = actions.home.onPinnedItemMove,
        backgroundGestures = HomeBackgroundGestureBindings(
            onEmptyAreaLongPress = { touchOffset ->
                onMenuAnchorChanged(touchOffset)
                actions.menu.onHomescreenMenuOpenChange(true)
            },
            onSwipeUp = if (canOpenDrawerFromSwipe) actions.home.onHomeSwipeUp else null
        ),
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
        modifier = modifier.padding(Spacing.mediumLarge)
    )
}

/**
 * Hosts folder popup lifecycle and delegates actions through folder contracts.
 */
@Composable
private fun FolderOverlayHost(
    homeUiState: HomeUiState,
    actions: LauncherActions,
    anchorBounds: Rect?
) {
    homeUiState.openFolderItem?.let { folder ->
        key(folder.id) {
            FolderPopupDialog(
                folder = folder,
                anchorBounds = anchorBounds,
                onClose = actions.folder.onFolderClose,
                onRenameFolder = { newName ->
                    actions.folder.onFolderRename(folder.id, newName)
                },
                onItemClick = actions.folder.onFolderItemClick,
                onReorderFolderItems = { newChildren ->
                    actions.folder.onFolderItemReorder(folder.id, newChildren)
                },
                onRemoveItemFromFolder = { itemId ->
                    actions.folder.onFolderItemRemove(folder.id, itemId)
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
    appDrawerSheetState: com.milki.launcher.ui.components.LauncherSheetState,
    isAppDrawerOpen: Boolean,
    appDrawerUiState: AppDrawerUiState,
    actions: LauncherActions
) {
    ManagedLauncherSheet(
        isOpen = isAppDrawerOpen,
        sheetState = appDrawerSheetState,
        onDismissRequest = { actions.drawer.onAppDrawerOpenChange(false) }
    ) {
        AppDrawerOverlay(
            uiState = appDrawerUiState,
            onDismiss = { actions.drawer.onAppDrawerOpenChange(false) },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Hosts widget picker bottom sheet and routes dismissal events through widget contracts.
 */
@Composable
private fun WidgetPickerHost(
    widgetPickerSheetState: com.milki.launcher.ui.components.LauncherSheetState,
    isWidgetPickerOpen: Boolean,
    widgetHostManager: WidgetHostManager?,
    actions: LauncherActions
) {
    if (widgetHostManager == null) return
    ManagedLauncherSheet(
        isOpen = isWidgetPickerOpen,
        sheetState = widgetPickerSheetState,
        onDismissRequest = { actions.widget.onWidgetPickerOpenChange(false) }
    ) {
        WidgetPickerBottomSheet(
            onDismiss = { actions.widget.onWidgetPickerOpenChange(false) },
            widgetHostManager = widgetHostManager,
            onExternalDragStarted = {
                actions.widget.onWidgetPickerOpenChange(false)
            }
        )
    }
}

@Composable
private fun SearchOverlayHost(
    searchUiState: SearchUiState,
    actions: LauncherActions
) {
    if (!searchUiState.isSearchVisible) return

    AppSearchDialog(
        uiState = searchUiState,
        onQueryChange = actions.search.onQueryChange,
        onDismiss = actions.search.onDismissSearch
    )
}

@Composable
private fun ManagedLauncherSheet(
    isOpen: Boolean,
    sheetState: com.milki.launcher.ui.components.LauncherSheetState,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    var isMounted by remember { mutableStateOf(isOpen) }

    LaunchedEffect(isOpen) {
        when (resolveLauncherSheetTargetChange(targetOpen = isOpen, isMounted = isMounted)) {
            LauncherSheetTargetChange.MountAndAnimateOpen -> {
                isMounted = true
                sheetState.animateToExpanded()
            }

            LauncherSheetTargetChange.AnimateClosedThenUnmount -> {
                sheetState.animateToHidden()
                isMounted = false
            }

            LauncherSheetTargetChange.None -> Unit
        }
    }

    if (!isMounted) return

    LauncherSheet(
        state = sheetState,
        modifier = Modifier.fillMaxSize(),
        onDismissedByUser = onDismissRequest
    ) {
        content()
    }
}
