package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem


internal data class FolderLookup(
    val index: Int,
    val folder: HomeItem.FolderItem
)

internal fun isWithinGrid(
    position: GridPosition,
    span: GridSpan,
    gridColumns: Int
): Boolean {
    return position.row >= 0 &&
        position.column >= 0 &&
        position.column + span.columns <= gridColumns
}

internal fun isSpanFree(
    position: GridPosition,
    span: GridSpan,
    occupied: Map<GridPosition, String>
): Boolean {
    return span.occupiedPositions(position).none { cell -> cell in occupied }
}

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

internal fun containsItemIdAnywhere(items: List<HomeItem>, itemId: String): Boolean {
    return items.any { it.id == itemId } ||
        items.any { candidate ->
            (candidate as? HomeItem.FolderItem)?.children?.any { it.id == itemId } == true
        }
}

internal fun findAvailablePosition(
    items: List<HomeItem>,
    maxRows: Int,
    gridColumns: Int
): GridPosition {
    val occupied = HomeGraph.buildOccupiedCells(items)
    val firstFreePosition = (0 until maxRows)
        .asSequence()
        .flatMap { row ->
            (0 until gridColumns).asSequence().map { column -> GridPosition(row, column) }
        }
        .firstOrNull { position -> position !in occupied }

    return firstFreePosition ?: GridPosition(maxRows, 0)
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
