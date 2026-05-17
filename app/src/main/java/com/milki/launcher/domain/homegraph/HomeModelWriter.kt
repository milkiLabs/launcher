package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.WidgetDisplayMode

/**
 * Deterministic mutation engine for home layout operations. The command
 * contract stays here; operation families live in focused mutation files.
 */
class HomeModelWriter(
    internal val gridColumns: Int = HomeGridDefaults.COLUMNS
) {

    sealed interface Command

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

    data class UpdateWidgetDisplayMode(
        val widgetId: String,
        val displayMode: WidgetDisplayMode
    ) : Command

    data class ExpandPopupWidget(
        val widgetId: String,
        val visibleRows: Int
    ) : Command

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
            is AddPinnedItem -> addPinnedItem(currentItems, command)
            is MoveTopLevelItem -> moveTopLevelItem(currentItems, command)
            is PinOrMoveToPosition -> pinOrMove(currentItems, command)
            is RemoveItemsById -> removeItemsById(currentItems, command)
            is CreateFolder -> createFolder(currentItems, command)
            is AddItemToFolder -> addItemToFolder(currentItems, command)
            is RemoveItemFromFolder -> removeItemFromFolder(currentItems, command)
            is ReorderFolderItems -> reorderFolderItems(currentItems, command)
            is MoveItemBetweenFolders -> moveItemBetweenFolders(currentItems, command)
            is ExtractFolderChildOntoItem -> extractFolderChildOntoItem(currentItems, command)
            is MergeFolders -> mergeFolders(currentItems, command)
            is RenameFolder -> renameFolder(currentItems, command)
            is ExtractItemFromFolder -> extractItemFromFolder(currentItems, command)
            is UpdateWidgetFrame -> updateWidgetFrame(currentItems, command)
            is UpdateWidgetDisplayMode -> updateWidgetDisplayMode(currentItems, command)
            is ExpandPopupWidget -> expandPopupWidget(currentItems, command)
        }
    }

}
