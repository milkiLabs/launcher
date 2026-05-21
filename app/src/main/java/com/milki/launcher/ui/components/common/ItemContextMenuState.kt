package com.milki.launcher.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.milki.launcher.data.contextmenu.AppContextDataCache
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.ui.components.launcher.ItemActionMenu
import com.milki.launcher.ui.components.launcher.MenuAction
import com.milki.launcher.ui.components.launcher.createAppInfoAction
import com.milki.launcher.ui.components.launcher.createLaunchShortcutAction
import com.milki.launcher.ui.components.launcher.createOpenAppWidgetsAction
import com.milki.launcher.ui.components.launcher.createPinAction
import com.milki.launcher.ui.components.launcher.createUninstallAppAction
import com.milki.launcher.ui.components.launcher.createUnpinAction

@Stable
class ItemContextMenuState {
    var showMenu by mutableStateOf(false)
        private set

    var isGestureActive by mutableStateOf(false)
        private set

    val isMenuFocusable: Boolean
        get() = !isGestureActive

    fun onLongPress() {
        showMenu = true
        isGestureActive = true
    }

    fun onLongPressRelease() {
        isGestureActive = false
    }

    fun onDragStart() {
        showMenu = false
        isGestureActive = false
    }

    fun onDragCancel() {
        isGestureActive = false
    }

    fun dismiss() {
        showMenu = false
        isGestureActive = false
    }
}

@Composable
fun rememberItemContextMenuState(): ItemContextMenuState {
    return remember { ItemContextMenuState() }
}

/**
 * Simple context menu that displays a list of actions.
 * Callers are responsible for building the correct actions for their item type.
 */
@Composable
fun ItemContextMenu(
    actions: List<MenuAction>,
    expanded: Boolean,
    onDismiss: () -> Unit,
    focusable: Boolean = true,
    onExternalDragStarted: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ItemActionMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        actions = actions,
        focusable = focusable,
        onExternalDragStarted = onExternalDragStarted,
        modifier = modifier
    )
}

/**
 * Builds menu actions for a [HomeItem] on the home screen.
 *
 * Rules:
 * - [HomeItem.PinnedApp]: widgets + quick shortcuts + app info + unpin
 * - All other items: unpin only (no parent app actions for shortcuts)
 */
@Composable
fun buildHomeItemMenuActions(
    item: HomeItem,
    extraActions: List<MenuAction> = emptyList()
): List<MenuAction> {
    val actionHandler = com.milki.launcher.presentation.search.LocalSearchActionHandler.current
    return remember(item, extraActions, actionHandler) {
        buildList {
            if (item is HomeItem.PinnedApp) {
                if (AppContextDataCache.hasWidgets(item.packageName)) {
                    add(createOpenAppWidgetsAction(item.label, actionHandler))
                }
                val quickActions = AppContextDataCache.getShortcuts(item.packageName).take(4)
                addAll(quickActions.map { createLaunchShortcutAction(it, actionHandler) })
                add(createAppInfoAction(item.packageName, actionHandler))
            }
            add(createUnpinAction(item.id, actionHandler))
            addAll(extraActions)
        }
    }
}

/**
 * Builds menu actions for an [AppInfo] in the app drawer or search results.
 *
 * Always includes the full app context: widgets + quick shortcuts + app info + uninstall.
 */
@Composable
fun buildAppDrawerMenuActions(
    appInfo: AppInfo,
    extraActions: List<MenuAction> = emptyList()
): List<MenuAction> {
    val actionHandler = com.milki.launcher.presentation.search.LocalSearchActionHandler.current
    return remember(appInfo, extraActions, actionHandler) {
        buildList {
            if (AppContextDataCache.hasWidgets(appInfo.packageName)) {
                add(createOpenAppWidgetsAction(appInfo.name, actionHandler))
            }
            val quickActions = AppContextDataCache.getShortcuts(appInfo.packageName).take(4)
            addAll(quickActions.map { createLaunchShortcutAction(it, actionHandler) })
            add(createAppInfoAction(appInfo.packageName, actionHandler))
            add(createUninstallAppAction(appInfo.packageName, actionHandler))
            addAll(extraActions)
        }
    }
}

/**
 * Convenience composable for displaying the context menu of an [AppInfo].
 * Used in the app drawer and search results.
 */
@Composable
fun AppItemContextMenu(
    appInfo: AppInfo,
    menuState: ItemContextMenuState,
    onExternalDragStarted: () -> Unit = {},
    extraActions: List<MenuAction> = emptyList(),
    modifier: Modifier = Modifier
) {
    ItemContextMenu(
        actions = buildAppDrawerMenuActions(appInfo, extraActions),
        expanded = menuState.showMenu,
        onDismiss = menuState::dismiss,
        focusable = menuState.isMenuFocusable,
        onExternalDragStarted = {
            menuState.dismiss()
            onExternalDragStarted()
        },
        modifier = modifier
    )
}

/**
 * Returns cached quick shortcuts for [packageName].
 */
fun getAppQuickActions(
    packageName: String,
    maxCount: Int = 4
): List<HomeItem.AppShortcut> {
    return AppContextDataCache.getShortcuts(packageName).take(maxCount)
}

/**
 * Builds menu actions for a file document in search results.
 */
fun buildFileItemMenuActions(
    file: FileDocument,
    actionHandler: (SearchResultAction) -> Unit
): List<MenuAction> {
    return listOf(
        createPinAction(
            isPinned = false,
            pinAction = SearchResultAction.PinFile(file),
            unpinAction = SearchResultAction.UnpinItem(
                HomeItem.PinnedFile.fromFileDocument(file).id
            ),
            actionHandler = actionHandler
        )
    )
}
