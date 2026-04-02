package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem

/**
 * Simple, deterministic mutation engine for top-level home layout operations.
 */
class HomeModelWriter(
    private val gridColumns: Int = HomeGridDefaults.COLUMNS
) {

    private val mutationContext = HomeModelMutationContext(gridColumns = gridColumns)
    private val topLevelMutations = HomeTopLevelMutations(context = mutationContext)
    private val folderMutations = HomeFolderMutations(context = mutationContext)
    private val widgetMutations = HomeWidgetMutations(context = mutationContext)

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

        data class RemoveItemsById(
            val itemIds: Set<String>
        ) : Command

        data class CreateFolder(
            val draggedItem: HomeItem,
            val targetItemId: String,
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
            val targetItemId: String,
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
            is Command.AddPinnedItem -> topLevelMutations.addPinnedItem(currentItems, command)
            is Command.MoveTopLevelItem -> topLevelMutations.moveTopLevelItem(currentItems, command)
            is Command.PinOrMoveToPosition -> topLevelMutations.pinOrMove(currentItems, command)
            is Command.RemoveItemById -> topLevelMutations.removeItemById(currentItems, command)
            is Command.RemoveItemsById -> topLevelMutations.removeItemsById(currentItems, command)
            is Command.CreateFolder -> folderMutations.createFolder(currentItems, command)
            is Command.AddItemToFolder -> folderMutations.addItemToFolder(currentItems, command)
            is Command.RemoveItemFromFolder -> folderMutations.removeItemFromFolder(currentItems, command)
            is Command.ReorderFolderItems -> folderMutations.reorderFolderItems(currentItems, command)
            is Command.MoveItemBetweenFolders -> folderMutations.moveItemBetweenFolders(currentItems, command)
            is Command.ExtractFolderChildOntoItem -> folderMutations.extractFolderChildOntoItem(currentItems, command)
            is Command.MergeFolders -> folderMutations.mergeFolders(currentItems, command)
            is Command.RenameFolder -> folderMutations.renameFolder(currentItems, command)
            is Command.ExtractItemFromFolder -> folderMutations.extractItemFromFolder(currentItems, command)
            is Command.UpdateWidgetFrame -> widgetMutations.updateWidgetFrame(currentItems, command)
        }
    }
}
