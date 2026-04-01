package com.milki.launcher.presentation.main

import com.milki.launcher.domain.model.SwipeUpAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SurfaceStateCoordinator].
 *
 * WHY THESE TESTS EXIST:
 * Surface close ordering (folder vs drawer vs widget vs search) is easy to regress.
 * These tests lock ordering and side effects so MainActivity extraction stays safe.
 */
class SurfaceStateCoordinatorTest {

    /**
     * HOME press should close app drawer first when open.
     */
    @Test
    fun home_press_closes_app_drawer_first() {
        var folderOpen = false
        var closeFolderCalls = 0
        var hideSearchCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { },
            hideSearch = { hideSearchCalls++ },
            isFolderOpen = { folderOpen },
            closeFolder = {
                closeFolderCalls++
                folderOpen = false
            }
        )

        coordinator.updateAppDrawerOpen(true)
        val consumed = coordinator.consumeHomePressForLayeredSurface()

        assertTrue(consumed)
        assertFalse(coordinator.isAppDrawerOpen)
        assertEquals(0, closeFolderCalls)
        assertEquals(0, hideSearchCalls)
    }

    /**
     * HOME press should clear drawer query first, then close drawer on next press.
     */
    @Test
    fun home_press_clears_drawer_query_before_closing_drawer() {
        var folderOpen = false
        var drawerQuery = "maps"
        var clearDrawerQueryCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { },
            hideSearch = { },
            isFolderOpen = { folderOpen },
            closeFolder = { folderOpen = false },
            getDrawerQuery = { drawerQuery },
            clearDrawerQuery = {
                clearDrawerQueryCalls++
                drawerQuery = ""
            }
        )

        coordinator.updateAppDrawerOpen(true)

        val firstConsumed = coordinator.consumeHomePressForLayeredSurface()
        assertTrue(firstConsumed)
        assertEquals(1, clearDrawerQueryCalls)
        assertEquals("", drawerQuery)
        assertTrue(coordinator.isAppDrawerOpen)

        val secondConsumed = coordinator.consumeHomePressForLayeredSurface()
        assertTrue(secondConsumed)
        assertFalse(coordinator.isAppDrawerOpen)
    }

    /**
     * HOME press should close widget picker when drawer is closed.
     */
    @Test
    fun home_press_closes_widget_picker_when_drawer_closed() {
        var folderOpen = false

        val coordinator = SurfaceStateCoordinator(
            showSearch = { },
            hideSearch = { },
            isFolderOpen = { folderOpen },
            closeFolder = { folderOpen = false }
        )

        coordinator.updateWidgetPickerOpen(true)
        val consumed = coordinator.consumeHomePressForLayeredSurface()

        assertTrue(consumed)
        assertFalse(coordinator.isWidgetPickerOpen)
    }

    /**
     * HOME press should close folder popup when drawer and widget picker are closed.
     */
    @Test
    fun home_press_closes_folder_when_no_other_surface_is_open() {
        var folderOpen = true
        var closeFolderCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { },
            hideSearch = { },
            isFolderOpen = { folderOpen },
            closeFolder = {
                closeFolderCalls++
                folderOpen = false
            }
        )

        val consumed = coordinator.consumeHomePressForLayeredSurface()

        assertTrue(consumed)
        assertEquals(1, closeFolderCalls)
        assertFalse(folderOpen)
    }

    /**
     * BACK press should close folder before any other surface.
     */
    @Test
    fun back_press_prioritizes_folder_close() {
        var folderOpen = true
        var closeFolderCalls = 0
        var hideSearchCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { },
            hideSearch = { hideSearchCalls++ },
            isFolderOpen = { folderOpen },
            closeFolder = {
                closeFolderCalls++
                folderOpen = false
            }
        )

        coordinator.updateAppDrawerOpen(true)
        coordinator.updateWidgetPickerOpen(true)

        val consumed = coordinator.handleBackPressed(isSearchVisible = true)

        assertTrue(consumed)
        assertEquals(1, closeFolderCalls)
        assertTrue(coordinator.isAppDrawerOpen)
        assertTrue(coordinator.isWidgetPickerOpen)
        assertEquals(0, hideSearchCalls)
    }

    /**
     * Swipe-up app-drawer action should close menu, hide search, close folder, then open drawer.
     */
    @Test
    fun swipe_open_drawer_applies_expected_transition_sequence() {
        var folderOpen = true
        var closeFolderCalls = 0
        var hideSearchCalls = 0
        var showSearchCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { showSearchCalls++ },
            hideSearch = { hideSearchCalls++ },
            isFolderOpen = { folderOpen },
            closeFolder = {
                closeFolderCalls++
                folderOpen = false
            }
        )

        coordinator.updateHomescreenMenuOpen(true)

        coordinator.handleHomeSwipeUp(SwipeUpAction.OPEN_APP_DRAWER)

        assertFalse(coordinator.isHomescreenMenuOpen)
        assertTrue(coordinator.isAppDrawerOpen)
        assertEquals(1, hideSearchCalls)
        assertEquals(0, showSearchCalls)
        assertEquals(1, closeFolderCalls)
        assertFalse(folderOpen)
    }

    @Test
    fun swipe_open_search_closes_transient_surfaces_and_opens_search() {
        var folderOpen = true
        var closeFolderCalls = 0
        var hideSearchCalls = 0
        var showSearchCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { showSearchCalls++ },
            hideSearch = { hideSearchCalls++ },
            isFolderOpen = { folderOpen },
            closeFolder = {
                closeFolderCalls++
                folderOpen = false
            }
        )

        coordinator.updateHomescreenMenuOpen(true)
        coordinator.updateAppDrawerOpen(true)
        coordinator.updateWidgetPickerOpen(true)

        coordinator.handleHomeSwipeUp(SwipeUpAction.OPEN_SEARCH)

        assertFalse(coordinator.isHomescreenMenuOpen)
        assertFalse(coordinator.isAppDrawerOpen)
        assertFalse(coordinator.isWidgetPickerOpen)
        assertEquals(1, showSearchCalls)
        assertEquals(0, hideSearchCalls)
        assertEquals(1, closeFolderCalls)
        assertFalse(folderOpen)
    }

    @Test
    fun swipe_do_nothing_leaves_state_unchanged() {
        var folderOpen = false
        var closeFolderCalls = 0
        var hideSearchCalls = 0
        var showSearchCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { showSearchCalls++ },
            hideSearch = { hideSearchCalls++ },
            isFolderOpen = { folderOpen },
            closeFolder = { closeFolderCalls++ }
        )

        coordinator.updateHomescreenMenuOpen(true)

        coordinator.handleHomeSwipeUp(SwipeUpAction.DO_NOTHING)

        assertTrue(coordinator.isHomescreenMenuOpen)
        assertEquals(0, showSearchCalls)
        assertEquals(0, hideSearchCalls)
        assertEquals(0, closeFolderCalls)
    }

    /**
     * onStop should clear transient drawer/widget surfaces and close folder.
     */
    @Test
    fun on_stop_clears_transient_surfaces_and_folder() {
        var folderOpen = true
        var closeFolderCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { },
            hideSearch = { },
            isFolderOpen = { folderOpen },
            closeFolder = {
                closeFolderCalls++
                folderOpen = false
            }
        )

        coordinator.updateAppDrawerOpen(true)
        coordinator.updateWidgetPickerOpen(true)

        coordinator.onStop()

        assertFalse(coordinator.isAppDrawerOpen)
        assertFalse(coordinator.isWidgetPickerOpen)
        assertEquals(1, closeFolderCalls)
    }
}
