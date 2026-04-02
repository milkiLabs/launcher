package com.milki.launcher.data.repository

import android.content.Context
import com.milki.launcher.data.repository.home.HomeGridOccupancyPolicy
import com.milki.launcher.data.repository.home.HomeLegacyMutationApi
import com.milki.launcher.data.repository.home.HomeSnapshotStore
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.HomeRepository

/**
 * DataStore-backed implementation of HomeRepository.
 *
 * ARCHITECTURE:
 * - HomeSnapshotStore: DataStore flow + transactional read/modify/write helper.
 * - HomeGridOccupancyPolicy: span-aware placement and occupancy checks.
 * - HomeLegacyMutationApi: compatibility helper surface retained for callers
 *   that still invoke repository-level mutations directly.
 */
class HomeRepositoryImpl(
    context: Context
) : HomeRepository {

    private val snapshotStore = HomeSnapshotStore(context)
    private val occupancyPolicy = HomeGridOccupancyPolicy()

    /**
     * Folder rules remain in this dedicated engine; repository owns transaction
     * boundaries while the engine owns folder invariants.
     */
    private val folderMutationEngine = FolderMutationEngine()

    /**
     * Backward-compatible mutation facade for legacy call sites.
     */
    private val legacyMutations = HomeLegacyMutationApi(
        snapshotStore = snapshotStore,
        folderMutationEngine = folderMutationEngine,
        occupancyPolicy = occupancyPolicy
    )

    override val pinnedItems = snapshotStore.pinnedItems

    override suspend fun replacePinnedItems(items: List<HomeItem>) {
        snapshotStore.replaceAll(items)
    }

    override suspend fun isPinned(id: String): Boolean {
        return snapshotStore.readSnapshot().any { item -> item.id == id }
    }

    override suspend fun findAvailablePosition(columns: Int, maxRows: Int): GridPosition {
        val currentItems = snapshotStore.readSnapshot()
        return occupancyPolicy.findFirstAvailableSingleCell(
            items = currentItems,
            columns = columns,
            maxRows = maxRows
        )
    }

    override suspend fun clearAll() {
        snapshotStore.clearAll()
    }

    suspend fun addPinnedItem(item: HomeItem) {
        legacyMutations.addPinnedItem(item)
    }

    suspend fun removePinnedItem(id: String) {
        legacyMutations.removePinnedItem(id)
    }

    suspend fun pinOrMoveItemToPosition(item: HomeItem, targetPosition: GridPosition): Boolean {
        return legacyMutations.pinOrMoveItemToPosition(item, targetPosition)
    }

    suspend fun updateItemPosition(itemId: String, newPosition: GridPosition) {
        legacyMutations.moveItemToPositionIfEmpty(itemId, newPosition)
    }

    suspend fun moveItemToPositionIfEmpty(itemId: String, newPosition: GridPosition): Boolean {
        return legacyMutations.moveItemToPositionIfEmpty(itemId, newPosition)
    }

    suspend fun createFolder(
        item1: HomeItem,
        item2: HomeItem,
        atPosition: GridPosition
    ): HomeItem.FolderItem? {
        return legacyMutations.createFolder(item1, item2, atPosition)
    }

    suspend fun addItemToFolder(
        folderId: String,
        item: HomeItem,
        targetIndex: Int?
    ): Boolean {
        return legacyMutations.addItemToFolder(folderId, item, targetIndex)
    }

    suspend fun removeItemFromFolder(
        folderId: String,
        itemId: String
    ): HomeItem.FolderItem? {
        return legacyMutations.removeItemFromFolder(folderId, itemId)
    }

    suspend fun reorderFolderItems(
        folderId: String,
        newChildren: List<HomeItem>
    ): Boolean {
        return legacyMutations.reorderFolderItems(folderId, newChildren)
    }

    suspend fun mergeFolders(
        sourceFolderId: String,
        targetFolderId: String
    ): Boolean {
        return legacyMutations.mergeFolders(sourceFolderId, targetFolderId)
    }

    suspend fun renameFolder(
        folderId: String,
        newName: String
    ): Boolean {
        return legacyMutations.renameFolder(folderId, newName)
    }

    suspend fun extractItemFromFolder(
        folderId: String,
        itemId: String,
        targetPosition: GridPosition
    ): Boolean {
        return legacyMutations.extractItemFromFolder(folderId, itemId, targetPosition)
    }

    suspend fun moveItemBetweenFolders(
        sourceFolderId: String,
        targetFolderId: String,
        itemId: String
    ): Boolean {
        return legacyMutations.moveItemBetweenFolders(sourceFolderId, targetFolderId, itemId)
    }

    suspend fun extractFolderChildOntoItem(
        sourceFolderId: String,
        childItemId: String,
        occupantItem: HomeItem,
        atPosition: GridPosition
    ): HomeItem.FolderItem? {
        return legacyMutations.extractFolderChildOntoItem(
            sourceFolderId = sourceFolderId,
            childItemId = childItemId,
            occupantItem = occupantItem,
            atPosition = atPosition
        )
    }

    suspend fun addWidget(widget: HomeItem.WidgetItem): Boolean {
        return legacyMutations.addWidget(widget)
    }

    suspend fun removeWidget(widgetId: String) {
        legacyMutations.removeWidget(widgetId)
    }

    suspend fun updateWidgetSpan(widgetId: String, newSpan: GridSpan): Boolean {
        return legacyMutations.updateWidgetSpan(widgetId, newSpan)
    }
}
