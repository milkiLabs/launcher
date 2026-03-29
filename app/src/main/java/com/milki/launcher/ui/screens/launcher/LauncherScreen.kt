package com.milki.launcher.ui.screens.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.presentation.drawer.AppDrawerUiState
import com.milki.launcher.presentation.home.HomeUiState
import com.milki.launcher.presentation.search.SearchUiState
import com.milki.launcher.ui.components.AppDrawerOverlay
import com.milki.launcher.ui.components.AppSearchDialog
import com.milki.launcher.ui.components.DraggablePinnedItemsGrid
import com.milki.launcher.ui.components.folder.FolderPopupDialog
import com.milki.launcher.ui.components.widget.WidgetPickerBottomSheet
import com.milki.launcher.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    isHomescreenMenuOpen: Boolean = false,
    isAppDrawerOpen: Boolean = false,
    appDrawerUiState: AppDrawerUiState = AppDrawerUiState(),
    isWidgetPickerOpen: Boolean = false,
    widgetHostManager: WidgetHostManager? = null,
) {
    val density = LocalDensity.current
    val drawerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val drawerSheetScope = rememberCoroutineScope()
    var homescreenMenuAnchorPx by remember { mutableStateOf(Offset.Zero) }

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

    val swipeOpenThresholdPx = with(density) { Spacing.mediumLarge.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(
                searchUiState.isSearchVisible,
                homeUiState.openFolderItem?.id,
                isHomescreenMenuOpen,
                isAppDrawerOpen,
                swipeOpenThresholdPx
            ) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent(PointerEventPass.Main)
                            .changes
                            .firstOrNull() ?: continue

                        if (
                            searchUiState.isSearchVisible ||
                            homeUiState.openFolderItem != null ||
                            isHomescreenMenuOpen ||
                            isAppDrawerOpen
                        ) {
                            continue
                        }

                        val activePointerId = down.id
                        var totalDragY = 0f
                        var totalDragX = 0f
                        var hasTriggeredOpen = false

                        while (!hasTriggeredOpen) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == activePointerId } ?: break

                            if (change.changedToUpIgnoreConsumed()) {
                                break
                            }

                            if (change.isConsumed) {
                                break
                            }

                            val delta = change.positionChange()
                            totalDragY += delta.y
                            totalDragX += delta.x

                            val isPredominantlyVertical = abs(totalDragY) > abs(totalDragX) * 1.2f
                            if (isPredominantlyVertical && totalDragY < -swipeOpenThresholdPx) {
                                hasTriggeredOpen = true
                                actions.home.onHomeSwipeUp()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        HomescreenMenuScrim(
            isVisible = isHomescreenMenuOpen,
            onDismiss = { actions.menu.onHomescreenMenuOpenChange(false) }
        )

        HomeSurface(
            homeUiState = homeUiState,
            actions = actions,
            onMenuAnchorChanged = { homescreenMenuAnchorPx = it },
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
            actions = actions
        )

        DrawerHost(
            isAppDrawerOpen = isAppDrawerOpen,
            appDrawerUiState = appDrawerUiState,
            drawerSheetState = drawerSheetState,
            drawerSheetScope = drawerSheetScope,
            actions = actions
        )

        WidgetPickerHost(
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
    val density = LocalDensity.current

    DropdownMenu(
        expanded = expanded,
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
    onMenuAnchorChanged: (Offset) -> Unit,
    widgetHostManager: WidgetHostManager?,
    modifier: Modifier = Modifier
) {
    DraggablePinnedItemsGrid(
        items = homeUiState.pinnedItems,
        onItemClick = actions.home.onPinnedItemClick,
        onItemLongPress = actions.home.onPinnedItemLongPress,
        onItemMove = actions.home.onPinnedItemMove,
        onEmptyAreaLongPress = { touchOffset ->
            onMenuAnchorChanged(touchOffset)
            actions.menu.onHomescreenMenuOpenChange(true)
        },
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
        onResizeWidget = actions.widget.onResizeWidget,
        onWidgetDroppedToHome = actions.widget.onWidgetDroppedToHome,
        modifier = modifier.padding(Spacing.mediumLarge)
    )
}

/**
 * Hosts folder popup lifecycle and delegates actions through folder contracts.
 */
@Composable
private fun FolderOverlayHost(
    homeUiState: HomeUiState,
    actions: LauncherActions
) {
    homeUiState.openFolderItem?.let { folder ->
        key(folder.id) {
            FolderPopupDialog(
                folder = folder,
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerHost(
    isAppDrawerOpen: Boolean,
    appDrawerUiState: AppDrawerUiState,
    drawerSheetState: androidx.compose.material3.SheetState,
    drawerSheetScope: CoroutineScope,
    actions: LauncherActions
) {
    if (!isAppDrawerOpen) return

    ModalBottomSheet(
        onDismissRequest = {
            actions.drawer.onAppDrawerOpenChange(false)
        },
        sheetState = drawerSheetState,
        sheetMaxWidth = Dp.Unspecified,
        shape = RectangleShape,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        AppDrawerOverlay(
            uiState = appDrawerUiState,
            onDismiss = {
                drawerSheetScope.launch {
                    drawerSheetState.hide()
                    actions.drawer.onAppDrawerOpenChange(false)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Hosts widget picker bottom sheet and routes dismissal events through widget contracts.
 */
@Composable
private fun WidgetPickerHost(
    isWidgetPickerOpen: Boolean,
    widgetHostManager: WidgetHostManager?,
    actions: LauncherActions
) {
    if (!isWidgetPickerOpen || widgetHostManager == null) return

    WidgetPickerBottomSheet(
        onDismiss = { actions.widget.onWidgetPickerOpenChange(false) },
        widgetHostManager = widgetHostManager,
        onExternalDragStarted = {
            actions.widget.onWidgetPickerOpenChange(false)
        }
    )
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
