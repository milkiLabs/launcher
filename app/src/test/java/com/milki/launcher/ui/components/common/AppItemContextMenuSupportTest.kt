package com.milki.launcher.ui.components.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ItemContextMenuStateTest {

    @Test
    fun initializes_dismissed() {
        val state = ItemContextMenuState()

        assertEquals(false, state.showMenu)
        assertEquals(false, state.isGestureActive)
        assertEquals(true, state.isMenuFocusable)
    }

    @Test
    fun long_press_opens_menu_and_disables_focus() {
        val state = ItemContextMenuState()

        state.onLongPress()

        assertEquals(true, state.showMenu)
        assertEquals(true, state.isGestureActive)
        assertEquals(false, state.isMenuFocusable)
    }

    @Test
    fun long_press_release_reenables_focus() {
        val state = ItemContextMenuState()
        state.onLongPress()

        state.onLongPressRelease()

        assertEquals(true, state.showMenu)
        assertEquals(false, state.isGestureActive)
        assertEquals(true, state.isMenuFocusable)
    }

    @Test
    fun dismiss_closes_menu() {
        val state = ItemContextMenuState()
        state.onLongPress()

        state.dismiss()

        assertEquals(false, state.showMenu)
        assertEquals(false, state.isGestureActive)
        assertEquals(true, state.isMenuFocusable)
    }

    @Test
    fun drag_start_closes_menu() {
        val state = ItemContextMenuState()
        state.onLongPress()

        state.onDragStart()

        assertEquals(false, state.showMenu)
        assertEquals(false, state.isGestureActive)
    }

    @Test
    fun drag_cancel_reenables_focus() {
        val state = ItemContextMenuState()
        state.onLongPress()

        state.onDragCancel()

        assertEquals(true, state.showMenu)
        assertEquals(false, state.isGestureActive)
        assertEquals(true, state.isMenuFocusable)
    }
}
