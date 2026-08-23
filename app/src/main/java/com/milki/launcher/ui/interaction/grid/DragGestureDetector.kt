/**
 * DragGestureDetector.kt - Reusable gesture detection for drag operations
 *
 * This file provides a modular gesture detector that handles the complex
 * interactions needed for drag-and-drop in a launcher grid:
 * - Detecting taps vs long-press vs drag
 * - Detecting quick swipe-up gestures (for popup widget icons)
 * - Managing multi-touch safety
 * - Providing haptic feedback coordination
 *
 * WHY SEPARATE GESTURE DETECTION?
 * - Reusability: Same detector can be used in different components
 * - Testability: Gesture logic isolated from UI rendering
 * - Customization: Easy to add new gesture types or modify behavior
 *
 * GESTURE TYPES HANDLED:
 * 1. Tap: Quick touch and release without significant upward movement
 * 2. Swipe-up: Quick upward flick released before long-press timeout
 * 3. Long-press: Touch held without release past the timeout (shows menu)
 * 4. Drag: Long-press followed by movement (moves item)
 *
 * INTERACTION MODEL:
 * ```
 * Touch Down -> Wait for Long Press Timeout
 *     |
 *     +-> Released before timeout (no upward movement) -> TAP
 *     |
 *     +-> Released before timeout (upward movement > threshold) -> SWIPE UP
 *     |
 *     +-> Long press detected -> Show Menu
 *             |
 *             +-> Released without movement -> Menu stays open
 *             |
 *             +-> Movement beyond threshold -> START DRAG
 *                     |
 *                     +-> Continue moving -> UPDATE DRAG
 *                     |
 *                     +-> Released -> END DRAG
 *                     |
 *                     +-> Cancelled -> CANCEL DRAG
 * ```
 *
 * MULTI-TOUCH SAFETY:
 * The detector tracks active pointers to prevent multiple simultaneous
 * drags. If a second finger touches during a drag, the drag is cancelled.
 */

package com.milki.launcher.ui.interaction.grid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import com.milki.launcher.ui.interaction.PreTimeoutResult
import com.milki.launcher.ui.interaction.trackPointerUntilLongPressOrRelease
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

/**
 * Detects tap, swipe-up, long-press, and drag gestures with individual callbacks.
 *
 * This is the primary gesture detection function. It uses two distinct detection
 * strategies depending on whether [onSwipeUp] is provided:
 *
 * **Standard path** ([onSwipeUp] == null):
 * Uses Compose's [awaitLongPressOrCancellation], which cancels on touch-slop
 * movement. This is the battle-tested original behavior for all items that
 * don't need swipe-up detection.
 *
 * **Enhanced path** ([onSwipeUp] != null):
 * Uses [awaitPreLongPressClassification], a custom classifier that continues
 * tracking the pointer until release or timeout (instead of cancelling on
 * movement). This cleanly distinguishes tap, swipe-up, and long-press because
 * swipe-up IS movement before the long-press timeout — something that
 * [awaitLongPressOrCancellation] treats as cancellation by design.
 *
 * Both paths share [handlePostLongPressDrag] for the post-long-press drag
 * tracking phase, which is identical regardless of how long-press was detected.
 *
 * @param dragThreshold Minimum pixels to move before drag starts
 * @param consumeChanges Whether to consume pointer changes during drag
 * @param onTap Called for tap gesture
 * @param onSwipeUp Called for quick upward swipe gesture; when null, the standard
 *                  detection path is used and upward flicks are treated as taps
 * @param onLongPress Called when long-press is detected
 * @param onLongPressRelease Called when finger lifts after long-press without drag
 * @param onDragStart Called when drag begins (threshold exceeded)
 * @param onDrag Called during drag with movement delta
 * @param onDragEnd Called when drag ends successfully
 * @param onDragCancel Called when drag is cancelled
 */
suspend fun PointerInputScope.detectDragOrTapGesture(
    dragThreshold: Float = 20f,
    consumeChanges: Boolean = true,
    onTap: () -> Unit,
    onSwipeUp: (() -> Unit)? = null,
    onLongPress: (Offset) -> Unit,
    onLongPressRelease: () -> Unit = {},
    onDragStart: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown()

        if (onSwipeUp != null) {
            // --- Enhanced path: classify tap / swipe-up / long-press ---
            val classification = awaitPreLongPressClassification(
                pointerId = down.id,
                startPosition = down.position,
                swipeUpThresholdPx = dragThreshold
            )

            when (classification) {
                PreLongPressOutcome.Tap -> {
                    onTap()
                    return@awaitEachGesture
                }

                PreLongPressOutcome.SwipeUp -> {
                    onSwipeUp()
                    return@awaitEachGesture
                }

                PreLongPressOutcome.Cancelled -> {
                    onDragCancel()
                    return@awaitEachGesture
                }

                is PreLongPressOutcome.LongPress -> {
                    onLongPress(classification.change.position)
                    handlePostLongPressDrag(
                        pointerId = classification.change.id,
                        dragThreshold = dragThreshold,
                        consumeChanges = consumeChanges,
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                        onLongPressRelease = onLongPressRelease
                    )
                }
            }
        } else {
            // --- Standard path: original awaitLongPressOrCancellation ---
            val longPress = awaitLongPressOrCancellation(down.id)

            if (longPress == null) {
                /**
                 * TAP VS CANCELLATION RESOLUTION:
                 * awaitLongPressOrCancellation() returns null in two cases:
                 * 1) Normal quick tap (finger lifted before long-press timeout)
                 * 2) True cancellation (multi-touch/system interruption)
                 *
                 * Inspect current pointer state to distinguish them:
                 * - Pointer no longer pressed → TAP
                 * - Pointer still pressed → cancellation
                 */
                val pointerStillPressed = currentEvent.changes.any { change ->
                    change.id == down.id && change.pressed
                }

                if (!pointerStillPressed) {
                    onTap()
                } else {
                    onDragCancel()
                }
                return@awaitEachGesture
            }

            // Long-press detected — proceed to drag tracking
            onLongPress(longPress.position)
            handlePostLongPressDrag(
                pointerId = longPress.id,
                dragThreshold = dragThreshold,
                consumeChanges = consumeChanges,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                onLongPressRelease = onLongPressRelease
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

/**
 * Result of classifying a gesture before the long-press timeout.
 *
 * This replaces [awaitLongPressOrCancellation] when swipe-up detection is
 * needed. Instead of a binary "long-press or null" outcome, we classify into
 * four possibilities based on the pointer's trajectory and lifetime.
 */
private sealed class PreLongPressOutcome {
    /** Quick release without significant upward movement. */
    object Tap : PreLongPressOutcome()

    /** Quick release with upward displacement exceeding the swipe threshold. */
    object SwipeUp : PreLongPressOutcome()

    /** Finger held past the long-press timeout. */
    data class LongPress(val change: PointerInputChange) : PreLongPressOutcome()

    /** Pointer disappeared (multi-touch, system cancel). */
    object Cancelled : PreLongPressOutcome()
}

/**
 * Custom gesture classifier that distinguishes tap, swipe-up, and long-press.
 *
 * Unlike [awaitLongPressOrCancellation] (which cancels on any movement beyond
 * touch-slop), this classifier continues tracking the pointer until release or
 * the long-press timeout (via the shared
 * [trackPointerUntilLongPressOrRelease] primitive). It classifies the gesture
 * on release based on the total upward displacement.
 *
 * WHEN TO USE:
 * Only called when onSwipeUp is non-null. When swipe-up detection is not
 * needed, the standard [awaitLongPressOrCancellation] path is used instead,
 * keeping the original behavior completely untouched.
 *
 * @param pointerId The pointer to track
 * @param startPosition The initial touch position
 * @param swipeUpThresholdPx Minimum upward displacement to classify as swipe-up
 */
private suspend fun AwaitPointerEventScope.awaitPreLongPressClassification(
    pointerId: PointerId,
    startPosition: Offset,
    swipeUpThresholdPx: Float
): PreLongPressOutcome {
    return when (val result = trackPointerUntilLongPressOrRelease(
        pointerId = pointerId,
        longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis,
        // No early resolution: classification only happens at lift time.
        onMove = { null },
        onLift = { change ->
            // Classify based on upward displacement (screen Y increases
            // downward, so upward movement = start.y - end.y > 0).
            val upwardDisplacement = startPosition.y - change.position.y
            if (upwardDisplacement > swipeUpThresholdPx) {
                PreLongPressOutcome.SwipeUp
            } else {
                PreLongPressOutcome.Tap
            }
        }
    )) {
        is PreTimeoutResult.Released -> result.value

        /**
         * Unreachable with `onMove = { null }`, but kept exhaustive for safety:
         * a pre-lift resolution would be meaningless for this classifier.
         */
        is PreTimeoutResult.Resolved -> PreLongPressOutcome.Cancelled

        // Pointer disappeared before lift or timeout (multi-touch, system cancel).
        PreTimeoutResult.Lost -> PreLongPressOutcome.Cancelled

        is PreTimeoutResult.LongPress -> PreLongPressOutcome.LongPress(
            result.change ?: currentEvent.changes.first { it.id == pointerId }
        )
    }
}

/**
 * Post-long-press drag tracking.
 *
 * Shared by both the standard and enhanced paths. After long-press is confirmed,
 * tracks continued movement to determine if the user wants to drag the item or
 * simply release after the long-press (leaving the context menu open).
 */
private suspend fun AwaitPointerEventScope.handlePostLongPressDrag(
    pointerId: PointerId,
    dragThreshold: Float,
    consumeChanges: Boolean,
    onDragStart: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onLongPressRelease: () -> Unit
) {
    var totalDrag = Offset.Zero
    var dragStarted = false

    try {
        drag(pointerId = pointerId) { change ->
            val dragAmount = change.position - change.previousPosition
            totalDrag += dragAmount

            if (!dragStarted && (abs(totalDrag.x) > dragThreshold || abs(totalDrag.y) > dragThreshold)) {
                dragStarted = true
                onDragStart()
            }

            if (dragStarted) {
                if (consumeChanges) {
                    change.consume()
                }
                onDrag(change, dragAmount)
            }
        }

        if (dragStarted) {
            onDragEnd()
        } else {
            /**
             * Finger lifted after long-press without exceeding drag threshold.
             *
             * This is the "long-press and release" case. Callers use this to
             * transition a non-focusable menu (shown during onLongPress to avoid
             * stealing the gesture) into its interactive/focusable state.
             */
            onLongPressRelease()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (dragStarted) {
            onDragCancel()
        } else {
            /**
             * Gesture cancelled before drag started (e.g., multi-touch or system
             * interrupt during long-press hold). Fire onLongPressRelease so callers
             * still get a clean end-of-long-press signal and can reset their state.
             */
            onLongPressRelease()
        }
    }
}

/**
 * Extension to add drag gesture detection with individual callbacks.
 *
 * @param key A stable key that invalidates the gesture detector when changed
 * @param dragThreshold Minimum pixels to move before drag starts
 * @param onTap Called for tap gesture
 * @param onSwipeUp Called for quick upward swipe gesture; when null, upward
 *                  flicks are treated as regular taps
 * @param onLongPress Called when long-press is detected
 * @param onLongPressRelease Called when finger lifts after long-press without drag
 * @param onDragStart Called when drag begins
 * @param onDrag Called during drag
 * @param onDragEnd Called when drag ends
 * @param onDragCancel Called when drag is cancelled
 */
fun Modifier.detectDragGesture(
    key: Any? = null,
    dragThreshold: Float = 20f,
    onTap: () -> Unit,
    onSwipeUp: (() -> Unit)? = null,
    onLongPress: (Offset) -> Unit,
    onLongPressRelease: () -> Unit = {},
    onDragStart: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
): Modifier {
    return pointerInput(key, dragThreshold) {
        detectDragOrTapGesture(
            dragThreshold = dragThreshold,
            onTap = onTap,
            onSwipeUp = onSwipeUp,
            onLongPress = onLongPress,
            onLongPressRelease = onLongPressRelease,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel
        )
    }
}
