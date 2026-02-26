/**
 * DropTarget.kt - Interface and result types for drop operations
 *
 * This file defines the contract for drop targets and the possible
 * outcomes of a drop operation. This abstraction enables:
 * - Flexible drop handling: Different targets can handle drops differently
 * - Extensibility: Easy to add new drop targets (folders, docks, etc.)
 * - Testability: Can mock drop targets for unit testing
 *
 * DROP TARGET CONCEPT:
 * A "drop target" is anywhere a dragged item can be dropped. Currently,
 * the main grid is a drop target, but future targets could include:
 * - Dock at bottom of screen
 * - Folders that can contain multiple items
 * - "Remove" zone for uninstalling/unpinning
 * - Another page/screen
 *
 * DROP RESULT TYPES:
 * - Success: Item placed in empty cell
 * - Swap: Item swapped with existing item
 * - Rejected: Drop not allowed (e.g., invalid position)
 *
 * USAGE:
 * ```kotlin
 * class GridDropTarget : DropTarget {
 *     override fun canDrop(item: HomeItem, position: GridPosition): Boolean {
 *         return position.row >= 0 && position.column >= 0
 *     }
 *     
 *     override fun onDrop(item: HomeItem, position: GridPosition): DropResult {
 *         // Handle the drop
 *         return DropResult.Success
 *     }
 * }
 * ```
 */

package com.milki.launcher.ui.components.grid

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem

/**
 * Result of a drop operation.
 *
 * This sealed class represents all possible outcomes when a user
 * drops a dragged item. Each result type carries relevant information
 * for the UI to respond appropriately.
 *
 * WHY SEALED CLASS?
 * - Exhaustive handling: Compiler ensures all cases are handled
 * - Type safety: Each result has its own specific data
 * - Clean API: Single return type with multiple possibilities
 */
sealed class DropResult {
    
    /**
     * Item was successfully placed in an empty cell.
     *
     * This is the most common result for drops on empty cells.
     * No other items were affected.
     *
     * @property finalPosition The position where the item was placed
     *                         (may differ from requested if adjusted)
     */
    data class Success(
        val finalPosition: GridPosition
    ) : DropResult()
    
    /**
     * Item was swapped with an existing item.
     *
     * When dropping on an occupied cell, the items trade positions.
     * This result provides information about both items for animations.
     *
     * @property movedItem The item that was dragged
     * @property movedToPosition Where the moved item ended up
     * @property displacedItem The item that was in the target position
     * @property displacedToPosition Where the displaced item moved to
     */
    data class Swap(
        val movedItem: HomeItem,
        val movedToPosition: GridPosition,
        val displacedItem: HomeItem,
        val displacedToPosition: GridPosition
    ) : DropResult()
    
    /**
     * Drop was rejected.
     *
     * The item cannot be placed at the requested position.
     * This could be due to:
     * - Invalid grid coordinates
     * - Position outside allowed bounds
     * - Target doesn't accept this item type
     *
     * @property reason Human-readable explanation for debugging
     */
    data class Rejected(
        val reason: String
    ) : DropResult()
    
    /**
     * Drop cancelled by user or system.
     *
     * The drag was cancelled before completion.
     * The item should return to its original position.
     */
    data object Cancelled : DropResult()
    
    /**
     * Checks if the drop was successful (not rejected or cancelled).
     */
    val isSuccess: Boolean
        get() = this is Success || this is Swap
}

/**
 * Interface for components that can receive dropped items.
 *
 * Implement this interface to create drop targets. Each target
 * can define its own rules for what items it accepts and how
 * it handles drops.
 *
 * COMMON IMPLEMENTATIONS:
 * - GridDropTarget: The main home screen grid
 * - FolderDropTarget: A folder that can contain items
 * - DockDropTarget: The dock at the bottom of the screen
 * - RemoveDropTarget: A zone for uninstalling/unpinning
 *
 * LIFECYCLE:
 * 1. User starts dragging: canDrop() called for valid targets
 * 2. User hovers over target: previewDrop() called for visual feedback
 * 3. User releases: onDrop() called to complete the operation
 */
interface DropTarget {
    
    /**
     * Checks if this target can accept a drop at the given position.
     *
     * Called continuously during drag to determine valid drop zones.
     * Return false to show "rejected" visual feedback.
     *
     * @param item The item being dragged
     * @param position The requested drop position
     * @return true if the drop is allowed at this position
     */
    fun canDrop(item: HomeItem, position: GridPosition): Boolean
    
    /**
     * Called when the user hovers over this target during a drag.
     *
     * Use this to provide visual feedback before the drop happens.
     * For example, highlight the drop zone or show a preview.
     *
     * @param item The item being dragged
     * @param position The current hover position
     */
    fun previewDrop(item: HomeItem, position: GridPosition) {}
    
    /**
     * Executes the drop operation.
     *
     * Called when the user releases the dragged item over this target.
     * Implementations should:
     * 1. Validate the drop (defensive programming)
     * 2. Update the data model
     * 3. Return appropriate DropResult
     *
     * @param item The item being dropped
     * @param position The requested drop position
     * @return The result of the drop operation
     */
    fun onDrop(item: HomeItem, position: GridPosition): DropResult
    
    /**
     * Called when a drag operation is cancelled.
     *
     * Use this to clean up any preview state or visual feedback.
     */
    fun onDragCancelled() {}
}

/**
 * A simple drop target that delegates to lambda functions.
 *
 * This is useful for creating one-off drop targets without
 * defining a full class implementation.
 *
 * EXAMPLE:
 * ```kotlin
 * val simpleTarget = LambdaDropTarget(
 *     canDropFn = { item, pos -> pos.column < 4 },
 *     onDropFn = { item, pos -> DropResult.Success(pos) }
 * )
 * ```
 */
class LambdaDropTarget(
    private val canDropFn: (HomeItem, GridPosition) -> Boolean,
    private val onDropFn: (HomeItem, GridPosition) -> DropResult,
    private val previewDropFn: ((HomeItem, GridPosition) -> Unit)? = null,
    private val onDragCancelledFn: (() -> Unit)? = null
) : DropTarget {
    
    override fun canDrop(item: HomeItem, position: GridPosition): Boolean {
        return canDropFn(item, position)
    }
    
    override fun previewDrop(item: HomeItem, position: GridPosition) {
        previewDropFn?.invoke(item, position)
    }
    
    override fun onDrop(item: HomeItem, position: GridPosition): DropResult {
        return onDropFn(item, position)
    }
    
    override fun onDragCancelled() {
        onDragCancelledFn?.invoke()
    }
}
