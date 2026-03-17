package com.milki.launcher.ui.components

import android.appwidget.AppWidgetProviderInfo
import com.milki.launcher.data.widget.WidgetHostManager
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
import com.milki.launcher.ui.components.dragdrop.ExternalDragDropItem
import com.milki.launcher.ui.components.dragdrop.ExternalDragPayloadCodec.ExternalDragItem

internal data class ExternalDropRoutingCallbacks(
    val onItemDroppedToHome: (item: HomeItem, position: GridPosition) -> Unit,
    val onCreateFolder: (item1: HomeItem, item2: HomeItem, position: GridPosition) -> Unit,
    val onAddItemToFolder: (folderId: String, item: HomeItem) -> Unit,
    val onFolderItemExtracted: (folderId: String, itemId: String, targetPosition: GridPosition) -> Unit,
    val onMoveFolderItemToFolder: (sourceFolderId: String, itemId: String, targetFolderId: String) -> Unit,
    val onFolderChildDroppedOnItem: (sourceFolderId: String, childItem: HomeItem, occupantItem: HomeItem, atPosition: GridPosition) -> Unit,
    val onWidgetDroppedToHome: (providerInfo: AppWidgetProviderInfo, span: GridSpan, dropPosition: GridPosition) -> Unit,
    val onConfirmDrop: () -> Unit
)

/**
 * Centralizes external drop routing so composables only bridge UI events.
 */
internal class ExternalHomeDropDispatcher(
    private val gridColumns: Int,
    private val maxVisibleRows: Int,
    private val widgetHostManager: WidgetHostManager?,
    private val callbacks: ExternalDropRoutingCallbacks
) {
    private val reorderEngine = GridReorderEngine()

    private val registry = DropTargetRegistry(
        listOf(
            FolderChildSameFolderNode,
            FolderChildToFolderNode,
            FolderChildToWidgetNode,
            FolderChildOntoItemNode,
            FolderChildExtractNode,
            WidgetDropNode,
            RegularToFolderNode,
            RegularToWidgetNode,
            RegularCreateFolderNode,
            RegularDropToEmptyNode
        )
    )

    fun dispatch(
        item: ExternalDragDropItem,
        dropPosition: GridPosition,
        items: List<HomeItem>
    ): Boolean {
        val context = ExternalDropContext(
            item = item,
            dropPosition = dropPosition,
            occupantAtDrop = items.findOccupantAt(dropPosition),
            previewHomeItem = item.toPreviewHomeItem(),
            items = items,
            gridColumns = gridColumns,
            maxVisibleRows = maxVisibleRows,
            widgetHostManager = widgetHostManager,
            reorderEngine = reorderEngine,
            callbacks = callbacks
        )

        return when (val decision = registry.dispatch(context)) {
            is DropDecision.Accepted -> true
            is DropDecision.Rejected -> decision.reason != RejectReason.PAYLOAD_UNSUPPORTED
            DropDecision.Pass -> false
        }
    }
}

private data class ExternalDropContext(
    val item: ExternalDragDropItem,
    val dropPosition: GridPosition,
    val occupantAtDrop: HomeItem?,
    val previewHomeItem: HomeItem?,
    val items: List<HomeItem>,
    val gridColumns: Int,
    val maxVisibleRows: Int,
    val widgetHostManager: WidgetHostManager?,
    val reorderEngine: GridReorderEngine,
    val callbacks: ExternalDropRoutingCallbacks
)

private object FolderChildSameFolderNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.folder-child.same-folder"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val folderChild = context.item as? ExternalDragItem.FolderChild ?: return DropDecision.Pass
        val occupant = context.occupantAtDrop as? HomeItem.FolderItem ?: return DropDecision.Pass
        return if (occupant.id == folderChild.folderId) {
            DropDecision.Rejected(RejectReason.INVALID_FOLDER_ROUTE)
        } else {
            DropDecision.Pass
        }
    }
}

private object FolderChildToFolderNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.folder-child.to-folder"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val folderChild = context.item as? ExternalDragItem.FolderChild ?: return DropDecision.Pass
        val occupant = context.occupantAtDrop as? HomeItem.FolderItem ?: return DropDecision.Pass
        if (occupant.id == folderChild.folderId) return DropDecision.Pass

        context.callbacks.onMoveFolderItemToFolder(folderChild.folderId, folderChild.childItem.id, occupant.id)
        context.callbacks.onConfirmDrop()
        return DropDecision.Accepted
    }
}

private object FolderChildToWidgetNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.folder-child.to-widget"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        if (context.item !is ExternalDragItem.FolderChild) return DropDecision.Pass
        return if (context.occupantAtDrop is HomeItem.WidgetItem) {
            DropDecision.Rejected(RejectReason.INVALID_WIDGET_ROUTE)
        } else {
            DropDecision.Pass
        }
    }
}

private object FolderChildOntoItemNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.folder-child.onto-item"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val folderChild = context.item as? ExternalDragItem.FolderChild ?: return DropDecision.Pass
        val occupant = context.occupantAtDrop ?: return DropDecision.Pass
        if (occupant is HomeItem.FolderItem || occupant is HomeItem.WidgetItem) return DropDecision.Pass

        context.callbacks.onFolderChildDroppedOnItem(
            folderChild.folderId,
            folderChild.childItem,
            occupant,
            context.dropPosition
        )
        context.callbacks.onConfirmDrop()
        return DropDecision.Accepted
    }
}

private object FolderChildExtractNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.folder-child.extract"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val folderChild = context.item as? ExternalDragItem.FolderChild ?: return DropDecision.Pass
        if (context.occupantAtDrop != null) return DropDecision.Pass

        context.callbacks.onFolderItemExtracted(folderChild.folderId, folderChild.childItem.id, context.dropPosition)
        context.callbacks.onConfirmDrop()
        return DropDecision.Accepted
    }
}

private object WidgetDropNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.widget"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val widgetItem = context.item as? ExternalDragItem.Widget ?: return DropDecision.Pass

        val normalizedSpan = normalizeWidgetSpanForHomeGrid(
            rawSpan = widgetItem.span,
            gridColumns = context.gridColumns
        )

        val clampedDrop = GridPosition(
            row = context.dropPosition.row.coerceIn(0, (context.maxVisibleRows - normalizedSpan.rows).coerceAtLeast(0)),
            column = context.dropPosition.column.coerceIn(0, (context.gridColumns - normalizedSpan.columns).coerceAtLeast(0))
        )

        val reorderPlan = context.reorderEngine.compute(
            ReorderInput(
                items = context.items,
                preferredCell = clampedDrop,
                draggedSpan = normalizedSpan,
                gridColumns = context.gridColumns,
                gridRows = context.maxVisibleRows,
                mode = ReorderMode.Commit
            )
        )
        if (!reorderPlan.isValid) {
            return DropDecision.Rejected(RejectReason.OCCUPIED_TARGET)
        }

        val resolvedProviderInfo = widgetItem.providerInfo
            ?: context.widgetHostManager?.findInstalledProvider(widgetItem.providerComponent)

        if (resolvedProviderInfo == null) {
            return DropDecision.Rejected(RejectReason.PAYLOAD_UNSUPPORTED)
        }

        context.callbacks.onWidgetDroppedToHome(resolvedProviderInfo, normalizedSpan, reorderPlan.anchorCell)
        context.callbacks.onConfirmDrop()
        return DropDecision.Accepted
    }
}

private object RegularToFolderNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.regular.to-folder"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val homeItem = context.previewHomeItem ?: return DropDecision.Pass
        val occupant = context.occupantAtDrop as? HomeItem.FolderItem ?: return DropDecision.Pass

        context.callbacks.onAddItemToFolder(occupant.id, homeItem)
        context.callbacks.onConfirmDrop()
        return DropDecision.Accepted
    }
}

private object RegularToWidgetNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.regular.to-widget"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        if (context.previewHomeItem == null) return DropDecision.Pass
        return if (context.occupantAtDrop is HomeItem.WidgetItem) {
            DropDecision.Rejected(RejectReason.INVALID_WIDGET_ROUTE)
        } else {
            DropDecision.Pass
        }
    }
}

private object RegularCreateFolderNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.regular.create-folder"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val homeItem = context.previewHomeItem ?: return DropDecision.Pass
        val occupant = context.occupantAtDrop ?: return DropDecision.Pass
        if (occupant is HomeItem.FolderItem || occupant is HomeItem.WidgetItem) return DropDecision.Pass

        context.callbacks.onCreateFolder(homeItem, occupant, occupant.position)
        context.callbacks.onConfirmDrop()
        return DropDecision.Accepted
    }
}

private object RegularDropToEmptyNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.regular.empty"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val homeItem = context.previewHomeItem ?: return DropDecision.Pass
        if (context.occupantAtDrop != null) return DropDecision.Pass

        context.callbacks.onItemDroppedToHome(homeItem, context.dropPosition)
        context.callbacks.onConfirmDrop()
        return DropDecision.Accepted
    }
}
