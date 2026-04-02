package com.milki.launcher.data.repository.home

import com.milki.launcher.data.repository.FolderMutationEngine
import com.milki.launcher.domain.homegraph.HomeGridDefaults
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem

/**
 * Legacy mutation facade retained for compatibility.
 *
 * The app now primarily mutates the home model through HomeModelWriter,
 * but this facade keeps existing repository helper methods available while
 * sharing one transactional and placement policy implementation.
 */
internal class HomeLegacyMutationApi(
    private val snapshotStore: HomeSnapshotStore,
    private val folderMutationEngine: FolderMutationEngine,
    private val occupancyPolicy: HomeGridOccupancyPolicy
) {

    suspend fun addPinnedItem(item: HomeItem) {
        snapshotStore.edit { items ->
            if (folderMutationEngine.containsItemIdAnywhere(items, item.id)) {
                return@edit HomeSnapshotStore.EditDecision.noChange(Unit)
            }

            val nextPosition = occupancyPolicy.findFirstAvailableSingleCell(
                items = items,
                columns = HomeGridDefaults.COLUMNS
            )

            items.add(item.withPosition(nextPosition))
            HomeSnapshotStore.EditDecision.persist(Unit)
        }
    }

    suspend fun removePinnedItem(id: String) {
        snapshotStore.edit { items ->
            val changed = items.removeAll { it.id == id }
            decision(applied = changed, value = Unit)
        }
    }

    suspend fun pinOrMoveItemToPosition(item: HomeItem, targetPosition: GridPosition): Boolean {
        return snapshotStore.edit { items ->
            val previousTopLevelIndex = items.indexOfFirst { it.id == item.id }

            folderMutationEngine.evictItemEverywhere(items, item.id)

            val occupiedCells = occupancyPolicy.buildOccupiedCells(
                items = items,
                excludeItemId = item.id
            )
            val span = (item as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE

            if (!occupancyPolicy.isSpanFree(targetPosition, span, occupiedCells, HomeGridDefaults.COLUMNS)) {
                return@edit HomeSnapshotStore.EditDecision.noChange(false)
            }

            val itemWithPosition = item.withPosition(targetPosition)
            if (previousTopLevelIndex >= 0) {
                val insertionIndex = previousTopLevelIndex.coerceIn(0, items.size)
                items.add(insertionIndex, itemWithPosition)
            } else {
                items.add(itemWithPosition)
            }

            HomeSnapshotStore.EditDecision.persist(true)
        }
    }

    suspend fun moveItemToPositionIfEmpty(itemId: String, newPosition: GridPosition): Boolean {
        return snapshotStore.edit { items ->
            val itemIndex = items.indexOfFirst { it.id == itemId }
            if (itemIndex == -1) {
                return@edit HomeSnapshotStore.EditDecision.noChange(false)
            }

            val currentItem = items[itemIndex]
            if (currentItem.position == newPosition) {
                return@edit HomeSnapshotStore.EditDecision.noChange(true)
            }

            val occupiedCells = occupancyPolicy.buildOccupiedCells(
                items = items,
                excludeItemId = itemId
            )
            val span = (currentItem as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE

            if (!occupancyPolicy.isSpanFree(newPosition, span, occupiedCells, HomeGridDefaults.COLUMNS)) {
                return@edit HomeSnapshotStore.EditDecision.noChange(false)
            }

            items[itemIndex] = currentItem.withPosition(newPosition)
            HomeSnapshotStore.EditDecision.persist(true)
        }
    }

    suspend fun createFolder(
        item1: HomeItem,
        item2: HomeItem,
        atPosition: GridPosition
    ): HomeItem.FolderItem? {
        return snapshotStore.edit { items ->
            val createdFolder = folderMutationEngine.createFolder(
                items = items,
                item1 = item1,
                item2 = item2,
                atPosition = atPosition
            )

            if (createdFolder == null) {
                HomeSnapshotStore.EditDecision.noChange(null)
            } else {
                HomeSnapshotStore.EditDecision.persist(createdFolder)
            }
        }
    }

    suspend fun addItemToFolder(
        folderId: String,
        item: HomeItem,
        targetIndex: Int?
    ): Boolean {
        return snapshotStore.edit { items ->
            val wasApplied = folderMutationEngine.addItemToFolder(
                items = items,
                folderId = folderId,
                item = item,
                targetIndex = targetIndex
            )

            decision(applied = wasApplied, value = wasApplied)
        }
    }

    suspend fun removeItemFromFolder(
        folderId: String,
        itemId: String
    ): HomeItem.FolderItem? {
        return snapshotStore.edit { items ->
            val result = folderMutationEngine.removeItemFromFolder(
                items = items,
                folderId = folderId,
                itemId = itemId
            )

            if (!result.wasApplied) {
                HomeSnapshotStore.EditDecision.noChange(null)
            } else {
                HomeSnapshotStore.EditDecision.persist(result.updatedFolder)
            }
        }
    }

    suspend fun reorderFolderItems(
        folderId: String,
        newChildren: List<HomeItem>
    ): Boolean {
        return snapshotStore.edit { items ->
            val wasApplied = folderMutationEngine.reorderFolderItems(
                items = items,
                folderId = folderId,
                newChildren = newChildren
            )

            decision(applied = wasApplied, value = wasApplied)
        }
    }

    suspend fun mergeFolders(
        sourceFolderId: String,
        targetFolderId: String
    ): Boolean {
        return snapshotStore.edit { items ->
            val wasApplied = folderMutationEngine.mergeFolders(
                items = items,
                sourceFolderId = sourceFolderId,
                targetFolderId = targetFolderId
            )

            decision(applied = wasApplied, value = wasApplied)
        }
    }

    suspend fun renameFolder(folderId: String, newName: String): Boolean {
        return snapshotStore.edit { items ->
            val wasApplied = folderMutationEngine.renameFolder(
                items = items,
                folderId = folderId,
                newName = newName
            )

            decision(applied = wasApplied, value = wasApplied)
        }
    }

    suspend fun extractItemFromFolder(
        folderId: String,
        itemId: String,
        targetPosition: GridPosition
    ): Boolean {
        return snapshotStore.edit { items ->
            val occupiedCells = occupancyPolicy.buildOccupiedCells(
                items = items,
                excludeItemId = folderId
            )

            val wasApplied = folderMutationEngine.extractItemFromFolder(
                items = items,
                folderId = folderId,
                itemId = itemId,
                targetPosition = targetPosition,
                targetPositionOccupiedByOtherItem = targetPosition in occupiedCells
            )

            decision(applied = wasApplied, value = wasApplied)
        }
    }

    suspend fun moveItemBetweenFolders(
        sourceFolderId: String,
        targetFolderId: String,
        itemId: String
    ): Boolean {
        return snapshotStore.edit { items ->
            val wasApplied = folderMutationEngine.moveItemBetweenFolders(
                items = items,
                sourceFolderId = sourceFolderId,
                targetFolderId = targetFolderId,
                itemId = itemId
            )

            decision(applied = wasApplied, value = wasApplied)
        }
    }

    suspend fun extractFolderChildOntoItem(
        sourceFolderId: String,
        childItemId: String,
        occupantItem: HomeItem,
        atPosition: GridPosition
    ): HomeItem.FolderItem? {
        return snapshotStore.edit { items ->
            val createdFolder = folderMutationEngine.extractFolderChildOntoItem(
                items = items,
                sourceFolderId = sourceFolderId,
                childItemId = childItemId,
                occupantItem = occupantItem,
                atPosition = atPosition
            )

            if (createdFolder == null) {
                HomeSnapshotStore.EditDecision.noChange(null)
            } else {
                HomeSnapshotStore.EditDecision.persist(createdFolder)
            }
        }
    }

    suspend fun addWidget(widget: HomeItem.WidgetItem): Boolean {
        return pinOrMoveItemToPosition(widget, widget.position)
    }

    suspend fun removeWidget(widgetId: String) {
        snapshotStore.edit { items ->
            val changed = items.removeAll { it.id == widgetId }
            decision(applied = changed, value = Unit)
        }
    }

    suspend fun updateWidgetSpan(widgetId: String, newSpan: GridSpan): Boolean {
        return snapshotStore.edit { items ->
            val widgetIndex = items.indexOfFirst { it.id == widgetId }
            if (widgetIndex == -1) {
                return@edit HomeSnapshotStore.EditDecision.noChange(false)
            }

            val widget = items[widgetIndex] as? HomeItem.WidgetItem
                ?: return@edit HomeSnapshotStore.EditDecision.noChange(false)

            val occupiedCells = occupancyPolicy.buildOccupiedCells(
                items = items,
                excludeItemId = widgetId
            )
            if (!occupancyPolicy.isSpanFree(widget.position, newSpan, occupiedCells)) {
                return@edit HomeSnapshotStore.EditDecision.noChange(false)
            }

            items[widgetIndex] = widget.withSpan(newSpan)
            HomeSnapshotStore.EditDecision.persist(true)
        }
    }

    private fun <T> decision(applied: Boolean, value: T): HomeSnapshotStore.EditDecision<T> {
        return if (applied) {
            HomeSnapshotStore.EditDecision.persist(value)
        } else {
            HomeSnapshotStore.EditDecision.noChange(value)
        }
    }
}
