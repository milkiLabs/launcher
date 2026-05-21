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
import com.milki.launcher.ui.components.launcher.createUninstallAppAction
import com.milki.launcher.ui.components.launcher.createPinAction

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
 * Returns cached quick shortcuts for [packageName].
 *
 * Reads synchronously from [AppContextDataCache], which is pre-populated
 * at startup and refreshed on package changes. No LaunchedEffect, no
 * async loading — data is available in the same frame.
 */
fun getAppQuickActions(
    packageName: String,
    maxCount: Int = 4
): List<HomeItem.AppShortcut> {
    return AppContextDataCache.getShortcuts(packageName).take(maxCount)
}

/**
 * Returns whether [packageName] has any widget providers.
 *
 * Reads synchronously from [AppContextDataCache].
 */
fun getAppHasWidgets(packageName: String): Boolean {
    return AppContextDataCache.hasWidgets(packageName)
}

@Composable
fun ItemContextMenu(
    packageName: String,
    appName: String? = null,
    expanded: Boolean,
    onDismiss: () -> Unit,
    focusable: Boolean = true,
    onExternalDragStarted: (() -> Unit)? = null,
    extraActions: List<MenuAction> = emptyList(),
    includeAppUtilityActions: Boolean = true,
    modifier: Modifier = Modifier
) {
    val actionHandler = com.milki.launcher.presentation.search.LocalSearchActionHandler.current
    val actions = remember(packageName, appName, extraActions, includeAppUtilityActions, actionHandler) {
        buildList {
            if (includeAppUtilityActions) {
                addAll(
                    buildAppUtilityMenuActions(
                        packageName = packageName,
                        appName = appName,
                        quickActions = getAppQuickActions(packageName),
                        hasWidgets = getAppHasWidgets(packageName),
                        actionHandler = actionHandler
                    )
                )
            }
            addAll(extraActions)
        }
    }
    
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
 * Convenience composable for displaying the context menu of an [AppInfo].
 *
 * Bundles the standard uninstall action and wires up the menu state
 * so callers don't repeat the same boilerplate.
 */
@Composable
fun AppItemContextMenu(
    appInfo: AppInfo,
    menuState: ItemContextMenuState,
    onExternalDragStarted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val actionHandler = com.milki.launcher.presentation.search.LocalSearchActionHandler.current
    ItemContextMenu(
        packageName = appInfo.packageName,
        appName = appInfo.name,
        expanded = menuState.showMenu,
        onDismiss = menuState::dismiss,
        focusable = menuState.isMenuFocusable,
        onExternalDragStarted = {
            menuState.dismiss()
            onExternalDragStarted()
        },
        extraActions = listOf(
            createUninstallAppAction(
                packageName = appInfo.packageName,
                actionHandler = actionHandler
            )
        ),
        modifier = modifier
    )
}

fun buildAppUtilityMenuActions(
    packageName: String,
    appName: String? = null,
    quickActions: List<HomeItem.AppShortcut> = emptyList(),
    hasWidgets: Boolean = false,
    actionHandler: (com.milki.launcher.presentation.search.SearchResultAction) -> Unit
): List<MenuAction> {
    return buildList {
        if (hasWidgets) {
            add(createOpenAppWidgetsAction(appName ?: packageName, actionHandler))
        }
        addAll(quickActions.map { createLaunchShortcutAction(it, actionHandler) })
        if (packageName.isNotBlank()) {
            add(createAppInfoAction(packageName, actionHandler))
        }
    }
}

fun HomeItem.appInfoPackageNameOrNull(): String? {
    return when (this) {
        is HomeItem.PinnedApp -> packageName
        is HomeItem.AppShortcut -> packageName
        is HomeItem.ActionShortcut -> packageName
        else -> null
    }
}

fun buildFileItemMenuActions(
    file: com.milki.launcher.domain.model.FileDocument,
    actionHandler: (com.milki.launcher.presentation.search.SearchResultAction) -> Unit
): List<MenuAction> {
    return listOf(
        createPinAction(
            isPinned = false,
            pinAction = com.milki.launcher.presentation.search.SearchResultAction.PinFile(file),
            unpinAction = com.milki.launcher.presentation.search.SearchResultAction.UnpinItem(
                HomeItem.PinnedFile.fromFileDocument(file).id
            ),
            actionHandler = actionHandler
        )
    )
}
