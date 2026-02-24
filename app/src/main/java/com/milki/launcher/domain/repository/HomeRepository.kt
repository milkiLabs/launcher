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
     * Reorder pinned items.
     *
     * Moves an item from one position to another.
     * This allows users to arrange their shortcuts.
     *
     * @param fromIndex Current position of the item
     * @param toIndex Target position for the item
     */
    suspend fun reorderPinnedItems(fromIndex: Int, toIndex: Int)

    /**
     * Clear all pinned items.
     *
     * Used for testing or if user wants to reset their home screen.
     */
    suspend fun clearAll()
}
