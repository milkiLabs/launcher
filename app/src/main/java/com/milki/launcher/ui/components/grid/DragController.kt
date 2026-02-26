/**
 * DragController.kt - Centralized drag operation controller
 *
 * This file provides a stateful controller that manages drag operations.
 * It encapsulates all drag-related state and logic, providing a clean API
 * for UI components to interact with.
 *
 * WHY A CONTROLLER?
 * - Separation of concerns: UI only renders, controller handles logic
 * - Testability: Controller can be unit tested without Compose
 * - Reusability: Same controller can be used in different components
 * - State encapsulation: All drag state is in one place
 *
 * RESPONSIBILITIES:
 * - Manage drag state (start, update, end, cancel)
 * - Coordinate with DropTarget for validation
 * - Provide callbacks for UI updates
 * - Handle haptic feedback coordination
 *
 * USAGE:
 * ```kotlin
 * val dragController = rememberDragController(config, calculator)
 * 
 * // In gesture handler:
 * dragController.startDrag(item, position)
 * dragController.updateDrag(offset)
 * val result = dragController.endDrag()
 * 
 * // In UI:
 * if (dragController.state.isActive) {
 *     // Render drag preview
 * }
 * ```
 */

package com.milki.launcher.ui.components.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem

/**
 * Controller that manages drag operations for a grid.
 *
 * This class is the central point for all drag-related state and logic.
 * It provides:
 * - State management through [state] property
 * - Configuration through [config] property
 * - Calculator for coordinate conversions through [calculator] property
 *
 * THREAD SAFETY:
 * This class is designed to be used from the main thread (Compose UI thread).
 * Do not access from background threads.
 *
 * LIFECYCLE:
 * 1. Create controller with configuration and calculator
 * 2. Use startDrag() when user initiates drag
 * 3. Use updateDrag() as user moves finger
 * 4. Use endDrag() when user releases
 * 5. Use cancelDrag() if drag is interrupted
 *
 * @property config The grid configuration
 * @property calculator The grid calculator for coordinate conversions
 */
@Stable
class DragController(
    val config: GridConfig,
    val calculator: GridCalculator
) {
    /**
     * The current state of the drag operation.
     *
     * Observe this property to update UI when drag state changes.
     */
    var state: DragState by mutableStateOf(DragState.Idle)
        private set
    
    /**
     * The current drop target, if set.
     *
     * Drop targets are used to validate and handle drops.
     */
    var dropTarget: DropTarget? by mutableStateOf(null)
        internal set
    
    /**
     * The current target position during a drag.
     *
     * Updated as the user moves their finger. Used for visual feedback.
     */
    var currentTargetPosition: GridPosition by mutableStateOf(GridPosition.DEFAULT)
        private set
    
    /**
     * Flag indicating if the drag has exceeded the threshold.
     *
     * Used to distinguish between long-press (show menu) and drag.
     */
    val hasExceededThreshold: Boolean
        get() = (state as? DragState.Dragging)?.hasExceededThreshold == true
    
    /**
     * The item currently being dragged, if any.
     */
    val draggedItem: HomeItem?
        get() = state.draggedItem
    
    /**
     * Whether a drag operation is currently active.
     */
    val isActive: Boolean
        get() = state.isActive
    
    /**
     * Starts a new drag operation.
     *
     * This should be called when the user has long-pressed and started
     * moving beyond the threshold.
     *
     * @param item The item to drag
     * @param startPosition The grid position where drag starts
     */
    fun startDrag(item: HomeItem, startPosition: GridPosition) {
        state = DragState.startDrag(item, startPosition)
        currentTargetPosition = startPosition
    }
    
    /**
     * Updates the drag offset during a drag operation.
     *
     * Call this as the user moves their finger.
     *
     * @param delta The change in pixel offset since last update
     */
    fun updateDrag(delta: Offset) {
        val currentState = state as? DragState.Dragging ?: return
        
        val newState = currentState.addOffset(delta, config.dragThresholdPx)
        state = newState
        
        // Update target position for visual feedback
        currentTargetPosition = calculator.calculateTargetPosition(
            newState.startPosition,
            newState.currentOffset
        )
        
        // Notify drop target of preview
        dropTarget?.previewDrop(newState.item, currentTargetPosition)
    }
    
    /**
     * Sets the pixel offset directly (for exact positioning).
     *
     * @param offset The new pixel offset from start position
     */
    fun setDragOffset(offset: Offset) {
        val currentState = state as? DragState.Dragging ?: return
        
        val newState = currentState.withOffset(offset, config.dragThresholdPx)
        state = newState
        
        currentTargetPosition = calculator.calculateTargetPosition(
            newState.startPosition,
            newState.currentOffset
        )
    }
    
    /**
     * Ends the drag operation and returns the result.
     *
     * This should be called when the user releases their finger.
     * The returned result indicates what happened with the drop.
     *
     * @return The result of the drop operation
     */
    fun endDrag(): DropResult {
        val currentState = state as? DragState.Dragging ?: return DropResult.Cancelled
        
        val target = dropTarget
        val targetPosition = currentTargetPosition
        
        // Validate with drop target
        if (target != null && !target.canDrop(currentState.item, targetPosition)) {
            cancelDrag()
            return DropResult.Rejected("Drop target rejected the position")
        }
        
        // Transition to pending state
        state = DragState.PendingDrop(
            item = currentState.item,
            startPosition = currentState.startPosition,
            targetPosition = targetPosition
        )
        
        // Execute the drop
        val result = target?.onDrop(currentState.item, targetPosition)
            ?: DropResult.Success(targetPosition)
        
        // Reset state
        resetState()
        
        return result
    }
    
    /**
     * Cancels the current drag operation.
     *
     * Call this when the drag is interrupted (e.g., multi-touch, system event).
     */
    fun cancelDrag() {
        dropTarget?.onDragCancelled()
        resetState()
    }
    
    /**
     * Resets all drag state to idle.
     */
    private fun resetState() {
        state = DragState.Idle
        currentTargetPosition = GridPosition.DEFAULT
    }
    
    /**
     * Checks if a specific item is currently being dragged.
     *
     * @param itemId The ID of the item to check
     * @return true if that item is being dragged
     */
    fun isDragging(itemId: String): Boolean {
        return state.draggedItem?.id == itemId
    }
    
    /**
     * Gets the current drag offset, if any.
     *
     * @return The pixel offset from drag start, or Zero if not dragging
     */
    fun getDragOffset(): Offset {
        return (state as? DragState.Dragging)?.currentOffset ?: Offset.Zero
    }
    
    /**
     * Gets the start position of the current drag, if any.
     *
     * @return The starting grid position, or null if not dragging
     */
    fun getStartPosition(): GridPosition? {
        return state.getDragStartPosition()
    }
}

/**
 * Remembers a DragController instance.
 *
 * This creates a new DragController or returns an existing one if
 * the configuration and calculator haven't changed.
 *
 * @param config The grid configuration
 * @param calculator The grid calculator
 * @return A remembered DragController instance
 */
@Composable
fun rememberDragController(
    config: GridConfig,
    calculator: GridCalculator
): DragController {
    return remember(config, calculator) {
        DragController(config, calculator)
    }
}

/**
 * Creates a simple DragController with default configuration.
 *
 * Useful for quick setup when custom configuration isn't needed.
 *
 * @param cellWidthPx Width of each cell in pixels
 * @param cellHeightPx Height of each cell in pixels
 * @param columns Number of columns in the grid
 * @param rows Number of rows in the grid
 * @return A new DragController instance
 */
fun createDragController(
    cellWidthPx: Float,
    cellHeightPx: Float,
    columns: Int = 4,
    rows: Int = 100
): DragController {
    val config = GridConfig(columns = columns, maxRows = rows)
    val calculator = GridCalculator(cellWidthPx, cellHeightPx, columns, rows)
    return DragController(config, calculator)
}
