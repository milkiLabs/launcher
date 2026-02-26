/**
 * HomeRepositoryImpl.kt - DataStore-backed implementation of HomeRepository
 *
 * Persists pinned home screen items using Jetpack DataStore Preferences.
 * Each item is serialized to JSON using kotlinx.serialization.
 *
 * STORAGE FORMAT:
 * Items are stored as a single string with JSON objects separated by newlines.
 * Each line is a complete JSON representation of a HomeItem subclass.
 *
 * WHY JSON SERIALIZATION?
 * - Type-safe: kotlinx.serialization handles polymorphic types correctly
 * - Robust: No delimiter collision issues (unlike pipe-delimited format)
 * - Schema evolution: New fields can be added without breaking old data
 * - Readable: JSON is human-readable for debugging
 *
 * EXAMPLE STORAGE FORMAT:
 * ```
 * {"type":"PinnedApp","id":"app:com.whatsapp/.Main","packageName":"com.whatsapp",...}
 * {"type":"PinnedFile","id":"file:content://...","uri":"content://...","name":"Report.pdf",...}
 * ```
 *
 * WHY NEWLINE-SEPARATED JSON?
 * - Each item is a complete, parseable JSON object
 * - Simple to add/remove items without reparsing everything
 * - Order is preserved (unlike StringSet)
 * - No nested JSON escaping issues
 *
 * Note: DataStore has a size limit, but for a typical home screen
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
 * Manages pinned items using DataStore for persistence with JSON serialization.
 * All operations are non-blocking and coroutine-safe.
 *
 * SERIALIZATION DETAILS:
 * - Uses kotlinx.serialization with polymorphic serialization for sealed classes
 * - Each HomeItem subclass is serialized with a "type" discriminator
 * - The Json instance is configured with classDiscriminator for polymorphic types
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
     * We use a String where each line is a JSON-serialized HomeItem.
     * The order is maintained by the order of lines in the string.
     */
    private object Keys {
        /**
         * Stores items as a single string with JSON objects separated by newlines.
         * We can't use StringSet directly because Set doesn't preserve order.
         *
         * Format:
         * ```
         * {"type":"PinnedApp",...}\n{"type":"PinnedFile",...}\n...
         * ```
         *
         * Each line is a complete, parseable JSON object.
         */
        val PINNED_ITEMS = stringPreferencesKey("pinned_items_ordered")
    }

    // ========================================================================
    // JSON SERIALIZER
    // ========================================================================

    /**
     * The Json instance used for serialization.
     *
     * Uses the same configuration as HomeItem.json for consistency:
     * - classDiscriminator: Uses "type" field to identify subclasses
     * - encodeDefaults: Ensures default values are written to JSON
     *
     * This is reused from HomeItem.companion.json to ensure consistent
     * serialization behavior across the app.
     */
    private val json: Json = HomeItem.json

    // ========================================================================
    // PINNED ITEMS FLOW
    // ========================================================================

    /**
     * Flow of pinned items, automatically updated when DataStore changes.
     *
     * ERROR HANDLING:
     * If DataStore encounters an IOException (corrupted file, etc.),
     * we emit an empty list instead of crashing.
     *
     * DESERIALIZATION:
     * Each line in the stored string is parsed as a JSON HomeItem.
     * Malformed lines are silently skipped to prevent crashes.
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
     * DESERIALIZATION PROCESS:
     * 1. Split the stored string by newlines
     * 2. For each line, parse as JSON using kotlinx.serialization
     * 3. The polymorphic serializer automatically creates the correct subclass
     * 4. Malformed lines are silently skipped
     *
     * ERROR HANDLING:
     * - Empty or missing data returns an empty list
     * - Malformed JSON lines are skipped (logged but don't crash)
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
            .mapNotNull { jsonLine ->
                try {
                    // Use kotlinx.serialization to parse the JSON
                    // The polymorphic serializer reads the "type" field
                    // and creates the appropriate HomeItem subclass
                    json.decodeFromString<HomeItem>(jsonLine)
                } catch (e: Exception) {
                    // Log the error but don't crash
                    // This handles corrupted data gracefully
                    null
                }
            }
    }

    /**
     * Serialize a list of HomeItems into the preferences.
     *
     * SERIALIZATION PROCESS:
     * 1. For each HomeItem, convert to JSON using kotlinx.serialization
     * 2. Each item becomes a complete JSON object with a "type" field
     * 3. Join all JSON objects with newlines
     *
     * JSON FORMAT:
     * Each item is serialized as a single line of JSON:
     * ```
     * {"type":"PinnedApp","id":"app:com.whatsapp/.Main","packageName":"com.whatsapp","activityName":".Main","label":"WhatsApp","position":{"row":0,"column":1}}
     * ```
     *
     * ADVANTAGES OVER PIPE-DELIMITED:
     * - No delimiter collision (labels/URIs can contain any character)
     * - Type-safe parsing (the "type" field identifies the subclass)
     * - Schema evolution (new fields can be added)
     * - Human-readable for debugging
     *
     * @param items The list of items to serialize
     * @param preferences The mutable preferences to write to
     */
    private fun serializeItems(items: List<HomeItem>, preferences: MutablePreferences) {
        val itemsString = items
            .joinToString("\n") { item ->
                // Use kotlinx.serialization to convert to JSON
                // Each item is serialized with its type discriminator
                json.encodeToString(item)
            }

        preferences[Keys.PINNED_ITEMS] = itemsString
    }
}
