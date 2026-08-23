package com.milki.launcher.ui.components.launcher.folder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.milki.launcher.ui.theme.Spacing
import com.milki.launcher.ui.util.center

internal data class FolderDropSlot(
    val page: Int,
    val slotIndex: Int
)

internal data class FolderSurfaceMetrics(
    val cellWidth: Dp,
    val cellHeight: Dp,
    val cellSpacing: Dp,
    val gridWidth: Dp,
    val gridHeight: Dp,
    val surfaceWidth: Dp,
    val surfaceHeight: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val headerBottomSpacing: Dp,
    val indicatorTopSpacing: Dp
) {
    companion object {
        fun create(
            density: Density,
            layout: FolderGridLayout,
            pageCount: Int,
            maxWidth: Dp,
            maxHeight: Dp
        ): FolderSurfaceMetrics {
            with(density) {
                val safeMaxWidthPx = maxWidth.toPx()
                val safeMaxHeightPx = maxHeight.toPx()
                val edgeMarginPx = FOLDER_EDGE_MARGIN.toPx()

                val baseGridWidthPx = (
                    (FOLDER_BASE_CELL_WIDTH * layout.columns) +
                        (FOLDER_CELL_SPACING * (layout.columns - 1).coerceAtLeast(0))
                    ).toPx()
                val baseGridHeightPx = (
                    (FOLDER_BASE_CELL_HEIGHT * layout.rows) +
                        (FOLDER_CELL_SPACING * (layout.rows - 1).coerceAtLeast(0))
                    ).toPx()
                val baseSurfaceWidthPx = (
                    baseGridWidthPx +
                        (FOLDER_HORIZONTAL_PADDING * 2).toPx()
                    )
                val baseSurfaceHeightPx = (
                    baseGridHeightPx +
                        (FOLDER_VERTICAL_PADDING * 2).toPx() +
                        FOLDER_HEADER_HEIGHT.toPx() +
                        FOLDER_HEADER_BOTTOM_SPACING.toPx() +
                        if (pageCount > 1) {
                            FOLDER_INDICATOR_TOP_SPACING.toPx() + FOLDER_INDICATOR_HEIGHT.toPx()
                        } else {
                            0f
                        }
                    )

                val fitScale = minOf(
                    1f,
                    (safeMaxWidthPx - (edgeMarginPx * 2)) / baseSurfaceWidthPx,
                    (safeMaxHeightPx - (edgeMarginPx * 2)) / baseSurfaceHeightPx
                ).coerceAtMost(1f)

                val cellWidth = FOLDER_BASE_CELL_WIDTH * fitScale
                val cellHeight = FOLDER_BASE_CELL_HEIGHT * fitScale
                val cellSpacing = FOLDER_CELL_SPACING * fitScale
                val horizontalPadding = FOLDER_HORIZONTAL_PADDING * fitScale
                val verticalPadding = FOLDER_VERTICAL_PADDING * fitScale
                val headerBottomSpacing = FOLDER_HEADER_BOTTOM_SPACING * fitScale
                val indicatorTopSpacing = FOLDER_INDICATOR_TOP_SPACING * fitScale
                val headerHeight = FOLDER_HEADER_HEIGHT * fitScale
                val indicatorHeight = FOLDER_INDICATOR_HEIGHT * fitScale

                val gridWidth = (cellWidth * layout.columns) + (cellSpacing * (layout.columns - 1).coerceAtLeast(0))
                val gridHeight = (cellHeight * layout.rows) + (cellSpacing * (layout.rows - 1).coerceAtLeast(0))
                val surfaceWidth = gridWidth + (horizontalPadding * 2)
                val surfaceHeight = gridHeight +
                    (verticalPadding * 2) +
                    headerHeight +
                    headerBottomSpacing +
                    if (pageCount > 1) indicatorTopSpacing + indicatorHeight else 0.dp

                return FolderSurfaceMetrics(
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    cellSpacing = cellSpacing,
                    gridWidth = gridWidth,
                    gridHeight = gridHeight,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                    horizontalPadding = horizontalPadding,
                    verticalPadding = verticalPadding,
                    headerBottomSpacing = headerBottomSpacing,
                    indicatorTopSpacing = indicatorTopSpacing
                )
            }
        }
    }
}

internal fun slotIndexForPageItem(
    itemIndex: Int,
    pageSize: Int
): Int {
    if (itemIndex < 0) return 0
    return itemIndex % pageSize.coerceAtLeast(1)
}

internal fun resolveHoveredSlot(
    pointer: Offset,
    currentPage: Int,
    layout: FolderGridLayout,
    gridWindowRect: Rect,
    cellSpacingPx: Float
): FolderDropSlot? {
    if (gridWindowRect == Rect.Zero) return null

    var closestSlotIndex = 0
    var closestDistance = Float.MAX_VALUE
    val spacingX = if (layout.columns > 1) cellSpacingPx else 0f
    val spacingY = if (layout.rows > 1) cellSpacingPx else 0f
    val cellWidth = (
        gridWindowRect.width - (spacingX * (layout.columns - 1).coerceAtLeast(0))
        ) / layout.columns.coerceAtLeast(1)
    val cellHeight = (
        gridWindowRect.height - (spacingY * (layout.rows - 1).coerceAtLeast(0))
        ) / layout.rows.coerceAtLeast(1)

    repeat(layout.pageSize) { slotIndex ->
        val row = slotIndex / layout.columns
        val column = slotIndex % layout.columns
        val centerX = gridWindowRect.left + (column * (cellWidth + spacingX)) + (cellWidth * 0.5f)
        val centerY = gridWindowRect.top + (row * (cellHeight + spacingY)) + (cellHeight * 0.5f)
        val dx = pointer.x - centerX
        val dy = pointer.y - centerY
        val distance = (dx * dx) + (dy * dy)

        if (distance < closestDistance) {
            closestDistance = distance
            closestSlotIndex = slotIndex
        }
    }

    return FolderDropSlot(
        page = currentPage,
        slotIndex = closestSlotIndex
    )
}

internal fun resolveFolderTargetBounds(
    density: Density,
    surfaceWidth: Dp,
    surfaceHeight: Dp,
    maxWidth: Dp,
    maxHeight: Dp,
    anchorBounds: Rect?
): Rect {
    with(density) {
        val surfaceWidthPx = surfaceWidth.toPx()
        val surfaceHeightPx = surfaceHeight.toPx()
        val maxWidthPx = maxWidth.toPx()
        val maxHeightPx = maxHeight.toPx()
        val edgeMarginPx = FOLDER_EDGE_MARGIN.toPx()

        val anchorCenter = anchorBounds?.center() ?: Offset(
            x = maxWidthPx * 0.5f,
            y = maxHeightPx * 0.5f
        )

        val unclampedLeft = anchorCenter.x - (surfaceWidthPx * 0.5f)
        val unclampedTop = anchorCenter.y - (surfaceHeightPx * 0.5f)
        val left = unclampedLeft.coerceIn(
            minimumValue = edgeMarginPx,
            maximumValue = (maxWidthPx - surfaceWidthPx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        )
        val top = unclampedTop.coerceIn(
            minimumValue = edgeMarginPx,
            maximumValue = (maxHeightPx - surfaceHeightPx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        )

        return Rect(
            left = left,
            top = top,
            right = left + surfaceWidthPx,
            bottom = top + surfaceHeightPx
        )
    }
}

internal fun resolveAutoPageTarget(
    pointer: Offset,
    popupBounds: Rect,
    currentPage: Int,
    pageCount: Int,
    edgeThresholdPx: Float
): Int? {
    if (pageCount <= 1 || popupBounds == Rect.Zero) return null

    return when {
        pointer.x <= popupBounds.left + edgeThresholdPx && currentPage > 0 -> currentPage - 1
        pointer.x >= popupBounds.right - edgeThresholdPx && currentPage < pageCount - 1 -> currentPage + 1
        else -> null
    }
}

internal val FOLDER_BASE_CELL_WIDTH = 80.dp
internal val FOLDER_BASE_CELL_HEIGHT = 96.dp
internal val FOLDER_CELL_SPACING = Spacing.small
internal val FOLDER_HORIZONTAL_PADDING = Spacing.medium
internal val FOLDER_VERTICAL_PADDING = Spacing.medium
internal val FOLDER_HEADER_HEIGHT = 44.dp
internal val FOLDER_HEADER_BOTTOM_SPACING = Spacing.medium
internal val FOLDER_INDICATOR_TOP_SPACING = Spacing.medium
internal val FOLDER_INDICATOR_HEIGHT = 8.dp
internal val FOLDER_EDGE_MARGIN = Spacing.mediumLarge
internal val FOLDER_AUTO_PAGE_EDGE_THRESHOLD = 52.dp
