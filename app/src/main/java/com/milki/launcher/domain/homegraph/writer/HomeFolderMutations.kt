package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem

/**
 * Handles folder-focused operations for home layout mutations.
 */
internal class HomeFolderMutations(
    private val context: HomeModelMutationContext
) {

    fun createFolder(
        currentItems: List<HomeItem>,
        command: HomeModelWriter.Command.CreateFolder
    ): HomeModelWriter.Result {
        if (command.draggedItem is HomeItem.FolderItem || command.draggedItem is HomeItem.WidgetItem) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidFolderOperation)
        }

        val mutable = currentItems.toMutableList()
        val liveTarget = context.findLiveNonFolderTarget(
            items = mutable,
            targetItemId = command.targetItemId,
            atPosition = command.atPosition
        ) ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)

        if (command.draggedItem.id == liveTarget.id) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidFolderOperation)
        }

        // Drag source may come from grid/folder or external source; keep one final copy.
        context.evictItemEverywhere(mutable, command.draggedItem.id)
        context.evictItemEverywhere(mutable, liveTarget.id)

        val folder = HomeItem.FolderItem.create(command.draggedItem, liveTarget, command.atPosition)
        mutable.add(folder)
        return HomeModelWriter.Result.Applied(mutable)
    }

    fun addItemToFolder(
        currentItems: List<HomeItem>,
        command: HomeModelWriter.Command.AddItemToFolder
    ): HomeModelWriter.Result {
        if (command.item is HomeItem.FolderItem || command.item is HomeItem.WidgetItem) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidFolderOperation)
        }

        val mutable = currentItems.toMutableList()
        val folderIndex = mutable.indexOfFirst { it.id == command.folderId }
        if (folderIndex == -1) return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
        val folder = mutable[folderIndex] as? HomeItem.FolderItem
            ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)

        if (folder.children.any { it.id == command.item.id }) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.DuplicateItem)
        }

        context.evictItemEverywhere(mutable, command.item.id)
        val updatedFolderIndex = mutable.indexOfFirst { it.id == command.folderId }
        if (updatedFolderIndex == -1) return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
        val updatedFolder = mutable[updatedFolderIndex] as? HomeItem.FolderItem
            ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)

        val children = updatedFolder.children.toMutableList()
        val insertAt = command.targetIndex?.coerceIn(0, children.size) ?: children.size
        children.add(insertAt, command.item.withPosition(GridPosition.DEFAULT))

        mutable[updatedFolderIndex] = updatedFolder.copy(children = children)
        return HomeModelWriter.Result.Applied(mutable)
    }

    fun removeItemFromFolder(
        currentItems: List<HomeItem>,
        command: HomeModelWriter.Command.RemoveItemFromFolder
    ): HomeModelWriter.Result {
        val mutable = currentItems.toMutableList()
        val removed = context.removeChildFromFolderWithCleanup(
            items = mutable,
            folderId = command.folderId,
            childItemId = command.itemId
        ) ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)

        return if (removed.id == command.itemId) {
            HomeModelWriter.Result.Applied(mutable)
        } else {
            HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)
        }
    }

    fun reorderFolderItems(
        currentItems: List<HomeItem>,
        command: HomeModelWriter.Command.ReorderFolderItems
    ): HomeModelWriter.Result {
        val mutable = currentItems.toMutableList()
        val folderIndex = mutable.indexOfFirst { it.id == command.folderId }
        if (folderIndex == -1) return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
        val folder = mutable[folderIndex] as? HomeItem.FolderItem
            ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)

        val safeChildren = command.newChildren
            .filterNot { it is HomeItem.FolderItem || it is HomeItem.WidgetItem }
            .map { it.withPosition(GridPosition.DEFAULT) }

        mutable[folderIndex] = folder.copy(children = safeChildren)
        return HomeModelWriter.Result.Applied(mutable)
    }

    fun moveItemBetweenFolders(
        currentItems: List<HomeItem>,
        command: HomeModelWriter.Command.MoveItemBetweenFolders
    ): HomeModelWriter.Result {
        if (command.sourceFolderId == command.targetFolderId) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidFolderOperation)
        }

        val mutable = currentItems.toMutableList()
        val source = mutable.firstOrNull { it.id == command.sourceFolderId } as? HomeItem.FolderItem
            ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
        val target = mutable.firstOrNull { it.id == command.targetFolderId } as? HomeItem.FolderItem
            ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)

        val child = source.children.firstOrNull { it.id == command.itemId }
            ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)
        if (child is HomeItem.WidgetItem) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidFolderOperation)
        }
        if (target.children.any { it.id == command.itemId }) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.DuplicateItem)
        }

        context.evictItemEverywhere(mutable, command.itemId)

        val targetIndex = mutable.indexOfFirst { it.id == command.targetFolderId }
        if (targetIndex == -1) return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
        val updatedTarget = mutable[targetIndex] as? HomeItem.FolderItem
            ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)

        val children = updatedTarget.children.toMutableList()
        children.add(child.withPosition(GridPosition.DEFAULT))
        mutable[targetIndex] = updatedTarget.copy(children = children)

        return HomeModelWriter.Result.Applied(mutable)
    }

    fun extractFolderChildOntoItem(
        currentItems: List<HomeItem>,
        command: HomeModelWriter.Command.ExtractFolderChildOntoItem
    ): HomeModelWriter.Result {
        val mutable = currentItems.toMutableList()
        val source = mutable.firstOrNull { it.id == command.sourceFolderId } as? HomeItem.FolderItem
            ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)

        val child = source.children.firstOrNull { it.id == command.childItemId }
            ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)
        if (child is HomeItem.WidgetItem) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidFolderOperation)
        }

        val liveTarget = context.findLiveNonFolderTarget(
            items = mutable,
            targetItemId = command.targetItemId,
            atPosition = command.atPosition
        ) ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)

        if (child.id == liveTarget.id) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidFolderOperation)
        }

        context.evictItemEverywhere(mutable, child.id)
        context.evictItemEverywhere(mutable, liveTarget.id)

        val folder = HomeItem.FolderItem.create(child, liveTarget, command.atPosition)
        mutable.add(folder)
        return HomeModelWriter.Result.Applied(mutable)
    }

    fun mergeFolders(
        currentItems: List<HomeItem>,
        command: HomeModelWriter.Command.MergeFolders
    ): HomeModelWriter.Result {
        val mutable = currentItems.toMutableList()
        val sourceIndex = mutable.indexOfFirst { it.id == command.sourceFolderId }
        val targetIndex = mutable.indexOfFirst { it.id == command.targetFolderId }
        if (sourceIndex == -1 || targetIndex == -1) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
        }

        val source = mutable[sourceIndex] as? HomeItem.FolderItem
            ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
        val target = mutable[targetIndex] as? HomeItem.FolderItem
            ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)

        val targetChildIds = target.children.map { it.id }.toSet()
        val merged = target.children + source.children
            .filterNot { it.id in targetChildIds }
            .filterNot { it is HomeItem.FolderItem || it is HomeItem.WidgetItem }
            .map { it.withPosition(GridPosition.DEFAULT) }

        mutable.removeAll { it.id == command.sourceFolderId }
        val updatedTargetIndex = mutable.indexOfFirst { it.id == command.targetFolderId }
        if (updatedTargetIndex == -1) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
        }
        val updatedTarget = mutable[updatedTargetIndex] as? HomeItem.FolderItem
            ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
        mutable[updatedTargetIndex] = updatedTarget.copy(children = merged)

        return HomeModelWriter.Result.Applied(mutable)
    }

    fun renameFolder(
        currentItems: List<HomeItem>,
        command: HomeModelWriter.Command.RenameFolder
    ): HomeModelWriter.Result {
        val mutable = currentItems.toMutableList()
        val folderIndex = mutable.indexOfFirst { it.id == command.folderId }
        if (folderIndex == -1) return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)

        val folder = mutable[folderIndex] as? HomeItem.FolderItem
            ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
        val safeName = command.newName.trim().ifBlank { "Folder" }
        mutable[folderIndex] = folder.copy(name = safeName)
        return HomeModelWriter.Result.Applied(mutable)
    }

    fun extractItemFromFolder(
        currentItems: List<HomeItem>,
        command: HomeModelWriter.Command.ExtractItemFromFolder
    ): HomeModelWriter.Result {
        val mutable = currentItems.toMutableList()
        val occupied = HomeGraph.buildOccupiedCells(mutable, excludeItemId = command.folderId)
        if (command.targetPosition in occupied) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.TargetOccupied)
        }

        val child = context.removeChildFromFolderWithCleanup(
            items = mutable,
            folderId = command.folderId,
            childItemId = command.itemId
        ) ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)

        context.evictItemEverywhere(mutable, child.id)
        mutable.add(child.withPosition(command.targetPosition))
        return HomeModelWriter.Result.Applied(mutable)
    }
}