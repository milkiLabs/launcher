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

    /**
     * Default column count used by repository-level auto-placement.
     *
     * IMPORTANT:
     * This value is only used for "find first available slot" behavior during
     * generic pin actions that do not specify an explicit drop target. Explicit
     * drag-drop placement always uses provided GridPosition values.
     */
    private companion object {
        private const val DEFAULT_GRID_COLUMNS = 4
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

    /**
     * Atomic pin-or-move operation used by external app drops.
     *
     * RULES ENFORCED:
     * 1) If target is occupied by another item -> reject (return false)
     * 2) If item already exists -> move it to target
     * 3) If item does not exist -> add it directly at target
     */
    override suspend fun pinOrMoveItemToPosition(item: HomeItem, targetPosition: GridPosition): Boolean {
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            // If the item lives inside a folder (not on the flat home-screen grid),
            // remove it from that folder BEFORE placing it on the grid.  Without this
            // step the item would appear both inside its folder AND on the grid.
            //
            // The folder cleanup policy is applied: if the folder becomes empty it is
            // deleted; if only one child remains the folder is unwrapped to a plain icon.
            evictItemFromFolderIfPresent(currentItems, item.id)

            // Re-evaluate indices AFTER the eviction — the list may have changed.
            val existingItemIndex = currentItems.indexOfFirst { it.id == item.id }
            val targetOccupantIndex = currentItems.indexOfFirst {
                it.id != item.id && it.position == targetPosition
            }

            if (targetOccupantIndex != -1) {
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
    override suspend fun updateItemPosition(itemId: String, newPosition: GridPosition) {
        moveItemToPositionIfEmpty(itemId, newPosition)
    }

    /**
     * Move operation that rejects occupied target cells.
     */
    override suspend fun moveItemToPositionIfEmpty(itemId: String, newPosition: GridPosition): Boolean {
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

            val targetOccupiedByAnotherItem = currentItems.any {
                it.id != itemId && it.position == newPosition
            }

            if (targetOccupiedByAnotherItem) {
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
    override suspend fun createFolder(
        item1: HomeItem,
        item2: HomeItem,
        atPosition: GridPosition
    ): HomeItem.FolderItem? {
        // Nesting guard: neither item may itself be a FolderItem.
        if (item1 is HomeItem.FolderItem || item2 is HomeItem.FolderItem) {
            return null
        }

        // Build the new folder using the factory method on FolderItem.
        val newFolder = HomeItem.FolderItem.create(item1, item2, atPosition)

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            // Remove both source items from the top-level list.
            // We use removeAll with id comparison to be safe if positions shifted.
            currentItems.removeAll { it.id == item1.id || it.id == item2.id }

            // Add the new folder at the requested position.
            currentItems.add(newFolder)

            serializeItems(currentItems, preferences)
        }

        return newFolder
    }

    /**
     * Adds an item to a folder's children list.
     *
     * - Removes the item from the top-level home screen list (if it is there).
     * - Appends or inserts it into the folder's children.
     * - Rejects FolderItem children (no nesting).
     * - Rejects duplicate children (by id).
     */
    override suspend fun addItemToFolder(
        folderId: String,
        item: HomeItem,
        targetIndex: Int?
    ): Boolean {
        // Nesting guard: we never put a folder inside a folder.
        if (item is HomeItem.FolderItem) return false

        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            // Find the target folder in the top-level list.
            val folderIndex = currentItems.indexOfFirst { it.id == folderId }
            if (folderIndex == -1) {
                // Folder not found — nothing to do.
                return@edit
            }

            val folder = currentItems[folderIndex] as? HomeItem.FolderItem ?: return@edit

            // Guard: item is already a child of this folder.
            if (folder.children.any { it.id == item.id }) {
                wasApplied = false
                return@edit
            }

            // Remove the item from the top-level flat list if it appears there.
            // (When dragging a home-screen icon onto a folder, the icon is
            //  at the top level and must be removed before being added to the folder.)
            currentItems.removeAll { it.id == item.id }

            // Also remove the item from any OTHER folder it may already be in.
            // Without this, an icon that lives inside folder A could be dragged (from
            // the search dialog) into folder B and appear in both — a duplication bug.
            // The same cleanup policy that governs individual item removal applies here:
            // empty folder → deleted; single-child folder → unwrapped.
            evictItemFromFolderIfPresent(currentItems, item.id)

            // Re-find folder index after the removal above (index may have shifted).
            val updatedFolderIndex = currentItems.indexOfFirst { it.id == folderId }
            if (updatedFolderIndex == -1) return@edit
            val updatedFolder = currentItems[updatedFolderIndex] as HomeItem.FolderItem

            // Build the updated children list.
            val updatedChildren = updatedFolder.children.toMutableList()
            val insertAt = targetIndex?.coerceIn(0, updatedChildren.size) ?: updatedChildren.size

            // Always store children with DEFAULT position so they don't carry stale
            // home-screen grid coordinates into the folder-internal grid.
            updatedChildren.add(insertAt, item.withPosition(GridPosition.DEFAULT))

            // Replace the folder in the top-level list with the updated version.
            currentItems[updatedFolderIndex] = updatedFolder.copy(children = updatedChildren)

            serializeItems(currentItems, preferences)
            wasApplied = true
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
    override suspend fun removeItemFromFolder(
        folderId: String,
        itemId: String
    ): HomeItem.FolderItem? {
        var resultFolder: HomeItem.FolderItem? = null

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            val folderIndex = currentItems.indexOfFirst { it.id == folderId }
            if (folderIndex == -1) return@edit

            val folder = currentItems[folderIndex] as? HomeItem.FolderItem ?: return@edit

            // Build the new children list without the removed item.
            val updatedChildren = folder.children.filterNot { it.id == itemId }

            when (updatedChildren.size) {
                0 -> {
                    // Folder is now empty — delete it entirely from the home screen.
                    currentItems.removeAt(folderIndex)
                    resultFolder = null
                }
                1 -> {
                    // Only one child left — promote it to the folder's grid position.
                    // The folder is deleted and the single remaining child takes its spot.
                    val promotedChild = updatedChildren.first().withPosition(folder.position)
                    currentItems.removeAt(folderIndex)
                    currentItems.add(promotedChild)
                    resultFolder = null
                }
                else -> {
                    // Two or more children remain — just update the folder.
                    val updatedFolder = folder.copy(children = updatedChildren)
                    currentItems[folderIndex] = updatedFolder
                    resultFolder = updatedFolder
                }
            }

            serializeItems(currentItems, preferences)
        }

        return resultFolder
    }

    /**
     * Replaces a folder's children list with a new ordered list.
     *
     * Used for internal folder reordering via drag-and-drop inside the popup.
     */
    override suspend fun reorderFolderItems(
        folderId: String,
        newChildren: List<HomeItem>
    ): Boolean {
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            val folderIndex = currentItems.indexOfFirst { it.id == folderId }
            if (folderIndex == -1) return@edit

            val folder = currentItems[folderIndex] as? HomeItem.FolderItem ?: return@edit

            // Replace children. Ensure no FolderItems sneak in (safety guard).
            val safeChildren = newChildren
                .filterNot { it is HomeItem.FolderItem }
                // Ensure children positions are reset to DEFAULT so the folder-internal
                // grid doesn't get confused by stale home-screen coordinates.
                .map { it.withPosition(GridPosition.DEFAULT) }

            currentItems[folderIndex] = folder.copy(children = safeChildren)
            serializeItems(currentItems, preferences)
            wasApplied = true
        }

        return wasApplied
    }

    /**
     * Merges all children of [sourceFolderId] into [targetFolderId], then deletes the source.
     *
     * Duplicate children (same id already in target) are skipped.
     */
    override suspend fun mergeFolders(
        sourceFolderId: String,
        targetFolderId: String
    ): Boolean {
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            val sourceIndex = currentItems.indexOfFirst { it.id == sourceFolderId }
            val targetIndex = currentItems.indexOfFirst { it.id == targetFolderId }

            if (sourceIndex == -1 || targetIndex == -1) return@edit

            val sourceFolder = currentItems[sourceIndex] as? HomeItem.FolderItem ?: return@edit
            val targetFolder = currentItems[targetIndex] as? HomeItem.FolderItem ?: return@edit

            // Build the merged children list: target's children first, then unique source children.
            val targetChildIds = targetFolder.children.map { it.id }.toSet()
            val newChildrenFromSource = sourceFolder.children
                .filterNot { it.id in targetChildIds }    // skip duplicates
                .filterNot { it is HomeItem.FolderItem }   // safety check (no nesting)
                .map { it.withPosition(GridPosition.DEFAULT) }

            val mergedChildren = targetFolder.children + newChildrenFromSource

            // Update the target folder with the merged children.
            val updatedTarget = targetFolder.copy(children = mergedChildren)

            // Remove the source folder from the list.
            currentItems.removeAll { it.id == sourceFolderId }

            // Update the target in its (potentially shifted) position.
            val newTargetIndex = currentItems.indexOfFirst { it.id == targetFolderId }
            if (newTargetIndex == -1) return@edit
            currentItems[newTargetIndex] = updatedTarget

            serializeItems(currentItems, preferences)
            wasApplied = true
        }

        return wasApplied
    }

    /**
     * Renames a folder.
     *
     * The name is trimmed; if blank after trimming, it falls back to "Folder".
     */
    override suspend fun renameFolder(
        folderId: String,
        newName: String
    ): Boolean {
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            val folderIndex = currentItems.indexOfFirst { it.id == folderId }
            if (folderIndex == -1) return@edit

            val folder = currentItems[folderIndex] as? HomeItem.FolderItem ?: return@edit

            // Use the user-supplied name if non-blank, otherwise default back to "Folder".
            val safeName = newName.trim().ifBlank { "Folder" }
            currentItems[folderIndex] = folder.copy(name = safeName)

            serializeItems(currentItems, preferences)
            wasApplied = true
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
    override suspend fun extractItemFromFolder(
        folderId: String,
        itemId: String,
        targetPosition: GridPosition
    ): Boolean {
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            // Find the folder.
            val folderIndex = currentItems.indexOfFirst { it.id == folderId }
            if (folderIndex == -1) return@edit

            val folder = currentItems[folderIndex] as? HomeItem.FolderItem ?: return@edit

            // Find the item inside the folder.
            val childItem = folder.children.find { it.id == itemId } ?: return@edit

            // Occupancy check: the target must be empty or occupied by the folder itself.
            val targetOccupant = currentItems.find {
                it.id != folderId && it.position == targetPosition
            }
            if (targetOccupant != null) {
                // Target is occupied by something else — reject the operation.
                return@edit
            }

            // Build the updated children list without the extracted item.
            val remainingChildren = folder.children.filterNot { it.id == itemId }

            when (remainingChildren.size) {
                0 -> {
                    // Folder becomes empty — delete it.
                    currentItems.removeAt(folderIndex)
                }
                1 -> {
                    // Only one child left — promote it to the folder's position
                    // and delete the folder.
                    val promotedChild = remainingChildren.first().withPosition(folder.position)
                    currentItems.removeAt(folderIndex)
                    currentItems.add(promotedChild)
                }
                else -> {
                    // Folder still has multiple children — just update it.
                    currentItems[folderIndex] = folder.copy(children = remainingChildren)
                }
            }

            // Place the extracted item at the target position.
            currentItems.add(childItem.withPosition(targetPosition))

            serializeItems(currentItems, preferences)
            wasApplied = true
        }

        return wasApplied
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
    // ========================================================================
    // PRIVATE HELPER — folder eviction
    // ========================================================================

    /**
     * Removes the item with [itemId] from whichever [HomeItem.FolderItem] it
     * currently lives in, applying the standard folder cleanup policy.
     *
     * This helper operates directly on the already-deserialized [items] list
     * (a [MutableList] obtained inside a DataStore [edit] block).  It must be
     * called BEFORE any index-based look-ups on [items], because the list may
     * change size.
     *
     * WHY THIS EXISTS:
     * Items in the data model can live in exactly ONE place:
     *   (a) the flat home-screen [pinnedItems] list, or
     *   (b) inside a [HomeItem.FolderItem]'s children list.
     *
     * When an item is added to a new location — either directly onto the grid
     * via [pinOrMoveItemToPosition], or into a folder via [addItemToFolder] —
     * we must first evict it from wherever it currently is.  The existing flat-
     * list removal (`removeAll { it.id == item.id }`) already handles case (a);
     * this helper handles case (b).
     *
     * CLEANUP POLICY (identical to [removeItemFromFolder]):
     *   - 0 children remain after removal → folder deleted from [items].
     *   - 1 child  remains after removal → folder deleted; sole remaining child
     *     is promoted to the folder's home-screen grid position.
     *   - 2+ children remain             → folder updated in place.
     *
     * If [itemId] is not a child of any folder, this function is a no-op.
     *
     * @param items  The mutable flat home-items list (modified in-place).
     * @param itemId The ID of the child item to evict.
     */
    private fun evictItemFromFolderIfPresent(items: MutableList<HomeItem>, itemId: String) {
        // Find the index of the folder that contains this item as a child.
        // An item can only live in one folder at a time, so we stop at the first match.
        val folderIndex = items.indexOfFirst { candidate ->
            candidate is HomeItem.FolderItem &&
                candidate.children.any { child -> child.id == itemId }
        }

        // Item is not inside any folder — nothing to do.
        if (folderIndex == -1) return

        val folder = items[folderIndex] as HomeItem.FolderItem

        // Build the updated children list with the target item removed.
        val updatedChildren = folder.children.filterNot { it.id == itemId }

        when (updatedChildren.size) {
            0 -> {
                // No children left — delete the folder from the home screen.
                items.removeAt(folderIndex)
            }
            1 -> {
                // Only one child left — "unwrap" the folder:
                // delete the folder and promote its last child to the folder's position.
                val promotedChild = updatedChildren.first().withPosition(folder.position)
                items.removeAt(folderIndex)
                items.add(promotedChild)
            }
            else -> {
                // Two or more children remain — keep the folder, just update children.
                items[folderIndex] = folder.copy(children = updatedChildren)
            }
        }
    }

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
