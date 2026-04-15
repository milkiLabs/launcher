package com.milki.launcher.presentation.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.milki.launcher.domain.model.SwipeUpAction
import com.milki.launcher.presentation.drawer.DrawerSurfaceController
import com.milki.launcher.presentation.drawer.DrawerTransitionState

/**
 * SurfaceStateCoordinator.kt - Centralized orchestration for layered launcher surfaces.
 *
 * WHY THIS FILE EXISTS:
 * MainActivity previously tracked and mutated several cross-feature surface flags directly:
 * - homescreen menu open/closed
 * - app drawer open/closed
 * - widget picker open/closed
 * - folder popup close behavior through HomeViewModel
 * - search close behavior through SearchViewModel
 *
 * This coordinator moves those cross-cutting transitions into one dedicated policy owner.
 *
 * DESIGN GOAL:
 * Keep MainActivity as a shell that delegates "which surface closes first" decisions,
 * while preserving existing user-visible behavior and dismissal ordering.
 */
interface SurfaceStateCoordinatorContract {

    /**
     * Monotonically increasing signal used by UI menus to dismiss themselves.
     */
    val contextMenuDismissSignal: Int

    /**
     * Whether the homescreen long-press dropdown menu is currently visible.
     */
    val isHomescreenMenuOpen: Boolean

    /**
     * Whether the app drawer bottom sheet is currently visible.
     */
    val isAppDrawerOpen: Boolean

    /** Current drawer transition state from drawer controller. */
    val drawerTransitionState: DrawerTransitionState

    /**
     * Whether the widget picker bottom sheet is currently visible.
     */
    val isWidgetPickerOpen: Boolean

    /** Current widget-picker search query. */
    val widgetPickerQuery: String

    /**
     * Updates homescreen menu visibility.
     */
    fun updateHomescreenMenuOpen(isOpen: Boolean)

    /**
     * Updates app drawer visibility.
     */
    fun updateAppDrawerOpen(isOpen: Boolean)

    /**
     * Updates widget picker visibility.
     */
    fun updateWidgetPickerOpen(isOpen: Boolean)

    /** Updates widget-picker search query state. */
    fun updateWidgetPickerQuery(query: String)

    /**
     * Requests dismissal of all transient item action/context menus.
     */
    fun dismissContextMenus()

    /**
     * Applies the configured homescreen swipe-up action.
     */
    fun handleHomeSwipeUp(action: SwipeUpAction)

    /**
     * Applies layered-dismiss policy for HOME button presses before running home policy.
     *
     * Returns true when a surface was consumed/closed and the home press should stop.
     */
    fun consumeHomePressForLayeredSurface(): Boolean

    /**
     * Applies layered-dismiss policy for back button presses.
     *
     * Returns true when back is consumed.
     */
    fun handleBackPressed(isSearchVisible: Boolean): Boolean

    /**
     * Applies lifecycle stop cleanup for transient surfaces.
     */
    fun onStop()
}

/**
 * Default implementation of [SurfaceStateCoordinatorContract].
 *
 * The constructor accepts lambdas instead of concrete ViewModel types so this coordinator
 * remains lightweight and unit-test friendly.
 */
class SurfaceStateCoordinator(
    private val showSearch: () -> Unit,
    private val hideSearch: () -> Unit,
    private val isSearchVisible: () -> Boolean = { false },
    private val isFolderOpen: () -> Boolean,
    private val closeFolder: () -> Unit,
    private val onAppDrawerVisibilityChanged: (Boolean) -> Unit = {}
) : SurfaceStateCoordinatorContract {

    private val drawerSurfaceController = DrawerSurfaceController()

    override var contextMenuDismissSignal by mutableIntStateOf(0)
        private set

    /**
     * Compose-observed state for homescreen menu visibility.
     */
    override var isHomescreenMenuOpen by mutableStateOf(false)
        private set

    /**
     * Compose-observed state for app drawer visibility.
     */
    override var isAppDrawerOpen by mutableStateOf(false)
        private set

    override val drawerTransitionState: DrawerTransitionState
        get() = drawerSurfaceController.state.value

    /**
     * Compose-observed state for widget picker visibility.
     */
    override var isWidgetPickerOpen by mutableStateOf(false)
        private set

    override var widgetPickerQuery by mutableStateOf("")
        private set

    /**
     * Updates homescreen menu visibility.
     */
    override fun updateHomescreenMenuOpen(isOpen: Boolean) {
        if (isHomescreenMenuOpen == isOpen) {
            return
        }
        if (isOpen) {
            dismissContextMenus()
        }
        isHomescreenMenuOpen = isOpen
    }

    /**
     * Updates app drawer visibility.
     */
    override fun updateAppDrawerOpen(isOpen: Boolean) {
        if (isAppDrawerOpen == isOpen) {
            return
        }
        dismissContextMenus()
        applyDrawerVisibility(isOpen)
    }

    /**
     * Updates widget picker visibility.
     */
    override fun updateWidgetPickerOpen(isOpen: Boolean) {
        if (isWidgetPickerOpen == isOpen) {
            return
        }
        dismissContextMenus()
        isWidgetPickerOpen = isOpen
        if (!isOpen) {
            widgetPickerQuery = ""
        }
    }

    override fun updateWidgetPickerQuery(query: String) {
        widgetPickerQuery = query
    }

    override fun dismissContextMenus() {
        contextMenuDismissSignal += 1
    }

    /**
     * Handles homescreen swipe-up behavior in one place.
     *
     * Keeping this decision here avoids splitting gesture intent handling across
     * UI code and MainActivity branches.
     */
    override fun handleHomeSwipeUp(action: SwipeUpAction) {
        when (action) {
            SwipeUpAction.OPEN_SEARCH -> {
                dismissContextMenus()
                if (isHomescreenMenuOpen) {
                    isHomescreenMenuOpen = false
                }
                if (isAppDrawerOpen) {
                    applyDrawerVisibility(false)
                }
                if (isWidgetPickerOpen) {
                    isWidgetPickerOpen = false
                }
                if (isFolderOpen()) {
                    closeFolder()
                }
                if (!isSearchVisible()) {
                    showSearch()
                }
            }

            SwipeUpAction.OPEN_APP_DRAWER -> {
                dismissContextMenus()
                if (isHomescreenMenuOpen) {
                    isHomescreenMenuOpen = false
                }
                if (isSearchVisible()) {
                    hideSearch()
                }
                if (isWidgetPickerOpen) {
                    isWidgetPickerOpen = false
                }
                if (isFolderOpen()) {
                    closeFolder()
                }
                if (!isAppDrawerOpen) {
                    applyDrawerVisibility(true)
                }
            }

            SwipeUpAction.DO_NOTHING -> Unit
        }
    }

    /**
     * Closes one active layered surface for HOME button behavior.
     *
     * ORDER IS IMPORTANT:
     * 1. App drawer
     * 2. Widget picker
     * 3. Folder popup
     *
     * This matches the previous MainActivity behavior exactly.
     */
    override fun consumeHomePressForLayeredSurface(): Boolean {
        if (isAppDrawerOpen) {
            dismissContextMenus()
            applyDrawerVisibility(false)
            return true
        }

        if (isWidgetPickerOpen) {
            dismissContextMenus()
            isWidgetPickerOpen = false
            widgetPickerQuery = ""
            return true
        }

        if (isFolderOpen()) {
            dismissContextMenus()
            closeFolder()
            return true
        }

        return false
    }

    /**
     * Closes one active layered surface for BACK button behavior.
     *
     * ORDER IS IMPORTANT:
     * 1. Folder popup
     * 2. App drawer
     * 3. Widget picker
     * 4. Search dialog
     *
     * If none are open, back is still consumed to keep launcher as root surface.
     */
    override fun handleBackPressed(isSearchVisible: Boolean): Boolean {
        if (isFolderOpen()) {
            dismissContextMenus()
            closeFolder()
            return true
        }

        if (isAppDrawerOpen) {
            dismissContextMenus()
            applyDrawerVisibility(false)
            return true
        }

        if (isWidgetPickerOpen) {
            dismissContextMenus()
            isWidgetPickerOpen = false
            widgetPickerQuery = ""
            return true
        }

        if (isSearchVisible) {
            dismissContextMenus()
            hideSearch()
            return true
        }

        // Launcher root behavior: consume back and stay on home.
        return true
    }

    /**
     * Clears transient state when activity leaves foreground.
     *
     * We intentionally keep homescreen menu unchanged because previous behavior only
     * reset drawer/picker and closed folder on stop.
     */
    override fun onStop() {
        dismissContextMenus()
        applyDrawerVisibility(false)
        isWidgetPickerOpen = false
        widgetPickerQuery = ""
        closeFolder()
    }

    private fun applyDrawerVisibility(isOpen: Boolean) {
        if (isOpen) {
            drawerSurfaceController.requestOpen()
        } else {
            drawerSurfaceController.requestClose()
        }
        val isVisible = drawerSurfaceController.isVisible()
        isAppDrawerOpen = isVisible
        onAppDrawerVisibilityChanged(isVisible)
    }
}
