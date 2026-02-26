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
 * ARCHITECTURE:
 * This component delegates to several specialized components:
 * - DragController: Manages drag state and logic
 * - GridCalculator: Handles coordinate conversions
 * - DragGestureDetector: Detects and processes gestures
 * - DragVisualEffects: Provides visual feedback animations
 *
 * This separation makes the code:
 * - More testable (each component can be tested in isolation)
 * - More reusable (components can be used in other contexts)
 * - More maintainable (changes are localized to specific components)
 *
 * GRID LAYOUT:
 * - 4 columns (configurable via GridConfig)
 * - Rows are calculated based on the highest item position
 * - Each cell is sized proportionally based on screen width
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.grid.GridConfig
import com.milki.launcher.ui.components.grid.animateDragVisuals
import com.milki.launcher.ui.components.grid.detectDragGesture
import com.milki.launcher.ui.theme.Spacing
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
 * @param config Grid configuration (columns, thresholds, visual effects)
 * @param onItemClick Called when user taps an item (not drag)
 * @param onItemLongPress Called when user long-presses without dragging (for menu)
 * @param onItemMove Called when user drags an item to a new position
 * @param modifier Optional modifier for external customization
 */
@Composable
fun DraggablePinnedItemsGrid(
    items: List<HomeItem>,
    config: GridConfig = GridConfig.Default,
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
     * Derived state for the dragged item's ID.
     * 
     * PERFORMANCE OPTIMIZATION:
     * Using derivedStateOf prevents unnecessary recompositions of ALL items
     * when draggedItem changes. Only items that need to check their drag status
     * will recompose.
     * 
     * Without this, when draggedItem changes (during drag start/end), every item
     * in the grid would recompose to evaluate `draggedItem?.id == item.id`.
     * With derivedStateOf, Compose can optimize the recomposition scope.
     */
    val draggedItemId by remember { derivedStateOf { draggedItem?.id } }

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

    /**
     * Flag to prevent race conditions between drag end and drag cancel.
     * Set to true when drag ends, reset when state is cleared.
     */
    var isDragEnding by remember { mutableStateOf(false) }

    // ========================================================================
    // CALCULATE GRID DIMENSIONS
    // ========================================================================

    /**
     * Calculate the number of rows needed.
     * We need at least enough rows for the item with the highest row index.
     * Also add extra rows for visual padding and potential new item placement.
     */
    val maxRow = items.maxOfOrNull { it.position.row } ?: 0
    val rows = maxRow + config.extraRows

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
        val cellWidthPx = with(LocalDensity.current) { maxWidth.toPx() / config.columns }
        val cellHeightPx = cellWidthPx // Square cells
        
        /**
         * Calculate the maximum drag bounds to prevent dragging beyond visible screen.
         * The item should stay within the grid bounds during drag:
         * - Left: column 0 position
         * - Right: last column position (columns - 1)
         * - Top: row 0 position
         * - Bottom: last visible row position (based on available height)
         * 
         * This prevents the drag preview from going off-screen and ensures
         * the item always drops within valid grid bounds.
         */
        val gridHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val maxVisibleRows = (gridHeightPx / cellHeightPx).toInt().coerceAtLeast(1)
        
        // Maximum drag offset from the starting position
        // For columns: can drag from column 0 to (columns-1)
        // For rows: can drag from row 0 to the last visible row
        val maxDragX = (config.columns - 1) * cellWidthPx
        val maxDragY = (maxVisibleRows - 1) * cellHeightPx

        // Update cell size state for use in gesture handlers
        cellSizePx = IntSize(cellWidthPx.roundToInt(), cellHeightPx.roundToInt())

        // ====================================================================
        // RENDER ITEMS AT THEIR GRID POSITIONS
        // ====================================================================

        items.forEach { item ->
            /**
             * PERFORMANCE OPTIMIZATION: Using key() to minimize recomposition scope.
             * 
             * The key() composable helps Compose identify which specific item changed,
             * allowing it to skip recomposition of unchanged items. This is especially
             * important during drag operations where we want only the affected items
             * to recompose, not all items in the grid.
             * 
             * The key includes both id and position to ensure the gesture detector
             * is recreated when an item moves to a new position.
             */
            key(item.id, item.position.row, item.position.column) {
                /**
                 * Determine if this item is currently being dragged.
                 * Dragged items are rendered with special effects (scaled up, semi-transparent).
                 * 
                 * PERFORMANCE: Using draggedItemId (derivedStateOf) instead of draggedItem?.id
                 * prevents all items from recomposing when draggedItem changes.
                 */
                val isBeingDragged = draggedItemId == item.id

                /**
                 * Animation values for the drag effect.
                 * The dragged item scales up slightly and becomes semi-transparent.
                 */
                val visuals = animateDragVisuals(isBeingDragged, config)

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
                        .zIndex(visuals.zIndex)
                        .graphicsLayer {
                            // Apply scale and alpha effects for dragged item
                            scaleX = visuals.scale
                            scaleY = visuals.scale
                            this.alpha = visuals.alpha
                        }
                        /**
                         * BUG FIX: Include item.position in the key.
                         * Previously used only item.id which caused the gesture detector
                         * closure to capture the OLD item with OLD position when the item
                         * was moved. By including position in the key, the gesture detector
                         * is recreated when the item moves to a new position, ensuring
                         * the closure has the correct current position.
                         */
                        .detectDragGesture(
                            key = "${item.id}-${item.position.row}-${item.position.column}",
                            dragThreshold = config.dragThresholdPx,
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
                                // BUG FIX: Check if another drag is already ending to prevent race condition
                                if (!isDragEnding) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    draggedItem = item
                                    dragStartPosition = item.position
                                    dragOffset = Offset.Zero
                                    hasDragStarted = true
                                    menuShownForItem = null // Hide menu if shown
                                }
                            },
                            onDrag = { change, dragAmount ->
                                // Update drag offset as user moves finger
                                if (draggedItem?.id == item.id && !isDragEnding) {
                                    change.consume()
                                    
                                    /**
                                     * Calculate the new drag offset by adding the drag amount.
                                     * We need to clamp this to prevent dragging beyond visible bounds.
                                     */
                                    val newOffsetX = dragOffset.x + dragAmount.x
                                    val newOffsetY = dragOffset.y + dragAmount.y
                                    
                                    /**
                                     * Calculate bounds based on the starting position.
                                     * The item can be dragged from its starting column/row to any valid position.
                                     * 
                                     * For X (columns):
                                     * - Minimum: can drag left to column 0, so min offset = -startColumn * cellWidth
                                     * - Maximum: can drag right to last column, so max offset = (columns-1-startColumn) * cellWidth
                                     * 
                                     * For Y (rows):
                                     * - Minimum: can drag up to row 0, so min offset = -startRow * cellHeight
                                     * - Maximum: can drag down to last visible row, so max offset = (maxVisibleRows-1-startRow) * cellHeight
                                     */
                                    val startColumn = dragStartPosition.column
                                    val startRow = dragStartPosition.row
                                    
                                    val minDragX = -startColumn * cellWidthPx
                                    val maxDragX = (config.columns - 1 - startColumn) * cellWidthPx
                                    val minDragY = -startRow * cellHeightPx
                                    val maxDragY = (maxVisibleRows - 1 - startRow) * cellHeightPx
                                    
                                    // Clamp the drag offset to stay within bounds
                                    dragOffset = Offset(
                                        x = newOffsetX.coerceIn(minDragX, maxDragX),
                                        y = newOffsetY.coerceIn(minDragY, maxDragY)
                                    )
                                }
                            },
                            onDragEnd = {
                                // BUG FIX: Set flag to prevent race condition with cancel
                                isDragEnding = true
                                
                                // User released finger - calculate drop position
                                if (draggedItem?.id == item.id && hasDragStarted) {
                                    // Calculate the target grid cell from drag offset
                                    val targetColumn = (dragStartPosition.column + (dragOffset.x / cellWidthPx)).roundToInt()
                                    val targetRow = (dragStartPosition.row + (dragOffset.y / cellHeightPx)).roundToInt()

                                // Clamp to valid grid bounds
                                val clampedColumn = targetColumn.coerceIn(0, config.columns - 1)
                                val clampedRow = targetRow.coerceIn(0, rows - 1)

                                val newPosition = GridPosition(clampedRow, clampedColumn)

                                // Only move if position changed
                                if (newPosition != dragStartPosition) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onItemMove(item.id, newPosition)
                                }
                            }

                            // Reset drag state
                            draggedItem = null
                            dragOffset = Offset.Zero
                            hasDragStarted = false
                            isDragEnding = false
                        },
                        onDragCancel = {
                            // BUG FIX: Check if drag is already ending
                            if (!isDragEnding) {
                                // User cancelled drag (e.g., moved finger out of bounds)
                                // Reset state without moving item
                                draggedItem = null
                                dragOffset = Offset.Zero
                                hasDragStarted = false
                            }
                        }
                    )
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
                val baseX = dragStartPosition.column * cellWidthPx
                val baseY = dragStartPosition.row * cellHeightPx
                val previewX = baseX + dragOffset.x
                val previewY = baseY + dragOffset.y

                /**
                 * Calculate the target grid cell for visual feedback.
                 * This shows where the item will land if dropped now.
                 */
                val targetColumn = (dragStartPosition.column + (dragOffset.x / cellWidthPx)).roundToInt()
                    .coerceIn(0, config.columns - 1)
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
                        .alpha(config.dropHighlightAlpha)
                        .graphicsLayer {
                            // Highlight effect for drop target
                            scaleX = config.dropHighlightScale
                            scaleY = config.dropHighlightScale
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
                /**
                 * BUG FIX: Simplified offset calculation.
                 * Previously had redundant - half + half calculation.
                 * Now just positions preview at drag position with slight centering adjustment.
                 */
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = previewX.roundToInt(),
                                y = previewY.roundToInt()
                            )
                        }
                        .size(with(LocalDensity.current) { cellWidthPx.toDp() })
                        .padding(Spacing.smallMedium)
                        .zIndex(config.previewZIndex)
                        .graphicsLayer {
                            // Dragging item is slightly larger and has shadow effect
                            scaleX = config.previewScale
                            scaleY = config.previewScale
                            alpha = config.previewAlpha
                            shadowElevation = config.shadowElevation
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
