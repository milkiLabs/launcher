/**
 * HomeRepository.kt - Repository interface for home screen pinned items
 *
 * This repository manages the items pinned to the launcher's home screen.
 * It follows the same repository pattern as SettingsRepository and AppRepository.
 *
 * RESPONSIBILITIES:
 * - Store and retrieve pinned items
 * - Provide a Flow for observing changes
 * - Handle add/remove operations
 *
 * IMPLEMENTATION:
 * HomeRepositoryImpl uses DataStore for persistence, similar to SettingsRepository.
 * Each item is serialized to a string and stored in a StringSet.
 */

package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing home screen pinned items.
 *
 * All operations are non-blocking (suspend or Flow-based).
 * The repository handles serialization/deserialization internally.
 */
interface HomeRepository {

    /**
     * Observe the list of pinned home items.
     *
     * Emits the current list immediately when collected,
     * then emits again whenever items are added or removed.
     *
     * The list is ordered by pin order (first pinned = first in list).
     */
    val pinnedItems: Flow<List<HomeItem>>

    /**
     * Add an item to the home screen.
     *
     * If an item with the same ID already exists, this is a no-op.
     * New items are added to the end of the list.
     *
     * @param item The item to pin
     */
    suspend fun addPinnedItem(item: HomeItem)

    /**
     * Add a new item at a specific target position, or move an already pinned item
     * with the same ID to that position.
     *
     * OCCUPANCY RULE:
     * The operation succeeds only when the target position is empty OR already
     * occupied by the same item ID. If another item occupies the target cell,
     * this operation is rejected and returns false.
     *
     * WHY THIS METHOD EXISTS:
     * External drag-and-drop needs an atomic "pin or move" operation so we avoid
     * two-phase writes (add first, then move) that can produce transient layout
     * flicker and ordering races.
     *
     * @param item The home item to pin or move.
     * @param targetPosition The exact grid position requested by the user drop.
     * @return true when the item is placed at targetPosition; false when rejected.
     */
    suspend fun pinOrMoveItemToPosition(item: HomeItem, targetPosition: GridPosition): Boolean

    /**
     * Remove an item from the home screen by its ID.
     *
     * If no item with the given ID exists, this is a no-op.
     *
     * @param id The ID of the item to remove
     */
    suspend fun removePinnedItem(id: String)

    /**
     * Check if an item with the given ID is pinned.
     *
     * @param id The ID to check
     * @return true if an item with this ID is pinned
     */
    suspend fun isPinned(id: String): Boolean

    /**
     * Update the grid position of a pinned item.
     *
      * This is used when the user drags an icon to a new location on the grid.
      *
      * COMPATIBILITY NOTE:
      * Current implementation keeps this method for backward compatibility and
      * applies the same semantics as moveItemToPositionIfEmpty (no swap).
      * New call sites should prefer moveItemToPositionIfEmpty.
     *
     * @param itemId The ID of the item to move
     * @param newPosition The new grid position (row, column)
     */
    suspend fun updateItemPosition(itemId: String, newPosition: GridPosition)

    /**
     * Move an existing pinned item to a target position if and only if that target
     * position is currently empty (or already occupied by the same item).
     *
     * @param itemId The ID of the item to move.
     * @param newPosition Requested destination.
     * @return true when the move was applied or already at that position; false if rejected.
     */
    suspend fun moveItemToPositionIfEmpty(itemId: String, newPosition: GridPosition): Boolean

    /**
     * Find the next available grid position for a new item.
     *
     * Searches the grid from top-left to bottom-right to find the first
     * empty cell. Used when pinning new items to place them automatically.
     *
     * @param columns The number of columns in the grid
     * @param maxRows The maximum number of rows to search (default: 100)
     * @return The first available GridPosition
     */
    suspend fun findAvailablePosition(columns: Int, maxRows: Int = 100): GridPosition

    /**
     * Clear all pinned items.
     *
     * Used for testing or if user wants to reset their home screen.
     */
    suspend fun clearAll()
}
