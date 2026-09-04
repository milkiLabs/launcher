package com.milki.launcher.ui.interaction.grid

import androidx.compose.ui.geometry.Offset
import com.milki.launcher.domain.model.LauncherTrigger

/**
 * A tap whose double-tap eligibility window has not yet resolved.
 *
 * Stored by [DoubleTapArbiter] between gestures so a pending tap survives
 * `pointerInput` coroutine restarts (home-layout mutations restart the
 * detector, but must not silently discard an unresolved tap).
 */
internal data class PendingTap(
    val position: Offset,
    val uptimeMillis: Long
)

/**
 * Outcome of classifying a new pointer-down against the arbiter's pending tap.
 */
internal enum class DoubleTapDownDecision {
    /** No tap was pending; this down starts a brand-new gesture sequence. */
    NO_PENDING_TAP,

    /** The pending tap was consumed; if this gesture releases cleanly it is a double tap. */
    SECOND_TAP,

    /**
     * A tap was pending but this down does not qualify as its second half
     * (wrong distance, expired window, occupied cell, or double-tap disabled).
     * The pending tap must be flushed as a single [LauncherTrigger.HOME_TAP].
     */
    PENDING_FLUSHED_AS_SINGLE_TAP
}

/**
 * State machine deciding whether consecutive taps form a double tap.
 *
 * Deliberately non-compose and side-effect free (aside from internal state):
 * the caller translates decisions into trigger invocations. Holding the state
 * here — instead of inside a `pointerInput` block — means a detector restart
 * caused by grid mutation cannot discard a pending tap, which used to break
 * double-tap detection right after any home-layout change.
 *
 * Note on expiry: the detector arms a timeout job to flush a pending tap as a
 * single tap. If the detector restarts before the job fires, the pending tap
 * lingers until the next pointer-down; [arbitrateDown] re-checks the elapsed
 * window at that point, so a stale tap is flushed late-but-correctly instead
 * of being lost.
 */
internal class DoubleTapArbiter {

    private var pendingTap: PendingTap? = null

    /**
     * Classifies a pointer-down against the currently pending tap (if any),
     * consuming it either way so state never leaks across gestures.
     *
     * @param downTimeMillis Uptime of the new pointer-down
     * @param downPosition Position of the new pointer-down
     * @param landsOnEmptyCell Whether the down lands on an unoccupied grid cell
     * @param supportsDoubleTap Whether HOME_DOUBLE_TAP is enabled at all
     * @param doubleTapTimeoutMillis Max delay between the two taps
     * @param doubleTapSlopPx Max displacement between the two taps
     */
    fun arbitrateDown(
        downTimeMillis: Long,
        downPosition: Offset,
        landsOnEmptyCell: Boolean,
        supportsDoubleTap: Boolean,
        doubleTapTimeoutMillis: Long,
        doubleTapSlopPx: Float
    ): DoubleTapDownDecision {
        val pending = pendingTap ?: return DoubleTapDownDecision.NO_PENDING_TAP
        pendingTap = null

        if (!supportsDoubleTap || !landsOnEmptyCell) {
            return DoubleTapDownDecision.PENDING_FLUSHED_AS_SINGLE_TAP
        }

        val elapsedMillis = downTimeMillis - pending.uptimeMillis
        val delta = downPosition - pending.position
        val withinTapDistance =
            (delta.x * delta.x) + (delta.y * delta.y) <= doubleTapSlopPx * doubleTapSlopPx

        val isSecondTap = elapsedMillis >= 0L &&
                elapsedMillis <= doubleTapTimeoutMillis &&
                withinTapDistance

        return if (isSecondTap) {
            DoubleTapDownDecision.SECOND_TAP
        } else {
            DoubleTapDownDecision.PENDING_FLUSHED_AS_SINGLE_TAP
        }
    }

    /** Records a completed single tap as a potential first half of a double tap. */
    fun recordTap(uptimeMillis: Long, position: Offset) {
        pendingTap = PendingTap(position = position, uptimeMillis = uptimeMillis)
    }

    /**
     * Consumes the pending tap, reporting whether one existed. Called when the
     * double-tap window expires to emit the deferred single tap exactly once.
     */
    fun resolvePendingTap(): Boolean {
        val hadPendingTap = pendingTap != null
        pendingTap = null
        return hadPendingTap
    }

    /**
     * Discards any pending tap without emitting anything.
     *
     * Called when the homescreen leaves the interactive lifecycle (pause/stop):
     * a tap recorded just before backgrounding must never fire late or pair
     * with a tap from the next foreground session.
     */
    fun clear() {
        pendingTap = null
    }
}
