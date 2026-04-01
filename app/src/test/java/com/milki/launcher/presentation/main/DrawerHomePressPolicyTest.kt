package com.milki.launcher.presentation.main

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerHomePressPolicyTest {

    private val policy = DrawerHomePressPolicy()

    @Test
    fun resolve_returns_none_when_drawer_is_closed() {
        val decision = policy.resolve(
            DrawerHomePressPolicy.InputState(
                isDrawerOpen = false,
                hasDrawerQuery = true
            )
        )

        assertEquals(DrawerHomePressPolicy.Decision.NONE, decision)
    }

    @Test
    fun resolve_returns_clear_query_when_drawer_open_and_query_exists() {
        val decision = policy.resolve(
            DrawerHomePressPolicy.InputState(
                isDrawerOpen = true,
                hasDrawerQuery = true
            )
        )

        assertEquals(DrawerHomePressPolicy.Decision.CLEAR_QUERY, decision)
    }

    @Test
    fun resolve_returns_close_drawer_when_drawer_open_and_query_empty() {
        val decision = policy.resolve(
            DrawerHomePressPolicy.InputState(
                isDrawerOpen = true,
                hasDrawerQuery = false
            )
        )

        assertEquals(DrawerHomePressPolicy.Decision.CLOSE_DRAWER, decision)
    }
}
