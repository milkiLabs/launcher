package com.milki.launcher.presentation.launcher

import com.milki.launcher.domain.model.LauncherTriggerAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherNavigatorTest {

    @Test
    fun navigate_replaces_existing_primary_overlay() {
        val visibilityChanges = mutableListOf<Boolean>()
        val navigator = navigator(
            onAppDrawerVisibilityChanged = visibilityChanges::add
        )

        navigator.navigate(LauncherRoute.AppDrawer)
        navigator.navigate(LauncherRoute.WidgetPicker("clock"))

        assertEquals(
            listOf(LauncherRoute.Home, LauncherRoute.WidgetPicker("clock")),
            navigator.backStack.toList()
        )
        assertEquals(listOf(true, false), visibilityChanges)
    }

    @Test
    fun pop_closes_current_overlay_and_returns_home() = runBlocking {
        val navigator = navigator()

        navigator.navigate(LauncherRoute.Search)
        val consumed = navigator.pop()

        assertTrue(consumed)
        assertTrue(navigator.isAtHome)
        assertFalse(navigator.searchVisibilityFlow.first())
    }

    @Test
    fun pop_at_home_is_not_consumed() {
        val navigator = navigator()

        assertFalse(navigator.pop())
        assertEquals(listOf(LauncherRoute.Home), navigator.backStack.toList())
    }

    @Test
    fun home_intent_opens_search_when_resumed_at_home() = runBlocking {
        val navigator = navigator()

        navigator.onResume()
        navigator.handleHomeIntent()

        assertEquals(LauncherRoute.Search, navigator.currentRoute)
        assertTrue(navigator.searchVisibilityFlow.first())
    }

    @Test
    fun home_intent_closes_current_overlay() = runBlocking {
        val navigator = navigator()

        navigator.onResume()
        navigator.navigate(LauncherRoute.Search)
        navigator.handleHomeIntent()

        assertTrue(navigator.isAtHome)
        assertFalse(navigator.searchVisibilityFlow.first())
    }

    @Test
    fun first_home_intent_after_stop_does_not_reopen_search() = runBlocking {
        val navigator = navigator()

        navigator.navigate(LauncherRoute.Search)
        navigator.onStop()
        navigator.handleHomeIntent()

        assertTrue(navigator.isAtHome)
        assertFalse(navigator.searchVisibilityFlow.first())
    }

    @Test
    fun widget_picker_query_updates_current_route() {
        val navigator = navigator()

        navigator.navigate(LauncherRoute.WidgetPicker())
        navigator.updateWidgetPickerQuery("weather")

        assertEquals(LauncherRoute.WidgetPicker("weather"), navigator.currentRoute)
    }

    @Test
    fun external_trigger_clears_overlay_before_running_action() = runBlocking {
        var shadeCalls = 0
        val navigator = navigator(
            openNotificationShade = { shadeCalls++ }
        )

        navigator.navigate(LauncherRoute.Search)
        navigator.handleHomeTriggerAction(
            LauncherTriggerAction.OPEN_NOTIFICATION_SHADE
        )

        assertTrue(navigator.isAtHome)
        assertFalse(navigator.searchVisibilityFlow.first())
        assertEquals(1, shadeCalls)
    }

    private fun navigator(
        closeFolder: () -> Unit = {},
        openFolder: (String) -> Unit = {},
        openNotificationShade: () -> Unit = {},
        lockScreen: () -> Unit = {},
        onAppDrawerVisibilityChanged: (Boolean) -> Unit = {}
    ): LauncherNavigator {
        return LauncherNavigator(
            closeFolder = closeFolder,
            openFolder = openFolder,
            openNotificationShade = openNotificationShade,
            lockScreen = lockScreen,
            onAppDrawerVisibilityChanged = onAppDrawerVisibilityChanged
        )
    }
}
