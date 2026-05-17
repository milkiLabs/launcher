package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.WidgetDisplayMode
import com.milki.launcher.domain.model.homeGridSpan

internal fun HomeModelWriter.createFolder(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.CreateFolder
): HomeModelWriter.Result {
    val mutable = currentItems.toMutableList()
    val liveTarget = findLiveNonFolderTarget(
        items = mutable,
        targetItemId = command.targetItemId,
        atPosition = command.atPosition
    )

    val rejection = when {
        command.draggedItem is HomeItem.FolderItem || command.draggedItem is HomeItem.WidgetItem ->
            HomeModelWriter.Error.InvalidFolderOperation
        liveTarget == null -> HomeModelWriter.Error.ItemNotFound
        command.draggedItem.id == liveTarget.id -> HomeModelWriter.Error.InvalidFolderOperation
        else -> null
    }

    val result = if (rejection != null) {
        HomeModelWriter.Result.Rejected(rejection)
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
        HomeModelWriter.Result.Applied(mutable)
    }
    return result
}

internal fun HomeModelWriter.addItemToFolder(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.AddItemToFolder
): HomeModelWriter.Result {
    val mutable = currentItems.toMutableList()
    val folderLookup = findFolderLookup(mutable, command.folderId)
    val rejection = when {
        command.item is HomeItem.FolderItem || command.item is HomeItem.WidgetItem ->
            HomeModelWriter.Error.InvalidFolderOperation
        folderLookup == null -> HomeModelWriter.Error.FolderNotFound
        folderLookup.folder.children.any { it.id == command.item.id } -> HomeModelWriter.Error.DuplicateItem
        else -> null
    }

    val result = if (rejection != null) {
        HomeModelWriter.Result.Rejected(rejection)
    } else {
        evictItemEverywhere(mutable, command.item.id)
        val updatedFolderLookup = findFolderLookup(mutable, command.folderId)
        if (updatedFolderLookup == null) {
            HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
        } else {
            val children = updatedFolderLookup.folder.children.toMutableList()
            val insertAt = command.targetIndex?.coerceIn(0, children.size) ?: children.size
            children.add(insertAt, command.item.withPosition(GridPosition.DEFAULT))

            mutable[updatedFolderLookup.index] = updatedFolderLookup.folder.copy(children = children)
            HomeModelWriter.Result.Applied(mutable)
        }
    }
    return result
}

internal fun HomeModelWriter.removeItemFromFolder(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.RemoveItemFromFolder
): HomeModelWriter.Result {
    val mutable = currentItems.toMutableList()
    val removed = removeChildFromFolderWithCleanup(
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

internal fun HomeModelWriter.reorderFolderItems(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.ReorderFolderItems
): HomeModelWriter.Result {
    val mutable = currentItems.toMutableList()
    val folderLookup = findFolderLookup(mutable, command.folderId)
        ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)

    val safeChildren = command.newChildren
        .filterNot { it is HomeItem.FolderItem || it is HomeItem.WidgetItem }
        .map { it.withPosition(GridPosition.DEFAULT) }

    mutable[folderLookup.index] = folderLookup.folder.copy(children = safeChildren)
    return HomeModelWriter.Result.Applied(mutable)
}

internal fun HomeModelWriter.moveItemBetweenFolders(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.MoveItemBetweenFolders
): HomeModelWriter.Result {
    val mutable = currentItems.toMutableList()
    val source = findFolderLookup(mutable, command.sourceFolderId)?.folder
    val target = findFolderLookup(mutable, command.targetFolderId)?.folder
    val child = source?.children?.firstOrNull { it.id == command.itemId }
    val rejection = when {
        command.sourceFolderId == command.targetFolderId -> HomeModelWriter.Error.InvalidFolderOperation
        source == null || target == null -> HomeModelWriter.Error.FolderNotFound
        child == null -> HomeModelWriter.Error.ItemNotFound
        child is HomeItem.WidgetItem -> HomeModelWriter.Error.InvalidFolderOperation
        target.children.any { it.id == command.itemId } -> HomeModelWriter.Error.DuplicateItem
        else -> null
    }

    val result = if (rejection != null) {
        HomeModelWriter.Result.Rejected(rejection)
    } else {
        evictItemEverywhere(mutable, command.itemId)

        val updatedTargetLookup = findFolderLookup(mutable, command.targetFolderId)
        if (updatedTargetLookup == null) {
            HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
        } else {
            val children = updatedTargetLookup.folder.children.toMutableList()
            children.add(requireNotNull(child).withPosition(GridPosition.DEFAULT))
            mutable[updatedTargetLookup.index] = updatedTargetLookup.folder.copy(children = children)
            HomeModelWriter.Result.Applied(mutable)
        }
    }
    return result
}

internal fun HomeModelWriter.extractFolderChildOntoItem(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.ExtractFolderChildOntoItem
): HomeModelWriter.Result {
    val mutable = currentItems.toMutableList()
    val source = findFolderLookup(mutable, command.sourceFolderId)?.folder
    val child = source?.children?.firstOrNull { it.id == command.childItemId }
    val liveTarget = findLiveNonFolderTarget(
        items = mutable,
        targetItemId = command.targetItemId,
        atPosition = command.atPosition
    )

    val rejection = when {
        source == null -> HomeModelWriter.Error.FolderNotFound
        child == null -> HomeModelWriter.Error.ItemNotFound
        child is HomeItem.WidgetItem -> HomeModelWriter.Error.InvalidFolderOperation
        liveTarget == null -> HomeModelWriter.Error.ItemNotFound
        child.id == liveTarget.id -> HomeModelWriter.Error.InvalidFolderOperation
        else -> null
    }

    val result = if (rejection != null) {
        HomeModelWriter.Result.Rejected(rejection)
    } else {
        val confirmedChild = requireNotNull(child)
        val confirmedTarget = requireNotNull(liveTarget)
        evictItemEverywhere(mutable, confirmedChild.id)
        evictItemEverywhere(mutable, confirmedTarget.id)

        val folder = HomeItem.FolderItem.create(confirmedChild, confirmedTarget, command.atPosition)
        mutable.add(folder)
        HomeModelWriter.Result.Applied(mutable)
    }
    return result
}

internal fun HomeModelWriter.mergeFolders(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.MergeFolders
): HomeModelWriter.Result {
    val mutable = currentItems.toMutableList()
    val sourceLookup = findFolderLookup(mutable, command.sourceFolderId)
    val targetLookup = findFolderLookup(mutable, command.targetFolderId)
    val rejection = if (sourceLookup == null || targetLookup == null) {
        HomeModelWriter.Error.FolderNotFound
    } else {
        null
    }

    val result = if (rejection != null) {
        HomeModelWriter.Result.Rejected(rejection)
    } else {
        val targetChildIds = requireNotNull(targetLookup).folder.children.map { it.id }.toSet()
        val merged = targetLookup.folder.children + requireNotNull(sourceLookup).folder.children
            .filterNot { it.id in targetChildIds }
            .filterNot { it is HomeItem.FolderItem || it is HomeItem.WidgetItem }
            .map { it.withPosition(GridPosition.DEFAULT) }

        mutable.removeAll { it.id == command.sourceFolderId }
        val updatedTargetLookup = findFolderLookup(mutable, command.targetFolderId)
        if (updatedTargetLookup == null) {
            HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
        } else {
            mutable[updatedTargetLookup.index] = updatedTargetLookup.folder.copy(children = merged)
            HomeModelWriter.Result.Applied(mutable)
        }
    }
    return result
}

internal fun HomeModelWriter.renameFolder(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.RenameFolder
): HomeModelWriter.Result {
    val mutable = currentItems.toMutableList()
    val folderLookup = findFolderLookup(mutable, command.folderId)
        ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.FolderNotFound)
    val safeName = command.newName.trim().ifBlank { "Folder" }
    mutable[folderLookup.index] = folderLookup.folder.copy(name = safeName)
    return HomeModelWriter.Result.Applied(mutable)
}

internal fun HomeModelWriter.extractItemFromFolder(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.ExtractItemFromFolder
): HomeModelWriter.Result {
    val mutable = currentItems.toMutableList()
    val occupied = HomeGraph.buildOccupiedCells(mutable, excludeItemId = command.folderId)
    val child = removeChildFromFolderWithCleanup(
        items = mutable,
        folderId = command.folderId,
        childItemId = command.itemId
    )

    val rejection = when {
        command.targetPosition in occupied -> HomeModelWriter.Error.TargetOccupied
        child == null -> HomeModelWriter.Error.ItemNotFound
        else -> null
    }

    val result = if (rejection != null) {
        HomeModelWriter.Result.Rejected(rejection)
    } else {
        val confirmedChild = requireNotNull(child)
        evictItemEverywhere(mutable, confirmedChild.id)
        mutable.add(confirmedChild.withPosition(command.targetPosition))
        HomeModelWriter.Result.Applied(mutable)
    }
    return result
}

