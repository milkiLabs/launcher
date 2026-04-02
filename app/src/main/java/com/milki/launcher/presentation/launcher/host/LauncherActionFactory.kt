package com.milki.launcher.presentation.launcher.host

import android.content.Context
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.SwipeUpAction
import com.milki.launcher.presentation.drawer.AppDrawerViewModel
import com.milki.launcher.presentation.home.HomeViewModel
import com.milki.launcher.presentation.launcher.SurfaceStateCoordinatorContract
import com.milki.launcher.presentation.launcher.WidgetPlacementCoordinator
import com.milki.launcher.presentation.search.SearchViewModel
import com.milki.launcher.ui.screens.launcher.DrawerActions
import com.milki.launcher.ui.screens.launcher.FolderActions
import com.milki.launcher.ui.screens.launcher.HomeActions
import com.milki.launcher.ui.screens.launcher.LauncherActions
import com.milki.launcher.ui.screens.launcher.MenuActions
import com.milki.launcher.ui.screens.launcher.SearchActions
import com.milki.launcher.ui.screens.launcher.WidgetActions
import com.milki.launcher.ui.screens.launcher.openPinnedItem

/**
 * Builds [LauncherActions] from feature-specific collaborators.
 *
 * MainActivity and composable host call this factory with runtime state and get a single
 * stable action surface for the UI, instead of wiring dozens of callbacks inline.
 */
internal class LauncherActionFactory(
    private val onOpenSettings: () -> Unit,
    private val homeViewModel: HomeViewModel,
    private val appDrawerViewModel: AppDrawerViewModel,
    private val searchViewModel: SearchViewModel,
    private val surfaceStateCoordinator: SurfaceStateCoordinatorContract,
    private val widgetPlacementCoordinator: WidgetPlacementCoordinator,
    private val widgetHostManager: WidgetHostManager
) {

    fun build(context: Context, swipeUpAction: SwipeUpAction): LauncherActions {
        return LauncherActions(
            search = buildSearchActions(),
            menu = buildMenuActions(),
            drawer = buildDrawerActions(),
            home = buildHomeActions(context = context, swipeUpAction = swipeUpAction),
            folder = buildFolderActions(context = context),
            widget = buildWidgetActions()
        )
    }

    private fun buildSearchActions(): SearchActions {
        return SearchActions(
            onQueryChange = searchViewModel::onQueryChange,
            onDismissSearch = {
                surfaceStateCoordinator.dismissContextMenus()
                searchViewModel.hideSearch()
            }
        )
    }

    private fun buildMenuActions(): MenuActions {
        return MenuActions(
            onOpenSettings = onOpenSettings,
            onHomescreenMenuOpenChange = { isOpen ->
                surfaceStateCoordinator.updateHomescreenMenuOpen(isOpen)
            }
        )
    }

    private fun buildDrawerActions(): DrawerActions {
        return DrawerActions(
            onAppDrawerOpenChange = { isOpen ->
                surfaceStateCoordinator.updateAppDrawerOpen(isOpen)
            },
            onQueryChange = appDrawerViewModel::updateQuery
        )
    }

    private fun buildHomeActions(
        context: Context,
        swipeUpAction: SwipeUpAction
    ): HomeActions {
        return HomeActions(
            onHomeSwipeUp = {
                surfaceStateCoordinator.handleHomeSwipeUp(action = swipeUpAction)
            },
            onPinnedItemClick = { item ->
                if (item is HomeItem.FolderItem) {
                    homeViewModel.openFolder(item.id)
                } else {
                    launchPinnedItem(item = item, context = context)
                }
            },
            onPinnedItemLongPress = {},
            onPinnedItemMove = homeViewModel::moveItemToPosition,
            onItemDroppedToHome = homeViewModel::pinOrMoveHomeItemToPosition
        )
    }

    private fun buildFolderActions(context: Context): FolderActions {
        return FolderActions(
            onCreateFolder = homeViewModel::createFolder,
            onAddItemToFolder = homeViewModel::addItemToFolder,
            onMergeFolders = homeViewModel::mergeFolders,
            onFolderClose = homeViewModel::closeFolder,
            onFolderRename = homeViewModel::renameFolder,
            onFolderItemClick = { item ->
                launchPinnedItem(item = item, context = context)
            },
            onFolderItemRemove = homeViewModel::removeItemFromFolder,
            onFolderItemReorder = homeViewModel::reorderFolderItems,
            onExtractItemFromFolder = homeViewModel::extractItemFromFolder,
            onMoveFolderItemToFolder = homeViewModel::moveItemBetweenFolders,
            onFolderChildDroppedOnItem = homeViewModel::extractFolderChildOntoItem
        )
    }

    private fun buildWidgetActions(): WidgetActions {
        return WidgetActions(
            onWidgetPickerOpenChange = { isOpen ->
                surfaceStateCoordinator.updateWidgetPickerOpen(isOpen)
            },
            onWidgetPickerQueryChange = surfaceStateCoordinator::updateWidgetPickerQuery,
            onRemoveWidget = { widgetId, _ ->
                homeViewModel.removeWidget(
                    widgetId = widgetId,
                    widgetHostManager = widgetHostManager
                )
            },
            onUpdateWidgetFrame = homeViewModel::updateWidgetFrame,
            onWidgetDroppedToHome = { providerInfo, span, dropPosition ->
                val command = homeViewModel.startWidgetPlacement(
                    providerInfo = providerInfo,
                    targetPosition = dropPosition,
                    span = span,
                    widgetHostManager = widgetHostManager
                )
                widgetPlacementCoordinator.execute(command)
            }
        )
    }

    private fun launchPinnedItem(item: HomeItem, context: Context) {
        openPinnedItem(
            item = item,
            context = context,
            onUnavailableItem = homeViewModel::unpinItem
        )
    }
}