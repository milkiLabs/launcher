/**
 * PointerTracking.kt - Shared low-level pointer-tracking primitives
 *
 * Extracted from the two hand-rolled copies of the same
 * "track a pointer until long-press timeout or release" loop that used to
 * live in DragGestureDetector and HomeBackgroundGestureDetector. Any fix to
 * pointer-loss handling now only needs to be made here.
 *
 * The primitive owns:
 * - The `withTimeoutOrNull(longPressTimeout)` + `while(true) { awaitPointerEvent() }` skeleton
 * - Detecting a pointer vanishing from the event stream (multi-touch / system cancel)
 * - Detecting release before the timeout
 * - Reporting the latest observed change when the timeout fires (long-press)
 */
package com.milki.launcher.ui.interaction

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Outcome of [trackPointerUntilLongPressOrRelease].
 *
 * @param T The classifier's resolution type, produced by either [Resolved]
 *          (decided while the pointer was still pressed) or [Released]
 *          (decided at lift time).
 */
sealed class PreTimeoutResult<out T> {

    /**
     * The tracked pointer was released before the long-press timeout.
     * [value] is what [trackPointerUntilLongPressOrRelease]'s `onLift`
     * classified for the final change.
     */
    data class Released<T>(val change: PointerInputChange, val value: T) : PreTimeoutResult<T>()

    /**
     * The classifier resolved early via `onMove` while the pointer was still
     * pressed (e.g. a directional trigger matched, or touch slop was exceeded).
     */
    data class Resolved<T>(val change: PointerInputChange, val value: T) : PreTimeoutResult<T>()

    /**
     * The pointer disappeared from the event stream before being released or
     * timing out (multi-touch interference, system cancellation).
     */
    data object Lost : PreTimeoutResult<Nothing>()

    /**
     * The long-press timeout elapsed with the pointer still pressed.
     * Carries the last observed change for the pointer (may be null if no
     * event ever arrived for it).
     */
    data class LongPress(val change: PointerInputChange?) : PreTimeoutResult<Nothing>()
}

/**
 * Internal wrapper so "pointer lost" is distinguishable from "timeout fired"
 * (both would otherwise surface as null from withTimeoutOrNull).
 */
private sealed class Tracked<out T> {
    data class Done<T>(val result: PreTimeoutResult<T>) : Tracked<T>()
    data object Lost : Tracked<Nothing>()
}

/**
 * Tracks [pointerId] until one of four things happens:
 *
 * 1. The finger lifts → [PreTimeoutResult.Released] with `onLift(change)` as value.
 * 2. `onMove(change)` returns non-null while pressed → [PreTimeoutResult.Resolved]
 *    with that value. Tracking stops immediately (the timeout no longer applies).
 * 3. The pointer disappears from the event stream → [PreTimeoutResult.Lost].
 * 4. [longPressTimeoutMillis] elapses with the finger still down →
 *    [PreTimeoutResult.LongPress] carrying the last observed change.
 *
 * @param pointerId The pointer to track
 * @param longPressTimeoutMillis How long to track before declaring long-press
 * @param onMove Invoked for every pressed-state change; return non-null to
 *               resolve tracking early (e.g. trigger match or slop exceeded)
 * @param onLift Invoked once with the final (released) change to classify a
 *               pre-timeout lift (e.g. tap vs swipe-up)
 */
suspend fun <T> AwaitPointerEventScope.trackPointerUntilLongPressOrRelease(
    pointerId: PointerId,
    longPressTimeoutMillis: Long,
    onMove: (PointerInputChange) -> T? = { null },
    onLift: (PointerInputChange) -> T
): PreTimeoutResult<T> {
    var latestChange: PointerInputChange? = null

    val outcome = withTimeoutOrNull(longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId }
                ?: return@withTimeoutOrNull Tracked.Lost

            latestChange = change

            if (!change.pressed) {
                val value = onLift(change)
                return@withTimeoutOrNull Tracked.Done(PreTimeoutResult.Released(change, value))
            }

            val resolved = onMove(change)
            if (resolved != null) {
                return@withTimeoutOrNull Tracked.Done(PreTimeoutResult.Resolved(change, resolved))
            }
        }

        @Suppress("UNREACHABLE_CODE")
        Tracked.Lost
    }

    return when (outcome) {
        null -> PreTimeoutResult.LongPress(latestChange)
        is Tracked.Lost -> PreTimeoutResult.Lost
        is Tracked.Done -> outcome.result
    }
}

/**
 * Suspends until [pointerId] is no longer pressed, or disappears from the
 * event stream. Does not consume any changes.
 */
suspend fun AwaitPointerEventScope.awaitPointerUp(pointerId: PointerId) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return
        if (!change.pressed) return
    }
}

/**
 * Suspends until [pointerId] is no longer pressed, or disappears from the
 * event stream, consuming every change along the way so downstream handlers
 * don't see them.
 */
suspend fun AwaitPointerEventScope.consumeUntilPointerUp(pointerId: PointerId) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return
        change.consume()
        if (!change.pressed) return
    }
}
