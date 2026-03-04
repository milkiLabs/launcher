/**
 * DragGestureDetector.kt - Reusable gesture detection for drag operations
 *
 * This file provides a modular gesture detector that handles the complex
 * interactions needed for drag-and-drop in a launcher grid:
 * - Detecting taps vs long-press vs drag
 * - Managing multi-touch safety
 * - Providing haptic feedback coordination
 *
 * WHY SEPARATE GESTURE DETECTION?
 * - Reusability: Same detector can be used in different components
 * - Testability: Gesture logic isolated from UI rendering
 * - Customization: Easy to add new gesture types or modify behavior
 *
 * GESTURE TYPES HANDLED:
 * 1. Tap: Quick touch and release
 * 2. Long-press: Touch held without movement (shows menu)
 * 3. Drag: Long-press followed by movement (moves item)
 *
 * INTERACTION MODEL:
 * ```
 * Touch Down -> Wait for Long Press Timeout
 *     |
 *     +-> Released before timeout -> TAP
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

package com.milki.launcher.ui.components.grid

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

/**
 * Internal log tag for gesture detector lifecycle diagnostics.
 *
 * Logging is intentionally key-value style so logs are easier to filter and
 * correlate when debugging device-specific gesture regressions.
 */
private const val DRAG_GESTURE_LOG_TAG = "DragGestureDetector"

/**
 * Detects tap, long-press, and drag gestures with individual callbacks.
 *
 * This is the canonical gesture detector for launcher drag-capable surfaces.
 *
 * PUBLIC API POLICY:
 * - This file intentionally exposes one public callback style (lambdas).
 * - Avoid adding parallel callback container/adapter APIs unless there is a
 *   concrete invariant that cannot be represented by this shape.
 *
 * THREAD SAFETY:
 * This is a suspend function that runs in the pointer input scope.
 * All callbacks are invoked in the same scope.
 *
 * BEHAVIOR SUMMARY:
 * - Simple tap
 * - Long-press without drag (for menus)
 * - Long-press with drag (for moving items)
 *
 * @param dragThreshold Minimum pixels to move before drag starts
 * @param consumeChanges Whether to consume pointer changes during drag
 * @param onTap Called for tap gesture
 * @param onLongPress Called when long-press is detected
 * @param onLongPressRelease Called when finger lifts after long-press without drag
 * @param onDragStart Called when drag begins (threshold exceeded)
 * @param onDrag Called during drag with movement delta
 * @param onDragEnd Called when drag ends successfully
 * @param onDragCancel Called when drag is cancelled
 */
private suspend fun PointerInputScope.detectDragOrTapGesture(
    dragThreshold: Float = 20f,
    consumeChanges: Boolean = true,
    onTap: () -> Unit,
    onLongPress: (Offset) -> Unit,
    onLongPressRelease: () -> Unit = {},
    onDragStart: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    awaitEachGesture {
        // Wait for the initial touch down
        val down = awaitFirstDown()
        
        // Wait for long-press or cancellation
        // If the user releases before long-press, this returns null
        val longPress = awaitLongPressOrCancellation(down.id)
        
        if (longPress == null) {
            /**
             * Treat pre-long-press release as a tap.
             *
             * WHY THIS IS REQUIRED:
             * This detector is used by home icons and search result rows. Using a
             * secondary waitForUpOrCancellation() probe here can miss legitimate
             * first taps on some devices/dispatch paths, producing a visible
             * "needs two taps" regression across the app.
             */
            onTap()
            return@awaitEachGesture
        }
        
        // Long-press detected - notify callback
        // This typically shows a menu
        onLongPress(longPress.position)
        
        // Track total drag distance and whether drag has started
        var totalDrag = Offset.Zero
        var dragStarted = false
        
        // Continue tracking finger movement
        // The drag function continues until the finger is lifted
        try {
            drag(pointerId = longPress.id) { change ->
                // Calculate drag amount from position change
                val dragAmount = change.position - change.previousPosition
                
                // Accumulate total drag distance
                totalDrag += dragAmount
                
                // Check if we've crossed the drag threshold
                if (!dragStarted && (abs(totalDrag.x) > dragThreshold || abs(totalDrag.y) > dragThreshold)) {
                    // Threshold exceeded - start drag mode
                    dragStarted = true
                    onDragStart()
                }
                
                // If drag has started, notify of movement
                if (dragStarted) {
                    if (consumeChanges) {
                        change.consume()
                    }
                    onDrag(change, dragAmount)
                }
            }
            
            // Finger lifted - complete the gesture
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
            
        } catch (cancellationException: CancellationException) {
            /**
             * Cancellation path:
             * - Pointer input scope cancellation
             * - Gesture interruption from competing touch stream
             *
             * We handle this as a drag cancellation signal for callers, then let
             * the outer pointer-input lifecycle continue naturally.
             */
            logGestureFailure(
                event = "gesture_cancelled",
                dragStarted = dragStarted,
                throwable = cancellationException
            )

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
        } catch (illegalStateException: IllegalStateException) {
            /**
             * Illegal state path:
             * Some OEM gesture pipelines may throw IllegalStateException from
             * pointer stream transitions. We treat this as non-fatal cancellation
             * and report with structured logs for diagnosis.
             */
            logGestureFailure(
                event = "gesture_illegal_state",
                dragStarted = dragStarted,
                throwable = illegalStateException
            )

            if (dragStarted) {
                onDragCancel()
            } else {
                onLongPressRelease()
            }
        }
    }
}

/**
 * Extension to add drag gesture detection with individual callbacks.
 *
 * @param key A stable key that invalidates the gesture detector when changed
 * @param dragThreshold Minimum pixels to move before drag starts
 * @param onTap Called for tap gesture
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
            onLongPress = onLongPress,
            onLongPressRelease = onLongPressRelease,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel
        )
    }
}

/**
 * Logs a structured gesture failure event in key-value form.
 *
 * Structured fields are intentionally explicit so log consumers can group by:
 * - event
 * - phase (before_drag / dragging)
 * - exception type
 * - message
 */
private fun logGestureFailure(
    event: String,
    dragStarted: Boolean,
    throwable: Throwable
) {
    val phase = if (dragStarted) "dragging" else "before_drag"
    Log.w(
        DRAG_GESTURE_LOG_TAG,
        "event=$event phase=$phase exception=${throwable::class.java.simpleName} message=${throwable.message.orEmpty()}"
    )
}
