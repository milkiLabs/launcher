package com.milki.launcher.ui.screens.launcher

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem

/**
 * Aggregates all callback contracts emitted from the launcher screen.
 *
 * Grouping actions by feature domain keeps the screen API stable as
 * individual features evolve. Instead of adding one callback parameter per
 * interaction, callers provide one [LauncherActions] object with
 * feature-scoped action groups.
 */
data class LauncherActions(
    val home: HomeActions = HomeActions(),
    val folder: FolderActions = FolderActions(),
    val widget: WidgetActions = WidgetActions(),
    val drawer: DrawerActions = DrawerActions(),
    val search: SearchActions = SearchActions(),
    val menu: MenuActions = MenuActions()
)

/**
 * Home-surface interactions emitted from the pinned grid.
 */
data class HomeActions(
    val onPinnedItemClick: (HomeItem) -> Unit = {},
    val onPinnedItemLongPress: (HomeItem) -> Unit = {},
    val onPinnedItemMove: (itemId: String, newPosition: GridPosition) -> Unit = { _, _ -> },
    val onItemDroppedToHome: (HomeItem, GridPosition) -> Unit = { _, _ -> },
    val onHomeSwipeUp: () -> Unit = {}
)

/**
 * Folder lifecycle and drag-drop interactions.
 */
data class FolderActions(
    val onCreateFolder: (item1: HomeItem, item2: HomeItem, atPosition: GridPosition) -> Unit = { _, _, _ -> },
    val onAddItemToFolder: (folderId: String, item: HomeItem) -> Unit = { _, _ -> },
    val onMergeFolders: (sourceFolderId: String, targetFolderId: String) -> Unit = { _, _ -> },
    val onFolderClose: () -> Unit = {},
    val onFolderRename: (folderId: String, newName: String) -> Unit = { _, _ -> },
    val onFolderItemClick: (HomeItem) -> Unit = {},
    val onFolderItemRemove: (folderId: String, itemId: String) -> Unit = { _, _ -> },
    val onFolderItemReorder: (folderId: String, newChildren: List<HomeItem>) -> Unit = { _, _ -> },
    val onExtractItemFromFolder: (folderId: String, itemId: String, targetPosition: GridPosition) -> Unit = { _, _, _ -> },
    val onMoveFolderItemToFolder: (sourceFolderId: String, itemId: String, targetFolderId: String) -> Unit = { _, _, _ -> },
    val onFolderChildDroppedOnItem: (sourceFolderId: String, childItem: HomeItem, occupantItem: HomeItem, atPosition: GridPosition) -> Unit = { _, _, _, _ -> }
)

/**
 * Widget picker visibility and widget-grid interactions.
 */
data class WidgetActions(
    val onWidgetPickerOpenChange: (Boolean) -> Unit = {},
    val onRemoveWidget: (widgetId: String, appWidgetId: Int) -> Unit = { _, _ -> },
    val onUpdateWidgetFrame: (
        widgetId: String,
        newPosition: GridPosition,
        newSpan: GridSpan
    ) -> Unit = { _, _, _ -> },
    val onWidgetDroppedToHome: (
        providerInfo: android.appwidget.AppWidgetProviderInfo,
        span: GridSpan,
        dropPosition: GridPosition
    ) -> Unit = { _, _, _ -> }
)

/**
 * App drawer lifecycle interactions.
 */
data class DrawerActions(
    val onAppDrawerOpenChange: (Boolean) -> Unit = {},
    val onQueryChange: (String) -> Unit = {}
)

/**
 * Search dialog interactions.
 */
data class SearchActions(
    val onQueryChange: (String) -> Unit = {},
    val onDismissSearch: () -> Unit = {}
)

/**
 * Homescreen context-menu interactions.
 */
data class MenuActions(
    val onOpenSettings: () -> Unit = {},
    val onHomescreenMenuOpenChange: (Boolean) -> Unit = {}
)
