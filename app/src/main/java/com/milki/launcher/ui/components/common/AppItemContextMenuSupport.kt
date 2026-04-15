package com.milki.launcher.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.milki.launcher.core.intent.queryAppQuickShortcuts
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.ui.components.launcher.MenuAction
import com.milki.launcher.ui.components.launcher.createAppInfoAction
import com.milki.launcher.ui.components.launcher.createLaunchShortcutAction
import com.milki.launcher.ui.components.launcher.createPinAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

@Composable
fun rememberAppQuickActions(
    packageName: String,
    shouldLoad: Boolean,
    maxCount: Int = 4
): List<HomeItem.AppShortcut> {
    val context = LocalContext.current
    var cachedActions by remember(packageName) {
        mutableStateOf<List<HomeItem.AppShortcut>?>(null)
    }

    LaunchedEffect(packageName, shouldLoad, maxCount) {
        if (!shouldLoad || cachedActions != null) return@LaunchedEffect

        cachedActions = withContext(Dispatchers.IO) {
            queryAppQuickShortcuts(
                context = context,
                packageName = packageName,
                maxCount = maxCount
            )
        }
    }

    return cachedActions.orEmpty()
}

fun buildAppItemMenuActions(
    appInfo: AppInfo,
    quickActions: List<HomeItem.AppShortcut> = emptyList()
): List<MenuAction> {
    return buildList {
        addAll(quickActions.map(::createLaunchShortcutAction))

        add(
            createPinAction(
                isPinned = false,
                pinAction = SearchResultAction.PinApp(appInfo),
                unpinAction = SearchResultAction.UnpinItem(
                    HomeItem.PinnedApp.fromAppInfo(appInfo).id
                )
            )
        )

        add(createAppInfoAction(appInfo.packageName))
    }
}

fun buildFileItemMenuActions(file: FileDocument): List<MenuAction> {
    return listOf(
        createPinAction(
            isPinned = false,
            pinAction = SearchResultAction.PinFile(file),
            unpinAction = SearchResultAction.UnpinItem(
                HomeItem.PinnedFile.fromFileDocument(file).id
            )
        )
    )
}

typealias AppItemContextMenuState = ItemContextMenuState

@Composable
fun rememberAppItemContextMenuState(): AppItemContextMenuState {
    return rememberItemContextMenuState()
}