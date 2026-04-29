package com.milki.launcher.ui.components.launcher

import android.appwidget.AppWidgetProviderInfo
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.drag.drop.DropDecision
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

/**
 * Centralizes external drop routing so composables only bridge UI events.
 *
 * The same pure resolver is used by both highlight preview and commit dispatch
 * so valid folder-creation drops do not drift from actual drop behavior.
 */
internal class ExternalHomeDropDispatcher(
    private val gridColumns: Int,
    private val maxVisibleRows: Int,
    private val widgetHostManager: WidgetHostManager?,
    private val callbacks: ExternalDropRoutingCallbacks
) {
    private val reorderEngine = GridReorderEngine()

    fun dispatch(
        item: ExternalDragDropItem,
        dropPosition: GridPosition,
        items: List<HomeItem>
    ): Boolean {
        val resolution = resolveExternalDropResolution(
            item = item,
            dropPosition = dropPosition,
            items = items,
            gridColumns = gridColumns,
            maxVisibleRows = maxVisibleRows,
            widgetHostManager = widgetHostManager,
            reorderEngine = reorderEngine,
            reorderMode = ReorderMode.Commit
        )

        return when (val decision = resolution.decision) {
            is DropDecision.Accepted -> {
                resolution.commitAction?.invoke(callbacks)
                callbacks.onConfirmDrop()
                true
            }
            is DropDecision.Rejected -> decision.reason != RejectReason.PAYLOAD_UNSUPPORTED
            DropDecision.Pass -> false
        }
    }
}

internal fun resolveExternalDropPreviewState(
    item: ExternalDragDropItem?,
    targetPosition: GridPosition,
    items: List<HomeItem>,
    gridColumns: Int,
    maxVisibleRows: Int,
    widgetHostManager: WidgetHostManager? = null,
    reorderEngine: GridReorderEngine = GridReorderEngine()
): ExternalDropPreviewState? {
    return resolveExternalDropResolution(
        item = item,
        dropPosition = targetPosition,
        items = items,
        gridColumns = gridColumns,
        maxVisibleRows = maxVisibleRows,
        widgetHostManager = widgetHostManager,
        reorderEngine = reorderEngine,
        reorderMode = ReorderMode.Preview
    ).previewState
}

private data class ExternalDropResolution(
    val decision: DropDecision,
    val previewState: ExternalDropPreviewState?,
    val commitAction: ((ExternalDropRoutingCallbacks) -> Unit)? = null
)

private fun resolveExternalDropResolution(
    item: ExternalDragDropItem?,
    dropPosition: GridPosition,
    items: List<HomeItem>,
    gridColumns: Int,
    maxVisibleRows: Int,
    widgetHostManager: WidgetHostManager?,
    reorderEngine: GridReorderEngine,
    reorderMode: ReorderMode
): ExternalDropResolution {
    if (item == null) return ExternalDropResolution(decision = DropDecision.Pass, previewState = null)

    return when (item) {
        is ExternalDragItem.FolderChild -> resolveFolderChildDrop(
            item = item,
            dropPosition = dropPosition,
            items = items
        )
        is ExternalDragItem.Widget -> resolveWidgetDrop(
            item = item,
            dropPosition = dropPosition,
            items = items,
            gridColumns = gridColumns,
            maxVisibleRows = maxVisibleRows,
            widgetHostManager = widgetHostManager,
            reorderEngine = reorderEngine,
            reorderMode = reorderMode
        )
        else -> resolveRegularExternalDrop(
            item = item,
            dropPosition = dropPosition,
            items = items
        )
    }
}

private fun resolveFolderChildDrop(
    item: ExternalDragItem.FolderChild,
    dropPosition: GridPosition,
    items: List<HomeItem>
): ExternalDropResolution {
    val occupant = items.findOccupantAt(dropPosition)
    return when {
        occupant is HomeItem.FolderItem && occupant.id == item.folderId -> rejectedExternalDrop(
            reason = RejectReason.INVALID_FOLDER_ROUTE,
            targetPosition = dropPosition
        )
        occupant is HomeItem.FolderItem -> acceptedExternalDrop(
            targetPosition = dropPosition,
            highlightKind = ExternalDropHighlightKind.Secondary
        ) { callbacks ->
            callbacks.onMoveFolderItemToFolder(
                item.folderId,
                item.childItem.id,
                occupant.id
            )
        }
        occupant is HomeItem.WidgetItem -> rejectedExternalDrop(
            reason = RejectReason.INVALID_WIDGET_ROUTE,
            targetPosition = dropPosition
        )
        occupant != null -> acceptedExternalDrop(
            targetPosition = dropPosition,
            highlightKind = ExternalDropHighlightKind.Secondary
        ) { callbacks ->
            callbacks.onFolderChildDroppedOnItem(
                item.folderId,
                item.childItem,
                occupant,
                dropPosition
            )
        }
        else -> acceptedExternalDrop(
            targetPosition = dropPosition,
            highlightKind = ExternalDropHighlightKind.Primary
        ) { callbacks ->
            callbacks.onFolderItemExtracted(
                item.folderId,
                item.childItem.id,
                dropPosition
            )
        }
    }
}

private fun resolveRegularExternalDrop(
    item: ExternalDragDropItem,
    dropPosition: GridPosition,
    items: List<HomeItem>
): ExternalDropResolution {
    val previewHomeItem = item.toPreviewHomeItem()
        ?: return ExternalDropResolution(decision = DropDecision.Pass, previewState = null)
    val occupant = items.findOccupantAt(dropPosition)

    return when (occupant) {
        is HomeItem.FolderItem -> acceptedExternalDrop(
            targetPosition = dropPosition,
            highlightKind = ExternalDropHighlightKind.Secondary
        ) { callbacks ->
            callbacks.onAddItemToFolder(occupant.id, previewHomeItem)
        }
        is HomeItem.WidgetItem -> rejectedExternalDrop(
            reason = RejectReason.INVALID_WIDGET_ROUTE,
            targetPosition = dropPosition
        )
        null -> acceptedExternalDrop(
            targetPosition = dropPosition,
            highlightKind = ExternalDropHighlightKind.Primary
        ) { callbacks ->
            callbacks.onItemDroppedToHome(previewHomeItem, dropPosition)
        }
        else -> acceptedExternalDrop(
            targetPosition = dropPosition,
            highlightKind = ExternalDropHighlightKind.Secondary
        ) { callbacks ->
            callbacks.onCreateFolder(previewHomeItem, occupant, occupant.position)
        }
    }
}

private fun resolveWidgetDrop(
    item: ExternalDragItem.Widget,
    dropPosition: GridPosition,
    items: List<HomeItem>,
    gridColumns: Int,
    maxVisibleRows: Int,
    widgetHostManager: WidgetHostManager?,
    reorderEngine: GridReorderEngine,
    reorderMode: ReorderMode
): ExternalDropResolution {
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
        !reorderPlan.isValid -> rejectedExternalDrop(
            reason = RejectReason.OCCUPIED_TARGET,
            targetPosition = resolvedTarget,
            dragSpan = normalizedSpan
        )
        resolvedProvider == null -> rejectedExternalDrop(
            reason = RejectReason.PAYLOAD_UNSUPPORTED,
            targetPosition = resolvedTarget,
            dragSpan = normalizedSpan
        )
        else -> acceptedExternalDrop(
            targetPosition = resolvedTarget,
            dragSpan = normalizedSpan,
            highlightKind = ExternalDropHighlightKind.Primary
        ) { callbacks ->
            callbacks.onWidgetDroppedToHome(
                resolvedProvider,
                normalizedSpan,
                reorderPlan.anchorCell
            )
        }
    }
}

private fun acceptedExternalDrop(
    targetPosition: GridPosition,
    dragSpan: GridSpan = GridSpan.SINGLE,
    highlightKind: ExternalDropHighlightKind,
    commitAction: (ExternalDropRoutingCallbacks) -> Unit
): ExternalDropResolution {
    return ExternalDropResolution(
        decision = DropDecision.Accepted,
        previewState = ExternalDropPreviewState(
            targetPosition = targetPosition,
            dragSpan = dragSpan,
            highlightKind = highlightKind
        ),
        commitAction = commitAction
    )
}

private fun rejectedExternalDrop(
    reason: RejectReason,
    targetPosition: GridPosition,
    dragSpan: GridSpan = GridSpan.SINGLE
): ExternalDropResolution {
    return ExternalDropResolution(
        decision = DropDecision.Rejected(reason),
        previewState = ExternalDropPreviewState(
            targetPosition = targetPosition,
            dragSpan = dragSpan,
            highlightKind = ExternalDropHighlightKind.Error
        )
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
