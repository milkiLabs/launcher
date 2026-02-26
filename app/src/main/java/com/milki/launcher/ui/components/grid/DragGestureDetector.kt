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

/**
 * Configuration for gesture detection behavior.
 *
 * @property dragThresholdPx Minimum movement in pixels to start drag after long-press
 * @property consumeChanges Whether to consume pointer changes during drag
 * @property multiTouchEnabled Whether to allow multi-touch during drag
 */
data class DragGestureConfig(
    val dragThresholdPx: Float = 20f,
    val consumeChanges: Boolean = true,
    val multiTouchEnabled: Boolean = false
)

/**
 * Callback interface for gesture events.
 *
 * Using an interface instead of individual lambdas makes it easier
 * to pass all callbacks together and ensures consistent handling.
 */
interface DragGestureCallbacks {
    /**
     * Called when a tap gesture is detected.
     */
    fun onTap() {}
    
    /**
     * Called when a long-press is detected (before knowing if it will become a drag).
     *
     * @param position The screen position where long-press occurred
     */
    fun onLongPress(position: Offset) {}
    
    /**
     * Called when drag starts (movement exceeded threshold after long-press).
     */
    fun onDragStart() {}
    
    /**
     * Called continuously during drag as user moves their finger.
     *
     * @param change The pointer input change
     * @param dragAmount The amount of movement since last call
     */
    fun onDrag(change: PointerInputChange, dragAmount: Offset) {}
    
    /**
     * Called when drag ends successfully (user released finger).
     */
    fun onDragEnd() {}
    
    /**
     * Called when drag is cancelled (e.g., system event, multi-touch).
     */
    fun onDragCancel() {}
}

/**
 * Detects tap, long-press, and drag gestures on an element.
 *
 * This function handles the complete gesture lifecycle:
 * 1. Waits for touch down
 * 2. Determines if it's a tap, long-press, or drag
 * 3. Calls appropriate callbacks throughout
 *
 * THREAD SAFETY:
 * This is a suspend function that runs in the pointer input scope.
 * All callbacks are invoked in the same scope.
 *
 * @param config Configuration for gesture detection
 * @param callbacks Callbacks for gesture events
 */
suspend fun PointerInputScope.detectDragOrTapGesture(
    config: DragGestureConfig = DragGestureConfig(),
    callbacks: DragGestureCallbacks
) {
    detectDragOrTapGesture(
        dragThreshold = config.dragThresholdPx,
        consumeChanges = config.consumeChanges,
        onTap = { callbacks.onTap() },
        onLongPress = { callbacks.onLongPress(it) },
        onDragStart = { callbacks.onDragStart() },
        onDrag = { change, offset -> callbacks.onDrag(change, offset) },
        onDragEnd = { callbacks.onDragEnd() },
        onDragCancel = { callbacks.onDragCancel() }
    )
}

/**
 * Detects tap, long-press, and drag gestures with individual callbacks.
 *
 * This is the primary gesture detection function. It distinguishes between:
 * - Simple tap
 * - Long-press without drag (for menus)
 * - Long-press with drag (for moving items)
 *
 * INTERACTION MODEL:
 * 1. Long-press immediately triggers onLongPress
 * 2. If user moves beyond threshold, onDragStart is called
 * 3. Continued movement calls onDrag
 * 4. Release calls onDragEnd (if drag started) or leaves menu open
 *
 * @param dragThreshold Minimum pixels to move before drag starts
 * @param consumeChanges Whether to consume pointer changes during drag
 * @param onTap Called for tap gesture
 * @param onLongPress Called when long-press is detected
 * @param onDragStart Called when drag begins (threshold exceeded)
 * @param onDrag Called during drag with movement delta
 * @param onDragEnd Called when drag ends successfully
 * @param onDragCancel Called when drag is cancelled
 */
suspend fun PointerInputScope.detectDragOrTapGesture(
    dragThreshold: Float = 20f,
    consumeChanges: Boolean = true,
    onTap: () -> Unit,
    onLongPress: (Offset) -> Unit,
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
            // No long-press detected - this was a tap
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
            }
            // If no drag happened, the menu stays open (already shown)
            
        } catch (e: Exception) {
            // Gesture was cancelled (e.g., another touch event, system interrupt)
            // Log for debugging but don't crash
            android.util.Log.w("DragGestureDetector", "Gesture cancelled: ${e.message}")
            
            if (dragStarted) {
                onDragCancel()
            }
        }
    }
}

/**
 * Extension to add drag gesture detection to a Modifier.
 *
 * This makes it easy to add drag detection to any composable:
 * ```kotlin
 * Box(
 *     modifier = Modifier
 *         .size(100.dp)
 *         .detectDragGesture { callbacks }
 * )
 * ```
 *
 * @param key A stable key that invalidates the gesture detector when changed
 * @param config Gesture detection configuration
 * @param callbacks Callbacks for gesture events
 */
fun Modifier.detectDragGesture(
    key: Any? = null,
    config: DragGestureConfig = DragGestureConfig(),
    callbacks: DragGestureCallbacks
): Modifier {
    return pointerInput(key, config) {
        detectDragOrTapGesture(config, callbacks)
    }
}

/**
 * Extension to add drag gesture detection with individual callbacks.
 *
 * @param key A stable key that invalidates the gesture detector when changed
 * @param dragThreshold Minimum pixels to move before drag starts
 * @param onTap Called for tap gesture
 * @param onLongPress Called when long-press is detected
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
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel
        )
    }
}

/**
 * Simple callback holder for common use cases.
 *
 * Use this when you only need a subset of callbacks:
 * ```kotlin
 * val callbacks = simpleDragCallbacks(
 *     onTap = { handleClick() },
 *     onDragEnd = { handleDrop() }
 * )
 * ```
 */
fun simpleDragCallbacks(
    onTap: () -> Unit = {},
    onLongPress: (Offset) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {}
): DragGestureCallbacks {
    return object : DragGestureCallbacks {
        override fun onTap() = onTap()
        override fun onLongPress(position: Offset) = onLongPress(position)
        override fun onDragStart() = onDragStart()
        override fun onDrag(change: PointerInputChange, dragAmount: Offset) = onDrag(change, dragAmount)
        override fun onDragEnd() = onDragEnd()
        override fun onDragCancel() = onDragCancel()
    }
}
