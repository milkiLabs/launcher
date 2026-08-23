package com.milki.launcher.ui.components.launcher.folder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.launcher.PinnedItemView
import kotlin.math.roundToInt

private const val FOLDER_CELL_CENTER_RATIO = 0.5f
private const val FOLDER_PREVIEW_ALPHA = 0.92f
private const val FOLDER_DRAG_PREVIEW_Z_INDEX = 12f

internal class FolderDialogDragState {
    var draggedItemId by mutableStateOf<String?>(null)
    var dragOffset by mutableStateOf(Offset.Zero)
    var dragStartWindowPos by mutableStateOf(Offset.Zero)
    var dragProbeOffset by mutableStateOf(Offset.Zero)
    var dragOutItem by mutableStateOf<HomeItem?>(null)
    var isDraggingOut by mutableStateOf(false)
    var popupWindowRect by mutableStateOf(Rect.Zero)
    var gridWindowRect by mutableStateOf(Rect.Zero)
    var hoveredSlot by mutableStateOf<FolderDropSlot?>(null)
    var pendingAutoPage by mutableStateOf<Int?>(null)
    var isAutoPaging by mutableStateOf(false)
    var isPlatformDragActive by mutableStateOf(false)

    fun startInternalDrag(
        item: HomeItem,
        page: Int,
        localChildren: List<HomeItem>,
        itemWindowTopLeft: Offset,
        metrics: FolderSurfaceMetrics,
        density: Density
    ) {
        draggedItemId = item.id
        dragOutItem = item
        dragOffset = Offset.Zero
        dragStartWindowPos = itemWindowTopLeft
        dragProbeOffset = with(density) {
            Offset(
                x = metrics.cellWidth.toPx() * FOLDER_CELL_CENTER_RATIO,
                y = metrics.cellHeight.toPx() * FOLDER_CELL_CENTER_RATIO
            )
        }
        isDraggingOut = false
        isPlatformDragActive = false
        hoveredSlot = FolderDropSlot(
            page = page,
            slotIndex = slotIndexForPageItem(
                itemIndex = localChildren.indexOfFirst { it.id == item.id },
                pageSize = folderGridLayoutForItemCount(localChildren.size).pageSize
            )
        )
        pendingAutoPage = null
        isAutoPaging = false
    }

    fun reset() {
        draggedItemId = null
        dragOffset = Offset.Zero
        dragProbeOffset = Offset.Zero
        dragOutItem = null
        isDraggingOut = false
        isPlatformDragActive = false
        hoveredSlot = null
        pendingAutoPage = null
        isAutoPaging = false
    }
}

/**
 * Folder-internal reorder drag machine, mirroring [com.milki.launcher.domain.reorder.GridReorderEngine]:
 * hover-slot resolution while dragging, platform drag-out detection, and drop-index resolution + reorder.
 * Pure decision logic; side effects (platform drag start, haptics, dialog close) stay at the call site.
 */
internal class FolderReorderEngine(
    val state: FolderDialogDragState = FolderDialogDragState()
) {
    /**
     * Advances the drag by [delta]. Returns the dragged item when the probe left the
     * popup and the caller must hand off to a platform drag-out, null otherwise.
     */
    fun onDragFrame(
        delta: Offset,
        currentPage: Int,
        layout: FolderGridLayout,
        metrics: FolderSurfaceMetrics,
        density: Density
    ): HomeItem? {
        state.dragOffset += delta

        if (state.isPlatformDragActive) {
            return null
        }

        val probePosition = state.dragStartWindowPos + state.dragProbeOffset + state.dragOffset
        if (!state.popupWindowRect.contains(probePosition)) {
            state.isDraggingOut = true
            val escapedItem = state.dragOutItem
            if (escapedItem != null) {
                state.isPlatformDragActive = true
            }
            return escapedItem
        }

        state.isDraggingOut = false
        state.hoveredSlot = resolveHoveredSlot(
            pointer = probePosition,
            currentPage = currentPage,
            layout = layout,
            gridWindowRect = state.gridWindowRect,
            cellSpacingPx = with(density) { metrics.cellSpacing.toPx() }
        )

        if (!state.isAutoPaging) {
            state.pendingAutoPage = resolveAutoPageTarget(
                pointer = probePosition,
                popupBounds = state.popupWindowRect,
                currentPage = currentPage,
                pageCount = layout.pageCount,
                edgeThresholdPx = with(density) { FOLDER_AUTO_PAGE_EDGE_THRESHOLD.toPx() }
            )
        }

        return null
    }

    /** Resolves the drop into a reordered child list, or null when nothing should change. */
    fun resolveDrop(
        currentPage: Int,
        children: List<HomeItem>,
        layout: FolderGridLayout,
        metrics: FolderSurfaceMetrics,
        density: Density
    ): List<HomeItem>? {
        if (state.isPlatformDragActive) {
            return null
        }
        val draggedItem = state.dragOutItem ?: return null

        val dropSlot = state.hoveredSlot ?: resolveHoveredSlot(
            pointer = state.dragStartWindowPos + state.dragProbeOffset + state.dragOffset,
            currentPage = currentPage,
            layout = layout,
            gridWindowRect = state.gridWindowRect,
            cellSpacingPx = with(density) { metrics.cellSpacing.toPx() }
        )
        val fromIndex = children.indexOfFirst { it.id == draggedItem.id }
        if (dropSlot == null || fromIndex < 0) {
            return null
        }

        val targetIndex = resolveFolderDropIndex(
            targetPage = dropSlot.page,
            slotIndex = dropSlot.slotIndex,
            pageSize = layout.pageSize
        )
        val reordered = reorderFolderItemsForDrop(
            items = children,
            fromIndex = fromIndex,
            targetIndex = targetIndex
        )

        return reordered.takeIf { it != children }
    }

    fun reset() {
        state.reset()
    }
}

@Composable
internal fun FolderDragPreview(
    dragState: FolderDialogDragState,
    metrics: FolderSurfaceMetrics
) {
    val draggedItem = dragState.dragOutItem
    if (draggedItem != null && dragState.draggedItemId != null && !dragState.isDraggingOut) {
        val previewOffset = dragState.dragStartWindowPos + dragState.dragOffset
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = previewOffset.x.roundToInt(),
                        y = previewOffset.y.roundToInt()
                    )
                }
                .size(metrics.cellWidth, metrics.cellHeight)
                .zIndex(FOLDER_DRAG_PREVIEW_Z_INDEX)
                .alpha(FOLDER_PREVIEW_ALPHA)
        ) {
            PinnedItemView(item = draggedItem)
        }
    }
}
