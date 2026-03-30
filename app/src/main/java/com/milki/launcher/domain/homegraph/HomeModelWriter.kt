package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem

/**
 * Simple, deterministic mutation engine for top-level home layout operations.
 */
class HomeModelWriter(
    private val gridColumns: Int = 5
) {

    sealed interface Command {
        data class AddPinnedItem(
            val item: HomeItem,
            val maxRows: Int = 100
        ) : Command

        data class MoveTopLevelItem(
            val itemId: String,
            val newPosition: GridPosition
        ) : Command

        data class PinOrMoveToPosition(
            val item: HomeItem,
            val targetPosition: GridPosition
        ) : Command

        data class RemoveItemById(
            val itemId: String
        ) : Command

        data class CreateFolder(
            val item1: HomeItem,
            val item2: HomeItem,
            val atPosition: GridPosition
        ) : Command

        data class AddItemToFolder(
            val folderId: String,
            val item: HomeItem,
            val targetIndex: Int? = null
        ) : Command

        data class RemoveItemFromFolder(
            val folderId: String,
            val itemId: String
        ) : Command

        data class ReorderFolderItems(
            val folderId: String,
            val newChildren: List<HomeItem>
        ) : Command

        data class MoveItemBetweenFolders(
            val sourceFolderId: String,
            val targetFolderId: String,
            val itemId: String
        ) : Command

        data class ExtractFolderChildOntoItem(
            val sourceFolderId: String,
            val childItemId: String,
            val occupantItem: HomeItem,
            val atPosition: GridPosition
        ) : Command

        data class MergeFolders(
            val sourceFolderId: String,
            val targetFolderId: String
        ) : Command

        data class RenameFolder(
            val folderId: String,
            val newName: String
        ) : Command

        data class ExtractItemFromFolder(
            val folderId: String,
            val itemId: String,
            val targetPosition: GridPosition
        ) : Command

        data class UpdateWidgetFrame(
            val widgetId: String,
            val newPosition: GridPosition,
            val newSpan: GridSpan
        ) : Command
    }

    sealed interface Error {
        data object ItemNotFound : Error
        data object DuplicateItem : Error
        data object TargetOccupied : Error
        data object OutOfBounds : Error
        data object FolderNotFound : Error
        data object InvalidFolderOperation : Error
        data object InvalidWidgetOperation : Error
    }

    sealed interface Result {
        data class Applied(val items: List<HomeItem>) : Result
        data class Rejected(val error: Error) : Result
    }

    fun apply(currentItems: List<HomeItem>, command: Command): Result {
        return when (command) {
            is Command.AddPinnedItem -> addPinnedItem(currentItems, command)
            is Command.MoveTopLevelItem -> moveTopLevelItem(currentItems, command)
            is Command.PinOrMoveToPosition -> pinOrMove(currentItems, command)
            is Command.RemoveItemById -> removeItemById(currentItems, command)
            is Command.CreateFolder -> createFolder(currentItems, command)
            is Command.AddItemToFolder -> addItemToFolder(currentItems, command)
            is Command.RemoveItemFromFolder -> removeItemFromFolder(currentItems, command)
            is Command.ReorderFolderItems -> reorderFolderItems(currentItems, command)
            is Command.MoveItemBetweenFolders -> moveItemBetweenFolders(currentItems, command)
            is Command.ExtractFolderChildOntoItem -> extractFolderChildOntoItem(currentItems, command)
            is Command.MergeFolders -> mergeFolders(currentItems, command)
            is Command.RenameFolder -> renameFolder(currentItems, command)
            is Command.ExtractItemFromFolder -> extractItemFromFolder(currentItems, command)
            is Command.UpdateWidgetFrame -> updateWidgetFrame(currentItems, command)
        }
    }

    private fun addPinnedItem(
        currentItems: List<HomeItem>,
        command: Command.AddPinnedItem
    ): Result {
        if (containsItemIdAnywhere(currentItems, command.item.id)) {
            return Result.Rejected(Error.DuplicateItem)
        }

        val mutable = currentItems.toMutableList()
        val position = findAvailablePosition(mutable, command.maxRows)
        val placed = command.item.withPosition(position)
        mutable.add(placed)
        return Result.Applied(mutable)
    }

    private fun moveTopLevelItem(
        currentItems: List<HomeItem>,
        command: Command.MoveTopLevelItem
    ): Result {
        val index = currentItems.indexOfFirst { it.id == command.itemId }
        if (index == -1) return Result.Rejected(Error.ItemNotFound)

        val existing = currentItems[index]
        val span = (existing as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
        if (!isWithinGrid(command.newPosition, span)) {
            return Result.Rejected(Error.OutOfBounds)
        }

        val occupied = HomeGraph.buildOccupiedCells(
            items = currentItems,
            excludeItemId = command.itemId
        )
        if (!isSpanFree(command.newPosition, span, occupied)) {
            return Result.Rejected(Error.TargetOccupied)
        }

        val updated = currentItems.toMutableList()
        updated[index] = existing.withPosition(command.newPosition)
        return Result.Applied(updated)
    }

    private fun pinOrMove(
        currentItems: List<HomeItem>,
        command: Command.PinOrMoveToPosition
    ): Result {
        val mutable = currentItems.toMutableList()

        val existingIndex = mutable.indexOfFirst { it.id == command.item.id }
        val existingItem = if (existingIndex >= 0) mutable[existingIndex] else null

        evictItemEverywhere(mutable, command.item.id)

        val canonicalItem = (existingItem ?: command.item)
        val span = (canonicalItem as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
        if (!isWithinGrid(command.targetPosition, span)) {
            return Result.Rejected(Error.OutOfBounds)
        }

        val occupied = HomeGraph.buildOccupiedCells(mutable)
        if (!isSpanFree(command.targetPosition, span, occupied)) {
            return Result.Rejected(Error.TargetOccupied)
        }

        val placed = canonicalItem.withPosition(command.targetPosition)
        val insertionIndex = if (existingIndex in 0..mutable.size) existingIndex else mutable.size
        mutable.add(insertionIndex, placed)

        return Result.Applied(mutable)
    }

    private fun removeItemById(
        currentItems: List<HomeItem>,
        command: Command.RemoveItemById
    ): Result {
        val mutable = currentItems.toMutableList()
        val before = mutable.size
        evictItemEverywhere(mutable, command.itemId)
        return if (mutable.size == before) {
            Result.Rejected(Error.ItemNotFound)
        } else {
            Result.Applied(mutable)
        }
    }

    private fun createFolder(
        currentItems: List<HomeItem>,
        command: Command.CreateFolder
    ): Result {
        if (command.item1 is HomeItem.FolderItem || command.item2 is HomeItem.FolderItem) {
            return Result.Rejected(Error.InvalidFolderOperation)
        }
        if (command.item1 is HomeItem.WidgetItem || command.item2 is HomeItem.WidgetItem) {
            return Result.Rejected(Error.InvalidFolderOperation)
        }

        val mutable = currentItems.toMutableList()
        evictItemEverywhere(mutable, command.item1.id)
        evictItemEverywhere(mutable, command.item2.id)

        val folder = HomeItem.FolderItem.create(command.item1, command.item2, command.atPosition)
        mutable.add(folder)
        return Result.Applied(mutable)
    }

    private fun addItemToFolder(
        currentItems: List<HomeItem>,
        command: Command.AddItemToFolder
    ): Result {
        if (command.item is HomeItem.FolderItem || command.item is HomeItem.WidgetItem) {
            return Result.Rejected(Error.InvalidFolderOperation)
        }

        val mutable = currentItems.toMutableList()
        val folderIndex = mutable.indexOfFirst { it.id == command.folderId }
        if (folderIndex == -1) return Result.Rejected(Error.FolderNotFound)
        val folder = mutable[folderIndex] as? HomeItem.FolderItem ?: return Result.Rejected(Error.FolderNotFound)

        if (folder.children.any { it.id == command.item.id }) {
            return Result.Rejected(Error.DuplicateItem)
        }

        evictItemEverywhere(mutable, command.item.id)
        val updatedFolderIndex = mutable.indexOfFirst { it.id == command.folderId }
        if (updatedFolderIndex == -1) return Result.Rejected(Error.FolderNotFound)
        val updatedFolder = mutable[updatedFolderIndex] as? HomeItem.FolderItem
            ?: return Result.Rejected(Error.FolderNotFound)

        val children = updatedFolder.children.toMutableList()
        val insertAt = command.targetIndex?.coerceIn(0, children.size) ?: children.size
        children.add(insertAt, command.item.withPosition(GridPosition.DEFAULT))

        mutable[updatedFolderIndex] = updatedFolder.copy(children = children)
        return Result.Applied(mutable)
    }

    private fun removeItemFromFolder(
        currentItems: List<HomeItem>,
        command: Command.RemoveItemFromFolder
    ): Result {
        val mutable = currentItems.toMutableList()
        val removed = removeChildFromFolderWithCleanup(
            items = mutable,
            folderId = command.folderId,
            childItemId = command.itemId
        ) ?: return Result.Rejected(Error.ItemNotFound)

        return if (removed.id == command.itemId) {
            Result.Applied(mutable)
        } else {
            Result.Rejected(Error.ItemNotFound)
        }
    }

    private fun reorderFolderItems(
        currentItems: List<HomeItem>,
        command: Command.ReorderFolderItems
    ): Result {
        val mutable = currentItems.toMutableList()
        val folderIndex = mutable.indexOfFirst { it.id == command.folderId }
        if (folderIndex == -1) return Result.Rejected(Error.FolderNotFound)
        val folder = mutable[folderIndex] as? HomeItem.FolderItem ?: return Result.Rejected(Error.FolderNotFound)

        val safeChildren = command.newChildren
            .filterNot { it is HomeItem.FolderItem || it is HomeItem.WidgetItem }
            .map { it.withPosition(GridPosition.DEFAULT) }

        mutable[folderIndex] = folder.copy(children = safeChildren)
        return Result.Applied(mutable)
    }

    private fun moveItemBetweenFolders(
        currentItems: List<HomeItem>,
        command: Command.MoveItemBetweenFolders
    ): Result {
        if (command.sourceFolderId == command.targetFolderId) {
            return Result.Rejected(Error.InvalidFolderOperation)
        }

        val mutable = currentItems.toMutableList()
        val source = mutable.firstOrNull { it.id == command.sourceFolderId } as? HomeItem.FolderItem
            ?: return Result.Rejected(Error.FolderNotFound)
        val target = mutable.firstOrNull { it.id == command.targetFolderId } as? HomeItem.FolderItem
            ?: return Result.Rejected(Error.FolderNotFound)

        val child = source.children.firstOrNull { it.id == command.itemId }
            ?: return Result.Rejected(Error.ItemNotFound)
        if (child is HomeItem.WidgetItem) return Result.Rejected(Error.InvalidFolderOperation)
        if (target.children.any { it.id == command.itemId }) return Result.Rejected(Error.DuplicateItem)

        evictItemEverywhere(mutable, command.itemId)

        val targetIndex = mutable.indexOfFirst { it.id == command.targetFolderId }
        if (targetIndex == -1) return Result.Rejected(Error.FolderNotFound)
        val updatedTarget = mutable[targetIndex] as? HomeItem.FolderItem ?: return Result.Rejected(Error.FolderNotFound)

        val children = updatedTarget.children.toMutableList()
        children.add(child.withPosition(GridPosition.DEFAULT))
        mutable[targetIndex] = updatedTarget.copy(children = children)

        return Result.Applied(mutable)
    }

    private fun extractFolderChildOntoItem(
        currentItems: List<HomeItem>,
        command: Command.ExtractFolderChildOntoItem
    ): Result {
        if (command.occupantItem is HomeItem.FolderItem || command.occupantItem is HomeItem.WidgetItem) {
            return Result.Rejected(Error.InvalidFolderOperation)
        }

        val mutable = currentItems.toMutableList()
        val source = mutable.firstOrNull { it.id == command.sourceFolderId } as? HomeItem.FolderItem
            ?: return Result.Rejected(Error.FolderNotFound)

        val child = source.children.firstOrNull { it.id == command.childItemId }
            ?: return Result.Rejected(Error.ItemNotFound)
        if (child is HomeItem.WidgetItem) return Result.Rejected(Error.InvalidFolderOperation)

        val liveOccupant = mutable.firstOrNull {
            it.id == command.occupantItem.id && it.position == command.atPosition && it !is HomeItem.FolderItem
        } ?: return Result.Rejected(Error.ItemNotFound)

        evictItemEverywhere(mutable, child.id)
        evictItemEverywhere(mutable, liveOccupant.id)

        val folder = HomeItem.FolderItem.create(child, liveOccupant, command.atPosition)
        mutable.add(folder)
        return Result.Applied(mutable)
    }

    private fun mergeFolders(
        currentItems: List<HomeItem>,
        command: Command.MergeFolders
    ): Result {
        val mutable = currentItems.toMutableList()
        val sourceIndex = mutable.indexOfFirst { it.id == command.sourceFolderId }
        val targetIndex = mutable.indexOfFirst { it.id == command.targetFolderId }
        if (sourceIndex == -1 || targetIndex == -1) return Result.Rejected(Error.FolderNotFound)

        val source = mutable[sourceIndex] as? HomeItem.FolderItem ?: return Result.Rejected(Error.FolderNotFound)
        val target = mutable[targetIndex] as? HomeItem.FolderItem ?: return Result.Rejected(Error.FolderNotFound)

        val targetChildIds = target.children.map { it.id }.toSet()
        val merged = target.children + source.children
            .filterNot { it.id in targetChildIds }
            .filterNot { it is HomeItem.FolderItem || it is HomeItem.WidgetItem }
            .map { it.withPosition(GridPosition.DEFAULT) }

        mutable.removeAll { it.id == command.sourceFolderId }
        val updatedTargetIndex = mutable.indexOfFirst { it.id == command.targetFolderId }
        if (updatedTargetIndex == -1) return Result.Rejected(Error.FolderNotFound)
        val updatedTarget = mutable[updatedTargetIndex] as? HomeItem.FolderItem
            ?: return Result.Rejected(Error.FolderNotFound)
        mutable[updatedTargetIndex] = updatedTarget.copy(children = merged)

        return Result.Applied(mutable)
    }

    private fun renameFolder(
        currentItems: List<HomeItem>,
        command: Command.RenameFolder
    ): Result {
        val mutable = currentItems.toMutableList()
        val folderIndex = mutable.indexOfFirst { it.id == command.folderId }
        if (folderIndex == -1) return Result.Rejected(Error.FolderNotFound)

        val folder = mutable[folderIndex] as? HomeItem.FolderItem ?: return Result.Rejected(Error.FolderNotFound)
        val safeName = command.newName.trim().ifBlank { "Folder" }
        mutable[folderIndex] = folder.copy(name = safeName)
        return Result.Applied(mutable)
    }

    private fun extractItemFromFolder(
        currentItems: List<HomeItem>,
        command: Command.ExtractItemFromFolder
    ): Result {
        val mutable = currentItems.toMutableList()

        val occupied = HomeGraph.buildOccupiedCells(mutable, excludeItemId = command.folderId)
        if (command.targetPosition in occupied) {
            return Result.Rejected(Error.TargetOccupied)
        }

        val child = removeChildFromFolderWithCleanup(
            items = mutable,
            folderId = command.folderId,
            childItemId = command.itemId
        ) ?: return Result.Rejected(Error.ItemNotFound)

        evictItemEverywhere(mutable, child.id)
        mutable.add(child.withPosition(command.targetPosition))
        return Result.Applied(mutable)
    }

    private fun updateWidgetFrame(
        currentItems: List<HomeItem>,
        command: Command.UpdateWidgetFrame
    ): Result {
        if (command.newSpan.columns < 1 || command.newSpan.rows < 1) {
            return Result.Rejected(Error.InvalidWidgetOperation)
        }

        val mutable = currentItems.toMutableList()
        val widgetIndex = mutable.indexOfFirst { it.id == command.widgetId }
        if (widgetIndex == -1) return Result.Rejected(Error.ItemNotFound)

        val widget = mutable[widgetIndex] as? HomeItem.WidgetItem
            ?: return Result.Rejected(Error.InvalidWidgetOperation)

        if (!isWithinGrid(command.newPosition, command.newSpan)) {
            return Result.Rejected(Error.OutOfBounds)
        }

        val occupied = HomeGraph.buildOccupiedCells(mutable, excludeItemId = command.widgetId)
        if (!isSpanFree(command.newPosition, command.newSpan, occupied)) {
            return Result.Rejected(Error.TargetOccupied)
        }

        mutable[widgetIndex] = widget
            .withPosition(command.newPosition)
            .withSpan(command.newSpan)
        return Result.Applied(mutable)
    }

    private fun isWithinGrid(position: GridPosition, span: GridSpan): Boolean {
        if (position.row < 0 || position.column < 0) return false
        return position.column + span.columns <= gridColumns
    }

    private fun isSpanFree(
        position: GridPosition,
        span: GridSpan,
        occupied: Map<GridPosition, String>
    ): Boolean {
        for (cell in span.occupiedPositions(position)) {
            if (cell in occupied) return false
        }
        return true
    }

    private fun evictItemEverywhere(items: MutableList<HomeItem>, itemId: String) {
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

    private fun removeChildFromFolderWithCleanup(
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

    private fun containsItemIdAnywhere(items: List<HomeItem>, itemId: String): Boolean {
        if (items.any { it.id == itemId }) return true
        return items.any { candidate ->
            val folder = candidate as? HomeItem.FolderItem ?: return@any false
            folder.children.any { it.id == itemId }
        }
    }

    private fun findAvailablePosition(items: List<HomeItem>, maxRows: Int): GridPosition {
        val occupied = HomeGraph.buildOccupiedCells(items)
        for (row in 0 until maxRows) {
            for (column in 0 until gridColumns) {
                val position = GridPosition(row, column)
                if (position !in occupied) return position
            }
        }
        return GridPosition(maxRows, 0)
    }
}
