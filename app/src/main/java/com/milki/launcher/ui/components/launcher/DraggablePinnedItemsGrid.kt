package com.milki.launcher.ui.components.launcher

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.drag.reorder.GridReorderEngine
import com.milki.launcher.domain.drag.reorder.ReorderInput
import com.milki.launcher.domain.drag.reorder.ReorderMode
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.widget.recommendWidgetPlacementSpan
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.interaction.dragdrop.ExternalDragDropItem
import com.milki.launcher.ui.interaction.dragdrop.ExternalDragPayloadCodec.ExternalDragItem
import com.milki.launcher.ui.interaction.dragdrop.rememberAppDragDropController
import com.milki.launcher.ui.interaction.grid.GridConfig
import com.milki.launcher.ui.interaction.grid.HomeBackgroundGestureBindings

/**
 * DraggablePinnedItemsGrid now acts as a composition/wiring root.
 *
 * ARCHITECTURE INTENT:
 * 1) Keep this composable focused on state ownership + dependency wiring.
 * 2) Move behavior-heavy responsibilities into dedicated layers:
 *    - InternalGridDragLayer
 *    - ExternalDropRoutingLayer
 *    - WidgetOverlayLayer
 *    - DropHighlightLayer
 * 3) Reuse one shared span-aware occupancy helper across routes.
 */
@Composable
fun DraggablePinnedItemsGrid(
    items: List<HomeItem>,
    config: GridConfig = GridConfig.Default,
    onItemClick: (HomeItem) -> Unit,
    onItemLongPress: (HomeItem) -> Unit,
    onItemMove: (itemId: String, newPosition: GridPosition) -> Unit,
    backgroundGestures: HomeBackgroundGestureBindings = HomeBackgroundGestureBindings(),
    onItemDroppedToHome: (item: HomeItem, position: GridPosition) -> Unit = { _, _ -> },
    onCreateFolder: (item1: HomeItem, item2: HomeItem, position: GridPosition) -> Unit = { _, _, _ -> },
    onAddItemToFolder: (folderId: String, item: HomeItem) -> Unit = { _, _ -> },
    onMergeFolders: (sourceFolderId: String, targetFolderId: String) -> Unit = { _, _ -> },
    onFolderItemExtracted: (folderId: String, itemId: String, targetPosition: GridPosition) -> Unit = { _, _, _ -> },
    onMoveFolderItemToFolder: (sourceFolderId: String, itemId: String, targetFolderId: String) -> Unit = { _, _, _ -> },
    onFolderChildDroppedOnItem: (sourceFolderId: String, childItem: HomeItem, occupantItem: HomeItem, atPosition: GridPosition) -> Unit = { _, _, _, _ -> },
    widgetHostManager: WidgetHostManager? = null,
    onRemoveWidget: (widgetId: String, appWidgetId: Int) -> Unit = { _, _ -> },
    onUpdateWidgetFrame: (
        widgetId: String,
        newPosition: GridPosition,
        newSpan: GridSpan
    ) -> Unit = { _, _, _ -> },
    onWidgetDroppedToHome: (providerInfo: android.appwidget.AppWidgetProviderInfo, span: GridSpan, dropPosition: GridPosition) -> Unit = { _, _, _ -> },
    onItemBoundsMeasured: (itemId: String, boundsInWindow: Rect) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val dragController = rememberAppDragDropController<HomeItem>(config)
    val interactionController = rememberHomeSurfaceInteractionController(dragController)
    val reorderEngine = remember { GridReorderEngine() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cellWidthPx = with(LocalDensity.current) { maxWidth.toPx() / config.columns }
        val cellHeightPx = cellWidthPx
        val gridHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

        val maxVisibleRows = (gridHeightPx / cellHeightPx).toInt().coerceAtLeast(1)

        val layoutMetrics = remember(
            cellWidthPx,
            cellHeightPx,
            config.columns,
            maxVisibleRows
        ) {
            AppDragDropLayoutMetrics(
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                columns = config.columns,
                rows = maxVisibleRows
            )
        }

        // Widget preview should use the same reorder planner as commit so users
        // see the final resolved anchor while dragging.
        val resolvedInternalPreviewPosition by remember(
            items,
            dragController.session,
            dragController.targetPosition,
            config.columns,
            maxVisibleRows
        ) {
            derivedStateOf {
                val session = dragController.session ?: return@derivedStateOf null
                val target = dragController.targetPosition ?: return@derivedStateOf null
                val draggedWidget = session.item as? HomeItem.WidgetItem ?: return@derivedStateOf target

                val reorderPlan = reorderEngine.compute(
                    ReorderInput(
                        items = items,
                        preferredCell = target,
                        draggedSpan = draggedWidget.span,
                        gridColumns = config.columns,
                        gridRows = maxVisibleRows,
                        excludeItemId = session.itemId,
                        mode = ReorderMode.Preview
                    )
                )
                if (reorderPlan.isValid) reorderPlan.anchorCell else target
            }
        }

        val dragTargetOccupant by remember(
            items,
            dragController.session,
            dragController.targetPosition,
            resolvedInternalPreviewPosition
        ) {
            derivedStateOf {
                val session = dragController.session ?: return@derivedStateOf null
                val target = resolvedInternalPreviewPosition
                    ?: dragController.targetPosition
                    ?: return@derivedStateOf null
                items.findOccupantAt(position = target, excludeItemId = session.itemId)
            }
        }

        InternalGridDragLayer(
            items = items,
            config = config,
            interactionController = interactionController,
            dragController = dragController,
            layoutMetrics = layoutMetrics,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            maxVisibleRows = maxVisibleRows,
            widgetHostManager = widgetHostManager,
            backgroundGestures = backgroundGestures,
            onItemClick = onItemClick,
            onItemLongPress = onItemLongPress,
            onItemMove = onItemMove,
            onCreateFolder = onCreateFolder,
            onAddItemToFolder = onAddItemToFolder,
            onMergeFolders = onMergeFolders,
            onRemoveWidget = onRemoveWidget,
            hapticLongPress = { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress) },
            hapticDragActivate = { hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
            hapticConfirm = { hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm) },
            onItemBoundsMeasured = onItemBoundsMeasured
        )

        WidgetOverlayLayer(
            items = items,
            widgetTransformSession = interactionController.widgetTransformSession,
            onFinishTransform = interactionController::finishWidgetTransform,
            onCancelTransform = interactionController::cancelWidgetTransform,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            gridColumns = config.columns,
            maxVisibleRows = maxVisibleRows,
            onUpdateWidgetFrame = onUpdateWidgetFrame
        )

        DropHighlightLayer(
            items = items,
            config = config,
            dragController = dragController,
            layoutMetrics = layoutMetrics,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            maxVisibleRows = maxVisibleRows,
            dragTargetOccupant = dragTargetOccupant,
            resolvedInternalPreviewPosition = resolvedInternalPreviewPosition,
            externalDragState = interactionController.externalDragState
        )

        ExternalDropRoutingLayer(
            items = items,
            config = config,
            interactionController = interactionController,
            layoutMetrics = layoutMetrics,
            maxVisibleRows = maxVisibleRows,
            widgetHostManager = widgetHostManager,
            onItemDroppedToHome = onItemDroppedToHome,
            onCreateFolder = onCreateFolder,
            onAddItemToFolder = onAddItemToFolder,
            onFolderItemExtracted = onFolderItemExtracted,
            onMoveFolderItemToFolder = onMoveFolderItemToFolder,
            onFolderChildDroppedOnItem = onFolderChildDroppedOnItem,
            onWidgetDroppedToHome = onWidgetDroppedToHome,
            hapticConfirm = { hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm) }
        )
    }
}

/**
 * Shared span-aware occupant lookup used by all routing/highlight paths.
 *
 * WHY THIS EXISTS:
 * Widgets occupy multiple cells while storing only one anchor [position].
 * A top-left-only check (`item.position == dropCell`) is wrong for any
 * non-anchor cell occupied by a widget span.
 */
internal fun List<HomeItem>.findOccupantAt(
    position: GridPosition,
    excludeItemId: String? = null
): HomeItem? {
    return firstOrNull { candidate ->
        if (excludeItemId != null && candidate.id == excludeItemId) return@firstOrNull false
        val candidateSpan = (candidate as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
        position in candidateSpan.occupiedPositions(candidate.position)
    }
}

/**
 * Finds the first top-level item whose occupied cells intersect [draggedSpan]
 * anchored at [droppedAt].
 */
internal fun List<HomeItem>.findOccupantForDroppedSpan(
    excludeItemId: String,
    draggedSpan: GridSpan,
    droppedAt: GridPosition
): HomeItem? {
    val draggedTargetCells = draggedSpan.occupiedPositions(droppedAt)
    return firstOrNull { other ->
        if (other.id == excludeItemId) return@firstOrNull false
        val otherSpan = (other as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
        val otherCells = otherSpan.occupiedPositions(other.position)
        otherCells.any { it in draggedTargetCells }
    }
}

/**
 * Maps an external drag payload to a home-item preview when possible.
 *
 * Widgets intentionally return null because widget placement must go through the
 * bind/configure flow and has no direct persisted HomeItem at drag time.
 */
internal fun ExternalDragDropItem.toPreviewHomeItem(): HomeItem? {
    return when (this) {
        is ExternalDragItem.App -> HomeItem.PinnedApp.fromAppInfo(appInfo)
        is ExternalDragItem.File -> HomeItem.PinnedFile.fromFileDocument(fileDocument)
        is ExternalDragItem.Contact -> HomeItem.PinnedContact.fromContact(contact)
        is ExternalDragItem.FolderChild -> childItem
        is ExternalDragItem.Widget -> null
    }
}

/**
 * Normalizes provider-reported span to a grid-friendly default.
 *
 * Heuristic:
 * 1) Clamp width to the home grid.
 * 2) Cap height to a practical default for first placement.
 * 3) If the result is still too large, trim area while preserving a sensible shape.
 */
internal fun normalizeWidgetSpanForHomeGrid(
    rawSpan: GridSpan,
    gridColumns: Int,
    maxDefaultRows: Int = 3
): GridSpan {
    return recommendWidgetPlacementSpan(
        rawSpan = rawSpan,
        gridColumns = gridColumns,
        maxDefaultRows = maxDefaultRows
    )
}
