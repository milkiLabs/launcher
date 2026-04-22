package com.milki.launcher.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherInteractionCatalogTest {

    @Test
    fun available_actions_include_notification_shade_action() {
        assertTrue(
            LauncherInteractionCatalog.availableActions()
                .contains(LauncherTriggerAction.OPEN_NOTIFICATION_SHADE)
        )
    }

    @Test
    fun configurable_triggers_include_swipe_down() {
        assertTrue(
            LauncherInteractionCatalog.configurableTriggers.contains(LauncherTrigger.HOME_SWIPE_DOWN)
        )
    }

    @Test
    fun swipe_triggers_only_include_swipe_gestures() {
        assertEquals(
            listOf(
                LauncherTrigger.HOME_SWIPE_UP,
                LauncherTrigger.HOME_SWIPE_DOWN
            ),
            LauncherInteractionCatalog.swipeTriggers
        )
    }

    @Test
    fun trigger_metadata_describes_tap_and_directional_swipes() {
        assertEquals(
            LauncherTriggerMetadata(
                kind = LauncherGestureKind.TAP,
                direction = null
            ),
            LauncherTrigger.HOME_TAP.metadata
        )

        assertEquals(
            LauncherTriggerMetadata(
                kind = LauncherGestureKind.SWIPE,
                direction = LauncherGestureDirection.UP
            ),
            LauncherTrigger.HOME_SWIPE_UP.metadata
        )

        assertEquals(
            LauncherTriggerMetadata(
                kind = LauncherGestureKind.SWIPE,
                direction = LauncherGestureDirection.DOWN
            ),
            LauncherTrigger.HOME_SWIPE_DOWN.metadata
        )
    }

    @Test
    fun trigger_for_direction_resolves_known_swipe_triggers() {
        assertEquals(
            LauncherTrigger.HOME_SWIPE_UP,
            LauncherInteractionCatalog.triggerForDirection(LauncherGestureDirection.UP)
        )
        assertEquals(
            LauncherTrigger.HOME_SWIPE_DOWN,
            LauncherInteractionCatalog.triggerForDirection(LauncherGestureDirection.DOWN)
        )
    }

    @Test
    fun trigger_for_direction_returns_null_for_unsupported_directions() {
        assertNull(LauncherInteractionCatalog.triggerForDirection(LauncherGestureDirection.LEFT))
        assertNull(LauncherInteractionCatalog.triggerForDirection(LauncherGestureDirection.RIGHT))
    }

    @Test
    fun default_actions_cover_all_current_home_gestures() {
        assertEquals(
            LauncherTriggerAction.DO_NOTHING,
            LauncherInteractionCatalog.defaultActionFor(LauncherTrigger.HOME_TAP)
        )
        assertEquals(
            LauncherTriggerAction.OPEN_APP_DRAWER,
            LauncherInteractionCatalog.defaultActionFor(LauncherTrigger.HOME_SWIPE_UP)
        )
        assertEquals(
            LauncherTriggerAction.OPEN_NOTIFICATION_SHADE,
            LauncherInteractionCatalog.defaultActionFor(LauncherTrigger.HOME_SWIPE_DOWN)
        )
    }

    @Test
    fun default_trigger_actions_are_generated_from_catalog_defaults() {
        val expected = LauncherInteractionCatalog.configurableTriggers.associateWith {
            LauncherInteractionCatalog.defaultActionFor(it)
        }

        assertEquals(expected, LauncherInteractionCatalog.defaultTriggerActions())
    }
}
