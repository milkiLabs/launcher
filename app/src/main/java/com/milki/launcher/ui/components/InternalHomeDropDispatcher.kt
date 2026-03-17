package com.milki.launcher.ui.components

import com.milki.launcher.domain.drag.drop.DropDecision
import com.milki.launcher.domain.drag.drop.DropTargetNode
import com.milki.launcher.domain.drag.drop.DropTargetRegistry
import com.milki.launcher.domain.drag.drop.RejectReason
import com.milki.launcher.domain.drag.reorder.GridReorderEngine
import com.milki.launcher.domain.drag.reorder.ReorderInput
import com.milki.launcher.domain.drag.reorder.ReorderMode
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem

internal data class InternalDropRoutingCallbacks(
    val onItemMove: (itemId: String, newPosition: GridPosition) -> Unit,
    val onCreateFolder: (item1: HomeItem, item2: HomeItem, position: GridPosition) -> Unit,
    val onAddItemToFolder: (folderId: String, item: HomeItem) -> Unit,
    val onMergeFolders: (sourceFolderId: String, targetFolderId: String) -> Unit,
    val onConfirmDrop: () -> Unit
)

internal class InternalHomeDropDispatcher(
    private val gridColumns: Int,
    private val gridRows: Int,
    private val callbacks: InternalDropRoutingCallbacks
) {
    private val reorderEngine = GridReorderEngine()

    fun dispatch(
        draggedItem: HomeItem,
        dropPosition: GridPosition,
        items: List<HomeItem>
    ): Boolean {
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
            if (reorderPlan.isValid) reorderPlan.anchorCell else dropPosition
        } else {
            dropPosition
        }

        val occupant = items.findOccupantForDroppedSpan(
            excludeItemId = draggedItem.id,
            draggedSpan = (draggedItem as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE,
            droppedAt = resolvedDropPosition
        )

        val context = InternalDropContext(
            draggedItem = draggedItem,
            dropPosition = resolvedDropPosition,
            occupant = occupant,
            callbacks = callbacks
        )

        val decision = registry.dispatch(context)
        return when (decision) {
            is DropDecision.Accepted -> true
            is DropDecision.Rejected -> true
            DropDecision.Pass -> false
        }
    }

    private val registry = DropTargetRegistry(
        listOf(
            EmptyCellMoveNode,
            WidgetRouteNode,
            FolderMergeNode,
            InvalidFolderRouteNode,
            AddToFolderNode,
            CreateFolderNode
        )
    )
}

private data class InternalDropContext(
    val draggedItem: HomeItem,
    val dropPosition: GridPosition,
    val occupant: HomeItem?,
    val callbacks: InternalDropRoutingCallbacks
)

private object EmptyCellMoveNode : DropTargetNode<InternalDropContext> {
    override val id: String = "internal.empty.move"

    override fun evaluate(context: InternalDropContext): DropDecision {
        if (context.occupant != null) return DropDecision.Pass
        context.callbacks.onItemMove(context.draggedItem.id, context.dropPosition)
        context.callbacks.onConfirmDrop()
        return DropDecision.Accepted
    }
}

private object WidgetRouteNode : DropTargetNode<InternalDropContext> {
    override val id: String = "internal.widget.guard"

    override fun evaluate(context: InternalDropContext): DropDecision {
        val occupant = context.occupant ?: return DropDecision.Pass
        return if (context.draggedItem is HomeItem.WidgetItem || occupant is HomeItem.WidgetItem) {
            DropDecision.Rejected(RejectReason.INVALID_WIDGET_ROUTE)
        } else {
            DropDecision.Pass
        }
    }
}

private object FolderMergeNode : DropTargetNode<InternalDropContext> {
    override val id: String = "internal.folder.merge"

    override fun evaluate(context: InternalDropContext): DropDecision {
        val occupant = context.occupant as? HomeItem.FolderItem ?: return DropDecision.Pass
        val draggedFolder = context.draggedItem as? HomeItem.FolderItem ?: return DropDecision.Pass

        context.callbacks.onMergeFolders(draggedFolder.id, occupant.id)
        context.callbacks.onConfirmDrop()
        return DropDecision.Accepted
    }
}

private object InvalidFolderRouteNode : DropTargetNode<InternalDropContext> {
    override val id: String = "internal.folder.invalid"

    override fun evaluate(context: InternalDropContext): DropDecision {
        val occupant = context.occupant ?: return DropDecision.Pass
        return if (context.draggedItem is HomeItem.FolderItem && occupant !is HomeItem.FolderItem) {
            DropDecision.Rejected(RejectReason.INVALID_FOLDER_ROUTE)
        } else {
            DropDecision.Pass
        }
    }
}

private object AddToFolderNode : DropTargetNode<InternalDropContext> {
    override val id: String = "internal.folder.add-item"

    override fun evaluate(context: InternalDropContext): DropDecision {
        val occupant = context.occupant as? HomeItem.FolderItem ?: return DropDecision.Pass
        if (context.draggedItem is HomeItem.FolderItem) return DropDecision.Pass

        context.callbacks.onAddItemToFolder(occupant.id, context.draggedItem)
        context.callbacks.onConfirmDrop()
        return DropDecision.Accepted
    }
}

private object CreateFolderNode : DropTargetNode<InternalDropContext> {
    override val id: String = "internal.folder.create"

    override fun evaluate(context: InternalDropContext): DropDecision {
        val occupant = context.occupant ?: return DropDecision.Pass
        if (occupant is HomeItem.FolderItem || context.draggedItem is HomeItem.FolderItem) {
            return DropDecision.Pass
        }

        context.callbacks.onCreateFolder(context.draggedItem, occupant, occupant.position)
        context.callbacks.onConfirmDrop()
        return DropDecision.Accepted
    }
}
