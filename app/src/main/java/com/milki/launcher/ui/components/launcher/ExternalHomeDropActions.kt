package com.milki.launcher.ui.components.launcher

import android.appwidget.AppWidgetProviderInfo
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.drag.drop.RejectReason
import com.milki.launcher.domain.drag.reorder.GridReorderEngine
import com.milki.launcher.domain.drag.reorder.ReorderInput
import com.milki.launcher.domain.drag.reorder.ReorderMode
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.interaction.dragdrop.ExternalDragDropItem
import com.milki.launcher.ui.interaction.dragdrop.ExternalDragPayloadCodec.ExternalDragItem

internal data class ExternalDropHandlers(
    val onItemDroppedToHome: (item: HomeItem, position: GridPosition) -> Unit,
    val onCreateFolder: (item1: HomeItem, item2: HomeItem, position: GridPosition) -> Unit,
    val onAddItemToFolder: (folderId: String, item: HomeItem) -> Unit,
    val onFolderItemExtracted: (folderId: String, itemId: String, targetPosition: GridPosition) -> Unit,
    val onMoveFolderItemToFolder: (sourceFolderId: String, itemId: String, targetFolderId: String) -> Unit,
    val onFolderChildDroppedOnItem: (
        sourceFolderId: String,
        childItem: HomeItem,
        occupantItem: HomeItem,
        atPosition: GridPosition
    ) -> Unit,
    val onWidgetDroppedToHome: (
        providerInfo: AppWidgetProviderInfo,
        span: GridSpan,
        dropPosition: GridPosition
    ) -> Unit,
    val onConfirmDrop: () -> Unit
)

internal enum class ExternalDropHighlightKind {
    Primary,
    Secondary,
    Error
}

internal data class ExternalDropPreviewState(
    val targetPosition: GridPosition,
    val dragSpan: GridSpan,
    val highlightKind: ExternalDropHighlightKind
)

internal sealed interface ExternalDropAction {
    val previewState: ExternalDropPreviewState

    data class DropToHome(
        val item: HomeItem,
        val position: GridPosition,
        override val previewState: ExternalDropPreviewState
    ) : ExternalDropAction

    data class CreateFolder(
        val draggedItem: HomeItem,
        val occupantItem: HomeItem,
        val position: GridPosition,
        override val previewState: ExternalDropPreviewState
    ) : ExternalDropAction

    data class AddToFolder(
        val folderId: String,
        val item: HomeItem,
        override val previewState: ExternalDropPreviewState
    ) : ExternalDropAction

    data class ExtractFolderChild(
        val folderId: String,
        val itemId: String,
        val position: GridPosition,
        override val previewState: ExternalDropPreviewState
    ) : ExternalDropAction

    data class MoveFolderChildToFolder(
        val sourceFolderId: String,
        val itemId: String,
        val targetFolderId: String,
        override val previewState: ExternalDropPreviewState
    ) : ExternalDropAction

    data class DropFolderChildOnItem(
        val sourceFolderId: String,
        val childItem: HomeItem,
        val occupantItem: HomeItem,
        val position: GridPosition,
        override val previewState: ExternalDropPreviewState
    ) : ExternalDropAction

    data class PlaceWidget(
        val providerInfo: AppWidgetProviderInfo,
        val span: GridSpan,
        val position: GridPosition,
        override val previewState: ExternalDropPreviewState
    ) : ExternalDropAction

    data class Reject(
        val reason: RejectReason,
        override val previewState: ExternalDropPreviewState
    ) : ExternalDropAction
}

internal fun resolveExternalDropAction(
    item: ExternalDragDropItem?,
    dropPosition: GridPosition,
    items: List<HomeItem>,
    gridColumns: Int,
    maxVisibleRows: Int,
    widgetHostManager: WidgetHostManager? = null,
    reorderEngine: GridReorderEngine = GridReorderEngine(),
    reorderMode: ReorderMode
): ExternalDropAction? {
    if (item == null) return null

    return when (item) {
        is ExternalDragItem.FolderChild -> resolveFolderChildDropAction(
            item = item,
            dropPosition = dropPosition,
            items = items
        )
        is ExternalDragItem.Widget -> resolveWidgetDropAction(
            item = item,
            dropPosition = dropPosition,
            items = items,
            gridColumns = gridColumns,
            maxVisibleRows = maxVisibleRows,
            widgetHostManager = widgetHostManager,
            reorderEngine = reorderEngine,
            reorderMode = reorderMode
        )
        else -> resolveRegularExternalDropAction(
            item = item,
            dropPosition = dropPosition,
            items = items
        )
    }
}

internal fun applyExternalDropAction(
    action: ExternalDropAction,
    handlers: ExternalDropHandlers
): Boolean {
    when (action) {
        is ExternalDropAction.DropToHome -> {
            handlers.onItemDroppedToHome(action.item, action.position)
            handlers.onConfirmDrop()
            return true
        }
        is ExternalDropAction.CreateFolder -> {
            handlers.onCreateFolder(action.draggedItem, action.occupantItem, action.position)
            handlers.onConfirmDrop()
            return true
        }
        is ExternalDropAction.AddToFolder -> {
            handlers.onAddItemToFolder(action.folderId, action.item)
            handlers.onConfirmDrop()
            return true
        }
        is ExternalDropAction.ExtractFolderChild -> {
            handlers.onFolderItemExtracted(action.folderId, action.itemId, action.position)
            handlers.onConfirmDrop()
            return true
        }
        is ExternalDropAction.MoveFolderChildToFolder -> {
            handlers.onMoveFolderItemToFolder(
                action.sourceFolderId,
                action.itemId,
                action.targetFolderId
            )
            handlers.onConfirmDrop()
            return true
        }
        is ExternalDropAction.DropFolderChildOnItem -> {
            handlers.onFolderChildDroppedOnItem(
                action.sourceFolderId,
                action.childItem,
                action.occupantItem,
                action.position
            )
            handlers.onConfirmDrop()
            return true
        }
        is ExternalDropAction.PlaceWidget -> {
            handlers.onWidgetDroppedToHome(action.providerInfo, action.span, action.position)
            handlers.onConfirmDrop()
            return true
        }
        is ExternalDropAction.Reject -> {
            return action.reason != RejectReason.PAYLOAD_UNSUPPORTED
        }
    }
}

private fun resolveFolderChildDropAction(
    item: ExternalDragItem.FolderChild,
    dropPosition: GridPosition,
    items: List<HomeItem>
): ExternalDropAction {
    val occupant = items.findOccupantAt(dropPosition)
    return when {
        occupant is HomeItem.FolderItem && occupant.id == item.folderId -> {
            ExternalDropAction.Reject(
                reason = RejectReason.INVALID_FOLDER_ROUTE,
                previewState = previewState(
                    targetPosition = dropPosition,
                    highlightKind = ExternalDropHighlightKind.Error
                )
            )
        }
        occupant is HomeItem.FolderItem -> {
            ExternalDropAction.MoveFolderChildToFolder(
                sourceFolderId = item.folderId,
                itemId = item.childItem.id,
                targetFolderId = occupant.id,
                previewState = previewState(
                    targetPosition = dropPosition,
                    highlightKind = ExternalDropHighlightKind.Secondary
                )
            )
        }
        occupant is HomeItem.WidgetItem -> {
            ExternalDropAction.Reject(
                reason = RejectReason.INVALID_WIDGET_ROUTE,
                previewState = previewState(
                    targetPosition = dropPosition,
                    highlightKind = ExternalDropHighlightKind.Error
                )
            )
        }
        occupant != null -> {
            ExternalDropAction.DropFolderChildOnItem(
                sourceFolderId = item.folderId,
                childItem = item.childItem,
                occupantItem = occupant,
                position = dropPosition,
                previewState = previewState(
                    targetPosition = dropPosition,
                    highlightKind = ExternalDropHighlightKind.Secondary
                )
            )
        }
        else -> {
            ExternalDropAction.ExtractFolderChild(
                folderId = item.folderId,
                itemId = item.childItem.id,
                position = dropPosition,
                previewState = previewState(
                    targetPosition = dropPosition,
                    highlightKind = ExternalDropHighlightKind.Primary
                )
            )
        }
    }
}

private fun resolveRegularExternalDropAction(
    item: ExternalDragDropItem,
    dropPosition: GridPosition,
    items: List<HomeItem>
): ExternalDropAction? {
    val previewHomeItem = item.toPreviewHomeItem() ?: return null
    val occupant = items.findOccupantAt(dropPosition)

    return when (occupant) {
        is HomeItem.FolderItem -> {
            ExternalDropAction.AddToFolder(
                folderId = occupant.id,
                item = previewHomeItem,
                previewState = previewState(
                    targetPosition = dropPosition,
                    highlightKind = ExternalDropHighlightKind.Secondary
                )
            )
        }
        is HomeItem.WidgetItem -> {
            ExternalDropAction.Reject(
                reason = RejectReason.INVALID_WIDGET_ROUTE,
                previewState = previewState(
                    targetPosition = dropPosition,
                    highlightKind = ExternalDropHighlightKind.Error
                )
            )
        }
        null -> {
            ExternalDropAction.DropToHome(
                item = previewHomeItem,
                position = dropPosition,
                previewState = previewState(
                    targetPosition = dropPosition,
                    highlightKind = ExternalDropHighlightKind.Primary
                )
            )
        }
        else -> {
            ExternalDropAction.CreateFolder(
                draggedItem = previewHomeItem,
                occupantItem = occupant,
                position = occupant.position,
                previewState = previewState(
                    targetPosition = dropPosition,
                    highlightKind = ExternalDropHighlightKind.Secondary
                )
            )
        }
    }
}

private fun resolveWidgetDropAction(
    item: ExternalDragItem.Widget,
    dropPosition: GridPosition,
    items: List<HomeItem>,
    gridColumns: Int,
    maxVisibleRows: Int,
    widgetHostManager: WidgetHostManager?,
    reorderEngine: GridReorderEngine,
    reorderMode: ReorderMode
): ExternalDropAction {
    val normalizedSpan = normalizeWidgetSpanForHomeGrid(
        rawSpan = item.span,
        gridColumns = gridColumns
    )
    val clampedDropPosition = clampWidgetDropPosition(
        dropPosition = dropPosition,
        normalizedSpan = normalizedSpan,
        gridColumns = gridColumns,
        maxVisibleRows = maxVisibleRows
    )
    val reorderPlan = reorderEngine.compute(
        ReorderInput(
            items = items,
            preferredCell = clampedDropPosition,
            draggedSpan = normalizedSpan,
            gridColumns = gridColumns,
            gridRows = maxVisibleRows,
            mode = reorderMode
        )
    )
    val resolvedTarget = if (reorderPlan.isValid) reorderPlan.anchorCell else clampedDropPosition
    val resolvedProvider = item.providerInfo
        ?: widgetHostManager?.findInstalledProvider(item.providerComponent)

    return when {
        !reorderPlan.isValid -> {
            ExternalDropAction.Reject(
                reason = RejectReason.OCCUPIED_TARGET,
                previewState = previewState(
                    targetPosition = resolvedTarget,
                    dragSpan = normalizedSpan,
                    highlightKind = ExternalDropHighlightKind.Error
                )
            )
        }
        resolvedProvider == null -> {
            ExternalDropAction.Reject(
                reason = RejectReason.PAYLOAD_UNSUPPORTED,
                previewState = previewState(
                    targetPosition = resolvedTarget,
                    dragSpan = normalizedSpan,
                    highlightKind = ExternalDropHighlightKind.Error
                )
            )
        }
        else -> {
            ExternalDropAction.PlaceWidget(
                providerInfo = resolvedProvider,
                span = normalizedSpan,
                position = reorderPlan.anchorCell,
                previewState = previewState(
                    targetPosition = reorderPlan.anchorCell,
                    dragSpan = normalizedSpan,
                    highlightKind = ExternalDropHighlightKind.Primary
                )
            )
        }
    }
}

private fun previewState(
    targetPosition: GridPosition,
    dragSpan: GridSpan = GridSpan.SINGLE,
    highlightKind: ExternalDropHighlightKind
): ExternalDropPreviewState {
    return ExternalDropPreviewState(
        targetPosition = targetPosition,
        dragSpan = dragSpan,
        highlightKind = highlightKind
    )
}

private fun clampWidgetDropPosition(
    dropPosition: GridPosition,
    normalizedSpan: GridSpan,
    gridColumns: Int,
    maxVisibleRows: Int
): GridPosition {
    return GridPosition(
        row = dropPosition.row.coerceIn(
            minimumValue = 0,
            maximumValue = (maxVisibleRows - normalizedSpan.rows).coerceAtLeast(0)
        ),
        column = dropPosition.column.coerceIn(
            minimumValue = 0,
            maximumValue = (gridColumns - normalizedSpan.columns).coerceAtLeast(0)
        )
    )
}
