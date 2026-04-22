package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem

/**
 * Deterministic mutation engine for home layout operations.
 *
 * The rules live in one file on purpose so the write path is easy to follow.
 */
class HomeModelWriter(
    private val gridColumns: Int = HomeGridDefaults.COLUMNS
) {

    sealed interface Command {
        fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result

        data class AddPinnedItem(
            val item: HomeItem,
            val maxRows: Int = 100
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.addPinnedItem(currentItems, this)
            }
        }

        data class MoveTopLevelItem(
            val itemId: String,
            val newPosition: GridPosition
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.moveTopLevelItem(currentItems, this)
            }
        }

        data class PinOrMoveToPosition(
            val item: HomeItem,
            val targetPosition: GridPosition
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.pinOrMove(currentItems, this)
            }
        }

        data class RemoveItemById(
            val itemId: String
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.removeItemsById(
                    currentItems = currentItems,
                    command = RemoveItemsById(itemIds = setOf(itemId))
                )
            }
        }

        data class RemoveItemsById(
            val itemIds: Set<String>
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.removeItemsById(currentItems, this)
            }
        }

        data class CreateFolder(
            val draggedItem: HomeItem,
            val targetItemId: String,
            val atPosition: GridPosition
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.createFolder(currentItems, this)
            }
        }

        data class AddItemToFolder(
            val folderId: String,
            val item: HomeItem,
            val targetIndex: Int? = null
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.addItemToFolder(currentItems, this)
            }
        }

        data class RemoveItemFromFolder(
            val folderId: String,
            val itemId: String
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.removeItemFromFolder(currentItems, this)
            }
        }

        data class ReorderFolderItems(
            val folderId: String,
            val newChildren: List<HomeItem>
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.reorderFolderItems(currentItems, this)
            }
        }

        data class MoveItemBetweenFolders(
            val sourceFolderId: String,
            val targetFolderId: String,
            val itemId: String
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.moveItemBetweenFolders(currentItems, this)
            }
        }

        data class ExtractFolderChildOntoItem(
            val sourceFolderId: String,
            val childItemId: String,
            val targetItemId: String,
            val atPosition: GridPosition
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.extractFolderChildOntoItem(currentItems, this)
            }
        }

        data class MergeFolders(
            val sourceFolderId: String,
            val targetFolderId: String
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.mergeFolders(currentItems, this)
            }
        }

        data class RenameFolder(
            val folderId: String,
            val newName: String
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.renameFolder(currentItems, this)
            }
        }

        data class ExtractItemFromFolder(
            val folderId: String,
            val itemId: String,
            val targetPosition: GridPosition
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.extractItemFromFolder(currentItems, this)
            }
        }

        data class UpdateWidgetFrame(
            val widgetId: String,
            val newPosition: GridPosition,
            val newSpan: GridSpan
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.updateWidgetFrame(currentItems, this)
            }
        }
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
        return command.execute(this, currentItems)
    }

    private fun addPinnedItem(
        currentItems: List<HomeItem>,
        command: Command.AddPinnedItem
    ): Result {
        if (containsItemIdAnywhere(currentItems, command.item.id)) {
            return Result.Rejected(Error.DuplicateItem)
        }

        val mutable = currentItems.toMutableList()
        val position = findAvailablePosition(mutable, command.maxRows, gridColumns)
        mutable.add(command.item.withPosition(position))
        return Result.Applied(mutable)
    }

    private fun moveTopLevelItem(
        currentItems: List<HomeItem>,
        command: Command.MoveTopLevelItem
    ): Result {
        val index = currentItems.indexOfFirst { it.id == command.itemId }
        val existing = currentItems.getOrNull(index)
        val span = (existing as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
        val occupied = if (existing == null) {
            emptyMap()
        } else {
            HomeGraph.buildOccupiedCells(
                items = currentItems,
                excludeItemId = command.itemId
            )
        }

        val rejection = when {
            existing == null -> Error.ItemNotFound
            !isWithinGrid(command.newPosition, span, gridColumns) -> Error.OutOfBounds
            !isSpanFree(command.newPosition, span, occupied) -> Error.TargetOccupied
            else -> null
        }

        val result = if (rejection != null) {
            Result.Rejected(rejection)
        } else {
            val updated = currentItems.toMutableList()
            updated[index] = requireNotNull(existing).withPosition(command.newPosition)
            Result.Applied(updated)
        }
        return result
    }

    private fun pinOrMove(
        currentItems: List<HomeItem>,
        command: Command.PinOrMoveToPosition
    ): Result {
        val mutable = currentItems.toMutableList()
        val existingIndex = mutable.indexOfFirst { it.id == command.item.id }
        val existingItem = if (existingIndex >= 0) mutable[existingIndex] else null

        evictItemEverywhere(mutable, command.item.id)

        val canonicalItem = existingItem ?: command.item
        val span = (canonicalItem as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
        val occupied = HomeGraph.buildOccupiedCells(mutable)
        val rejection = when {
            !isWithinGrid(command.targetPosition, span, gridColumns) -> Error.OutOfBounds
            !isSpanFree(command.targetPosition, span, occupied) -> Error.TargetOccupied
            else -> null
        }

        val result = if (rejection != null) {
            Result.Rejected(rejection)
        } else {
            val placed = canonicalItem.withPosition(command.targetPosition)
            val insertionIndex = if (existingIndex in 0..mutable.size) existingIndex else mutable.size
            mutable.add(insertionIndex, placed)
            Result.Applied(mutable)
        }
        return result
    }

    private fun removeItemsById(
        currentItems: List<HomeItem>,
        command: Command.RemoveItemsById
    ): Result {
        val existingIds = command.itemIds.filterTo(mutableSetOf()) { itemId ->
            containsItemIdAnywhere(currentItems, itemId)
        }

        val rejection = if (command.itemIds.isEmpty() || existingIds.isEmpty()) {
            Error.ItemNotFound
        } else {
            null
        }

        val result = if (rejection != null) {
            Result.Rejected(rejection)
        } else {
            val mutable = currentItems.toMutableList()
            existingIds.forEach { itemId ->
                evictItemEverywhere(mutable, itemId)
            }
            Result.Applied(mutable)
        }
        return result
    }

    private fun createFolder(
        currentItems: List<HomeItem>,
        command: Command.CreateFolder
    ): Result {
        val mutable = currentItems.toMutableList()
        val liveTarget = findLiveNonFolderTarget(
            items = mutable,
            targetItemId = command.targetItemId,
            atPosition = command.atPosition
        )

        val rejection = when {
            command.draggedItem is HomeItem.FolderItem || command.draggedItem is HomeItem.WidgetItem ->
                Error.InvalidFolderOperation
            liveTarget == null -> Error.ItemNotFound
            command.draggedItem.id == liveTarget.id -> Error.InvalidFolderOperation
            else -> null
        }

        val result = if (rejection != null) {
            Result.Rejected(rejection)
        } else {
            val confirmedTarget = requireNotNull(liveTarget)
            evictItemEverywhere(mutable, command.draggedItem.id)
            evictItemEverywhere(mutable, confirmedTarget.id)

            val folder = HomeItem.FolderItem.create(
                command.draggedItem,
                confirmedTarget,
                command.atPosition
            )
            mutable.add(folder)
            Result.Applied(mutable)
        }
        return result
    }

    private fun addItemToFolder(
        currentItems: List<HomeItem>,
        command: Command.AddItemToFolder
    ): Result {
        val mutable = currentItems.toMutableList()
        val folderLookup = findFolderLookup(mutable, command.folderId)
        val rejection = when {
            command.item is HomeItem.FolderItem || command.item is HomeItem.WidgetItem ->
                Error.InvalidFolderOperation
            folderLookup == null -> Error.FolderNotFound
            folderLookup.folder.children.any { it.id == command.item.id } -> Error.DuplicateItem
            else -> null
        }

        val result = if (rejection != null) {
            Result.Rejected(rejection)
        } else {
            evictItemEverywhere(mutable, command.item.id)
            val updatedFolderLookup = findFolderLookup(mutable, command.folderId)
            if (updatedFolderLookup == null) {
                Result.Rejected(Error.FolderNotFound)
            } else {
                val children = updatedFolderLookup.folder.children.toMutableList()
                val insertAt = command.targetIndex?.coerceIn(0, children.size) ?: children.size
                children.add(insertAt, command.item.withPosition(GridPosition.DEFAULT))

                mutable[updatedFolderLookup.index] = updatedFolderLookup.folder.copy(children = children)
                Result.Applied(mutable)
            }
        }
        return result
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
        val folderLookup = findFolderLookup(mutable, command.folderId)
            ?: return Result.Rejected(Error.FolderNotFound)

        val safeChildren = command.newChildren
            .filterNot { it is HomeItem.FolderItem || it is HomeItem.WidgetItem }
            .map { it.withPosition(GridPosition.DEFAULT) }

        mutable[folderLookup.index] = folderLookup.folder.copy(children = safeChildren)
        return Result.Applied(mutable)
    }

    private fun moveItemBetweenFolders(
        currentItems: List<HomeItem>,
        command: Command.MoveItemBetweenFolders
    ): Result {
        val mutable = currentItems.toMutableList()
        val source = findFolderLookup(mutable, command.sourceFolderId)?.folder
        val target = findFolderLookup(mutable, command.targetFolderId)?.folder
        val child = source?.children?.firstOrNull { it.id == command.itemId }
        val rejection = when {
            command.sourceFolderId == command.targetFolderId -> Error.InvalidFolderOperation
            source == null || target == null -> Error.FolderNotFound
            child == null -> Error.ItemNotFound
            child is HomeItem.WidgetItem -> Error.InvalidFolderOperation
            target.children.any { it.id == command.itemId } -> Error.DuplicateItem
            else -> null
        }

        val result = if (rejection != null) {
            Result.Rejected(rejection)
        } else {
            evictItemEverywhere(mutable, command.itemId)

            val updatedTargetLookup = findFolderLookup(mutable, command.targetFolderId)
            if (updatedTargetLookup == null) {
                Result.Rejected(Error.FolderNotFound)
            } else {
                val children = updatedTargetLookup.folder.children.toMutableList()
                children.add(requireNotNull(child).withPosition(GridPosition.DEFAULT))
                mutable[updatedTargetLookup.index] = updatedTargetLookup.folder.copy(children = children)
                Result.Applied(mutable)
            }
        }
        return result
    }

    private fun extractFolderChildOntoItem(
        currentItems: List<HomeItem>,
        command: Command.ExtractFolderChildOntoItem
    ): Result {
        val mutable = currentItems.toMutableList()
        val source = findFolderLookup(mutable, command.sourceFolderId)?.folder
        val child = source?.children?.firstOrNull { it.id == command.childItemId }
        val liveTarget = findLiveNonFolderTarget(
            items = mutable,
            targetItemId = command.targetItemId,
            atPosition = command.atPosition
        )

        val rejection = when {
            source == null -> Error.FolderNotFound
            child == null -> Error.ItemNotFound
            child is HomeItem.WidgetItem -> Error.InvalidFolderOperation
            liveTarget == null -> Error.ItemNotFound
            child.id == liveTarget.id -> Error.InvalidFolderOperation
            else -> null
        }

        val result = if (rejection != null) {
            Result.Rejected(rejection)
        } else {
            val confirmedChild = requireNotNull(child)
            val confirmedTarget = requireNotNull(liveTarget)
            evictItemEverywhere(mutable, confirmedChild.id)
            evictItemEverywhere(mutable, confirmedTarget.id)

            val folder = HomeItem.FolderItem.create(confirmedChild, confirmedTarget, command.atPosition)
            mutable.add(folder)
            Result.Applied(mutable)
        }
        return result
    }

    private fun mergeFolders(
        currentItems: List<HomeItem>,
        command: Command.MergeFolders
    ): Result {
        val mutable = currentItems.toMutableList()
        val sourceLookup = findFolderLookup(mutable, command.sourceFolderId)
        val targetLookup = findFolderLookup(mutable, command.targetFolderId)
        val rejection = if (sourceLookup == null || targetLookup == null) {
            Error.FolderNotFound
        } else {
            null
        }

        val result = if (rejection != null) {
            Result.Rejected(rejection)
        } else {
            val targetChildIds = requireNotNull(targetLookup).folder.children.map { it.id }.toSet()
            val merged = targetLookup.folder.children + requireNotNull(sourceLookup).folder.children
                .filterNot { it.id in targetChildIds }
                .filterNot { it is HomeItem.FolderItem || it is HomeItem.WidgetItem }
                .map { it.withPosition(GridPosition.DEFAULT) }

            mutable.removeAll { it.id == command.sourceFolderId }
            val updatedTargetLookup = findFolderLookup(mutable, command.targetFolderId)
            if (updatedTargetLookup == null) {
                Result.Rejected(Error.FolderNotFound)
            } else {
                mutable[updatedTargetLookup.index] = updatedTargetLookup.folder.copy(children = merged)
                Result.Applied(mutable)
            }
        }
        return result
    }

    private fun renameFolder(
        currentItems: List<HomeItem>,
        command: Command.RenameFolder
    ): Result {
        val mutable = currentItems.toMutableList()
        val folderLookup = findFolderLookup(mutable, command.folderId)
            ?: return Result.Rejected(Error.FolderNotFound)
        val safeName = command.newName.trim().ifBlank { "Folder" }
        mutable[folderLookup.index] = folderLookup.folder.copy(name = safeName)
        return Result.Applied(mutable)
    }

    private fun extractItemFromFolder(
        currentItems: List<HomeItem>,
        command: Command.ExtractItemFromFolder
    ): Result {
        val mutable = currentItems.toMutableList()
        val occupied = HomeGraph.buildOccupiedCells(mutable, excludeItemId = command.folderId)
        val child = removeChildFromFolderWithCleanup(
            items = mutable,
            folderId = command.folderId,
            childItemId = command.itemId
        )

        val rejection = when {
            command.targetPosition in occupied -> Error.TargetOccupied
            child == null -> Error.ItemNotFound
            else -> null
        }

        val result = if (rejection != null) {
            Result.Rejected(rejection)
        } else {
            val confirmedChild = requireNotNull(child)
            evictItemEverywhere(mutable, confirmedChild.id)
            mutable.add(confirmedChild.withPosition(command.targetPosition))
            Result.Applied(mutable)
        }
        return result
    }

    private fun updateWidgetFrame(
        currentItems: List<HomeItem>,
        command: Command.UpdateWidgetFrame
    ): Result {
        val mutable = currentItems.toMutableList()
        val widgetIndex = mutable.indexOfFirst { it.id == command.widgetId }
        val widget = mutable.getOrNull(widgetIndex) as? HomeItem.WidgetItem
        val occupied = if (widget == null) {
            emptyMap()
        } else {
            HomeGraph.buildOccupiedCells(mutable, excludeItemId = command.widgetId)
        }
        val rejection = when {
            command.newSpan.columns < 1 || command.newSpan.rows < 1 -> Error.InvalidWidgetOperation
            widgetIndex == -1 -> Error.ItemNotFound
            widget == null -> Error.InvalidWidgetOperation
            !isWithinGrid(command.newPosition, command.newSpan, gridColumns) -> Error.OutOfBounds
            !isSpanFree(command.newPosition, command.newSpan, occupied) -> Error.TargetOccupied
            else -> null
        }

        val result = if (rejection != null) {
            Result.Rejected(rejection)
        } else {
            mutable[widgetIndex] = requireNotNull(widget)
                .withPosition(command.newPosition)
                .withSpan(command.newSpan)
            Result.Applied(mutable)
        }
        return result
    }
}

private data class FolderLookup(
    val index: Int,
    val folder: HomeItem.FolderItem
)

private fun isWithinGrid(
    position: GridPosition,
    span: GridSpan,
    gridColumns: Int
): Boolean {
    return position.row >= 0 &&
        position.column >= 0 &&
        position.column + span.columns <= gridColumns
}

private fun isSpanFree(
    position: GridPosition,
    span: GridSpan,
    occupied: Map<GridPosition, String>
): Boolean {
    return span.occupiedPositions(position).none { cell -> cell in occupied }
}

private fun evictItemEverywhere(items: MutableList<HomeItem>, itemId: String) {
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

private fun removeChildFromFolderWithCleanup(
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

private fun applyFolderCleanup(
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

private fun findFolderLookup(items: List<HomeItem>, folderId: String): FolderLookup? {
    val folderIndex = items.indexOfFirst { it.id == folderId }
    val folder = items.getOrNull(folderIndex) as? HomeItem.FolderItem
    return folder?.let { FolderLookup(folderIndex, it) }
}

private fun containsItemIdAnywhere(items: List<HomeItem>, itemId: String): Boolean {
    return items.any { it.id == itemId } ||
        items.any { candidate ->
            (candidate as? HomeItem.FolderItem)?.children?.any { it.id == itemId } == true
        }
}

private fun findAvailablePosition(
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

private fun findLiveNonFolderTarget(
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
