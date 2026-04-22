package com.milki.launcher.ui.interaction.grid

import androidx.compose.ui.geometry.Offset
import com.milki.launcher.domain.model.LauncherTrigger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBackgroundGesturePolicyTest {

    @Test
    fun background_gesture_tracks_even_on_occupied_cells_when_interactions_are_idle() {
        val policy = HomeBackgroundGesturePolicy(
            canStartBackgroundGesture = true,
            enabledTriggers = setOf(LauncherTrigger.HOME_SWIPE_UP)
        )

        assertTrue(policy.canStartBackgroundGesture)
        assertTrue(LauncherTrigger.HOME_SWIPE_UP in policy.enabledTriggers)
        assertFalse(LauncherTrigger.HOME_SWIPE_DOWN in policy.enabledTriggers)
    }

    @Test
    fun policy_reports_enabled_trigger_membership() {
        val policy = HomeBackgroundGesturePolicy(
            canStartBackgroundGesture = true,
            enabledTriggers = setOf(
                LauncherTrigger.HOME_SWIPE_UP,
                LauncherTrigger.HOME_SWIPE_DOWN
            )
        )

        assertTrue(LauncherTrigger.HOME_SWIPE_UP in policy.enabledTriggers)
        assertTrue(LauncherTrigger.HOME_SWIPE_DOWN in policy.enabledTriggers)
        assertFalse(LauncherTrigger.HOME_TAP in policy.enabledTriggers)
    }

    @Test
    fun bindings_expose_supported_triggers_from_callbacks() {
        val bindings = HomeBackgroundGestureBindings(
            onEmptyAreaTap = {},
            onTrigger = {},
            configuredTriggers = setOf(
                LauncherTrigger.HOME_TAP,
                LauncherTrigger.HOME_SWIPE_UP,
                LauncherTrigger.HOME_SWIPE_DOWN
            )
        )

        val enabledTriggers = bindings.enabledTriggers()

        assertTrue(LauncherTrigger.HOME_TAP in enabledTriggers)
        assertTrue(LauncherTrigger.HOME_SWIPE_UP in enabledTriggers)
        assertTrue(LauncherTrigger.HOME_SWIPE_DOWN in enabledTriggers)
    }

    @Test
    fun bindings_without_trigger_callback_only_enable_tap() {
        val bindings = HomeBackgroundGestureBindings(
            onEmptyAreaTap = {},
            configuredTriggers = setOf(
                LauncherTrigger.HOME_TAP,
                LauncherTrigger.HOME_SWIPE_UP,
                LauncherTrigger.HOME_SWIPE_DOWN
            )
        )

        val enabledTriggers = bindings.enabledTriggers()

        assertTrue(LauncherTrigger.HOME_TAP in enabledTriggers)
        assertFalse(LauncherTrigger.HOME_SWIPE_UP in enabledTriggers)
        assertFalse(LauncherTrigger.HOME_SWIPE_DOWN in enabledTriggers)
    }

    @Test
    fun swipe_up_requires_clear_upward_and_vertical_motion() {
        val swipe = Offset(x = 18f, y = -140f)
        val tooDiagonal = Offset(x = 120f, y = -140f)
        val tooShort = Offset(x = 0f, y = -48f)

        assertTrue(
            swipe.matchesTriggerDirection(
                trigger = LauncherTrigger.HOME_SWIPE_UP,
                minimumDistancePx = 80f
            )
        )
        assertFalse(
            tooDiagonal.matchesTriggerDirection(
                trigger = LauncherTrigger.HOME_SWIPE_UP,
                minimumDistancePx = 80f
            )
        )
        assertFalse(
            tooShort.matchesTriggerDirection(
                trigger = LauncherTrigger.HOME_SWIPE_UP,
                minimumDistancePx = 80f
            )
        )
    }

    @Test
    fun swipe_down_requires_clear_downward_and_vertical_motion() {
        val swipe = Offset(x = 16f, y = 140f)
        val tooDiagonal = Offset(x = 120f, y = 140f)
        val tooShort = Offset(x = 0f, y = 48f)

        assertTrue(
            swipe.matchesTriggerDirection(
                trigger = LauncherTrigger.HOME_SWIPE_DOWN,
                minimumDistancePx = 80f
            )
        )
        assertFalse(
            tooDiagonal.matchesTriggerDirection(
                trigger = LauncherTrigger.HOME_SWIPE_DOWN,
                minimumDistancePx = 80f
            )
        )
        assertFalse(
            tooShort.matchesTriggerDirection(
                trigger = LauncherTrigger.HOME_SWIPE_DOWN,
                minimumDistancePx = 80f
            )
        )
    }

    @Test
    fun touch_slop_detection_rejects_resting_finger_noise() {
        val restingNoise = Offset(x = 4f, y = 6f)
        val realMotion = Offset(x = 0f, y = 18f)

        assertFalse(restingNoise.exceedsTouchSlop(touchSlopPx = 8f))
        assertTrue(realMotion.exceedsTouchSlop(touchSlopPx = 8f))
    }
}
