package com.milki.launcher.presentation.launcher

import com.milki.launcher.domain.model.SwipeUpAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceStateCoordinatorTest {

    @Test
    fun home_intent_closes_app_drawer_first() {
        val coordinator = SurfaceStateCoordinator(
            showSearch = { },
            hideSearch = { },
            isSearchVisible = { false },
            isFolderOpen = { false },
            closeFolder = { }
        )

        coordinator.updateAppDrawerOpen(true)
        coordinator.handleHomeIntent()

        assertFalse(coordinator.isAppDrawerOpen)
    }

    @Test
    fun home_intent_closes_widget_picker_when_drawer_closed() {
        val coordinator = SurfaceStateCoordinator(
            showSearch = { },
            hideSearch = { },
            isSearchVisible = { false },
            isFolderOpen = { false },
            closeFolder = { }
        )

        coordinator.updateWidgetPickerOpen(true)
        coordinator.handleHomeIntent()

        assertFalse(coordinator.isWidgetPickerOpen)
    }

    @Test
    fun home_intent_closes_folder_when_no_other_surface_is_open() {
        var folderOpen = true
        var closeFolderCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { },
            hideSearch = { },
            isSearchVisible = { false },
            isFolderOpen = { folderOpen },
            closeFolder = {
                closeFolderCalls++
                folderOpen = false
            }
        )

        coordinator.handleHomeIntent()

        assertEquals(1, closeFolderCalls)
        assertFalse(folderOpen)
    }

    @Test
    fun first_home_intent_after_background_hides_search_without_reopening_it() {
        var hideSearchCalls = 0
        var showSearchCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { showSearchCalls++ },
            hideSearch = { hideSearchCalls++ },
            isSearchVisible = { true },
            isFolderOpen = { false },
            closeFolder = { }
        )

        coordinator.onStop()
        coordinator.handleHomeIntent()

        assertEquals(1, hideSearchCalls)
        assertEquals(0, showSearchCalls)
    }

    @Test
    fun home_intent_closes_menu_then_opens_search() {
        var showSearchCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { showSearchCalls++ },
            hideSearch = { },
            isSearchVisible = { false },
            isFolderOpen = { false },
            closeFolder = { }
        )

        coordinator.onResume()
        coordinator.updateHomescreenMenuOpen(true)
        coordinator.handleHomeIntent()

        assertFalse(coordinator.isHomescreenMenuOpen)
        assertEquals(1, showSearchCalls)
    }

    @Test
    fun home_intent_opens_search_when_already_on_home_and_search_hidden() {
        var showSearchCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { showSearchCalls++ },
            hideSearch = { },
            isSearchVisible = { false },
            isFolderOpen = { false },
            closeFolder = { }
        )

        coordinator.onResume()
        coordinator.handleHomeIntent()

        assertEquals(1, showSearchCalls)
    }

    @Test
    fun home_intent_hides_search_when_search_is_visible() {
        var hideSearchCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { },
            hideSearch = { hideSearchCalls++ },
            isSearchVisible = { true },
            isFolderOpen = { false },
            closeFolder = { }
        )

        coordinator.onResume()
        coordinator.handleHomeIntent()

        assertEquals(1, hideSearchCalls)
    }

    @Test
    fun back_press_prioritizes_folder_close() {
        var folderOpen = true
        var closeFolderCalls = 0
        var hideSearchCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { },
            hideSearch = { hideSearchCalls++ },
            isSearchVisible = { true },
            isFolderOpen = { folderOpen },
            closeFolder = {
                closeFolderCalls++
                folderOpen = false
            }
        )

        coordinator.updateAppDrawerOpen(true)
        coordinator.updateWidgetPickerOpen(true)

        val consumed = coordinator.handleBackPressed()

        assertTrue(consumed)
        assertEquals(1, closeFolderCalls)
        assertTrue(coordinator.isAppDrawerOpen)
        assertTrue(coordinator.isWidgetPickerOpen)
        assertEquals(0, hideSearchCalls)
    }

    @Test
    fun swipe_open_drawer_applies_expected_transition_sequence() {
        var folderOpen = true
        var closeFolderCalls = 0
        var hideSearchCalls = 0
        var showSearchCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { showSearchCalls++ },
            hideSearch = { hideSearchCalls++ },
            isSearchVisible = { true },
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
    fun drawer_visibility_callback_tracks_swipe_open_and_back_close() {
        val visibilityChanges = mutableListOf<Boolean>()

        val coordinator = SurfaceStateCoordinator(
            showSearch = { },
            hideSearch = { },
            isSearchVisible = { false },
            isFolderOpen = { false },
            closeFolder = { },
            onAppDrawerVisibilityChanged = visibilityChanges::add
        )

        coordinator.handleHomeSwipeUp(SwipeUpAction.OPEN_APP_DRAWER)
        coordinator.handleBackPressed()

        assertEquals(listOf(true, false), visibilityChanges)
    }

    @Test
    fun repeated_drawer_visibility_updates_are_noops() {
        val visibilityChanges = mutableListOf<Boolean>()

        val coordinator = SurfaceStateCoordinator(
            showSearch = { },
            hideSearch = { },
            isSearchVisible = { false },
            isFolderOpen = { false },
            closeFolder = { },
            onAppDrawerVisibilityChanged = visibilityChanges::add
        )

        coordinator.updateAppDrawerOpen(true)
        coordinator.updateAppDrawerOpen(true)
        coordinator.updateAppDrawerOpen(false)
        coordinator.updateAppDrawerOpen(false)

        assertEquals(listOf(true, false), visibilityChanges)
    }

    @Test
    fun swipe_open_drawer_does_not_hide_search_when_search_already_hidden() {
        var hideSearchCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { },
            hideSearch = { hideSearchCalls++ },
            isSearchVisible = { false },
            isFolderOpen = { false },
            closeFolder = { }
        )

        coordinator.handleHomeSwipeUp(SwipeUpAction.OPEN_APP_DRAWER)

        assertEquals(0, hideSearchCalls)
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
            isSearchVisible = { false },
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
            isSearchVisible = { false },
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

    @Test
    fun on_stop_clears_transient_surfaces_and_folder() {
        var folderOpen = true
        var closeFolderCalls = 0

        val coordinator = SurfaceStateCoordinator(
            showSearch = { },
            hideSearch = { },
            isSearchVisible = { false },
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
        assertFalse(coordinator.isHomescreenMenuOpen)
        assertEquals(1, closeFolderCalls)
    }
}
