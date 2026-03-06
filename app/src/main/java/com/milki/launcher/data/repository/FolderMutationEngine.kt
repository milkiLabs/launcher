package com.milki.launcher.data.repository

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem

/**
 * Encapsulates folder-domain mutation rules for the home model.
 *
 * WHY THIS CLASS EXISTS:
 * `HomeRepositoryImpl` is responsible for persistence orchestration (DataStore read/edit/write),
 * but folder operations carry substantial domain logic (invariants, cleanup policy, dedup behavior).
 *
 * Extracting those rules into this engine gives us:
 * 1) A single home for folder invariants.
 * 2) Pure mutable-list transformations that are easy to unit test.
 * 3) A smaller repository class focused on storage concerns.
 *
 * IMPORTANT DESIGN CHOICE:
 * This class never touches DataStore or Android APIs. It mutates in-memory lists only.
 * The repository controls transaction boundaries and decides when to persist changes.
 */
class FolderMutationEngine {

    /**
     * Result object for remove-child operations where we need to distinguish:
     * - no-op rejections,
     * - successful deletion/unwrapping,
     * - successful mutation where folder still exists.
     */
    data class RemoveItemFromFolderResult(
        val wasApplied: Boolean,
        val updatedFolder: HomeItem.FolderItem?
    )

    /**
     * Creates a folder from two non-folder, non-widget items.
     *
     * Returns null when guards reject the operation.
     * On success, mutates [items] in place and returns the created folder.
     */
    fun createFolder(
        items: MutableList<HomeItem>,
        item1: HomeItem,
        item2: HomeItem,
        atPosition: GridPosition
    ): HomeItem.FolderItem? {
        if (
            item1 is HomeItem.FolderItem ||
            item2 is HomeItem.FolderItem ||
            item1 is HomeItem.WidgetItem ||
            item2 is HomeItem.WidgetItem
        ) {
            return null
        }

        val newFolder = HomeItem.FolderItem.create(item1, item2, atPosition)

        // Remove any existing copies first so final state keeps ID uniqueness.
        evictItemEverywhere(items, item1.id)
        evictItemEverywhere(items, item2.id)

        // Defensive top-level removal in case callers passed stale references.
        items.removeAll { it.id == item1.id || it.id == item2.id }
        items.add(newFolder)

        return newFolder
    }

    /**
     * Adds [item] to [folderId] at optional [targetIndex], enforcing no-nesting and no-widget-child rules.
     */
    fun addItemToFolder(
        items: MutableList<HomeItem>,
        folderId: String,
        item: HomeItem,
        targetIndex: Int?
    ): Boolean {
        if (item is HomeItem.FolderItem || item is HomeItem.WidgetItem) return false

        val folderIndex = items.indexOfFirst { it.id == folderId }
        if (folderIndex == -1) return false

        val folder = items[folderIndex] as? HomeItem.FolderItem ?: return false
        if (folder.children.any { it.id == item.id }) return false

        // Remove this ID from anywhere else before inserting into target folder.
        evictItemEverywhere(items, item.id)

        val updatedFolderIndex = items.indexOfFirst { it.id == folderId }
        if (updatedFolderIndex == -1) return false

        val updatedFolder = items[updatedFolderIndex] as? HomeItem.FolderItem ?: return false
        val updatedChildren = updatedFolder.children.toMutableList()
        val insertAt = targetIndex?.coerceIn(0, updatedChildren.size) ?: updatedChildren.size
        updatedChildren.add(insertAt, item.withPosition(GridPosition.DEFAULT))

        items[updatedFolderIndex] = updatedFolder.copy(children = updatedChildren)
        return true
    }

    /**
     * Removes [itemId] from [folderId] and applies cleanup policy (delete/unwrap/update).
     */
    fun removeItemFromFolder(
        items: MutableList<HomeItem>,
        folderId: String,
        itemId: String
    ): RemoveItemFromFolderResult {
        val removedChild = removeChildFromFolderWithCleanup(
            items = items,
            folderId = folderId,
            childItemId = itemId
        ) ?: return RemoveItemFromFolderResult(
            wasApplied = false,
            updatedFolder = null
        )

        val updatedFolder = items.firstOrNull { it.id == folderId } as? HomeItem.FolderItem

        return RemoveItemFromFolderResult(
            wasApplied = removedChild.id == itemId,
            updatedFolder = updatedFolder
        )
    }

    /**
     * Replaces folder children with [newChildren] after filtering out invalid child types.
     */
    fun reorderFolderItems(
        items: MutableList<HomeItem>,
        folderId: String,
        newChildren: List<HomeItem>
    ): Boolean {
        val folderIndex = items.indexOfFirst { it.id == folderId }
        if (folderIndex == -1) return false

        val folder = items[folderIndex] as? HomeItem.FolderItem ?: return false

        val safeChildren = newChildren
            .filterNot { it is HomeItem.FolderItem }
            .filterNot { it is HomeItem.WidgetItem }
            .map { it.withPosition(GridPosition.DEFAULT) }

        items[folderIndex] = folder.copy(children = safeChildren)
        return true
    }

    /**
     * Merges source children into target and removes the source folder.
     */
    fun mergeFolders(
        items: MutableList<HomeItem>,
        sourceFolderId: String,
        targetFolderId: String
    ): Boolean {
        val sourceIndex = items.indexOfFirst { it.id == sourceFolderId }
        val targetIndex = items.indexOfFirst { it.id == targetFolderId }
        if (sourceIndex == -1 || targetIndex == -1) return false

        val sourceFolder = items[sourceIndex] as? HomeItem.FolderItem ?: return false
        val targetFolder = items[targetIndex] as? HomeItem.FolderItem ?: return false

        val targetChildIds = targetFolder.children.map { it.id }.toSet()
        val newChildrenFromSource = sourceFolder.children
            .filterNot { it.id in targetChildIds }
            .filterNot { it is HomeItem.FolderItem }
            .filterNot { it is HomeItem.WidgetItem }
            .map { it.withPosition(GridPosition.DEFAULT) }

        val mergedChildren = targetFolder.children + newChildrenFromSource
        val updatedTarget = targetFolder.copy(children = mergedChildren)

        items.removeAll { it.id == sourceFolderId }

        val newTargetIndex = items.indexOfFirst { it.id == targetFolderId }
        if (newTargetIndex == -1) return false

        items[newTargetIndex] = updatedTarget
        return true
    }

    /**
     * Renames folder; blank names are normalized to "Folder".
     */
    fun renameFolder(
        items: MutableList<HomeItem>,
        folderId: String,
        newName: String
    ): Boolean {
        val folderIndex = items.indexOfFirst { it.id == folderId }
        if (folderIndex == -1) return false

        val folder = items[folderIndex] as? HomeItem.FolderItem ?: return false
        val safeName = newName.trim().ifBlank { "Folder" }

        items[folderIndex] = folder.copy(name = safeName)
        return true
    }

    /**
     * Extracts a folder child to top-level at [targetPosition].
     *
     * [targetPositionOccupiedByOtherItem] must be computed by the caller before mutation.
     */
    fun extractItemFromFolder(
        items: MutableList<HomeItem>,
        folderId: String,
        itemId: String,
        targetPosition: GridPosition,
        targetPositionOccupiedByOtherItem: Boolean
    ): Boolean {
        if (targetPositionOccupiedByOtherItem) return false

        val removedChild = removeChildFromFolderWithCleanup(
            items = items,
            folderId = folderId,
            childItemId = itemId
        ) ?: return false

        evictItemEverywhere(items, removedChild.id)
        items.add(removedChild.withPosition(targetPosition))
        return true
    }

    /**
     * Moves child [itemId] from source folder to target folder in a single in-memory mutation.
     */
    fun moveItemBetweenFolders(
        items: MutableList<HomeItem>,
        sourceFolderId: String,
        targetFolderId: String,
        itemId: String
    ): Boolean {
        if (sourceFolderId == targetFolderId) return false

        val sourceFolder = items.firstOrNull { it.id == sourceFolderId } as? HomeItem.FolderItem
            ?: return false
        if (sourceFolder.children.none { it.id == itemId }) return false

        val targetFolder = items.firstOrNull { it.id == targetFolderId } as? HomeItem.FolderItem
            ?: return false
        if (targetFolder.children.any { it.id == itemId }) return false

        val childToMove = sourceFolder.children.first { it.id == itemId }
        if (childToMove is HomeItem.WidgetItem) return false

        evictItemEverywhere(items, itemId)

        val updatedTargetIndex = items.indexOfFirst { it.id == targetFolderId }
        if (updatedTargetIndex == -1) return false

        val updatedTarget = items[updatedTargetIndex] as? HomeItem.FolderItem ?: return false
        val updatedChildren = updatedTarget.children.toMutableList()
        updatedChildren.add(childToMove.withPosition(GridPosition.DEFAULT))

        items[updatedTargetIndex] = updatedTarget.copy(children = updatedChildren)
        return true
    }

    /**
     * Removes a source-folder child and creates a new folder with that child plus [occupantItem].
     */
    fun extractFolderChildOntoItem(
        items: MutableList<HomeItem>,
        sourceFolderId: String,
        childItemId: String,
        occupantItem: HomeItem,
        atPosition: GridPosition
    ): HomeItem.FolderItem? {
        if (occupantItem is HomeItem.FolderItem || occupantItem is HomeItem.WidgetItem) return null

        val sourceFolder = items.firstOrNull { it.id == sourceFolderId } as? HomeItem.FolderItem
            ?: return null
        val childToMove = sourceFolder.children.firstOrNull { it.id == childItemId }
            ?: return null
        if (childToMove is HomeItem.WidgetItem) return null

        val liveOccupant = items.firstOrNull {
            it.id == occupantItem.id && it.position == atPosition && it !is HomeItem.FolderItem
        } ?: return null

        evictItemEverywhere(items, childToMove.id)
        evictItemEverywhere(items, liveOccupant.id)

        val newFolder = HomeItem.FolderItem.create(
            item1 = childToMove,
            item2 = liveOccupant,
            atPosition = atPosition
        )

        items.add(newFolder)
        return newFolder
    }

    /**
     * Folder-aware dedup helper shared by both folder and non-folder repository paths.
     */
    fun evictItemEverywhere(items: MutableList<HomeItem>, itemId: String) {
        items.removeAll { it.id == itemId }
        while (evictItemFromFolderIfPresent(items, itemId)) {
            // Keep evicting until no folder contains this ID.
        }
    }

    /**
     * Returns true when [itemId] exists either top-level or as a folder child.
     */
    fun containsItemIdAnywhere(items: List<HomeItem>, itemId: String): Boolean {
        if (items.any { it.id == itemId }) return true

        return items.any { item ->
            val folder = item as? HomeItem.FolderItem ?: return@any false
            folder.children.any { child -> child.id == itemId }
        }
    }

    /**
     * Removes the first occurrence of [itemId] found as a folder child.
     */
    private fun evictItemFromFolderIfPresent(items: MutableList<HomeItem>, itemId: String): Boolean {
        val folder = items.firstOrNull { candidate ->
            candidate is HomeItem.FolderItem && candidate.children.any { child -> child.id == itemId }
        } as? HomeItem.FolderItem

        if (folder == null) return false

        return removeChildFromFolderWithCleanup(
            items = items,
            folderId = folder.id,
            childItemId = itemId
        ) != null
    }

    /**
     * Core cleanup policy for folder-child removal.
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
}
