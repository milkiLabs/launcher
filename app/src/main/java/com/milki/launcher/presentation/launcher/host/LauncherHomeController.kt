package com.milki.launcher.presentation.launcher.host

import android.content.Context
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.presentation.home.HomeViewModel
import com.milki.launcher.presentation.launcher.WidgetPlacementCoordinator
import com.milki.launcher.ui.screens.launcher.openPinnedItem

/**
 * Straightforward homescreen interaction handler.
 *
 * This keeps home, folder, and widget behavior together in one place without
 * introducing extra policy layers.
 */
internal class LauncherHomeController(
    private val homeViewModel: HomeViewModel,
    private val widgetPlacementCoordinator: WidgetPlacementCoordinator,
    private val widgetHostManager: WidgetHostManager
) {

    fun onPinnedItemClick(item: HomeItem, context: Context) {
        if (item is HomeItem.FolderItem) {
            homeViewModel.openFolder(item.id)
            return
        }

        openPinnedItem(
            item = item,
            context = context,
            onUnavailableItem = homeViewModel::unpinItem
        )
    }

    fun onPinnedItemMove(itemId: String, newPosition: GridPosition) {
        homeViewModel.moveItemToPosition(itemId, newPosition)
    }

    fun onItemDroppedToHome(item: HomeItem, dropPosition: GridPosition) {
        homeViewModel.pinOrMoveHomeItemToPosition(item, dropPosition)
    }

    fun onFolderClose() {
        homeViewModel.closeFolder()
    }

    fun onFolderRename(folderId: String, newName: String) {
        homeViewModel.renameFolder(folderId, newName)
    }

    fun onFolderItemClick(item: HomeItem, context: Context) {
        openPinnedItem(
            item = item,
            context = context,
            onUnavailableItem = homeViewModel::unpinItem
        )
    }

    fun onCreateFolder(item1: HomeItem, item2: HomeItem, atPosition: GridPosition) {
        homeViewModel.createFolder(item1, item2, atPosition)
    }

    fun onAddItemToFolder(folderId: String, item: HomeItem) {
        homeViewModel.addItemToFolder(folderId, item)
    }

    fun onMergeFolders(sourceFolderId: String, targetFolderId: String) {
        homeViewModel.mergeFolders(sourceFolderId, targetFolderId)
    }

    fun onRemoveItemFromFolder(folderId: String, itemId: String) {
        homeViewModel.removeItemFromFolder(folderId, itemId)
    }

    fun onReorderFolderItems(folderId: String, newChildren: List<HomeItem>) {
        homeViewModel.reorderFolderItems(folderId, newChildren)
    }

    fun onExtractItemFromFolder(folderId: String, itemId: String, targetPosition: GridPosition) {
        homeViewModel.extractItemFromFolder(folderId, itemId, targetPosition)
    }

    fun onMoveFolderItemToFolder(sourceFolderId: String, itemId: String, targetFolderId: String) {
        homeViewModel.moveItemBetweenFolders(sourceFolderId, itemId, targetFolderId)
    }

    fun onFolderChildDroppedOnItem(
        sourceFolderId: String,
        childItem: HomeItem,
        occupantItem: HomeItem,
        atPosition: GridPosition
    ) {
        homeViewModel.extractFolderChildOntoItem(
            sourceFolderId = sourceFolderId,
            childItem = childItem,
            occupantItem = occupantItem,
            atPosition = atPosition
        )
    }

    fun onRemoveWidget(widgetId: String) {
        homeViewModel.removeWidget(
            widgetId = widgetId,
            widgetHostManager = widgetHostManager
        )
    }

    fun onUpdateWidgetFrame(widgetId: String, newPosition: GridPosition, newSpan: GridSpan) {
        homeViewModel.updateWidgetFrame(widgetId, newPosition, newSpan)
    }

    fun onWidgetDroppedToHome(
        providerInfo: android.appwidget.AppWidgetProviderInfo,
        span: GridSpan,
        dropPosition: GridPosition
    ) {
        val command = homeViewModel.startWidgetPlacement(
            providerInfo = providerInfo,
            targetPosition = dropPosition,
            span = span,
            widgetHostManager = widgetHostManager
        )
        widgetPlacementCoordinator.execute(command)
    }
}
