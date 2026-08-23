package com.milki.launcher.ui.interaction.grid

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleTapArbiterTest {

    private fun arbiterWithPendingTap(
        uptimeMillis: Long = 1_000L,
        position: Offset = Offset.Zero
    ): DoubleTapArbiter {
        return DoubleTapArbiter().apply { recordTap(uptimeMillis, position) }
    }

    @Test
    fun down_without_pending_tap_starts_new_sequence() {
        val decision = DoubleTapArbiter().arbitrateDown(
            downTimeMillis = 1_100L,
            downPosition = Offset.Zero,
            landsOnEmptyCell = true,
            supportsDoubleTap = true,
            doubleTapTimeoutMillis = 300L,
            doubleTapSlopPx = 32f
        )

        assertEquals(DoubleTapDownDecision.NO_PENDING_TAP, decision)
    }

    @Test
    fun qualifying_down_consumes_pending_tap_as_second_tap() {
        val arbiter = arbiterWithPendingTap(uptimeMillis = 1_000L)

        val decision = arbiter.arbitrateDown(
            downTimeMillis = 1_150L,
            downPosition = Offset(x = 10f, y = -8f),
            landsOnEmptyCell = true,
            supportsDoubleTap = true,
            doubleTapTimeoutMillis = 300L,
            doubleTapSlopPx = 32f
        )

        assertEquals(DoubleTapDownDecision.SECOND_TAP, decision)
        assertFalse(arbiter.resolvePendingTap())
    }

    @Test
    fun second_tap_beyond_timeout_flushes_pending_tap() {
        val arbiter = arbiterWithPendingTap(uptimeMillis = 1_000L)

        val decision = arbiter.arbitrateDown(
            downTimeMillis = 1_400L,
            downPosition = Offset.Zero,
            landsOnEmptyCell = true,
            supportsDoubleTap = true,
            doubleTapTimeoutMillis = 300L,
            doubleTapSlopPx = 32f
        )

        assertEquals(DoubleTapDownDecision.PENDING_FLUSHED_AS_SINGLE_TAP, decision)
    }

    @Test
    fun second_tap_too_far_away_flushes_pending_tap() {
        val arbiter = arbiterWithPendingTap(uptimeMillis = 1_000L)

        val decision = arbiter.arbitrateDown(
            downTimeMillis = 1_100L,
            downPosition = Offset(x = 40f, y = 0f),
            landsOnEmptyCell = true,
            supportsDoubleTap = true,
            doubleTapTimeoutMillis = 300L,
            doubleTapSlopPx = 32f
        )

        assertEquals(DoubleTapDownDecision.PENDING_FLUSHED_AS_SINGLE_TAP, decision)
    }

    @Test
    fun second_tap_on_occupied_cell_flushes_pending_tap() {
        val arbiter = arbiterWithPendingTap(uptimeMillis = 1_000L)

        val decision = arbiter.arbitrateDown(
            downTimeMillis = 1_100L,
            downPosition = Offset.Zero,
            landsOnEmptyCell = false,
            supportsDoubleTap = true,
            doubleTapTimeoutMillis = 300L,
            doubleTapSlopPx = 32f
        )

        assertEquals(DoubleTapDownDecision.PENDING_FLUSHED_AS_SINGLE_TAP, decision)
    }

    @Test
    fun arbitration_consumes_pending_tap_even_when_flushing() {
        val arbiter = arbiterWithPendingTap()

        arbiter.arbitrateDown(
            downTimeMillis = 5_000L,
            downPosition = Offset(x = 500f, y = 500f),
            landsOnEmptyCell = false,
            supportsDoubleTap = false,
            doubleTapTimeoutMillis = 300L,
            doubleTapSlopPx = 32f
        )

        assertFalse(arbiter.resolvePendingTap())
    }

    @Test
    fun expired_window_resolution_emits_single_tap_exactly_once() {
        val arbiter = arbiterWithPendingTap()

        assertTrue(arbiter.resolvePendingTap())
        assertFalse(arbiter.resolvePendingTap())
    }
}
