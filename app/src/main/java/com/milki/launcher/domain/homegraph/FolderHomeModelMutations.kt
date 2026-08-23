package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.GridOccupancy
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.WidgetDisplayMode
import com.milki.launcher.domain.model.homeGridSpan

internal fun HomeModelWriter.createFolder(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.CreateFolder
): HomeModelWriter.Result {
    if (command.draggedItem is HomeItem.FolderItem || command.draggedItem is HomeItem.WidgetItem) {
        return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidFolderOperation)
    }
    val liveTarget = findLiveNonFolderTarget(
        items = currentItems,
        targetItemId = command.targetItemId,
        atPosition = command.atPosition
    ) ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)
    if (command.draggedItem.id == liveTarget.id) {
        return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidFolderOperation)
    }

    val mutable = currentItems.toMutableList()
    evictItemEverywhere(mutable, command.draggedItem.id)
    evictItemEverywhere(mutable, liveTarget.id)

    val folder = HomeItem.FolderItem.create(
        command.draggedItem,
        liveTarget,
        command.atPosition
    )
    mutable.add(folder)
    return HomeModelWriter.Result.Applied(mutable)
}

internal fun HomeModelWriter.addItemToFolder(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.AddItemToFolder
): HomeModelWriter.Result {
    if (command.item is HomeItem.FolderItem || command.item is HomeItem.WidgetItem) {
        return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidFolderOperation)
    }
    val folderLookup = findFolderLookup(currentItems, command.folderId)
        ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
    if (folderLookup.folder.children.any { it.id == command.item.id }) {
        return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.DuplicateItem)
    }

    val mutable = currentItems.toMutableList()
    evictItemEverywhere(mutable, command.item.id)
    if (!mutable.appendToFolder(command.folderId, listOf(command.item), command.targetIndex)) {
        return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
    }
    return HomeModelWriter.Result.Applied(mutable)
}

internal fun HomeModelWriter.removeItemFromFolder(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.RemoveItemFromFolder
): HomeModelWriter.Result {
    val folderLookup = findFolderLookup(currentItems, command.folderId)
    val removed = folderLookup?.folder?.children?.firstOrNull { it.id == command.itemId }
        ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)

    val mutable = currentItems.toMutableList()
    removeChildFromFolderWithCleanup(
        items = mutable,
        folderId = command.folderId,
        childItemId = command.itemId
    )
    return if (removed.id == command.itemId) {
        HomeModelWriter.Result.Applied(mutable)
    } else {
        HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)
    }
}

internal fun HomeModelWriter.reorderFolderItems(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.ReorderFolderItems
): HomeModelWriter.Result {
    val folderLookup = findFolderLookup(currentItems, command.folderId)
        ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)

    val safeChildren = command.newChildren
        .filterNot { it is HomeItem.FolderItem || it is HomeItem.WidgetItem }
        .map { it.withPosition(GridPosition.DEFAULT) }

    val mutable = currentItems.toMutableList()
    mutable[folderLookup.index] = folderLookup.folder.copy(children = safeChildren)
    return HomeModelWriter.Result.Applied(mutable)
}

internal fun HomeModelWriter.moveItemBetweenFolders(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.MoveItemBetweenFolders
): HomeModelWriter.Result {
    if (command.sourceFolderId == command.targetFolderId) {
        return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidFolderOperation)
    }
    val source = findFolderLookup(currentItems, command.sourceFolderId)?.folder
        ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
    val target = findFolderLookup(currentItems, command.targetFolderId)?.folder
        ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
    val child = source.children.firstOrNull { it.id == command.itemId }
        ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)
    if (child is HomeItem.WidgetItem) {
        return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidFolderOperation)
    }
    if (target.children.any { it.id == command.itemId }) {
        return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.DuplicateItem)
    }

    val mutable = currentItems.toMutableList()
    evictItemEverywhere(mutable, command.itemId)
    if (!mutable.appendToFolder(command.targetFolderId, listOf(child))) {
        return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
    }
    return HomeModelWriter.Result.Applied(mutable)
}

internal fun HomeModelWriter.extractFolderChildOntoItem(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.ExtractFolderChildOntoItem
): HomeModelWriter.Result {
    val source = findFolderLookup(currentItems, command.sourceFolderId)?.folder
        ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
    val child = source.children.firstOrNull { it.id == command.childItemId }
        ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)
    if (child is HomeItem.WidgetItem) {
        return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidFolderOperation)
    }
    val liveTarget = findLiveNonFolderTarget(
        items = currentItems,
        targetItemId = command.targetItemId,
        atPosition = command.atPosition
    ) ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)
    if (child.id == liveTarget.id) {
        return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidFolderOperation)
    }

    val mutable = currentItems.toMutableList()
    evictItemEverywhere(mutable, child.id)
    evictItemEverywhere(mutable, liveTarget.id)

    val folder = HomeItem.FolderItem.create(child, liveTarget, command.atPosition)
    mutable.add(folder)
    return HomeModelWriter.Result.Applied(mutable)
}

internal fun HomeModelWriter.mergeFolders(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.MergeFolders
): HomeModelWriter.Result {
    val sourceLookup = findFolderLookup(currentItems, command.sourceFolderId)
        ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
    val targetLookup = findFolderLookup(currentItems, command.targetFolderId)
        ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)

    val targetChildIds = targetLookup.folder.children.map { it.id }.toSet()
    val sourceChildren = sourceLookup.folder.children
        .filterNot { it.id in targetChildIds }

    val mutable = currentItems.toMutableList()
    mutable.removeAll { it.id == command.sourceFolderId }

    if (!mutable.appendToFolder(command.targetFolderId, sourceChildren)) {
        return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
    }
    return HomeModelWriter.Result.Applied(mutable)
}

internal fun HomeModelWriter.renameFolder(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.RenameFolder
): HomeModelWriter.Result {
    val folderLookup = findFolderLookup(currentItems, command.folderId)
        ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
    val safeName = command.newName.trim().ifBlank { HomeItem.FolderItem.DEFAULT_NAME }

    val mutable = currentItems.toMutableList()
    mutable[folderLookup.index] = folderLookup.folder.copy(name = safeName)
    return HomeModelWriter.Result.Applied(mutable)
}

internal fun HomeModelWriter.extractItemFromFolder(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.ExtractItemFromFolder
): HomeModelWriter.Result {
    val occupied = GridOccupancy.fromItems(currentItems, excludeItemId = command.folderId)
    if (command.targetPosition in occupied.occupiedCells()) {
        return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.TargetOccupied)
    }
    val folderLookup = findFolderLookup(currentItems, command.folderId)
    val child = folderLookup?.folder?.children?.firstOrNull { it.id == command.itemId }
        ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)

    val mutable = currentItems.toMutableList()
    removeChildFromFolderWithCleanup(
        items = mutable,
        folderId = command.folderId,
        childItemId = command.itemId
    )
    evictItemEverywhere(mutable, child.id)
    mutable.add(child.withPosition(command.targetPosition))
    return HomeModelWriter.Result.Applied(mutable)
}
