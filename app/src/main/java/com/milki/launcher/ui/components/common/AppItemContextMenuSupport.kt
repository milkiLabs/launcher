package com.milki.launcher.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.ui.components.launcher.MenuAction
import com.milki.launcher.ui.components.launcher.createAppInfoAction
import com.milki.launcher.ui.components.launcher.createPinAction

@Stable
class AppItemContextMenuState {
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
fun rememberAppItemContextMenuState(): AppItemContextMenuState {
    return remember { AppItemContextMenuState() }
}

fun buildAppItemMenuActions(appInfo: AppInfo): List<MenuAction> {
    return listOf(
        createPinAction(
            isPinned = false,
            pinAction = SearchResultAction.PinApp(appInfo),
            unpinAction = SearchResultAction.UnpinItem(
                HomeItem.PinnedApp.fromAppInfo(appInfo).id
            )
        ),
        createAppInfoAction(appInfo.packageName)
    )
}