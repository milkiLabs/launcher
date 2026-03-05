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

            // Check for duplicate by ID across BOTH top-level items and folder children.
            if (containsItemIdAnywhere(currentItems, item.id)) {
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

            // Enforce uniqueness globally before placing this item on the top-level grid.
            // This evicts the same ID from any folder and from accidental duplicate
            // top-level entries, then we write exactly one canonical copy below.
            evictItemEverywhere(currentItems, item.id)

            // Re-evaluate indices AFTER the eviction — the list may have changed.
            val existingItemIndex = currentItems.indexOfFirst { it.id == item.id }

            // Build span-aware occupancy map excluding the item being placed.
            val occupiedCells = buildOccupiedCellsMap(currentItems, excludeItemId = item.id)

            // For widgets, verify the full span fits; for single-cell items, check just the cell.
            val span = (item as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
            if (!isSpanFree(targetPosition, span, occupiedCells, gridColumns = 4)) {
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

            // Build span-aware occupancy map excluding the item being moved.
            val occupiedCells = buildOccupiedCellsMap(currentItems, excludeItemId = itemId)

            // For widgets, check that the full span fits; for single-cell items, check the single cell.
            val span = (currentItem as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
            val wouldOverlap = !isSpanFree(newPosition, span, occupiedCells, gridColumns = 4)

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

            // Enforce global uniqueness before creating a new folder.
            // This guarantees each logical item ID exists in exactly one location.
            evictItemEverywhere(currentItems, item1.id)
            evictItemEverywhere(currentItems, item2.id)

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

            // Enforce global uniqueness by removing this ID from any other location
            // (top-level and/or folder children) before inserting into target folder.
            evictItemEverywhere(currentItems, item.id)

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
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            // STRICT GUARD: if the child is not present in the folder, this must be
            // a no-op. We intentionally do not apply cleanup in that case.
            val removedChild = removeChildFromFolderWithCleanup(
                items = currentItems,
                folderId = folderId,
                childItemId = itemId
            ) ?: return@edit

            // Child was found and removed. Determine whether the folder still exists.
            resultFolder = currentItems.firstOrNull { it.id == folderId } as? HomeItem.FolderItem
            wasApplied = removedChild.id == itemId

            serializeItems(currentItems, preferences)
        }

        return if (wasApplied) resultFolder else null
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
            if (folder.children.none { it.id == itemId }) return@edit

            // Span-aware occupancy check: the target must be empty or occupied by the folder itself.
            val occupiedCells = buildOccupiedCellsMap(currentItems, excludeItemId = folderId)
            if (targetPosition in occupiedCells) {
                // Target is occupied by something else — reject the operation.
                return@edit
            }

            // Remove from source folder and apply cleanup policy in one shared helper.
            // This keeps folder-removal logic identical across all write paths.
            val removedChild = removeChildFromFolderWithCleanup(
                items = currentItems,
                folderId = folderId,
                childItemId = itemId
            ) ?: return@edit

            // Enforce uniqueness before adding to top-level grid position.
            evictItemEverywhere(currentItems, removedChild.id)

            // Place the extracted item at the target position.
            currentItems.add(removedChild.withPosition(targetPosition))

            serializeItems(currentItems, preferences)
            wasApplied = true
        }

        return wasApplied
    }

    /**
     * Atomically moves a child from one folder into another folder.
     */
    override suspend fun moveItemBetweenFolders(
        sourceFolderId: String,
        targetFolderId: String,
        itemId: String
    ): Boolean {
        if (sourceFolderId == targetFolderId) return false

        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            val sourceFolder = currentItems.firstOrNull { it.id == sourceFolderId } as? HomeItem.FolderItem
                ?: return@edit
            if (sourceFolder.children.none { it.id == itemId }) {
                return@edit
            }

            val targetFolder = currentItems.firstOrNull { it.id == targetFolderId } as? HomeItem.FolderItem
                ?: return@edit

            // Keep existing semantics explicit: moving an item onto a folder that
            // already contains the same ID is rejected.
            if (targetFolder.children.any { it.id == itemId }) {
                return@edit
            }

            val childToMove = sourceFolder.children.first { it.id == itemId }

            // Remove any copy of this ID first (including source folder child) so the
            // final write is globally unique by item ID.
            evictItemEverywhere(currentItems, itemId)

            val updatedTargetIndex = currentItems.indexOfFirst { it.id == targetFolderId }
            if (updatedTargetIndex == -1) return@edit

            val updatedTarget = currentItems[updatedTargetIndex] as? HomeItem.FolderItem ?: return@edit
            val updatedChildren = updatedTarget.children.toMutableList()
            updatedChildren.add(childToMove.withPosition(GridPosition.DEFAULT))

            currentItems[updatedTargetIndex] = updatedTarget.copy(children = updatedChildren)

            serializeItems(currentItems, preferences)
            wasApplied = true
        }

        return wasApplied
    }

    /**
     * Atomically extracts a child from one folder and creates a new folder at an
     * occupied cell with that child + the current occupant.
     */
    override suspend fun extractFolderChildOntoItem(
        sourceFolderId: String,
        childItemId: String,
        occupantItem: HomeItem,
        atPosition: GridPosition
    ): HomeItem.FolderItem? {
        if (occupantItem is HomeItem.FolderItem) return null

        var createdFolder: HomeItem.FolderItem? = null

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            val sourceFolder = currentItems.firstOrNull { it.id == sourceFolderId } as? HomeItem.FolderItem
                ?: return@edit
            val childToMove = sourceFolder.children.firstOrNull { it.id == childItemId }
                ?: return@edit

            // The occupant must still be a top-level non-folder item at the exact
            // drop position; otherwise this drop route is no longer valid.
            val liveOccupant = currentItems.firstOrNull {
                it.id == occupantItem.id && it.position == atPosition && it !is HomeItem.FolderItem
            } ?: return@edit

            // Remove all existing copies before creating the final folder.
            evictItemEverywhere(currentItems, childToMove.id)
            evictItemEverywhere(currentItems, liveOccupant.id)

            val newFolder = HomeItem.FolderItem.create(
                item1 = childToMove,
                item2 = liveOccupant,
                atPosition = atPosition
            )

            currentItems.add(newFolder)
            serializeItems(currentItems, preferences)
            createdFolder = newFolder
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
    private fun evictItemFromFolderIfPresent(items: MutableList<HomeItem>, itemId: String): Boolean {
        // Find the index of the folder that contains this item as a child.
        // We remove one match per call; caller can loop to remove all matches.
        val folder = items.firstOrNull { candidate ->
            candidate is HomeItem.FolderItem && candidate.children.any { child -> child.id == itemId }
        } as? HomeItem.FolderItem

        // Item is not inside any folder — nothing to do.
        if (folder == null) return false

        return removeChildFromFolderWithCleanup(
            items = items,
            folderId = folder.id,
            childItemId = itemId
        ) != null
    }

    /**
     * Removes an item ID from top-level and all folders, applying source-folder
     * cleanup policy each time a folder child is evicted.
     */
    private fun evictItemEverywhere(items: MutableList<HomeItem>, itemId: String) {
        items.removeAll { it.id == itemId }

        // Run until no folder contains this child ID. We intentionally clean all
        // duplicates so every mutation leaves the data model globally unique by ID.
        while (evictItemFromFolderIfPresent(items, itemId)) {
            // Keep evicting until exhausted.
        }
    }

    /**
     * Removes one child from a specific folder and applies folder cleanup policy.
     *
     * @return the removed child when the child existed; null when no mutation happened.
     */
    private fun removeChildFromFolderWithCleanup(
        items: MutableList<HomeItem>,
        folderId: String,
        childItemId: String
    ): HomeItem? {
        val folderIndex = items.indexOfFirst { it.id == folderId }
        if (folderIndex == -1) return null

        val folder = items[folderIndex] as? HomeItem.FolderItem ?: return null
        val removedChild = folder.children.firstOrNull { it.id == childItemId } ?: return null
        val remainingChildren = folder.children.filterNot { it.id == childItemId }

        when (remainingChildren.size) {
            0 -> {
                items.removeAt(folderIndex)
            }
            1 -> {
                val promotedChild = remainingChildren.first().withPosition(folder.position)
                items.removeAt(folderIndex)
                items.add(promotedChild)
            }
            else -> {
                items[folderIndex] = folder.copy(children = remainingChildren)
            }
        }

        return removedChild
    }

    /**
     * Checks whether an item ID exists anywhere in the model (top-level or folder child).
     */
    private fun containsItemIdAnywhere(items: List<HomeItem>, itemId: String): Boolean {
        if (items.any { it.id == itemId }) return true

        return items.any { item ->
            val folder = item as? HomeItem.FolderItem ?: return@any false
            folder.children.any { child -> child.id == itemId }
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
    override suspend fun addWidget(widget: HomeItem.WidgetItem): Boolean {
        var wasApplied = false

        context.homeDataStore.edit { preferences ->
            val currentItems = deserializeItems(preferences).toMutableList()

            // Check that none of the widget's cells are occupied.
            val occupiedCells = buildOccupiedCellsMap(currentItems)
            if (!isSpanFree(widget.position, widget.span, occupiedCells)) {
                wasApplied = false
                return@edit
            }

            currentItems.add(widget)
            serializeItems(currentItems, preferences)
            wasApplied = true
        }

        return wasApplied
    }

    /**
     * Removes a widget from the home screen by its ID.
     *
     * The caller is responsible for deallocating the widget ID from
     * AppWidgetHost after this method returns.
     */
    override suspend fun removeWidget(widgetId: String) {
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
    override suspend fun updateWidgetSpan(widgetId: String, newSpan: GridSpan): Boolean {
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
