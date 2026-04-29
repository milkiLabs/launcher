package com.milki.launcher.ui.components.launcher

import com.milki.launcher.domain.drag.drop.RejectReason
import com.milki.launcher.domain.drag.reorder.GridReorderEngine
import com.milki.launcher.domain.drag.reorder.ReorderInput
import com.milki.launcher.domain.drag.reorder.ReorderMode
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem

internal data class InternalDropHandlers(
    val onItemMove: (itemId: String, newPosition: GridPosition) -> Unit,
    val onCreateFolder: (item1: HomeItem, item2: HomeItem, position: GridPosition) -> Unit,
    val onAddItemToFolder: (folderId: String, item: HomeItem) -> Unit,
    val onMergeFolders: (sourceFolderId: String, targetFolderId: String) -> Unit,
    val onConfirmDrop: () -> Unit
)

internal sealed interface InternalDropAction {
    data class MoveItem(
        val itemId: String,
        val position: GridPosition
    ) : InternalDropAction

    data class CreateFolder(
        val draggedItem: HomeItem,
        val occupantItem: HomeItem,
        val position: GridPosition
    ) : InternalDropAction

    data class AddToFolder(
        val folderId: String,
        val item: HomeItem
    ) : InternalDropAction

    data class MergeFolders(
        val sourceFolderId: String,
        val targetFolderId: String
    ) : InternalDropAction

    data class Reject(
        val reason: RejectReason
    ) : InternalDropAction
}

internal fun resolveInternalDropAction(
    draggedItem: HomeItem,
    dropPosition: GridPosition,
    items: List<HomeItem>,
    gridColumns: Int,
    gridRows: Int,
    reorderEngine: GridReorderEngine = GridReorderEngine()
): InternalDropAction {
    val resolvedDropPosition = if (draggedItem is HomeItem.WidgetItem) {
        val reorderPlan = reorderEngine.compute(
            ReorderInput(
                items = items,
                preferredCell = dropPosition,
                draggedSpan = draggedItem.span,
                gridColumns = gridColumns,
                gridRows = gridRows,
                excludeItemId = draggedItem.id,
                mode = ReorderMode.Commit
            )
        )
        if (!reorderPlan.isValid) {
            return InternalDropAction.Reject(RejectReason.OCCUPIED_TARGET)
        }
        reorderPlan.anchorCell
    } else {
        dropPosition
    }

    val occupant = items.findOccupantForDroppedSpan(
        excludeItemId = draggedItem.id,
        draggedSpan = (draggedItem as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE,
        droppedAt = resolvedDropPosition
    )

    return when {
        occupant == null -> InternalDropAction.MoveItem(
            itemId = draggedItem.id,
            position = resolvedDropPosition
        )
        draggedItem is HomeItem.WidgetItem || occupant is HomeItem.WidgetItem -> {
            InternalDropAction.Reject(RejectReason.INVALID_WIDGET_ROUTE)
        }
        draggedItem is HomeItem.FolderItem && occupant is HomeItem.FolderItem -> {
            InternalDropAction.MergeFolders(
                sourceFolderId = draggedItem.id,
                targetFolderId = occupant.id
            )
        }
        draggedItem is HomeItem.FolderItem -> {
            InternalDropAction.Reject(RejectReason.INVALID_FOLDER_ROUTE)
        }
        occupant is HomeItem.FolderItem -> {
            InternalDropAction.AddToFolder(
                folderId = occupant.id,
                item = draggedItem
            )
        }
        else -> {
            InternalDropAction.CreateFolder(
                draggedItem = draggedItem,
                occupantItem = occupant,
                position = occupant.position
            )
        }
    }
}

internal fun applyInternalDropAction(
    action: InternalDropAction,
    handlers: InternalDropHandlers
) {
    when (action) {
        is InternalDropAction.MoveItem -> {
            handlers.onItemMove(action.itemId, action.position)
            handlers.onConfirmDrop()
        }
        is InternalDropAction.CreateFolder -> {
            handlers.onCreateFolder(action.draggedItem, action.occupantItem, action.position)
            handlers.onConfirmDrop()
        }
        is InternalDropAction.AddToFolder -> {
            handlers.onAddItemToFolder(action.folderId, action.item)
            handlers.onConfirmDrop()
        }
        is InternalDropAction.MergeFolders -> {
            handlers.onMergeFolders(action.sourceFolderId, action.targetFolderId)
            handlers.onConfirmDrop()
        }
        is InternalDropAction.Reject -> Unit
    }
}
