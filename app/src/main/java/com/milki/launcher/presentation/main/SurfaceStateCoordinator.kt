package com.milki.launcher.presentation.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
     * Whether the homescreen long-press dropdown menu is currently visible.
     */
    val isHomescreenMenuOpen: Boolean

    /**
     * Whether the app drawer bottom sheet is currently visible.
     */
    val isAppDrawerOpen: Boolean

    /**
     * Whether the widget picker bottom sheet is currently visible.
     */
    val isWidgetPickerOpen: Boolean

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

    /**
     * Applies swipe-up policy transition to open app drawer.
     *
     * This mirrors previous behavior in MainActivity:
     * 1. Close homescreen menu.
     * 2. Hide search.
     * 3. Close open folder.
     * 4. Open app drawer.
     */
    fun openAppDrawerFromSwipeGesture()

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
    private val hideSearch: () -> Unit,
    private val isFolderOpen: () -> Boolean,
    private val closeFolder: () -> Unit
) : SurfaceStateCoordinatorContract {

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

    /**
     * Compose-observed state for widget picker visibility.
     */
    override var isWidgetPickerOpen by mutableStateOf(false)
        private set

    /**
     * Updates homescreen menu visibility.
     */
    override fun updateHomescreenMenuOpen(isOpen: Boolean) {
        isHomescreenMenuOpen = isOpen
    }

    /**
     * Updates app drawer visibility.
     */
    override fun updateAppDrawerOpen(isOpen: Boolean) {
        isAppDrawerOpen = isOpen
    }

    /**
     * Updates widget picker visibility.
     */
    override fun updateWidgetPickerOpen(isOpen: Boolean) {
        isWidgetPickerOpen = isOpen
    }

    /**
     * Handles the launcher swipe-up action that opens app drawer.
     */
    override fun openAppDrawerFromSwipeGesture() {
        isHomescreenMenuOpen = false
        hideSearch()
        closeFolder()
        isAppDrawerOpen = true
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
            isAppDrawerOpen = false
            return true
        }

        if (isWidgetPickerOpen) {
            isWidgetPickerOpen = false
            return true
        }

        if (isFolderOpen()) {
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
            closeFolder()
            return true
        }

        if (isAppDrawerOpen) {
            isAppDrawerOpen = false
            return true
        }

        if (isWidgetPickerOpen) {
            isWidgetPickerOpen = false
            return true
        }

        if (isSearchVisible) {
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
        isAppDrawerOpen = false
        isWidgetPickerOpen = false
        closeFolder()
    }
}
