package com.milki.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.dragdrop.AppDragDropController
import com.milki.launcher.ui.components.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.components.dragdrop.ExternalDragPayloadCodec.ExternalDragItem
import com.milki.launcher.ui.components.grid.GridConfig
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * DropHighlightLayer renders both internal-drag and external-drag highlights.
 *
 * UNIFIED APPROACH:
 * Both internal (homescreen-to-homescreen) and external (folder/search/drawer)
 * drags use the same [DropTargetHighlightBox] composable for the blue-glow
 * highlight at the target cell, and the same [DropPreviewContent] for the
 * dimmed icon or widget-size text inside the box.
 */
@Composable
internal fun DropHighlightLayer(
    items: List<HomeItem>,
    config: GridConfig,
    dragController: AppDragDropController<HomeItem>,
    layoutMetrics: AppDragDropLayoutMetrics,
    cellWidthPx: Float,
    cellHeightPx: Float,
    maxVisibleRows: Int,
    dragTargetOccupant: HomeItem?,
    resolvedInternalPreviewPosition: GridPosition?,
    externalDragState: HomeSurfaceExternalDragState
) {
    // Internal drag highlight + floating preview.
    dragController.session?.let { activeSession ->
        val target = resolvedInternalPreviewPosition
            ?: dragController.targetPosition
            ?: activeSession.startPosition
        val previewBaseOffset = layoutMetrics.cellToPixel(activeSession.startPosition)
        val previewOffset = previewBaseOffset + activeSession.currentOffset
        val previewSpan = (activeSession.item as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE

        val isDraggingFolder = activeSession.item is HomeItem.FolderItem
        val isInvalidDrop = isDraggingFolder && dragTargetOccupant != null && dragTargetOccupant !is HomeItem.FolderItem

        if (!isInvalidDrop) {
            val isFolderMerge = dragTargetOccupant != null
            val highlightColor = if (isFolderMerge) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.primary
            }
            val highlightScale = when {
                activeSession.item is HomeItem.WidgetItem -> 1f
                isFolderMerge -> config.dropHighlightScale * 1.05f
                else -> config.dropHighlightScale
            }

            DropTargetHighlightBox(
                column = target.column,
                row = target.row,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                spanColumns = previewSpan.columns,
                spanRows = previewSpan.rows,
                highlightColor = highlightColor,
                highlightScale = highlightScale,
                zIndex = config.dragZIndex
            ) {
                val previewItem = if (activeSession.item is HomeItem.WidgetItem) null
                    else (dragTargetOccupant ?: activeSession.item)
                val widgetSpan = if (activeSession.item is HomeItem.WidgetItem) previewSpan else null

                DropPreviewContent(
                    item = previewItem,
                    highlightAlpha = config.dropHighlightAlpha,
                    widgetSpan = widgetSpan
                )
            }
        }

        // External drags already get a platform drag shadow, so only internal drags need this.
        if (activeSession.item !is HomeItem.WidgetItem) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = previewOffset.x.roundToInt(),
                            y = previewOffset.y.roundToInt()
                        )
                    }
                    .size(
                        width = with(LocalDensity.current) { (cellWidthPx * previewSpan.columns).toDp() },
                        height = with(LocalDensity.current) { (cellHeightPx * previewSpan.rows).toDp() }
                    )
                    .padding(Spacing.extraSmall)
                    .zIndex(config.previewZIndex)
                    .graphicsLayer {
                        scaleX = config.previewScale
                        scaleY = config.previewScale
                        alpha = config.previewAlpha
                        shadowElevation = config.shadowElevation
                    }
            ) {
                PinnedItem(
                    item = activeSession.item,
                    onClick = {},
                    onLongClick = {},
                    handleLongPress = false
                )
            }
        }
    }

    // External drag highlight.
    if (externalDragState.isActive) {
        externalDragState.targetPosition?.let { targetPosition ->
            val currentExternalItem = externalDragState.item
            val rawDragSpan = (currentExternalItem as? ExternalDragItem.Widget)?.span ?: GridSpan.SINGLE
            val dragSpan = normalizeWidgetSpanForHomeGrid(rawSpan = rawDragSpan, gridColumns = config.columns)

            val clampedTarget = GridPosition(
                row = targetPosition.row.coerceIn(0, (maxVisibleRows - dragSpan.rows).coerceAtLeast(0)),
                column = targetPosition.column.coerceIn(0, (config.columns - dragSpan.columns).coerceAtLeast(0))
            )

            val spanCells = dragSpan.occupiedPositions(clampedTarget)
            val hasCollision = items.any { existingItem ->
                val existingSpan = (existingItem as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
                val existingCells = existingSpan.occupiedPositions(existingItem.position)
                existingCells.any { it in spanCells }
            }

            val highlightColor = if (hasCollision) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary
            val highlightScale = if (currentExternalItem is ExternalDragItem.Widget) 1f else config.dropHighlightScale

            DropTargetHighlightBox(
                column = clampedTarget.column,
                row = clampedTarget.row,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                spanColumns = dragSpan.columns,
                spanRows = dragSpan.rows,
                highlightColor = highlightColor,
                highlightScale = highlightScale,
                zIndex = config.dragZIndex
            ) {
                val previewItem = currentExternalItem?.toPreviewHomeItem()
                val widgetSpan = if (currentExternalItem is ExternalDragItem.Widget) dragSpan else null

                DropPreviewContent(
                    item = previewItem,
                    highlightAlpha = config.dropHighlightAlpha,
                    widgetSpan = widgetSpan
                )
            }
        }
    }
}

@Composable
private fun DropTargetHighlightBox(
    column: Int,
    row: Int,
    cellWidthPx: Float,
    cellHeightPx: Float,
    spanColumns: Int,
    spanRows: Int,
    highlightColor: Color,
    highlightScale: Float,
    zIndex: Float,
    content: @Composable () -> Unit
) {
    val highlightShape = RoundedCornerShape(CornerRadius.medium)
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (column * cellWidthPx).roundToInt(),
                    y = (row * cellHeightPx).roundToInt()
                )
            }
            .size(
                width = with(LocalDensity.current) { (cellWidthPx * spanColumns).toDp() },
                height = with(LocalDensity.current) { (cellHeightPx * spanRows).toDp() }
            )
            .padding(Spacing.extraSmall)
            .zIndex(zIndex)
            .shadow(
                elevation = Spacing.smallMedium,
                shape = highlightShape,
                ambientColor = highlightColor.copy(alpha = 0.6f),
                spotColor = highlightColor.copy(alpha = 0.6f)
            )
            .background(
                color = highlightColor.copy(alpha = 0.15f),
                shape = highlightShape
            )
            .border(
                width = Spacing.extraSmall,
                color = highlightColor.copy(alpha = 0.4f),
                shape = highlightShape
            )
            .graphicsLayer {
                scaleX = highlightScale
                scaleY = highlightScale
            }
    ) {
        content()
    }
}

@Composable
private fun DropPreviewContent(
    item: HomeItem?,
    highlightAlpha: Float,
    widgetSpan: GridSpan? = null
) {
    when {
        item != null -> {
            Box(modifier = Modifier.alpha(highlightAlpha)) {
                PinnedItem(
                    item = item,
                    onClick = {},
                    onLongClick = {},
                    handleLongPress = false
                )
            }
        }
        widgetSpan != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${widgetSpan.columns} x ${widgetSpan.rows}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}
