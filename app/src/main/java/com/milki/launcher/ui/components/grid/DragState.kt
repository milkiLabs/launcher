/**
 * DragState.kt - Immutable state representation for drag operations
 *
 * This file defines the state object that represents the current state
 * of a drag operation. Using an immutable data class ensures:
 * - Thread safety: State changes are atomic
 * - Predictability: State can only change through defined transitions
 * - Debuggability: Easy to log/inspect state at any point
 *
 * STATE LIFECYCLE:
 * ```
 * Idle -> (startDrag) -> Dragging -> (endDrag/cancelDrag) -> Idle
 * ```
 *
 * WHY IMMUTABLE STATE?
 * - Compose optimizes recomposition when state objects are stable
 * - Prevents accidental partial state updates
 * - Makes state transitions explicit and traceable
 * - Enables easy state snapshots for debugging/undo
 *
 * USAGE:
 * ```kotlin
 * var dragState by mutableStateOf(DragState.Idle)
 * 
 * // Start drag
 * dragState = DragState.dragging(item, startPosition)
 * 
 * // Update offset
 * dragState = (dragState as DragState.Dragging).withOffset(newOffset)
 * ```
 */

package com.milki.launcher.ui.components.grid

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import androidx.compose.ui.geometry.Offset

/**
 * Sealed class representing the state of a drag operation.
 *
 * Using a sealed class ensures:
 * - Exhaustive when expressions: Compiler enforces handling all states
 * - Type safety: Each state has its own specific data
 * - Clear state machine: Transitions are explicit
 *
 * STATES:
 * - Idle: No drag in progress
 * - Dragging: Item is being dragged, contains all drag data
 * - PendingDrop: Drag ended, waiting for drop confirmation
 *
 * TRANSITIONS:
 * - Idle -> Dragging: User starts drag (after long-press + movement)
 * - Dragging -> Dragging: User moves finger (offset updates)
 * - Dragging -> PendingDrop: User releases finger
 * - PendingDrop -> Idle: Drop handled (success or rejected)
 * - Dragging -> Idle: Drag cancelled
 */
sealed class DragState {
    
    /**
     * No drag operation in progress.
     *
     * This is the default state when the user is not interacting
     * with any items or when a drag has ended/cancelled.
     */
    data object Idle : DragState()
    
    /**
     * A drag operation is in progress.
     *
     * This state contains all the information needed to render
     * the drag preview and calculate the drop position.
     *
     * @property item The HomeItem being dragged
     * @property startPosition The grid position where the drag started
     * @property currentOffset The pixel offset from the start position
     * @property hasExceededThreshold Whether drag has exceeded the threshold (for visual feedback)
     */
    data class Dragging(
        val item: HomeItem,
        val startPosition: GridPosition,
        val currentOffset: Offset = Offset.Zero,
        val hasExceededThreshold: Boolean = false
    ) : DragState() {
        
        /**
         * Creates a new Dragging state with an updated offset.
         *
         * @param newOffset The new pixel offset from start position
         * @param threshold The threshold in pixels to mark as exceeded
         * @return New Dragging state with updated offset
         */
        fun withOffset(newOffset: Offset, threshold: Float = 0f): Dragging {
            val exceeded = if (threshold > 0) {
                kotlin.math.abs(newOffset.x) > threshold || 
                kotlin.math.abs(newOffset.y) > threshold
            } else {
                hasExceededThreshold
            }
            
            return copy(
                currentOffset = newOffset,
                hasExceededThreshold = exceeded
            )
        }
        
        /**
         * Adds to the current offset (for incremental updates).
         *
         * @param delta The amount to add to the current offset
         * @param threshold The threshold in pixels to mark as exceeded
         * @return New Dragging state with updated offset
         */
        fun addOffset(delta: Offset, threshold: Float = 0f): Dragging {
            return withOffset(currentOffset + delta, threshold)
        }
        
        /**
         * Checks if this drag state matches a specific item ID.
         *
         * @param itemId The item ID to check
         * @return true if this drag is for the specified item
         */
        fun isDraggingItem(itemId: String): Boolean {
            return item.id == itemId
        }
    }
    
    /**
     * Drag has ended and is waiting for drop to be processed.
     *
     * This intermediate state allows the UI to show a "dropping" animation
     * while the data layer processes the position update.
     *
     * @property item The item that was dragged
     * @property startPosition Where the drag started
     * @property targetPosition Where the item will be dropped
     */
    data class PendingDrop(
        val item: HomeItem,
        val startPosition: GridPosition,
        val targetPosition: GridPosition
    ) : DragState()
    
    /**
     * Checks if a drag is currently active (Dragging or PendingDrop).
     *
     * @return true if a drag is in progress
     */
    val isActive: Boolean
        get() = this is Dragging || this is PendingDrop
    
    /**
     * Gets the item being dragged, if any.
     *
     * @return The HomeItem being dragged, or null if not dragging
     */
    val draggedItem: HomeItem?
        get() = when (this) {
            is Dragging -> item
            is PendingDrop -> item
            is Idle -> null
        }
    
    /**
     * Gets the start position of the current drag, if any.
     *
     * @return The starting GridPosition, or null if not dragging
     */
    fun getDragStartPosition(): GridPosition? = when (this) {
        is Dragging -> this.startPosition
        is PendingDrop -> this.startPosition
        is Idle -> null
    }
    
    companion object {
        /**
         * Creates a new Dragging state.
         *
         * Factory method for starting a drag operation.
         *
         * @param item The item to drag
         * @param startPosition The grid position where drag starts
         * @return New Dragging state
         */
        fun startDrag(
            item: HomeItem,
            startPosition: GridPosition
        ): Dragging {
            return Dragging(
                item = item,
                startPosition = startPosition,
                currentOffset = Offset.Zero,
                hasExceededThreshold = false
            )
        }
    }
}
