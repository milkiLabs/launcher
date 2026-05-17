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

        data class UpdateWidgetDisplayMode(
            val widgetId: String,
            val displayMode: WidgetDisplayMode
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.updateWidgetDisplayMode(currentItems, this)
            }
        }

        data class ExpandPopupWidget(
            val widgetId: String,
            val visibleRows: Int
        ) : Command {
            override fun execute(writer: HomeModelWriter, currentItems: List<HomeItem>): Result {
                return writer.expandPopupWidget(currentItems, this)
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

}
