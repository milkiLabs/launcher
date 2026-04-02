package com.milki.launcher.ui.interaction.grid

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBackgroundGesturePolicyTest {

    @Test
    fun background_gesture_tracks_even_on_occupied_cells_when_interactions_are_idle() {
        val policy = HomeBackgroundGesturePolicy(
            canStartBackgroundGesture = true,
            canSwipeUp = true
        )

        assertTrue(policy.shouldTrackGesture())
    }

    @Test
    fun swipe_up_requires_clear_upward_and_vertical_motion() {
        val swipe = Offset(x = 18f, y = -140f)
        val tooDiagonal = Offset(x = 120f, y = -140f)
        val tooShort = Offset(x = 0f, y = -48f)

        assertTrue(swipe.isSwipeUpGesture(minimumDistancePx = 80f))
        assertFalse(tooDiagonal.isSwipeUpGesture(minimumDistancePx = 80f))
        assertFalse(tooShort.isSwipeUpGesture(minimumDistancePx = 80f))
    }

    @Test
    fun swipe_down_requires_clear_downward_and_vertical_motion() {
        val swipe = Offset(x = 16f, y = 140f)
        val tooDiagonal = Offset(x = 120f, y = 140f)
        val tooShort = Offset(x = 0f, y = 48f)

        assertTrue(swipe.isSwipeDownGesture(minimumDistancePx = 80f))
        assertFalse(tooDiagonal.isSwipeDownGesture(minimumDistancePx = 80f))
        assertFalse(tooShort.isSwipeDownGesture(minimumDistancePx = 80f))
    }

    @Test
    fun touch_slop_detection_rejects_resting_finger_noise() {
        val restingNoise = Offset(x = 4f, y = 6f)
        val realMotion = Offset(x = 0f, y = 18f)

        assertFalse(restingNoise.exceedsTouchSlop(touchSlopPx = 8f))
        assertTrue(realMotion.exceedsTouchSlop(touchSlopPx = 8f))
    }
}
