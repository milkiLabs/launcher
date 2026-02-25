/**
 * DraggablePinnedItemsGrid.kt - Free placement grid for home screen icons
 *
 * This component provides a grid where users can place icons at any cell position
 * and drag them to rearrange. Unlike a traditional reorderable list, this allows
 * for true launcher-style behavior with gaps between icons.
 *
 * FEATURES:
 * - Free placement: Icons can be placed at any grid cell
 * - Drag and drop: Long-press to start dragging, release to drop
 * - Position swapping: Dropping on an occupied cell swaps the two items
 * - Grid snapping: Icons snap to the nearest grid cell when dropped
 *
 * INTERACTION MODEL:
 * 1. Tap: Opens/launches the item
 * 2. Long press (without movement): Shows the action menu
 * 3. Long press + drag: Enters drag mode, icon follows finger
 * 4. Release during drag: Drops icon at current position
 *
 * GRID LAYOUT:
 * - 4 columns (configurable)
 * - Rows are calculated based on the highest item position
 * - Each cell is sized proportionally based on screen width
 *
 * IMPLEMENTATION DETAILS:
 * This component uses Compose's gesture detection system to handle:
 * - Tap gestures for launching items
 * - Long press gestures for showing menus or starting drag
 * - Drag gestures for moving items
 *
 * The key challenge is distinguishing between:
 * - A long press that should show a menu
 * - A long press followed by movement that should start drag
 *
 * We solve this by using a custom gesture detector that tracks whether
 * movement occurred during the long press.
 */

package com.milki.launcher.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * DraggablePinnedItemsGrid - A grid where icons can be placed freely and dragged.
 *
 * This is the main component for the launcher home screen. It displays pinned items
 * at their grid positions and allows users to drag them to new locations.
 *
 * DRAG BEHAVIOR:
 * - Long press starts drag mode after a delay
 * - The dragged item follows the finger with a slight scale-up effect
 * - Other items remain in place until the dragged item is dropped
 * - If dropped on an occupied cell, the items swap positions
 *
 * LONG-PRESS VS DRAG:
 * We distinguish between a "menu long-press" and a "drag start" by:
 * 1. On long-press, we wait to see if the user moves their finger
 * 2. If they move while still pressed (beyond a threshold), we start dragging
 * 3. If they release without significant movement, we show the menu
 *
 * @param items List of pinned items to display, each with a grid position
 * @param columns Number of columns in the grid (default: 4)
 * @param onItemClick Called when user taps an item (not drag)
 * @param onItemLongPress Called when user long-presses without dragging (for menu)
 * @param onItemMove Called when user drags an item to a new position
 * @param modifier Optional modifier for external customization
 */
@Composable
fun DraggablePinnedItemsGrid(
    items: List<HomeItem>,
    columns: Int = 4,
    onItemClick: (HomeItem) -> Unit,
    onItemLongPress: (HomeItem) -> Unit,
    onItemMove: (itemId: String, newPosition: GridPosition) -> Unit,
    modifier: Modifier = Modifier
) {
    /**
     * Haptic feedback controller for providing tactile responses to user actions.
     * 
     * HAPTIC FEEDBACK EVENTS:
     * - LongPress: When the user long-presses an item (triggers menu or drag preparation)
     * - GestureThresholdActivate: When drag operation actually starts (finger moved beyond threshold)
     * - Confirm: When item is successfully dropped in a new position
     * 
     * These haptic feedbacks make the interface feel more responsive and provide
     * confirmation to the user that their action was recognized.
     */
    val hapticFeedback = LocalHapticFeedback.current

    // ========================================================================
    // STATE FOR DRAG AND DROP
    // ========================================================================

    /**
     * The item currently being dragged, if any.
     * When null, no drag is in progress.
     */
    var draggedItem by remember { mutableStateOf<HomeItem?>(null) }

    /**
     * The current drag offset in pixels from the original touch point.
     * Updated continuously as the user moves their finger during drag.
     */
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    /**
     * The starting grid position of the dragged item.
     * Used to calculate the drop target and for potential cancellation.
     */
    var dragStartPosition by remember { mutableStateOf(GridPosition.DEFAULT) }

    /**
     * The size of each grid cell in pixels.
     * Calculated from the screen width divided by number of columns.
     */
    var cellSizePx by remember { mutableStateOf(IntSize.Zero) }

    /**
     * The position of the grid within the root layout.
     * Used to convert screen coordinates to grid coordinates.
     */
    var gridOffset by remember { mutableStateOf(Offset.Zero) }

    /**
     * Flag to track if a drag has actually started (movement beyond threshold).
     * Used to distinguish between "show menu" and "start drag" long-press actions.
     */
    var hasDragStarted by remember { mutableStateOf(false) }

    /**
     * The item whose menu is currently shown.
     * When null, no menu is visible.
     */
    var menuShownForItem by remember { mutableStateOf<HomeItem?>(null) }

    // ========================================================================
    // CALCULATE GRID DIMENSIONS
    // ========================================================================

    /**
     * Calculate the number of rows needed.
     * We need at least enough rows for the item with the highest row index.
     * Also add extra rows for visual padding and potential new item placement.
     */
    val maxRow = items.maxOfOrNull { it.position.row } ?: 0
    val rows = maxRow + 4 // Extra rows for visual breathing room and new items

    // ========================================================================
    // EMPTY STATE
    // ========================================================================

    if (items.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Tap to search",
                color = Color.White.copy(alpha = 0.3f),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    // ========================================================================
    // MAIN GRID LAYOUT
    // ========================================================================

    /**
     * We use BoxWithConstraints to get the available width and calculate cell size.
     * Each cell is 1/columns of the width, maintaining a square aspect ratio.
     */
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                // Store the grid's position for coordinate conversion
                gridOffset = coordinates.positionInRoot()
            }
    ) {
        // Calculate cell dimensions based on available width
        // Using LocalDensity to convert Dp to pixels
        val cellWidthPx = with(LocalDensity.current) { maxWidth.toPx() / columns }
        val cellHeightPx = cellWidthPx // Square cells

        // Update cell size state for use in gesture handlers
        cellSizePx = IntSize(cellWidthPx.roundToInt(), cellHeightPx.roundToInt())

        // ====================================================================
        // RENDER ITEMS AT THEIR GRID POSITIONS
        // ====================================================================

        items.forEach { item ->
            /**
             * Determine if this item is currently being dragged.
             * Dragged items are rendered with special effects (scaled up, semi-transparent).
             */
            val isBeingDragged = draggedItem?.id == item.id

            /**
             * Animation values for the drag effect.
             * The dragged item scales up slightly and becomes semi-transparent.
             */
            val scale by animateFloatAsState(
                targetValue = if (isBeingDragged) 1.15f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "itemScale"
            )

            val alpha by animateFloatAsState(
                targetValue = if (isBeingDragged) 0.6f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "itemAlpha"
            )

            /**
             * Calculate the item's visual position.
             * For dragged items, this is the starting position (the item stays in place
             * while a visual copy follows the finger).
             */
            val basePosition = if (isBeingDragged) dragStartPosition else item.position

            /**
             * Render the item at its grid position.
             * We use a Box with offset for absolute positioning within the grid.
             */
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
                    .zIndex(if (isBeingDragged) 10f else 0f)
                    .graphicsLayer {
                        // Apply scale and alpha effects for dragged item
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .pointerInput(item, items, columns, rows, cellWidthPx, cellHeightPx) {
                        /**
                         * Handle all touch gestures for this item.
                         * This includes tap, long-press, and drag.
                         */
                        detectDragOrTapGesture(
                            onTap = {
                                // Only handle tap if not in drag mode
                                if (draggedItem == null) {
                                    onItemClick(item)
                                }
                            },
                            onLongPress = {
                                // Long press without drag movement - show menu
                                // Provide haptic feedback for long-press recognition
                                // This gives the user tactile confirmation that the long-press was detected
                                if (!hasDragStarted) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuShownForItem = item
                                }
                            },
                            onDragStart = {
                                // User has moved finger after long press - start drag
                                // Provide haptic feedback to confirm drag mode has started
                                // This is a different haptic than long-press to distinguish the two states
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                draggedItem = item
                                dragStartPosition = item.position
                                dragOffset = Offset.Zero
                                hasDragStarted = true
                                menuShownForItem = null // Hide menu if shown
                            },
                            onDrag = { change, dragAmount ->
                                // Update drag offset as user moves finger
                                if (draggedItem?.id == item.id) {
                                    change.consume()
                                    dragOffset += dragAmount
                                }
                            },
                            onDragEnd = {
                                // User released finger - calculate drop position
                                if (draggedItem?.id == item.id && hasDragStarted) {
                                    // Calculate the target grid cell from drag offset
                                    val targetColumn = (dragStartPosition.column + (dragOffset.x / cellWidthPx)).roundToInt()
                                    val targetRow = (dragStartPosition.row + (dragOffset.y / cellHeightPx)).roundToInt()

                                    // Clamp to valid grid bounds
                                    val clampedColumn = targetColumn.coerceIn(0, columns - 1)
                                    val clampedRow = targetRow.coerceIn(0, rows - 1)

                                    val newPosition = GridPosition(clampedRow, clampedColumn)

                                    // Only move if position changed
                                    if (newPosition != dragStartPosition) {
                                        // Provide haptic feedback to confirm successful drop
                                        // This confirms to the user that their drag-and-drop was completed
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onItemMove(item.id, newPosition)
                                    }
                                }

                                // Reset drag state
                                draggedItem = null
                                dragOffset = Offset.Zero
                                hasDragStarted = false
                            },
                            onDragCancel = {
                                // User cancelled drag (e.g., moved finger out of bounds)
                                // Reset state without moving item
                                draggedItem = null
                                dragOffset = Offset.Zero
                                hasDragStarted = false
                            }
                        )
                    }
            ) {
                /**
                 * Render the item content using PinnedItem.
                 * We pass handleLongPress = false because gestures are handled by the parent.
                 * The menu visibility is controlled by menuShownForItem state.
                 */
                PinnedItem(
                    item = item,
                    onClick = { /* Handled by parent gesture detector */ },
                    onLongClick = { /* Handled by parent gesture detector */ },
                    handleLongPress = false,
                    showMenu = menuShownForItem?.id == item.id,
                    onMenuDismiss = { menuShownForItem = null }
                )
            }
        }

        // ====================================================================
        // DRAG PREVIEW (FOLLOWS FINGER)
        // ====================================================================

        /**
         * When dragging, we render a preview of the item that follows the finger.
         * This provides visual feedback during the drag operation.
         */
        draggedItem?.let { item ->
            if (hasDragStarted) {
                /**
                 * Calculate the preview position based on:
                 * 1. Starting grid position (converted to pixels)
                 * 2. Drag offset (how far the finger has moved)
                 */
                val previewX = (dragStartPosition.column * cellWidthPx) + dragOffset.x
                val previewY = (dragStartPosition.row * cellHeightPx) + dragOffset.y

                /**
                 * Calculate the target grid cell for visual feedback.
                 * This shows where the item will land if dropped now.
                 */
                val targetColumn = (dragStartPosition.column + (dragOffset.x / cellWidthPx)).roundToInt()
                    .coerceIn(0, columns - 1)
                val targetRow = (dragStartPosition.row + (dragOffset.y / cellHeightPx)).roundToInt()
                    .coerceIn(0, rows - 1)

                // Render drop target highlight (semi-transparent placeholder)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (targetColumn * cellWidthPx).roundToInt(),
                                y = (targetRow * cellHeightPx).roundToInt()
                            )
                        }
                        .size(with(LocalDensity.current) { cellWidthPx.toDp() })
                        .padding(Spacing.smallMedium)
                        .alpha(0.3f)
                        .graphicsLayer {
                            // Highlight effect for drop target
                            scaleX = 0.9f
                            scaleY = 0.9f
                        }
                ) {
                    PinnedItem(
                        item = item,
                        onClick = {},
                        onLongClick = {},
                        handleLongPress = false
                    )
                }

                // Render the dragging item preview (follows finger)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = previewX.roundToInt() - (cellWidthPx / 2).roundToInt() + cellWidthPx.roundToInt() / 2,
                                y = previewY.roundToInt() - (cellHeightPx / 2).roundToInt() + cellHeightPx.roundToInt() / 2
                            )
                        }
                        .size(with(LocalDensity.current) { cellWidthPx.toDp() })
                        .padding(Spacing.smallMedium)
                        .zIndex(100f)
                        .graphicsLayer {
                            // Dragging item is slightly larger and has shadow effect
                            scaleX = 1.2f
                            scaleY = 1.2f
                            alpha = 0.9f
                            shadowElevation = 8f
                        }
                ) {
                    PinnedItem(
                        item = item,
                        onClick = {},
                        onLongClick = {},
                        handleLongPress = false
                    )
                }
            }
        }
    }
}

// ============================================================================
// CUSTOM GESTURE DETECTOR
// ============================================================================

/**
 * Custom gesture detector that handles tap, long-press, and drag gestures.
 *
 * This detector distinguishes between:
 * - A simple tap
 * - A long-press without movement (shows menu)
 * - A long-press followed by drag (moves item)
 *
 * INTERACTION MODEL (more natural feel):
 * 1. Long-press immediately shows the dropdown menu
 * 2. If user starts moving (beyond threshold), close menu and start drag
 * 3. If user releases without moving, menu stays open
 *
 * @param onTap Called when user taps without long-press
 * @param onLongPress Called immediately when long-press is detected (show menu)
 * @param onDragStart Called when drag starts (movement exceeds threshold)
 * @param onDrag Called continuously during drag
 * @param onDragEnd Called when drag ends successfully
 * @param onDragCancel Called when drag is cancelled
 */
private suspend fun PointerInputScope.detectDragOrTapGesture(
    onTap: () -> Unit,
    onLongPress: (Offset) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val dragThreshold = 20f

    awaitEachGesture {
        // FIX: Wait for the first down event to identify the pointer
        val down = awaitFirstDown()

        // FIX: Pass the pointerId to awaitLongPressOrCancellation
        val longPress = awaitLongPressOrCancellation(down.id)

        if (longPress == null) {
            // No long-press detected - this was a tap
            onTap()
            return@awaitEachGesture
        }

        /**
         * Long-press detected - immediately show the menu.
         * This gives instant feedback to the user.
         */
        onLongPress(longPress.position)

        /**
         * Now track subsequent movement to determine if this becomes a drag.
         */
        var totalDrag = Offset.Zero
        var dragStarted = false

        /**
         * Continue tracking finger movement.
         * The drag function will continue until the finger is lifted.
         */
        try {
            drag(pointerId = longPress.id) { change ->
                // Calculate drag amount from position change
                val dragAmount = change.position - change.previousPosition
                
                // Accumulate total drag distance
                totalDrag += dragAmount

                // Check if we've crossed the drag threshold
                if (!dragStarted && (abs(totalDrag.x) > dragThreshold || abs(totalDrag.y) > dragThreshold)) {
                    // Threshold exceeded - start drag mode
                    dragStarted = true
                    onDragStart()
                }

                // If drag has started, notify parent of movement
                if (dragStarted) {
                    onDrag(change, dragAmount)
                }
            }

            // Finger lifted - complete the gesture
            if (dragStarted) {
                onDragEnd()
            }
            // If no drag happened, the menu stays open (already shown)
        } catch (e: Exception) {
            // Gesture was cancelled (e.g., another touch event)
            if (dragStarted) {
                onDragCancel()
            }
        }
    }
}
