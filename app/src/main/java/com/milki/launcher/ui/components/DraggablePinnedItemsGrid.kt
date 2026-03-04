/**
 * DraggablePinnedItemsGrid.kt - Home grid that uses the shared drag-drop core
 *
 * This component renders pinned items in a free-placement grid and delegates drag
 * state handling to the reusable controller in ui/components/dragdrop.
 *
 * WHY THIS VERSION IS MORE REUSABLE:
 * 1) Gesture wiring is delegated to detectDragGesture().
 * 2) Drag lifecycle (start/update/end/cancel) lives in AppDragDropController.
 * 3) Grid math (clamp, target resolution, offset bounds) lives in metrics data.
 *
 * This means other launcher surfaces can reuse the same primitives without
 * duplicating complex pointer + state logic.
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.components.dragdrop.AppDragDropResult
import com.milki.launcher.ui.components.dragdrop.AppExternalDropTargetOverlay
import com.milki.launcher.ui.components.dragdrop.ExternalDragDropItem
import com.milki.launcher.ui.components.dragdrop.ExternalDragPayloadCodec.ExternalDragItem
import com.milki.launcher.ui.components.dragdrop.rememberAppDragDropController
import com.milki.launcher.ui.components.grid.GridConfig
import com.milki.launcher.ui.components.grid.animateDragVisuals
import com.milki.launcher.ui.components.grid.detectDragGesture
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * DraggablePinnedItemsGrid - Grid that supports long-press menu and drag-reorder.
 *
 * @param items Current pinned home items.
 * @param config Grid behavior and visual configuration.
 * @param onItemClick Called when user taps an item.
 * @param onItemLongPress Called when user long-presses without dragging.
 * @param onItemMove Called when user drops an item into a new cell.
 * @param onEmptyAreaLongPress Called when user long-presses an unoccupied area of the grid.
 *                              Provides the local touch position so callers can anchor menus.
 * @param onItemDroppedToHome Called when an external drag payload is dropped into the grid.
 * @param modifier Optional modifier for parent layout.
 */
@Composable
fun DraggablePinnedItemsGrid(
    items: List<HomeItem>,
    config: GridConfig = GridConfig.Default,
    onItemClick: (HomeItem) -> Unit,
    onItemLongPress: (HomeItem) -> Unit,
    onItemMove: (itemId: String, newPosition: GridPosition) -> Unit,
    onEmptyAreaLongPress: (Offset) -> Unit = {},
    onItemDroppedToHome: (item: HomeItem, position: GridPosition) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val dragController = rememberAppDragDropController<HomeItem>(config)

    var menuShownForItemId by remember { mutableStateOf<String?>(null) }

    /**
     * Tracks whether a long-press gesture is actively in progress (finger still
     * down after long-press detected). While true, the dropdown menu popup is
     * rendered with focusable=false so it doesn't steal touch events from the
     * gesture detector — allowing drag detection to continue working even when
     * the popup visually covers the finger position.
     */
    var isMenuGestureActive by remember { mutableStateOf(false) }
    var externalDragTargetPosition by remember { mutableStateOf<GridPosition?>(null) }
    var isExternalDragActive by remember { mutableStateOf(false) }
    var externalDragItem by remember { mutableStateOf<ExternalDragDropItem?>(null) }

    /**
     * Stable synthetic home item used only for target highlight visuals.
     *
     * WHY SYNTHETIC ITEM:
     * PinnedItem already defines the launcher's visual language for icons.
     * Reusing it keeps external-drop highlight behavior consistent with
     * internal drag preview/highlight without introducing duplicate UI code.
     */
    val externalDragPreviewItem by remember(externalDragItem) {
        derivedStateOf {
            externalDragItem?.toPreviewHomeItem()
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(items, cellWidthPx, cellHeightPx, config.columns, maxVisibleRows) {
                    detectTapGestures(
                        onLongPress = { longPressOffset ->
                            if (isExternalDragActive) return@detectTapGestures
                            if (dragController.session != null) return@detectTapGestures

                            val pressedCell = layoutMetrics.pixelToCell(longPressOffset)
                            val isCellOccupied = items.any { it.position == pressedCell }

                            if (!isCellOccupied) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onEmptyAreaLongPress(longPressOffset)
                            }
                        }
                    )
                }
        ) {
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap to search",
                    color = Color.White.copy(alpha = 0.3f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        items.forEach { item ->
            key(item.id, item.position.row, item.position.column) {
                val isBeingDragged = dragController.isDraggingItem(item.id)
                val visuals = animateDragVisuals(isBeingDragged, config)
                val basePosition = dragController.resolveBasePosition(item.id, item.position)

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (basePosition.column * cellWidthPx).roundToInt(),
                                y = (basePosition.row * cellHeightPx).roundToInt()
                            )
                        }
                        .size(with(LocalDensity.current) { cellWidthPx.toDp() })
                        .padding(Spacing.smallMedium)
                        .zIndex(visuals.zIndex)
                        .graphicsLayer {
                            scaleX = visuals.scale
                            scaleY = visuals.scale
                            alpha = visuals.alpha
                        }
                        .detectDragGesture(
                            key = "${item.id}-${item.position.row}-${item.position.column}",
                            dragThreshold = config.dragThresholdPx,
                            onTap = {
                                if (dragController.session == null) {
                                    onItemClick(item)
                                }
                            },
                            onLongPress = {
                                if (dragController.session == null) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuShownForItemId = item.id
                                    isMenuGestureActive = true
                                    onItemLongPress(item)
                                }
                            },
                            onLongPressRelease = {
                                /**
                                 * Finger lifted after long-press without dragging.
                                 * Switch the menu popup to focusable so items become
                                 * tappable.
                                 */
                                isMenuGestureActive = false
                            },
                            onDragStart = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                menuShownForItemId = null
                                isMenuGestureActive = false
                                dragController.startDrag(
                                    item = item,
                                    itemId = item.id,
                                    startPosition = item.position
                                )
                            },
                            onDrag = { change, dragAmount ->
                                if (dragController.isDraggingItem(item.id)) {
                                    change.consume()
                                    dragController.updateDrag(dragAmount, layoutMetrics)
                                }
                            },
                            onDragEnd = {
                                val result = dragController.endDrag(layoutMetrics)
                                if (result is AppDragDropResult.Moved && result.itemId == item.id) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onItemMove(result.itemId, result.to)
                                }
                            },
                            onDragCancel = {
                                dragController.cancelDrag()
                                isMenuGestureActive = false
                            }
                        )
                ) {
                    PinnedItem(
                        item = item,
                        onClick = {},
                        onLongClick = {},
                        handleLongPress = false,
                        showMenu = menuShownForItemId == item.id,
                        onMenuDismiss = { menuShownForItemId = null; isMenuGestureActive = false },
                        menuFocusable = !isMenuGestureActive
                    )
                }
            }
        }

        dragController.session?.let { activeSession ->
            val target = dragController.targetPosition ?: activeSession.startPosition
            val previewBaseOffset = layoutMetrics.cellToPixel(activeSession.startPosition)
            val previewOffset = previewBaseOffset + activeSession.currentOffset

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (target.column * cellWidthPx).roundToInt(),
                            y = (target.row * cellHeightPx).roundToInt()
                        )
                    }
                    .size(with(LocalDensity.current) { cellWidthPx.toDp() })
                    .padding(Spacing.smallMedium)
                    .alpha(config.dropHighlightAlpha)
                    .graphicsLayer {
                        scaleX = config.dropHighlightScale
                        scaleY = config.dropHighlightScale
                    }
            ) {
                PinnedItem(
                    item = activeSession.item,
                    onClick = {},
                    onLongClick = {},
                    handleLongPress = false
                )
            }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = previewOffset.x.roundToInt(),
                            y = previewOffset.y.roundToInt()
                        )
                    }
                    .size(with(LocalDensity.current) { cellWidthPx.toDp() })
                    .padding(Spacing.smallMedium)
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

        /**
         * External drag highlight — shows where the dragged app will land.
         *
         * This block is only rendered while an external drag (from the search
         * dialog) is active over the home grid. It draws:
         * 1. A rounded, semi-transparent background "glow" to mark the cell.
         * 2. The app icon preview (when the payload is known) so the user
         *    knows exactly which app will be pinned in that cell.
         *
         * WHY SHADOW + BACKGROUND:
         * The platform drag shadow follows the finger, but the user also
         * needs to know which *cell* will receive the drop. A subtle
         * background highlight plus an icon preview makes the target
         * unambiguous, especially on busy wallpapers.
         */
        if (isExternalDragActive) {
            externalDragTargetPosition?.let { targetPosition ->
                val highlightShape = RoundedCornerShape(CornerRadius.medium)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (targetPosition.column * cellWidthPx).roundToInt(),
                                y = (targetPosition.row * cellHeightPx).roundToInt()
                            )
                        }
                        .size(with(LocalDensity.current) { cellWidthPx.toDp() })
                        .padding(Spacing.smallMedium)
                        .zIndex(config.dragZIndex)
                        .shadow(
                            elevation = Spacing.smallMedium,
                            shape = highlightShape,
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = highlightShape
                        )
                        .border(
                            width = Spacing.extraSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            shape = highlightShape
                        )
                        .graphicsLayer {
                            scaleX = config.dropHighlightScale
                            scaleY = config.dropHighlightScale
                        }
                ) {
                    /**
                     * External target highlight mirrors internal drag style:
                     * - If payload app is known: show icon-shaped preview via PinnedItem
                     * - If payload is temporarily unavailable: the background/border
                     *   alone serves as a sufficient drop zone indicator.
                     */
                    val previewItem = externalDragPreviewItem
                    if (previewItem != null) {
                        Box(modifier = Modifier.alpha(config.dropHighlightAlpha)) {
                            PinnedItem(
                                item = previewItem,
                                onClick = {},
                                onLongClick = {},
                                handleLongPress = false
                            )
                        }
                    }
                }
            }
        }

        AppExternalDropTargetOverlay(
            onDragStarted = {
                isExternalDragActive = true
                externalDragTargetPosition = null
                externalDragItem = null
            },
            onDragMoved = { localOffset, item ->
                externalDragTargetPosition = layoutMetrics.pixelToCell(localOffset)
                if (item != null) {
                    externalDragItem = item
                }
            },
            onDragEnded = {
                isExternalDragActive = false
                externalDragTargetPosition = null
                externalDragItem = null
            },
            onItemDropped = { item, localOffset ->
                /**
                 * IMPORTANT DROP-TARGET RULE:
                 * Prefer the last hovered target position when available.
                 *
                 * WHY:
                 * During cross-window drag (dialog -> activity), some Android
                 * variants can report less-stable ACTION_DROP coordinates than
                 * ACTION_DRAG_LOCATION. The hover highlight is driven by the
                 * location stream, so using that as primary source ensures the
                 * final pinned position matches what user saw highlighted.
                 *
                 * Fallback to ACTION_DROP coordinates when no hover sample
                 * exists (for extremely quick drop interactions).
                 */
                val dropPosition = if (isExternalDragActive && externalDragTargetPosition != null) {
                    externalDragTargetPosition!!
                } else {
                    layoutMetrics.pixelToCell(localOffset)
                }

                externalDragTargetPosition = dropPosition
                externalDragItem = item
                val homeItem = item.toPreviewHomeItem() ?: return@AppExternalDropTargetOverlay false
                onItemDroppedToHome(homeItem, dropPosition)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                true
            },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(config.previewZIndex + 1f)
        )
        }
    }
}

/**
 * Maps an external drag payload to the equivalent HomeItem preview/persisted model.
 */
private fun ExternalDragDropItem.toPreviewHomeItem(): HomeItem? {
    return when (this) {
        is ExternalDragItem.App -> HomeItem.PinnedApp.fromAppInfo(appInfo)
        is ExternalDragItem.File -> HomeItem.PinnedFile.fromFileDocument(fileDocument)
        is ExternalDragItem.Contact -> HomeItem.PinnedContact.fromContact(contact)
    }
}
