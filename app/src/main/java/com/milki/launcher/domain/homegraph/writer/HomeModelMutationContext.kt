package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem

/**
 * Shared low-level utilities used by writer mutation modules.
 *
 * Keeping these helpers in one place avoids subtle behavior drift between
 * top-level, folder, and widget operations.
 */
internal class HomeModelMutationContext(
    private val gridColumns: Int
) {

    fun isWithinGrid(position: GridPosition, span: GridSpan): Boolean {
        if (position.row < 0 || position.column < 0) return false
        return position.column + span.columns <= gridColumns
    }

    fun isSpanFree(
        position: GridPosition,
        span: GridSpan,
        occupied: Map<GridPosition, String>
    ): Boolean {
        for (cell in span.occupiedPositions(position)) {
            if (cell in occupied) return false
        }
        return true
    }

    fun evictItemEverywhere(items: MutableList<HomeItem>, itemId: String) {
        items.removeAll { it.id == itemId }

        var removed = true
        while (removed) {
            removed = false
            for (index in items.indices) {
                val folder = items[index] as? HomeItem.FolderItem ?: continue
                if (folder.children.none { it.id == itemId }) continue

                val remaining = folder.children.filterNot { it.id == itemId }
                when (remaining.size) {
                    0 -> items.removeAt(index)
                    1 -> {
                        val promoted = remaining.first().withPosition(folder.position)
                        items.removeAt(index)
                        items.add(promoted)
                    }

                    else -> items[index] = folder.copy(children = remaining)
                }

                removed = true
                break
            }
        }
    }

    fun removeChildFromFolderWithCleanup(
        items: MutableList<HomeItem>,
        folderId: String,
        childItemId: String
    ): HomeItem? {
        val folderIndex = items.indexOfFirst { it.id == folderId }
        if (folderIndex == -1) return null

        val folder = items[folderIndex] as? HomeItem.FolderItem ?: return null
        val removedChild = folder.children.firstOrNull { it.id == childItemId } ?: return null
        val remaining = folder.children.filterNot { it.id == childItemId }

        when (remaining.size) {
            0 -> items.removeAt(folderIndex)
            1 -> {
                val promoted = remaining.first().withPosition(folder.position)
                items.removeAt(folderIndex)
                items.add(promoted)
            }

            else -> items[folderIndex] = folder.copy(children = remaining)
        }

        return removedChild
    }

    fun containsItemIdAnywhere(items: List<HomeItem>, itemId: String): Boolean {
        if (items.any { it.id == itemId }) return true
        return items.any { candidate ->
            val folder = candidate as? HomeItem.FolderItem ?: return@any false
            folder.children.any { it.id == itemId }
        }
    }

    fun findAvailablePosition(items: List<HomeItem>, maxRows: Int): GridPosition {
        val occupied = HomeGraph.buildOccupiedCells(items)
        for (row in 0 until maxRows) {
            for (column in 0 until gridColumns) {
                val position = GridPosition(row, column)
                if (position !in occupied) return position
            }
        }
        return GridPosition(maxRows, 0)
    }

    fun findLiveNonFolderTarget(
        items: List<HomeItem>,
        targetItemId: String,
        atPosition: GridPosition
    ): HomeItem? {
        return items.firstOrNull {
            it.id == targetItemId &&
                it.position == atPosition &&
                it !is HomeItem.FolderItem &&
                it !is HomeItem.WidgetItem
        }
    }
}