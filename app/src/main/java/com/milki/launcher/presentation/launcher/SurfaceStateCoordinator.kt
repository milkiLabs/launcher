package com.milki.launcher.presentation.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.milki.launcher.domain.model.HomeTapAction
import com.milki.launcher.domain.model.SwipeUpAction

/**
 * Simple state owner for launcher surfaces.
 *
 * This class keeps the launcher's transient UI rules in one place:
 * homescreen menu, drawer, widget picker, folder popup, and search.
 */
class SurfaceStateCoordinator(
    private val showSearch: () -> Unit,
    private val hideSearch: () -> Unit,
    private val isSearchVisible: () -> Boolean,
    private val isFolderOpen: () -> Boolean,
    private val closeFolder: () -> Unit,
    private val onAppDrawerVisibilityChanged: (Boolean) -> Unit = {}
) {
    private var wasResumed = false

    var contextMenuDismissSignal by mutableIntStateOf(0)
        private set

    var isHomescreenMenuOpen by mutableStateOf(false)
        private set

    var isAppDrawerOpen by mutableStateOf(false)
        private set

    var isWidgetPickerOpen by mutableStateOf(false)
        private set

    var widgetPickerQuery by mutableStateOf("")
        private set

    fun onResume() {
        wasResumed = true
    }

    fun updateHomescreenMenuOpen(isOpen: Boolean) {
        if (isHomescreenMenuOpen == isOpen) return

        if (isOpen) {
            dismissContextMenus()
        }
        isHomescreenMenuOpen = isOpen
    }

    fun updateAppDrawerOpen(isOpen: Boolean) {
        if (isAppDrawerOpen == isOpen) return

        dismissContextMenus()
        applyAppDrawerVisibility(isOpen)
    }

    fun updateWidgetPickerOpen(isOpen: Boolean) {
        if (isWidgetPickerOpen == isOpen) return

        dismissContextMenus()
        isWidgetPickerOpen = isOpen
        if (!isOpen) {
            widgetPickerQuery = ""
        }
    }

    fun updateWidgetPickerQuery(query: String) {
        if (widgetPickerQuery == query) return
        widgetPickerQuery = query
    }

    fun dismissContextMenus() {
        contextMenuDismissSignal += 1
    }

    fun handleHomeSwipeUp(action: SwipeUpAction) {
        when (action) {
            SwipeUpAction.OPEN_SEARCH -> {
                dismissContextMenus()
                closeTransientSurfaces(keepSearch = true)
                if (!isSearchVisible()) {
                    showSearch()
                }
            }

            SwipeUpAction.OPEN_APP_DRAWER -> {
                dismissContextMenus()
                closeTransientSurfaces(keepSearch = false)
                applyAppDrawerVisibility(true)
            }

            SwipeUpAction.DO_NOTHING -> Unit
        }
    }

    fun handleHomeTap(action: HomeTapAction) {
        when (action) {
            HomeTapAction.OPEN_SEARCH -> {
                dismissContextMenus()
                closeTransientSurfaces(keepSearch = true)
                if (!isSearchVisible()) {
                    showSearch()
                }
            }

            HomeTapAction.DO_NOTHING -> Unit
        }
    }

    fun handleHomeIntent() {
        when {
            isAppDrawerOpen -> updateAppDrawerOpen(false)
            isWidgetPickerOpen -> updateWidgetPickerOpen(false)
            isFolderOpen() -> {
                dismissContextMenus()
                closeFolder()
            }

            !wasResumed -> {
                dismissContextMenus()
                isHomescreenMenuOpen = false
                if (isSearchVisible()) {
                    hideSearch()
                }
            }

            isHomescreenMenuOpen -> {
                dismissContextMenus()
                isHomescreenMenuOpen = false
                if (!isSearchVisible()) {
                    showSearch()
                }
            }

            isSearchVisible() -> {
                dismissContextMenus()
                hideSearch()
            }

            else -> {
                dismissContextMenus()
                showSearch()
            }
        }
    }

    fun handleBackPressed(): Boolean {
        when {
            isFolderOpen() -> {
                dismissContextMenus()
                closeFolder()
            }

            isAppDrawerOpen -> updateAppDrawerOpen(false)
            isWidgetPickerOpen -> updateWidgetPickerOpen(false)
            isSearchVisible() -> {
                dismissContextMenus()
                hideSearch()
            }
        }

        return true
    }

    fun onStop() {
        wasResumed = false
        dismissContextMenus()
        isHomescreenMenuOpen = false
        applyAppDrawerVisibility(false)
        isWidgetPickerOpen = false
        widgetPickerQuery = ""
        if (isFolderOpen()) {
            closeFolder()
        }
    }

    private fun closeTransientSurfaces(keepSearch: Boolean) {
        isHomescreenMenuOpen = false
        applyAppDrawerVisibility(false)
        isWidgetPickerOpen = false
        widgetPickerQuery = ""
        if (isFolderOpen()) {
            closeFolder()
        }
        if (!keepSearch && isSearchVisible()) {
            hideSearch()
        }
    }

    private fun applyAppDrawerVisibility(isOpen: Boolean) {
        if (isAppDrawerOpen == isOpen) return

        isAppDrawerOpen = isOpen
        onAppDrawerVisibilityChanged(isOpen)
    }
}
