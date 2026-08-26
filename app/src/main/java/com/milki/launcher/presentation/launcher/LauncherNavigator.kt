package com.milki.launcher.presentation.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import com.milki.launcher.domain.model.LauncherTriggerAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * Transient launcher destinations. This stack is intentionally not saveable:
 * launcher overlays are dismissed when the activity stops.
 */
@Serializable
sealed interface LauncherRoute : NavKey {
    @Serializable
    data object Home : LauncherRoute

    @Serializable
    data object Search : LauncherRoute

    @Serializable
    data object AppDrawer : LauncherRoute

    @Serializable
    data object WidgetPicker : LauncherRoute

    @Serializable
    data object ShortcutManager : LauncherRoute

    @Serializable
    data class Folder(val folderId: String) : LauncherRoute
}

/**
 * Owns transient launcher navigation.
 *
 * [backStack] is authoritative for search, drawer, widget picker, shortcut
 * manager, and folder visibility. Only the homescreen context menu remains
 * local because menus are not navigation destinations.
 *
 * SEARCH VISIBILITY:
 * The navigator is the single owner of search visibility. [searchVisibility]
 * projects the back-stack state as a hot flow so consumers (e.g.
 * SearchViewModel) can observe it without keeping their own mirror copy.
 */
class LauncherNavigator(
    private val closeFolder: () -> Unit,
    private val openFolder: (String) -> Unit = {},
    private val openNotificationShade: () -> Unit = {},
    private val lockScreen: () -> Unit = {},
    private val onAppDrawerVisibilityChanged: (Boolean) -> Unit = {}
) {
    val backStack = mutableStateListOf<LauncherRoute>(LauncherRoute.Home)

    private var wasResumed = false

    var widgetPickerQuery by mutableStateOf("")
        private set

    /**
     * Hot projection of "is the search route open". Updated only from
     * [openRoute]/[closeRoute] so it can never diverge from [backStack].
     */
    private val searchVisibility = MutableStateFlow(false)
    val searchVisibilityFlow: StateFlow<Boolean> = searchVisibility.asStateFlow()

    var isHomescreenMenuOpen by mutableStateOf(false)
        private set

    val currentRoute: LauncherRoute
        get() = backStack.lastOrNull() ?: LauncherRoute.Home

    val isAtHome: Boolean
        get() = currentRoute == LauncherRoute.Home

    val isSearchOpen: Boolean
        get() = currentRoute == LauncherRoute.Search

    val isAppDrawerOpen: Boolean
        get() = currentRoute == LauncherRoute.AppDrawer

    val isWidgetPickerOpen: Boolean
        get() = currentRoute == LauncherRoute.WidgetPicker

    val isShortcutManagerOpen: Boolean
        get() = currentRoute == LauncherRoute.ShortcutManager

    fun onResume() {
        wasResumed = true
    }

    fun updateHomescreenMenuOpen(isOpen: Boolean) {
        if (isHomescreenMenuOpen == isOpen) return
        isHomescreenMenuOpen = isOpen
    }

    fun updateAppDrawerOpen(isOpen: Boolean) {
        if (isOpen) {
            navigate(LauncherRoute.AppDrawer)
        } else if (isAppDrawerOpen) {
            pop()
        }
    }

    fun updateWidgetPickerOpen(isOpen: Boolean) {
        if (isOpen) {
            openWidgetPicker()
        } else if (isWidgetPickerOpen) {
            pop()
        }
    }

    fun openWidgetPicker(initialQuery: String = "") {
        widgetPickerQuery = initialQuery
        navigate(LauncherRoute.WidgetPicker)
    }

    fun updateShortcutManagerOpen(isOpen: Boolean) {
        if (isOpen) {
            navigate(LauncherRoute.ShortcutManager)
        } else if (isShortcutManagerOpen) {
            pop()
        }
    }

    fun updateFolderOpen(folderId: String?) {
        if (folderId == null) {
            if (currentRoute is LauncherRoute.Folder) pop()
        } else {
            navigate(LauncherRoute.Folder(folderId))
        }
    }

    fun updateWidgetPickerQuery(query: String) {
        if (!isWidgetPickerOpen || widgetPickerQuery == query) return
        widgetPickerQuery = query
    }

    fun navigate(route: LauncherRoute) {
        isHomescreenMenuOpen = false

        val oldRoute = currentRoute
        if (oldRoute == route) return

        closeRoute(oldRoute)
        trimToHome()
        backStack.add(route)
        openRoute(route)
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false

        isHomescreenMenuOpen = false
        val oldRoute = backStack.removeLast()
        closeRoute(oldRoute)
        openRoute(currentRoute)
        return true
    }

    fun clearTransientRoutes() {
        isHomescreenMenuOpen = false
        closeRoute(currentRoute)
        trimToHome()
    }

    fun handleHomeTriggerAction(
        action: LauncherTriggerAction,
        onOpenAppTarget: (() -> Unit)? = null
    ) {
        when (action) {
            LauncherTriggerAction.OPEN_SEARCH -> navigate(LauncherRoute.Search)
            LauncherTriggerAction.OPEN_APP_DRAWER -> navigate(LauncherRoute.AppDrawer)
            LauncherTriggerAction.OPEN_NOTIFICATION_SHADE -> {
                clearTransientRoutes()
                openNotificationShade()
            }

            LauncherTriggerAction.LOCK_SCREEN -> {
                clearTransientRoutes()
                lockScreen()
            }

            LauncherTriggerAction.OPEN_APP,
            LauncherTriggerAction.OPEN_ACTION_SHORTCUT -> {
                clearTransientRoutes()
                onOpenAppTarget?.invoke()
            }

            LauncherTriggerAction.DO_NOTHING -> Unit
        }
    }

    fun handleHomeIntent() {
        when {
            !isAtHome -> clearTransientRoutes()
            !wasResumed -> {
                isHomescreenMenuOpen = false
            }

            isHomescreenMenuOpen -> {
                updateHomescreenMenuOpen(false)
                navigate(LauncherRoute.Search)
            }

            else -> navigate(LauncherRoute.Search)
        }
    }

    fun onStop() {
        wasResumed = false
        clearTransientRoutes()
    }

    private fun trimToHome() {
        if (backStack.isEmpty()) {
            backStack.add(LauncherRoute.Home)
            return
        }
        while (backStack.size > 1) {
            backStack.removeLast()
        }
        if (backStack.first() != LauncherRoute.Home) {
            backStack.clear()
            backStack.add(LauncherRoute.Home)
        }
    }

    private fun openRoute(route: LauncherRoute) {
        when (route) {
            LauncherRoute.Search -> searchVisibility.value = true
            LauncherRoute.AppDrawer -> onAppDrawerVisibilityChanged(true)
            is LauncherRoute.Folder -> openFolder(route.folderId)
            LauncherRoute.Home,
            LauncherRoute.WidgetPicker,
            LauncherRoute.ShortcutManager -> Unit
        }
    }

    private fun closeRoute(route: LauncherRoute) {
        when (route) {
            LauncherRoute.Search -> searchVisibility.value = false
            LauncherRoute.AppDrawer -> onAppDrawerVisibilityChanged(false)
            is LauncherRoute.Folder -> closeFolder()
            LauncherRoute.WidgetPicker -> widgetPickerQuery = ""
            LauncherRoute.Home,
            LauncherRoute.ShortcutManager -> Unit
        }
    }
}
