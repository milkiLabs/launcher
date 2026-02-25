/**
 * HomeRepositoryImpl.kt - DataStore-backed implementation of HomeRepository
 *
 * Persists pinned home screen items using Jetpack DataStore Preferences.
 * Each item is serialized to a string and stored in a StringSet.
 *
 * STORAGE FORMAT:
 * Items are stored as a Set<String> where each string is a serialized HomeItem.
 * The format is defined by HomeItem.toStorageString() for each subtype.
 *
 * WHY STRINGSET?
 * - Atomic updates: Adding/removing one item doesn't rewrite everything
 * - Order preservation: We maintain order by storing the entire list as a JSON-like format
 * - Simple serialization: Each item knows how to serialize itself
 *
 * Note: DataStore StringSet has a size limit, but for a typical home screen
 * (20-50 items), this is not a concern.
 */

package com.milki.launcher.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.HomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * DataStore instance for home screen items, scoped to the application context.
 * The name "home_items" determines the file name on disk.
 */
private val Context.homeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "home_items"
)

/**
 * DataStore-backed implementation of HomeRepository.
 *
 * Manages pinned items using a DataStore StringSet for persistence.
 * All operations are non-blocking and coroutine-safe.
 *
 * @param context Application context for DataStore access
 */
class HomeRepositoryImpl(
    private val context: Context
) : HomeRepository {

    // ========================================================================
    // PREFERENCE KEY
    // ========================================================================

    /**
     * The single preference key for storing all pinned items.
     *
     * We use a StringSet where each string is a serialized HomeItem.
     * The order is maintained by storing items as an ordered list string.
     */
    private object Keys {
        /**
         * Stores items as a single string with items separated by newlines.
         * We can't use StringSet directly because Set doesn't preserve order.
         *
         * Format: "item1_string\nitem2_string\nitem3_string"
         */
        val PINNED_ITEMS = stringPreferencesKey("pinned_items_ordered")
    }

    // ========================================================================
    // PINNED ITEMS FLOW
    // ========================================================================

    /**
     * Flow of pinned items, automatically updated when DataStore changes.
     *
     * ERROR HANDLING:
     * If DataStore encounters an IOException (corrupted file, etc.),
     * we emit an empty list instead of crashing.
     */
    override val pinnedItems: Flow<List<HomeItem>> = context.homeDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            deserializeItems(preferences)
        }

    // ========================================================================
    // ADD PINNED ITEM
    // ========================================================================

    /**
     * Add a new item to the pinned items list.
     *
     * If the item ID already exists, the operation is silently ignored.
     * The item is placed at the next available grid position automatically.
     * The grid has 4 columns by default.
     */
    override suspend fun addPinnedItem(item: HomeItem) {
        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences)

            // Check for duplicate by ID
            if (currentItems.any { it.id == item.id }) {
                return@edit // Item already pinned, do nothing
            }

            // Find the next available position for the new item
            // The grid has 4 columns by default
            val availablePosition = findAvailablePositionInList(currentItems, columns = 4)

            // Place the item at the available position
            val itemWithPosition = item.withPosition(availablePosition)

            // Add new item to the end
            val updatedItems = currentItems + itemWithPosition
            serializeItems(updatedItems, preferences)
        }
    }

    /**
     * Helper function to find an available position within a list of items.
     *
     * This is used internally to find an empty cell when adding new items.
     * It doesn't require accessing DataStore, making it more efficient.
     *
     * @param items The current list of items
     * @param columns Number of columns in the grid
     * @return The first available GridPosition
     */
    private fun findAvailablePositionInList(items: List<HomeItem>, columns: Int): GridPosition {
        // Collect all occupied positions
        val occupiedPositions = items.map { it.position }.toSet()

        // Search for the first available position
        // Start from row 0, column 0 and search left-to-right, top-to-bottom
        for (row in 0..100) { // Limit to 100 rows to prevent infinite loop
            for (column in 0 until columns) {
                val position = GridPosition(row, column)
                if (position !in occupiedPositions) {
                    return position
                }
            }
        }

        // Fallback: return position at the end
        return GridPosition(100, 0)
    }

    // ========================================================================
    // REMOVE PINNED ITEM
    // ========================================================================

    /**
     * Remove an item from the pinned items list by its ID.
     *
     * If no item with the given ID exists, the operation is silently ignored.
     */
    override suspend fun removePinnedItem(id: String) {
        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences)
            val updatedItems = currentItems.filterNot { it.id == id }
            serializeItems(updatedItems, preferences)
        }
    }

    // ========================================================================
    // IS PINNED CHECK
    // ========================================================================

    /**
     * Check if an item with the given ID is currently pinned.
     *
     * This is more efficient than collecting the entire list.
     */
    override suspend fun isPinned(id: String): Boolean {
        val items = context.homeDataStore.data.map { preferences ->
            deserializeItems(preferences)
        }.first()

        return items.any { it.id == id }
    }

    // ========================================================================
    // REORDER PINNED ITEMS
    // ========================================================================

    /**
     * Move an item from one position to another in the list.
     *
     * This allows users to rearrange their shortcuts.
     * Invalid indices are silently ignored.
     *
     * @deprecated This is kept for backward compatibility but position-based
     * movement is preferred. Use updateItemPosition instead.
     */
    override suspend fun reorderPinnedItems(fromIndex: Int, toIndex: Int) {
        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            // Validate indices
            if (fromIndex !in currentItems.indices || toIndex !in currentItems.indices) {
                return@edit
            }

            // Move the item and update its position
            val item = currentItems.removeAt(fromIndex)
            currentItems.add(toIndex, item)

            // Recalculate all positions based on new order
            val reorderedItems = currentItems.mapIndexed { index, homeItem ->
                val newRow = index / 4 // Assuming 4 columns
                val newCol = index % 4
                homeItem.withPosition(GridPosition(newRow, newCol))
            }

            serializeItems(reorderedItems, preferences)
        }
    }

    // ========================================================================
    // UPDATE ITEM POSITION
    // ========================================================================

    /**
     * Update the grid position of a specific item.
     *
     * This is called when the user drags an icon to a new location.
     * If the target position is occupied by another item, the items swap positions.
     *
     * @param itemId The ID of the item to move
     * @param newPosition The new grid position (row, column)
     */
    override suspend fun updateItemPosition(itemId: String, newPosition: GridPosition) {
        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            // Find the item to move
            val itemIndex = currentItems.indexOfFirst { it.id == itemId }
            if (itemIndex == -1) return@edit // Item not found

            val itemToMove = currentItems[itemIndex]

            // Check if the target position is occupied
            val occupantIndex = currentItems.indexOfFirst { 
                it.id != itemId && it.position == newPosition 
            }

            if (occupantIndex != -1) {
                // Position is occupied - swap the items
                val occupant = currentItems[occupantIndex]
                // Give the occupant the old position of the moving item
                currentItems[occupantIndex] = occupant.withPosition(itemToMove.position)
            }

            // Update the item's position
            currentItems[itemIndex] = itemToMove.withPosition(newPosition)

            serializeItems(currentItems, preferences)
        }
    }

    // ========================================================================
    // FIND AVAILABLE POSITION
    // ========================================================================

    /**
     * Find the next available grid position for a new item.
     *
     * Searches the grid from top-left (0,0) to find the first empty cell.
     * This is used when pinning a new item to automatically place it.
     *
     * @param columns The number of columns in the grid (typically 4)
     * @param maxRows Maximum rows to search (prevents infinite loop)
     * @return The first available GridPosition
     */
    override suspend fun findAvailablePosition(columns: Int, maxRows: Int): GridPosition {
        val currentItems = context.homeDataStore.data.map { preferences ->
            deserializeItems(preferences)
        }.first()

        // Collect all occupied positions
        val occupiedPositions = currentItems.map { it.position }.toSet()

        // Search for the first available position
        for (row in 0 until maxRows) {
            for (column in 0 until columns) {
                val position = GridPosition(row, column)
                if (position !in occupiedPositions) {
                    return position
                }
            }
        }

        // Fallback: return a position at the end
        return GridPosition(maxRows, 0)
    }

    // ========================================================================
    // CLEAR ALL
    // ========================================================================

    /**
     * Remove all pinned items.
     *
     * Used for testing or when the user wants to reset their home screen.
     */
    override suspend fun clearAll() {
        context.homeDataStore.edit { preferences ->
            preferences.remove(Keys.PINNED_ITEMS)
        }
    }

    // ========================================================================
    // SERIALIZATION HELPERS
    // ========================================================================

    /**
     * Deserialize the items string from preferences into a list of HomeItems.
     *
     * The items are stored as newline-separated strings, with each line
     * being a serialized HomeItem (from HomeItem.toStorageString()).
     *
     * @param preferences The DataStore preferences to read from
     * @return List of deserialized HomeItems, empty if none stored
     */
    private fun deserializeItems(preferences: Preferences): List<HomeItem> {
        val itemsString = preferences[Keys.PINNED_ITEMS] ?: return emptyList()

        if (itemsString.isEmpty()) return emptyList()

        return itemsString
            .split("\n")
            .filter { it.isNotBlank() }
            .mapNotNull { itemString ->
                HomeItem.fromStorageString(itemString)
            }
    }

    /**
     * Serialize a list of HomeItems into the preferences.
     *
     * Each item is converted to its storage string representation,
     * then joined with newlines as separators.
     *
     * @param items The list of items to serialize
     * @param preferences The mutable preferences to write to
     */
    private fun serializeItems(items: List<HomeItem>, preferences: MutablePreferences) {
        val itemsString = items
            .joinToString("\n") { it.toStorageString() }

        preferences[Keys.PINNED_ITEMS] = itemsString
    }
}
