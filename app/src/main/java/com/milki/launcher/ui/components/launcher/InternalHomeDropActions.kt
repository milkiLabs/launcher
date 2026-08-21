package com.milki.launcher.ui.components.launcher

import com.milki.launcher.domain.drop.RejectReason
import com.milki.launcher.domain.reorder.GridReorderEngine
import com.milki.launcher.domain.reorder.ReorderInput
import com.milki.launcher.domain.model.GridOccupancy
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.homeGridSpan

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
    reorderEngine: GridReorderEngine = GridReorderEngine(),
    occupancy: GridOccupancy? = null
): InternalDropAction {
    val resolved = occupancy ?: GridOccupancy.fromItems(items)

    val resolvedDropPosition = if (draggedItem is HomeItem.WidgetItem) {
        reorderEngine.compute(
            input = ReorderInput(
                items = items,
                preferredCell = dropPosition,
                draggedSpan = draggedItem.homeGridSpan,
                gridColumns = gridColumns,
                gridRows = gridRows,
                excludeItemId = draggedItem.id
            ),
            occupancy = resolved
        ) ?: return InternalDropAction.Reject(RejectReason.OCCUPIED_TARGET)
    } else {
        dropPosition
    }

    val occupant = resolved.overlappingOccupants(
        anchor = resolvedDropPosition,
        span = draggedItem.homeGridSpan,
        excludeItemId = draggedItem.id
    ).firstOrNull()

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
