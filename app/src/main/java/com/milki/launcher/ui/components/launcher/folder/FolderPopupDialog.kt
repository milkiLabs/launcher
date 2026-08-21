package com.milki.launcher.ui.components.launcher.folder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import android.view.View
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.interaction.dragdrop.startExternalFolderItemDrag
import kotlinx.coroutines.delay

@Composable
fun FolderPopupDialog(
    folder: HomeItem.FolderItem,
    anchorBounds: Rect?,
    onClose: () -> Unit,
    onRenameFolder: (newName: String) -> Unit,
    onItemClick: (HomeItem) -> Unit,
    onReorderFolderItems: (newChildren: List<HomeItem>) -> Unit,
    onRemoveItemFromFolder: (itemId: String) -> Unit
) {
    var localChildren by remember(folder.id) { mutableStateOf(folder.children) }
    var isDraggingInternally by remember { mutableStateOf(false) }
    var editingName by remember(folder.id) { mutableStateOf(folder.name) }
    var isEditingName by remember { mutableStateOf(false) }
    var menuShownForItemId by remember { mutableStateOf<String?>(null) }

    val reorderEngine = remember { FolderReorderEngine() }
    val dragState = reorderEngine.state

    val nameFocusRequester = remember { FocusRequester() }
    val hapticFeedback = LocalHapticFeedback.current
    val hostView = LocalView.current
    val density = LocalDensity.current

    LaunchedEffect(folder.children) {
        if (!isDraggingInternally) {
            localChildren = folder.children
        }
    }
    LaunchedEffect(folder.name) {
        if (!isEditingName) {
            editingName = folder.name
        }
    }

    val layout = remember(localChildren.size) {
        folderGridLayoutForItemCount(localChildren.size)
    }
    val pagerState = rememberPagerState(initialPage = 0) { layout.pageCount }

    // Clamp the current page when items were removed and the last page disappeared.
    LaunchedEffect(layout.pageCount) {
        val lastPage = (layout.pageCount - 1).coerceAtLeast(0)
        if (pagerState.currentPage > lastPage) {
            pagerState.scrollToPage(lastPage)
        }
    }
    // Auto-page when a drag probe lingers near the popup's horizontal edges.
    LaunchedEffect(dragState.pendingAutoPage, dragState.draggedItemId) {
        val targetPage = dragState.pendingAutoPage ?: return@LaunchedEffect
        if (dragState.draggedItemId == null) return@LaunchedEffect

        delay(FOLDER_AUTO_PAGE_DELAY_MS)
        if (dragState.pendingAutoPage == targetPage && pagerState.currentPage != targetPage) {
            dragState.isAutoPaging = true
            pagerState.animateScrollToPage(targetPage)
            pagerState.scrollToPage(targetPage)
            dragState.isAutoPaging = false
        }
        if (dragState.pendingAutoPage == targetPage) {
            dragState.pendingAutoPage = null
        }
    }

    val openProgress = rememberFolderOpenProgress()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val metrics = rememberFolderSurfaceMetrics(
            localChildrenSize = localChildren.size,
            layout = layout,
            density = density,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
        val targetBoundsPx = rememberFolderTargetBounds(
            anchorBounds = anchorBounds,
            metrics = metrics,
            density = density,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
        val transform = rememberFolderSurfaceTransform(
            anchorBounds = anchorBounds,
            targetBoundsPx = targetBoundsPx
        )

        Box(modifier = Modifier.fillMaxSize()) {
            FolderPopupScrim(
                openProgress = openProgress,
                onClose = onClose
            )

            FolderPopupSurface(
                metrics = metrics,
                targetBoundsPx = targetBoundsPx,
                transform = transform,
                openProgress = openProgress,
                onPopupBoundsMeasured = { dragState.popupWindowRect = it }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = metrics.horizontalPadding,
                            vertical = metrics.verticalPadding
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FolderNameHeader(
                        name = editingName,
                        isEditing = isEditingName,
                        focusRequester = nameFocusRequester,
                        itemCount = localChildren.size,
                        onNameChange = { editingName = it },
                        onEditingChanged = { isEditing ->
                            val wasEditing = isEditingName
                            isEditingName = isEditing
                            if (wasEditing && !isEditing) {
                                onRenameFolder(editingName)
                            }
                        },
                        onEditRequested = { isEditingName = true }
                    )

                    Spacer(modifier = Modifier.height(metrics.headerBottomSpacing))

                    Box(
                        modifier = Modifier
                            .width(metrics.gridWidth)
                            .height(metrics.gridHeight)
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(0.dp),
                            beyondViewportPageCount = 1,
                            userScrollEnabled = dragState.draggedItemId == null && layout.pageCount > 1
                        ) { page ->
                            FolderPagerPage(
                                page = page,
                                folder = folder,
                                layout = layout,
                                localChildren = localChildren,
                                metrics = metrics,
                                pagerState = pagerState,
                                reorderEngine = reorderEngine,
                                menuShownForItemId = menuShownForItemId,
                                density = density,
                                hapticFeedback = hapticFeedback,
                                hostView = hostView,
                                onClose = onClose,
                                onItemClick = onItemClick,
                                onRemoveItemFromFolder = onRemoveItemFromFolder,
                                onReorderFolderItems = { reordered ->
                                    localChildren = reordered
                                    onReorderFolderItems(reordered)
                                },
                                onMenuShownForItemIdChange = { menuShownForItemId = it },
                                onDraggingInternallyChange = { isDraggingInternally = it }
                            )
                        }
                    }

                    if (layout.pageCount > 1) {
                        Spacer(modifier = Modifier.height(metrics.indicatorTopSpacing))
                        FolderPagerIndicator(
                            pageCount = layout.pageCount,
                            currentPage = pagerState.currentPage
                        )
                    }
                }
            }

            FolderDragPreview(
                dragState = dragState,
                metrics = metrics
            )
        }
    }
}

@Composable
private fun FolderPagerPage(
    page: Int,
    folder: HomeItem.FolderItem,
    layout: FolderGridLayout,
    localChildren: List<HomeItem>,
    metrics: FolderSurfaceMetrics,
    pagerState: PagerState,
    reorderEngine: FolderReorderEngine,
    menuShownForItemId: String?,
    density: Density,
    hapticFeedback: HapticFeedback,
    hostView: View,
    onClose: () -> Unit,
    onItemClick: (HomeItem) -> Unit,
    onRemoveItemFromFolder: (String) -> Unit,
    onReorderFolderItems: (List<HomeItem>) -> Unit,
    onMenuShownForItemIdChange: (String?) -> Unit,
    onDraggingInternallyChange: (Boolean) -> Unit
) {
    val dragState = reorderEngine.state

    FolderGridPage(
        page = page,
        layout = layout,
        localChildren = localChildren,
        cellWidth = metrics.cellWidth,
        cellHeight = metrics.cellHeight,
        cellSpacing = metrics.cellSpacing,
        draggedItemId = dragState.draggedItemId,
        hoveredSlot = dragState.hoveredSlot,
        menuShownForItemId = menuShownForItemId,
        onGridBoundsMeasured = { pageBounds ->
            if (page == pagerState.currentPage) {
                dragState.gridWindowRect = pageBounds
            }
        },
        onMenuDismiss = { onMenuShownForItemIdChange(null) },
        onTap = onItemClick,
        onLongPress = { itemId ->
            onMenuShownForItemIdChange(itemId)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onRemoveFromFolder = { itemId ->
            onMenuShownForItemIdChange(null)
            onRemoveItemFromFolder(itemId)
        },
        onExternalDragStarted = onClose,
        onDragStart = { item, itemWindowTopLeft ->
            onDraggingInternallyChange(true)
            dragState.startInternalDrag(
                item = item,
                page = page,
                localChildren = localChildren,
                itemWindowTopLeft = itemWindowTopLeft,
                metrics = metrics,
                density = density
            )
            onMenuShownForItemIdChange(null)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        },
        onDragDelta = { delta ->
            val escapedItem = reorderEngine.onDragFrame(
                delta = delta,
                currentPage = pagerState.currentPage,
                layout = layout,
                metrics = metrics,
                density = density
            )
            if (escapedItem != null) {
                startExternalFolderItemDrag(
                    hostView = hostView,
                    folderId = folder.id,
                    item = escapedItem
                )
                onClose()
            }
        },
        onDragEnd = {
            val reordered = reorderEngine.resolveDrop(
                currentPage = pagerState.currentPage,
                children = localChildren,
                layout = layout,
                metrics = metrics,
                density = density
            )
            if (reordered != null) {
                onReorderFolderItems(reordered)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            }
            reorderEngine.reset()
            onDraggingInternallyChange(false)
        },
        onDragCancel = {
            reorderEngine.reset()
            onDraggingInternallyChange(false)
        }
    )
}
