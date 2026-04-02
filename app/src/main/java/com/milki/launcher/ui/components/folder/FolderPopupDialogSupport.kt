package com.milki.launcher.ui.components.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.launcher.ItemActionMenu
import com.milki.launcher.ui.components.launcher.MenuAction
import com.milki.launcher.ui.components.launcher.PinnedItem
import com.milki.launcher.ui.components.grid.detectDragGesture
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.Spacing

@Composable
internal fun FolderNameHeader(
    name: String,
    isEditing: Boolean,
    focusRequester: FocusRequester,
    itemCount: Int,
    onNameChange: (String) -> Unit,
    onEditingChanged: (Boolean) -> Unit,
    onEditRequested: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onEditRequested
                )
        ) {
            BasicTextField(
                value = name,
                onValueChange = onNameChange,
                textStyle = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        onEditingChanged(focusState.isFocused)
                    },
                readOnly = !isEditing
            )
        }

        Spacer(modifier = Modifier.height(Spacing.extraSmall))
        Text(
            text = if (itemCount == 1) "1 item" else "$itemCount items",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )

        if (isEditing) {
            Spacer(modifier = Modifier.height(Spacing.extraSmall))
            Box(
                modifier = Modifier
                    .widthIn(max = 140.dp)
                    .fillMaxWidth(0.45f)
                    .height(Spacing.hairline)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
            )
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
internal fun FolderGridPage(
    page: Int,
    layout: FolderGridLayout,
    localChildren: List<HomeItem>,
    cellWidth: Dp,
    cellHeight: Dp,
    cellSpacing: Dp,
    draggedItemId: String?,
    hoveredSlot: FolderDropSlot?,
    menuShownForItemId: String?,
    onGridBoundsMeasured: (Rect) -> Unit,
    onMenuDismiss: () -> Unit,
    onTap: (HomeItem) -> Unit,
    onLongPress: (String) -> Unit,
    onRemoveFromFolder: (String) -> Unit,
    onWindowPositionMeasured: (String, Offset) -> Unit,
    onDragStart: (HomeItem) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val pageStart = page * layout.pageSize
    val pageItems = localChildren.drop(pageStart).take(layout.pageSize)
    val slots = List(layout.pageSize) { slotIndex ->
        pageItems.getOrNull(slotIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                onGridBoundsMeasured(coords.windowRect())
            }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(cellSpacing)
        ) {
            repeat(layout.rows) { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(cellSpacing)
                ) {
                    repeat(layout.columns) { column ->
                        val slotIndex = (row * layout.columns) + column
                        val item = slots.getOrNull(slotIndex)

                        Box(
                            modifier = Modifier
                                .size(width = cellWidth, height = cellHeight)
                                .then(
                                    if (hoveredSlot == FolderDropSlot(page, slotIndex)) {
                                        Modifier
                                            .clip(RoundedCornerShape(CornerRadius.medium))
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            )
                                            .border(
                                                width = Spacing.hairline,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                                shape = RoundedCornerShape(CornerRadius.medium)
                                            )
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            if (item != null) {
                                FolderPopupItem(
                                    item = item,
                                    isDragged = item.id == draggedItemId,
                                    showMenu = menuShownForItemId == item.id,
                                    onMenuDismiss = onMenuDismiss,
                                    onTap = { onTap(item) },
                                    onLongPress = { onLongPress(item.id) },
                                    onRemoveFromFolder = { onRemoveFromFolder(item.id) },
                                    onWindowPositionMeasured = { offset ->
                                        onWindowPositionMeasured(item.id, offset)
                                    },
                                    onDragStart = { onDragStart(item) },
                                    onDragDelta = onDragDelta,
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FolderPagerIndicator(
    pageCount: Int,
    currentPage: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.smallMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { page ->
            val isSelected = page == currentPage
            Box(
                modifier = Modifier
                    .size(
                        width = if (isSelected) 18.dp else 8.dp,
                        height = 8.dp
                    )
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun FolderPopupItem(
    item: HomeItem,
    isDragged: Boolean,
    showMenu: Boolean,
    onMenuDismiss: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onRemoveFromFolder: () -> Unit,
    onWindowPositionMeasured: (Offset) -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLongPressGestureActive by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                onWindowPositionMeasured(coords.localToWindow(Offset.Zero))
            }
            .alpha(if (isDragged) FOLDER_DRAG_GHOST_ALPHA else 1f)
            .detectDragGesture(
                key = item.id,
                dragThreshold = FOLDER_DRAG_THRESHOLD_PX,
                onTap = { onTap() },
                onLongPress = {
                    isLongPressGestureActive = true
                    onLongPress()
                },
                onLongPressRelease = {
                    isLongPressGestureActive = false
                },
                onDragStart = {
                    isLongPressGestureActive = false
                    onDragStart()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount)
                },
                onDragEnd = onDragEnd,
                onDragCancel = {
                    isLongPressGestureActive = false
                    onDragCancel()
                }
            )
    ) {
        PinnedItem(
            item = item,
            onClick = {},
            onLongClick = {},
            handleLongPress = false,
            showMenu = false
        )

        ItemActionMenu(
            expanded = showMenu,
            onDismiss = onMenuDismiss,
            focusable = !isLongPressGestureActive,
            actions = listOf(
                MenuAction(
                    label = "Remove from folder",
                    icon = Icons.Filled.Delete,
                    onClick = onRemoveFromFolder,
                    isDestructive = true
                )
            )
        )
    }
}

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

internal fun LayoutCoordinates.windowRect(): Rect {
    val topLeft = localToWindow(Offset.Zero)
    return Rect(
        left = topLeft.x,
        top = topLeft.y,
        right = topLeft.x + size.width,
        bottom = topLeft.y + size.height
    )
}

internal fun Rect.center(): Offset {
    return Offset(
        x = (left + right) * 0.5f,
        y = (top + bottom) * 0.5f
    )
}

internal fun lerp(start: Float, end: Float, progress: Float): Float {
    return start + ((end - start) * progress)
}

internal val FOLDER_BASE_CELL_WIDTH = 108.dp
internal val FOLDER_BASE_CELL_HEIGHT = 112.dp
internal val FOLDER_CELL_SPACING = Spacing.smallMedium
internal val FOLDER_HORIZONTAL_PADDING = Spacing.mediumLarge
internal val FOLDER_VERTICAL_PADDING = Spacing.mediumLarge
internal val FOLDER_HEADER_HEIGHT = 44.dp
internal val FOLDER_HEADER_BOTTOM_SPACING = Spacing.medium
internal val FOLDER_INDICATOR_TOP_SPACING = Spacing.medium
internal val FOLDER_INDICATOR_HEIGHT = 8.dp
internal val FOLDER_EDGE_MARGIN = Spacing.mediumLarge
internal val FOLDER_AUTO_PAGE_EDGE_THRESHOLD = 52.dp

internal const val FOLDER_DRAG_THRESHOLD_PX = 20f
internal const val FOLDER_DRAG_GHOST_ALPHA = 0.18f
internal const val FOLDER_PREVIEW_ALPHA = 0.92f
internal const val FOLDER_DRAG_PREVIEW_Z_INDEX = 12f
internal const val FOLDER_MIN_START_SCALE = 0.28f
internal const val FOLDER_OPEN_ANIMATION_MS = 240
internal const val FOLDER_AUTO_PAGE_DELAY_MS = 170L
