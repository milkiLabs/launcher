package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem

internal data class FolderLookup(
    val index: Int,
    val folder: HomeItem.FolderItem
)

internal fun evictItemEverywhere(items: MutableList<HomeItem>, itemId: String) {
    items.removeAll { it.id == itemId }

    var folderIndex = items.indexOfFirst { candidate ->
        (candidate as? HomeItem.FolderItem)?.children?.any { it.id == itemId } == true
    }

    while (folderIndex != -1) {
        val folder = items[folderIndex] as HomeItem.FolderItem
        val remaining = folder.children.filterNot { it.id == itemId }
        applyFolderCleanup(items, folderIndex, folder, remaining)
        folderIndex = items.indexOfFirst { candidate ->
            (candidate as? HomeItem.FolderItem)?.children?.any { it.id == itemId } == true
        }
    }
}

internal fun removeChildFromFolderWithCleanup(
    items: MutableList<HomeItem>,
    folderId: String,
    childItemId: String
): HomeItem? {
    val folderLookup = findFolderLookup(items, folderId)
    val removedChild = folderLookup?.folder?.children?.firstOrNull { it.id == childItemId }

    if (folderLookup != null && removedChild != null) {
        val remaining = folderLookup.folder.children.filterNot { it.id == childItemId }
        applyFolderCleanup(items, folderLookup.index, folderLookup.folder, remaining)
    }

    return removedChild
}

internal fun applyFolderCleanup(
    items: MutableList<HomeItem>,
    folderIndex: Int,
    folder: HomeItem.FolderItem,
    remainingChildren: List<HomeItem>
) {
    when (remainingChildren.size) {
        0 -> items.removeAt(folderIndex)
        1 -> {
            val promoted = remainingChildren.first().withPosition(folder.position)
            items.removeAt(folderIndex)
            items.add(promoted)
        }

        else -> items[folderIndex] = folder.copy(children = remainingChildren)
    }
}

internal fun findFolderLookup(items: List<HomeItem>, folderId: String): FolderLookup? {
    val folderIndex = items.indexOfFirst { it.id == folderId }
    val folder = items.getOrNull(folderIndex) as? HomeItem.FolderItem
    return folder?.let { FolderLookup(folderIndex, it) }
}

/**
 * Appends [additions] to the folder [folderId], normalizing positions.
 * Returns false when the folder does not exist. Callers must not pass
 * nested folders or widgets; violations fail loudly instead of being
 * silently dropped, preserving the deterministic mutation contract.
 */
internal fun MutableList<HomeItem>.appendToFolder(
    folderId: String,
    additions: List<HomeItem>,
    targetIndex: Int? = null
): Boolean {
    require(additions.all { it !is HomeItem.FolderItem && it !is HomeItem.WidgetItem }) {
        "appendToFolder received invalid children (folders/widgets): " +
            additions.filter { it is HomeItem.FolderItem || it is HomeItem.WidgetItem }
                .joinToString { it.id }
    }
    val folderLookup = findFolderLookup(this, folderId) ?: return false

    val children = folderLookup.folder.children.toMutableList()
    val insertAt = targetIndex?.coerceIn(0, children.size) ?: children.size
    children.addAll(
        insertAt,
        additions.map { it.withPosition(GridPosition.DEFAULT) }
    )

    this[folderLookup.index] = folderLookup.folder.copy(children = children)
    return true
}

internal fun containsItemIdAnywhere(items: List<HomeItem>, itemId: String): Boolean {
    return items.any { it.id == itemId } ||
        items.any { candidate ->
            (candidate as? HomeItem.FolderItem)?.children?.any { it.id == itemId } == true
        }
}

internal fun findLiveNonFolderTarget(
    items: List<HomeItem>,
    targetItemId: String,
    atPosition: GridPosition
): HomeItem? {
    return items.firstOrNull { item ->
        item.id == targetItemId &&
            item.position == atPosition &&
            item !is HomeItem.FolderItem &&
            item !is HomeItem.WidgetItem
    }
}
