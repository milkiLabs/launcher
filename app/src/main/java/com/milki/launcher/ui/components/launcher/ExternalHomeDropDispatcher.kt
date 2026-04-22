package com.milki.launcher.ui.components.launcher

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
import com.milki.launcher.ui.interaction.dragdrop.ExternalDragDropItem
import com.milki.launcher.ui.interaction.dragdrop.ExternalDragPayloadCodec.ExternalDragItem

private typealias OnItemDroppedToHome =
    (item: HomeItem, position: GridPosition) -> Unit

private typealias OnCreateFolder =
    (item1: HomeItem, item2: HomeItem, position: GridPosition) -> Unit

private typealias OnAddItemToFolder =
    (folderId: String, item: HomeItem) -> Unit

private typealias OnFolderItemExtracted =
    (folderId: String, itemId: String, targetPosition: GridPosition) -> Unit

private typealias OnMoveFolderItemToFolder =
    (sourceFolderId: String, itemId: String, targetFolderId: String) -> Unit

private typealias OnFolderChildDroppedOnItem =
    (
        sourceFolderId: String,
        childItem: HomeItem,
        occupantItem: HomeItem,
        atPosition: GridPosition
    ) -> Unit

private typealias OnWidgetDroppedToHome =
    (
        providerInfo: AppWidgetProviderInfo,
        span: GridSpan,
        dropPosition: GridPosition
    ) -> Unit

internal data class ExternalDropRoutingCallbacks(
    val onItemDroppedToHome: OnItemDroppedToHome,
    val onCreateFolder: OnCreateFolder,
    val onAddItemToFolder: OnAddItemToFolder,
    val onFolderItemExtracted: OnFolderItemExtracted,
    val onMoveFolderItemToFolder: OnMoveFolderItemToFolder,
    val onFolderChildDroppedOnItem: OnFolderChildDroppedOnItem,
    val onWidgetDroppedToHome: OnWidgetDroppedToHome,
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
        val folderChild = context.item as? ExternalDragItem.FolderChild
        val occupant = context.occupantAtDrop as? HomeItem.FolderItem
        val decision = when {
            folderChild == null || occupant == null -> DropDecision.Pass
            occupant.id == folderChild.folderId ->
                DropDecision.Rejected(RejectReason.INVALID_FOLDER_ROUTE)
            else -> DropDecision.Pass
        }
        return decision
    }
}

private object FolderChildToFolderNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.folder-child.to-folder"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val folderChild = context.item as? ExternalDragItem.FolderChild
        val occupant = context.occupantAtDrop as? HomeItem.FolderItem
        val decision = when {
            folderChild == null || occupant == null -> DropDecision.Pass
            occupant.id == folderChild.folderId -> DropDecision.Pass
            else -> context.acceptDrop {
                context.callbacks.onMoveFolderItemToFolder(
                    folderChild.folderId,
                    folderChild.childItem.id,
                    occupant.id
                )
            }
        }
        return decision
    }
}

private object FolderChildToWidgetNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.folder-child.to-widget"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val decision = when {
            context.item !is ExternalDragItem.FolderChild -> DropDecision.Pass
            context.occupantAtDrop is HomeItem.WidgetItem ->
                DropDecision.Rejected(RejectReason.INVALID_WIDGET_ROUTE)
            else -> DropDecision.Pass
        }
        return decision
    }
}

private object FolderChildOntoItemNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.folder-child.onto-item"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val folderChild = context.item as? ExternalDragItem.FolderChild
        val occupant = context.occupantAtDrop
        val decision = when {
            folderChild == null || occupant == null -> DropDecision.Pass
            occupant is HomeItem.FolderItem || occupant is HomeItem.WidgetItem -> DropDecision.Pass
            else -> context.acceptDrop {
                context.callbacks.onFolderChildDroppedOnItem(
                    folderChild.folderId,
                    folderChild.childItem,
                    occupant,
                    context.dropPosition
                )
            }
        }
        return decision
    }
}

private object FolderChildExtractNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.folder-child.extract"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val folderChild = context.item as? ExternalDragItem.FolderChild
        val decision = when {
            folderChild == null -> DropDecision.Pass
            context.occupantAtDrop != null -> DropDecision.Pass
            else -> context.acceptDrop {
                context.callbacks.onFolderItemExtracted(
                    folderChild.folderId,
                    folderChild.childItem.id,
                    context.dropPosition
                )
            }
        }
        return decision
    }
}

private object WidgetDropNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.widget"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val widgetItem = context.item as? ExternalDragItem.Widget
        val decision = if (widgetItem == null) {
            DropDecision.Pass
        } else {
            context.evaluateWidgetDrop(widgetItem)
        }
        return decision
    }
}

private object RegularToFolderNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.regular.to-folder"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val homeItem = context.previewHomeItem
        val occupant = context.occupantAtDrop as? HomeItem.FolderItem
        val decision = when {
            homeItem == null || occupant == null -> DropDecision.Pass
            else -> context.acceptDrop {
                context.callbacks.onAddItemToFolder(occupant.id, homeItem)
            }
        }
        return decision
    }
}

private object RegularToWidgetNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.regular.to-widget"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val decision = when {
            context.previewHomeItem == null -> DropDecision.Pass
            context.occupantAtDrop is HomeItem.WidgetItem ->
                DropDecision.Rejected(RejectReason.INVALID_WIDGET_ROUTE)
            else -> DropDecision.Pass
        }
        return decision
    }
}

private object RegularCreateFolderNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.regular.create-folder"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val homeItem = context.previewHomeItem
        val occupant = context.occupantAtDrop
        val decision = when {
            homeItem == null || occupant == null -> DropDecision.Pass
            occupant is HomeItem.FolderItem || occupant is HomeItem.WidgetItem -> DropDecision.Pass
            else -> context.acceptDrop {
                context.callbacks.onCreateFolder(homeItem, occupant, occupant.position)
            }
        }
        return decision
    }
}

private object RegularDropToEmptyNode : DropTargetNode<ExternalDropContext> {
    override val id: String = "external.regular.empty"

    override fun evaluate(context: ExternalDropContext): DropDecision {
        val homeItem = context.previewHomeItem
        val decision = when {
            homeItem == null -> DropDecision.Pass
            context.occupantAtDrop != null -> DropDecision.Pass
            else -> context.acceptDrop {
                context.callbacks.onItemDroppedToHome(homeItem, context.dropPosition)
            }
        }
        return decision
    }
}

private inline fun ExternalDropContext.acceptDrop(onAccept: () -> Unit): DropDecision {
    onAccept()
    callbacks.onConfirmDrop()
    return DropDecision.Accepted
}

private fun ExternalDropContext.evaluateWidgetDrop(
    widgetItem: ExternalDragItem.Widget
): DropDecision {
    val normalizedSpan = normalizeWidgetSpanForHomeGrid(
        rawSpan = widgetItem.span,
        gridColumns = gridColumns
    )
    val reorderPlan = reorderEngine.compute(
        ReorderInput(
            items = items,
            preferredCell = clampedWidgetDrop(normalizedSpan),
            draggedSpan = normalizedSpan,
            gridColumns = gridColumns,
            gridRows = maxVisibleRows,
            mode = ReorderMode.Commit
        )
    )
    val resolvedProviderInfo = resolveWidgetProvider(widgetItem)
    val decision = when {
        !reorderPlan.isValid ->
            DropDecision.Rejected(RejectReason.OCCUPIED_TARGET)
        resolvedProviderInfo == null ->
            DropDecision.Rejected(RejectReason.PAYLOAD_UNSUPPORTED)
        else -> acceptDrop {
            callbacks.onWidgetDroppedToHome(
                resolvedProviderInfo,
                normalizedSpan,
                reorderPlan.anchorCell
            )
        }
    }
    return decision
}

private fun ExternalDropContext.clampedWidgetDrop(normalizedSpan: GridSpan): GridPosition {
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

private fun ExternalDropContext.resolveWidgetProvider(
    widgetItem: ExternalDragItem.Widget
): AppWidgetProviderInfo? {
    return widgetItem.providerInfo
        ?: widgetHostManager?.findInstalledProvider(widgetItem.providerComponent)
}
