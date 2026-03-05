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

    // ========================================================================
    // FOLDER OPERATIONS
    // ========================================================================

    /**
     * Creates a new folder from two existing home screen items.
     *
     * WHY THIS OPERATION IS ATOMIC:
     * We need to remove both source items from the top-level list AND add the
     * new FolderItem in a single DataStore edit transaction. If we did this in
     * separate steps, an intermediate state (one item removed, folder not yet
     * added) could be observed by the Flow subscriber, causing a visual glitch.
     *
     * FOLDER PLACEMENT:
     * The folder will appear at [atPosition] on the grid — the cell that was
     * previously occupied by one of the two items.
     *
     * NESTING GUARD:
     * If either item is itself a FolderItem, this method returns null and performs
     * no writes. Nested folders are not supported.
     *
     * @param item1 The first item (the one that was dragged onto item2)
     * @param item2 The second item (the one being dropped onto)
     * @param atPosition Where the new folder should appear on the home grid (typically item2's position)
     * @return The created FolderItem, or null if either item was already a folder.
     */
    suspend fun createFolder(
        item1: HomeItem,
        item2: HomeItem,
        atPosition: GridPosition
    ): HomeItem.FolderItem?

    /**
     * Adds an existing home screen item into a folder's children list.
     *
     * BEHAVIOR:
     * - If [item] is already a top-level home screen item, it is removed from
     *   the top level and appended to the folder's children.
     * - If [item] is already a child of the target folder, this is a no-op.
     * - If [item] is a FolderItem, this call is rejected (no nesting).
     * - [targetIndex] controls where in the children list the item is inserted.
     *   If null, item is appended at the end.
     *
     * CLEANUP:
     * No cleanup is performed for the source position — the item is simply moved
     * from the top-level list into the folder's children.
     *
     * @param folderId The [HomeItem.FolderItem.id] of the target folder.
     * @param item The home item to add. Must not be a FolderItem.
     * @param targetIndex Optional insertion index within the folder's children list.
     *                    Null means append at end.
     * @return true if the item was successfully added; false if rejected (folder not found,
     *         item is a folder, or item is already in the folder).
     */
    suspend fun addItemToFolder(
        folderId: String,
        item: HomeItem,
        targetIndex: Int? = null
    ): Boolean

    /**
     * Removes an item from a folder's children list and applies the cleanup policy.
     *
     * CLEANUP POLICY (applied atomically after removal):
     * - 0 children remaining → the folder itself is deleted from the home screen.
     * - 1 child remaining   → the folder is deleted and the remaining child is promoted
     *                         to the home screen at the folder's former position.
     * - 2+ children remaining → folder is updated with the smaller children list.
     *
     * RETURN SEMANTICS:
     * Returns the updated folder (after removal) if it still exists, or null if the
     * folder was deleted by the cleanup policy. The caller can use this to decide
     * whether to keep a popup open.
     *
     * @param folderId The [HomeItem.FolderItem.id] of the folder.
     * @param itemId The [HomeItem.id] of the child to remove.
     * @return The updated FolderItem (with child removed) if folder still exists; null if folder deleted.
     */
    suspend fun removeItemFromFolder(
        folderId: String,
        itemId: String
    ): HomeItem.FolderItem?

    /**
     * Replaces the children list of a folder with a new ordered list.
     *
     * Used when the user drags items around inside the folder popup to reorder them.
     *
     * Only the children order/content changes; folder ID, name, and home screen
     * position remain the same.
     *
     * @param folderId The [HomeItem.FolderItem.id] of the folder.
     * @param newChildren The complete new children list in display order.
     * @return true if the folder was found and updated; false if not found.
     */
    suspend fun reorderFolderItems(
        folderId: String,
        newChildren: List<HomeItem>
    ): Boolean

    /**
     * Merges the children of [sourceFolderId] into [targetFolderId] and deletes the source folder.
     *
     * This is called when the user drags one folder onto another.
     *
     * BEHAVIOR:
     * - All children of the source folder are appended to the target folder's children.
     * - The source folder is removed from the home screen.
     * - The target folder remains at its original position.
     *
     * DUPLICATE GUARD:
     * Children with IDs already present in the target folder are skipped
     * (same item shouldn't appear twice in a folder).
     *
     * @param sourceFolderId The folder being dragged (will be deleted after merge).
     * @param targetFolderId The folder being dropped onto (will receive the merged children).
     * @return true if the merge succeeded; false if either folder was not found.
     */
    suspend fun mergeFolders(
        sourceFolderId: String,
        targetFolderId: String
    ): Boolean

    /**
     * Renames a folder.
     *
     * Called when the user edits the folder title text field inside the popup dialog.
     * The name is trimmed of leading/trailing whitespace; if blank after trimming,
     * it defaults back to "Folder".
     *
     * @param folderId The [HomeItem.FolderItem.id] of the folder to rename.
     * @param newName The desired new name.
     * @return true if the folder was found and renamed; false if not found.
     */
    suspend fun renameFolder(
        folderId: String,
        newName: String
    ): Boolean

    /**
     * Moves an item from inside a folder to the home screen at a specific grid position.
     *
     * This is the "drag out" operation: when the user drags a folder icon outside
     * the folder popup and drops it onto the home grid.
     *
     * STEPS (all in one atomic DataStore edit):
     * 1. Remove the item from the folder's children.
     * 2. Apply folder cleanup policy (delete folder if ≤1 child left after removal).
     * 3. Place the item at [targetPosition] on the home screen grid (if space is free).
     *
     * OCCUPANCY CHECK:
     * If [targetPosition] is already occupied by another item (other than the current
     * folder), the operation is rejected and returns false. The item stays in the folder.
     *
     * @param folderId The [HomeItem.FolderItem.id] containing the item to extract.
     * @param itemId The [HomeItem.id] of the item to extract.
     * @param targetPosition The desired home screen grid cell for the extracted item.
     * @return true if the item was extracted and placed; false if the target was occupied
     *         or either ID was not found.
     */
    suspend fun extractItemFromFolder(
        folderId: String,
        itemId: String,
        targetPosition: GridPosition
    ): Boolean

    /**
     * Atomically moves a child item from one folder into another folder.
     *
     * WHY THIS API EXISTS:
     * Older call sites implemented this as two independent repository calls:
     * 1) removeItemFromFolder(source)
     * 2) addItemToFolder(target)
     *
     * Even when both calls happen quickly, they are still separate DataStore edit
     * transactions with an observable intermediate state. This method removes that
     * gap by performing the entire transfer in one edit block.
     *
     * OPERATION RULES:
     * - [sourceFolderId] and [targetFolderId] must both exist and be FolderItems.
     * - [itemId] must exist as a child in source folder.
     * - Source and target must be different folder IDs.
     * - Target folder will not receive duplicate IDs.
     * - Folder cleanup policy is applied to source after child removal.
     *
     * @param sourceFolderId Folder the child is moved FROM.
     * @param targetFolderId Folder the child is moved TO.
     * @param itemId ID of the child being moved.
     * @return true when transfer succeeds; false when rejected by guards.
     */
    suspend fun moveItemBetweenFolders(
        sourceFolderId: String,
        targetFolderId: String,
        itemId: String
    ): Boolean

    /**
     * Atomically removes a child from a source folder and creates a new folder at
     * [atPosition] with that child + [occupantItem].
     *
     * This models dropping a folder child onto an occupied non-folder home cell.
     *
     * ATOMICITY GUARANTEE:
     * - Child removal from source folder
     * - Source-folder cleanup policy
     * - Occupant removal from top-level grid
     * - New folder creation at [atPosition]
     *
     * All happen in a single DataStore edit transaction.
     *
     * VALIDATION RULES:
     * - Source folder must exist and contain [childItemId].
     * - [occupantItem] must be a top-level non-folder item currently at [atPosition].
     * - Nested folders are not allowed.
     * - Global uniqueness by ID is enforced before writing the new folder.
     *
     * @param sourceFolderId Folder containing the child to extract.
     * @param childItemId ID of the source-folder child to extract.
     * @param occupantItem Top-level item currently occupying [atPosition].
     * @param atPosition Grid position where the new folder should appear.
     * @return The newly created folder when successful; null when rejected.
     */
    suspend fun extractFolderChildOntoItem(
        sourceFolderId: String,
        childItemId: String,
        occupantItem: HomeItem,
        atPosition: GridPosition
    ): HomeItem.FolderItem?
}
