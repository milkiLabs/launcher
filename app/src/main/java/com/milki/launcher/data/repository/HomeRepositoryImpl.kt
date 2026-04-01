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
import com.milki.launcher.domain.model.GridSpan
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

    /**
     * Default column count used by repository-level auto-placement.
     *
     * IMPORTANT:
     * This value is only used for "find first available slot" behavior during
     * generic pin actions that do not specify an explicit drop target. Explicit
     * drag-drop placement always uses provided GridPosition values.
     */
    private companion object {
        private const val DEFAULT_GRID_COLUMNS = 5
    }

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

    /**
     * Dedicated folder-domain mutation engine.
     *
     * Repository keeps DataStore transaction ownership, while folder invariants
     * and in-memory mutation policy live in this dedicated class.
     */
    private val folderMutationEngine = FolderMutationEngine()

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

    override suspend fun replacePinnedItems(items: List<HomeItem>) {
        context.homeDataStore.edit { preferences ->
            serializeItems(items, preferences)
        }
    }

    // ========================================================================
    // ADD PINNED ITEM
    // ========================================================================

    /**
     * Add a new item to the pinned items list.
     *
     * If the item ID already exists, the operation is silently ignored.
     * The item is placed at the next available grid position automatically.
    * The grid uses the shared default column count.
     */
    suspend fun addPinnedItem(item: HomeItem) {
        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences)

            // Check for duplicate by ID across BOTH top-level items and folder children.
            if (folderMutationEngine.containsItemIdAnywhere(currentItems, item.id)) {
                return@edit // Item already pinned, do nothing
            }

            // Find the next available position for the new item.
            val availablePosition = findAvailablePositionInList(currentItems, columns = DEFAULT_GRID_COLUMNS)

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
        // Collect all occupied positions, including cells spanned by widgets.
        val occupiedPositions = buildOccupiedCellsMap(items).keys

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
    suspend fun removePinnedItem(id: String) {
        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences)
            val updatedItems = currentItems.filterNot { it.id == id }
            serializeItems(updatedItems, preferences)
        }
    }

    /**
     * Atomic pin-or-move operation used by external app drops.
     *
     * RULES ENFORCED:
     * 1) If target is occupied by another item -> reject (return false)
     * 2) If item already exists -> move it to target
     * 3) If item does not exist -> add it directly at target
     */
    suspend fun pinOrMoveItemToPosition(item: HomeItem, targetPosition: GridPosition): Boolean {
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            // Enforce uniqueness globally before placing this item on the top-level grid.
            // This evicts the same ID from any folder and from accidental duplicate
            // top-level entries, then we write exactly one canonical copy below.
            folderMutationEngine.evictItemEverywhere(currentItems, item.id)

            // Re-evaluate indices AFTER the eviction — the list may have changed.
            val existingItemIndex = currentItems.indexOfFirst { it.id == item.id }

            // Build span-aware occupancy map excluding the item being placed.
            val occupiedCells = buildOccupiedCellsMap(currentItems, excludeItemId = item.id)

            // For widgets, verify the full span fits; for single-cell items, check just the cell.
            val span = (item as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
            if (!isSpanFree(targetPosition, span, occupiedCells, gridColumns = DEFAULT_GRID_COLUMNS)) {
                wasApplied = false
                return@edit
            }

            if (existingItemIndex != -1) {
                val existingItem = currentItems[existingItemIndex]
                currentItems[existingItemIndex] = existingItem.withPosition(targetPosition)
            } else {
                currentItems.add(item.withPosition(targetPosition))
            }

            serializeItems(currentItems, preferences)
            wasApplied = true
        }

        return wasApplied
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
      *
      * BEHAVIOR:
      * For compatibility, this delegates to moveItemToPositionIfEmpty(), which
      * rejects moves into occupied target cells.
     *
     * @param itemId The ID of the item to move
     * @param newPosition The new grid position (row, column)
     */
    suspend fun updateItemPosition(itemId: String, newPosition: GridPosition) {
        moveItemToPositionIfEmpty(itemId, newPosition)
    }

    /**
     * Move operation that rejects occupied target cells.
     */
    suspend fun moveItemToPositionIfEmpty(itemId: String, newPosition: GridPosition): Boolean {
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            val itemIndex = currentItems.indexOfFirst { it.id == itemId }
            if (itemIndex == -1) {
                wasApplied = false
                return@edit
            }

            val currentItem = currentItems[itemIndex]
            if (currentItem.position == newPosition) {
                wasApplied = true
                return@edit
            }

            // Build span-aware occupancy map excluding the item being moved.
            val occupiedCells = buildOccupiedCellsMap(currentItems, excludeItemId = itemId)

            // For widgets, check that the full span fits; for single-cell items, check the single cell.
            val span = (currentItem as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
            val wouldOverlap = !isSpanFree(newPosition, span, occupiedCells, gridColumns = DEFAULT_GRID_COLUMNS)

            if (wouldOverlap) {
                wasApplied = false
                return@edit
            }

            currentItems[itemIndex] = currentItem.withPosition(newPosition)
            serializeItems(currentItems, preferences)
            wasApplied = true
        }

        return wasApplied
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

        // Build span-aware set of all occupied cells.
        val occupiedPositions = buildOccupiedCellsMap(currentItems).keys

        // Search for the first available position (single-cell).
        for (row in 0 until maxRows) {
            for (column in 0 until columns) {
                val position = GridPosition(row, column)
                if (position !in occupiedPositions) {
                    return position
                }
            }
        }

        // Fallback: return a position at the end.
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
    // FOLDER OPERATIONS
    // ========================================================================

    /**
     * Creates a new folder from two existing home screen items.
     *
     * ATOMIC OPERATION:
     * In a single DataStore edit:
     * 1. Both items are removed from the top-level list.
     * 2. A new FolderItem is inserted at [atPosition].
     *
     * NESTING GUARD:
     * If either item is itself a FolderItem, the method returns null without
     * any writes. We do not allow folders inside folders.
     *
     * @param item1 The dragged item
     * @param item2 The item that was dropped onto
     * @param atPosition The grid cell where the folder should appear
     * @return The created FolderItem, or null if nesting was detected.
     */
    suspend fun createFolder(
        item1: HomeItem,
        item2: HomeItem,
        atPosition: GridPosition
    ): HomeItem.FolderItem? {
        var createdFolder: HomeItem.FolderItem? = null

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            createdFolder = folderMutationEngine.createFolder(
                items = currentItems,
                item1 = item1,
                item2 = item2,
                atPosition = atPosition
            )
            if (createdFolder == null) return@edit

            serializeItems(currentItems, preferences)
        }

        return createdFolder
    }

    /**
     * Adds an item to a folder's children list.
     *
     * - Removes the item from the top-level home screen list (if it is there).
     * - Appends or inserts it into the folder's children.
     * - Rejects FolderItem children (no nesting).
     * - Rejects duplicate children (by id).
     */
    suspend fun addItemToFolder(
        folderId: String,
        item: HomeItem,
        targetIndex: Int?
    ): Boolean {
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            wasApplied = folderMutationEngine.addItemToFolder(
                items = currentItems,
                folderId = folderId,
                item = item,
                targetIndex = targetIndex
            )
            if (!wasApplied) return@edit

            serializeItems(currentItems, preferences)
        }

        return wasApplied
    }

    /**
     * Removes an item from a folder and applies the cleanup policy.
     *
     * CLEANUP POLICY:
     * - 0 children → folder deleted
     * - 1 child    → folder deleted; child promoted to folder's home screen position
     * - 2+ children → folder updated with smaller children list
     *
     * Returns the updated folder if it still exists, null if it was deleted.
     */
    suspend fun removeItemFromFolder(
        folderId: String,
        itemId: String
    ): HomeItem.FolderItem? {
        var resultFolder: HomeItem.FolderItem? = null
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            val result = folderMutationEngine.removeItemFromFolder(
                items = currentItems,
                folderId = folderId,
                itemId = itemId
            )
            if (!result.wasApplied) return@edit

            resultFolder = result.updatedFolder
            wasApplied = true

            serializeItems(currentItems, preferences)
        }

        return if (wasApplied) resultFolder else null
    }

    /**
     * Replaces a folder's children list with a new ordered list.
     *
     * Used for internal folder reordering via drag-and-drop inside the popup.
     */
    suspend fun reorderFolderItems(
        folderId: String,
        newChildren: List<HomeItem>
    ): Boolean {
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            wasApplied = folderMutationEngine.reorderFolderItems(
                items = currentItems,
                folderId = folderId,
                newChildren = newChildren
            )
            if (!wasApplied) return@edit

            serializeItems(currentItems, preferences)
        }

        return wasApplied
    }

    /**
     * Merges all children of [sourceFolderId] into [targetFolderId], then deletes the source.
     *
     * Duplicate children (same id already in target) are skipped.
     */
    suspend fun mergeFolders(
        sourceFolderId: String,
        targetFolderId: String
    ): Boolean {
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            wasApplied = folderMutationEngine.mergeFolders(
                items = currentItems,
                sourceFolderId = sourceFolderId,
                targetFolderId = targetFolderId
            )
            if (!wasApplied) return@edit

            serializeItems(currentItems, preferences)
        }

        return wasApplied
    }

    /**
     * Renames a folder.
     *
     * The name is trimmed; if blank after trimming, it falls back to "Folder".
     */
    suspend fun renameFolder(
        folderId: String,
        newName: String
    ): Boolean {
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            wasApplied = folderMutationEngine.renameFolder(
                items = currentItems,
                folderId = folderId,
                newName = newName
            )
            if (!wasApplied) return@edit

            serializeItems(currentItems, preferences)
        }

        return wasApplied
    }

    /**
     * Extracts an item from a folder and places it on the home screen grid.
     *
     * This is the "drag-out" operation: the user dragged a folder icon outside
     * the folder popup and released it on the home screen.
     *
     * STEPS (atomic):
     * 1. Find the folder and the item inside it.
     * 2. Check that [targetPosition] is not occupied by another item.
     * 3. Remove the item from the folder's children.
     * 4. Apply folder cleanup policy (delete/unwrap if ≤1 child left).
     * 5. Add the item to the home screen at [targetPosition].
     *
     * Returns false if:
     * - The folder or item was not found.
     * - [targetPosition] is occupied by a different item (not the folder).
     */
    suspend fun extractItemFromFolder(
        folderId: String,
        itemId: String,
        targetPosition: GridPosition
    ): Boolean {
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            // Span-aware occupancy check: the target must be empty or occupied by the folder itself.
            val occupiedCells = buildOccupiedCellsMap(currentItems, excludeItemId = folderId)

            wasApplied = folderMutationEngine.extractItemFromFolder(
                items = currentItems,
                folderId = folderId,
                itemId = itemId,
                targetPosition = targetPosition,
                targetPositionOccupiedByOtherItem = targetPosition in occupiedCells
            )
            if (!wasApplied) return@edit

            serializeItems(currentItems, preferences)
        }

        return wasApplied
    }

    /**
     * Atomically moves a child from one folder into another folder.
     */
    suspend fun moveItemBetweenFolders(
        sourceFolderId: String,
        targetFolderId: String,
        itemId: String
    ): Boolean {
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            wasApplied = folderMutationEngine.moveItemBetweenFolders(
                items = currentItems,
                sourceFolderId = sourceFolderId,
                targetFolderId = targetFolderId,
                itemId = itemId
            )
            if (!wasApplied) return@edit

            serializeItems(currentItems, preferences)
        }

        return wasApplied
    }

    /**
     * Atomically extracts a child from one folder and creates a new folder at an
     * occupied cell with that child + the current occupant.
     */
    suspend fun extractFolderChildOntoItem(
        sourceFolderId: String,
        childItemId: String,
        occupantItem: HomeItem,
        atPosition: GridPosition
    ): HomeItem.FolderItem? {
        var createdFolder: HomeItem.FolderItem? = null

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            createdFolder = folderMutationEngine.extractFolderChildOntoItem(
                items = currentItems,
                sourceFolderId = sourceFolderId,
                childItemId = childItemId,
                occupantItem = occupantItem,
                atPosition = atPosition
            )
            if (createdFolder == null) return@edit

            serializeItems(currentItems, preferences)
        }

        return createdFolder
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

    // ========================================================================
    // SPAN-AWARE OCCUPANCY HELPERS
    // ========================================================================

    /**
     * Builds a map of every occupied grid cell → the item ID occupying it.
     *
     * This is the core helper that enables multi-cell widget support. For
     * single-cell items (PinnedApp, PinnedFile, etc.), only one cell is claimed.
     * For WidgetItems, ALL cells in the widget's span are claimed.
     *
     * USAGE:
     * Used by widget add/move/resize operations to check if the requested cells
     * are free before placing or resizing a widget.
     *
     * @param items The current list of home screen items.
     * @param excludeItemId Optional item ID to exclude from the map (used when
     *                       moving or resizing an item — it shouldn't collide with
     *                       its own current cells).
     * @return A map from GridPosition → item ID for every occupied cell.
     */
    private fun buildOccupiedCellsMap(
        items: List<HomeItem>,
        excludeItemId: String? = null
    ): Map<GridPosition, String> {
        val occupiedCells = mutableMapOf<GridPosition, String>()
        for (item in items) {
            if (item.id == excludeItemId) continue
            if (item is HomeItem.WidgetItem) {
                // Widget occupies multiple cells based on its span.
                for (pos in item.span.occupiedPositions(item.position)) {
                    occupiedCells[pos] = item.id
                }
            } else {
                // Single-cell items occupy only their position.
                occupiedCells[item.position] = item.id
            }
        }
        return occupiedCells
    }

    /**
     * Checks whether ALL cells in a span are free (not occupied by any other item).
     *
     * Used before placing or resizing a widget to ensure it won't overlap anything.
     *
     * @param position The top-left anchor of the span to check.
     * @param span The size (columns × rows) to check.
     * @param occupiedCells The pre-computed map from [buildOccupiedCellsMap].
     * @param gridColumns The number of columns in the grid (for bounds checking).
     * @return true if all cells in the span are free and within grid bounds.
     */
    private fun isSpanFree(
        position: GridPosition,
        span: GridSpan,
        occupiedCells: Map<GridPosition, String>,
        gridColumns: Int = DEFAULT_GRID_COLUMNS
    ): Boolean {
        // Bounds check: the span must not extend beyond the grid columns.
        if (position.column + span.columns > gridColumns) return false
        if (position.row < 0 || position.column < 0) return false

        // Check every cell in the span for occupancy.
        for (pos in span.occupiedPositions(position)) {
            if (pos in occupiedCells) return false
        }
        return true
    }

    // ========================================================================
    // WIDGET OPERATIONS
    // ========================================================================

    /**
     * Adds a widget to the home screen at the specified position.
     *
     * Checks ALL cells in the widget's span for occupancy before placement.
     * If any cell is occupied, the operation is rejected.
     */
    suspend fun addWidget(widget: HomeItem.WidgetItem): Boolean {
        // Delegate to the canonical placement operation so widget placement and
        // external pin/move placement always share identical occupancy/uniqueness rules.
        return pinOrMoveItemToPosition(widget, widget.position)
    }

    /**
     * Removes a widget from the home screen by its ID.
     *
     * The caller is responsible for deallocating the widget ID from
     * AppWidgetHost after this method returns.
     */
    suspend fun removeWidget(widgetId: String) {
        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()
            currentItems.removeAll { it.id == widgetId }
            serializeItems(currentItems, preferences)
        }
    }

    /**
     * Updates the span (size) of an existing widget.
     *
     * Checks that the new span doesn't overlap with any other items
     * (excluding the widget being resized from the occupancy check).
     */
    suspend fun updateWidgetSpan(widgetId: String, newSpan: GridSpan): Boolean {
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            val widgetIndex = currentItems.indexOfFirst { it.id == widgetId }
            if (widgetIndex == -1) {
                wasApplied = false
                return@edit
            }

            val widget = currentItems[widgetIndex] as? HomeItem.WidgetItem
            if (widget == null) {
                wasApplied = false
                return@edit
            }

            // Build occupancy map EXCLUDING the widget being resized,
            // so it doesn't collide with its own current cells.
            val occupiedCells = buildOccupiedCellsMap(currentItems, excludeItemId = widgetId)
            if (!isSpanFree(widget.position, newSpan, occupiedCells)) {
                wasApplied = false
                return@edit
            }

            currentItems[widgetIndex] = widget.withSpan(newSpan)
            serializeItems(currentItems, preferences)
            wasApplied = true
        }

        return wasApplied
    }
}
