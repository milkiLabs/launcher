package com.milki.launcher.ui.interaction.grid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.ui.components.launcher.findOccupantAt
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropLayoutMetrics
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private enum class BackgroundGestureOutcome {
    Released,
    Triggered,
    Moved,
    Cancelled
}

private data class PendingTap(
    val position: Offset,
    val uptimeMillis: Long
)

internal fun Modifier.detectHomeBackgroundGestures(
    key: Any? = null,
    items: List<HomeItem>,
    layoutMetrics: AppDragDropLayoutMetrics,
    policy: HomeBackgroundGesturePolicy,
    gestureThresholdPx: Float,
    bindings: HomeBackgroundGestureBindings
): Modifier {
    return pointerInput(key, items, layoutMetrics, policy, gestureThresholdPx, bindings) {
        coroutineScope {
            var pendingTap: PendingTap? = null
            var pendingTapJob: Job? = null

            fun clearPendingTap() {
                pendingTapJob?.cancel()
                pendingTapJob = null
                pendingTap = null
            }

            fun flushPendingTapAsSingleTap() {
                if (pendingTap == null) return
                bindings.invoke(LauncherTrigger.HOME_TAP)
                clearPendingTap()
            }

            fun schedulePendingTapResolution(
                tap: PendingTap,
                timeoutMillis: Long
            ) {
                clearPendingTap()
                pendingTap = tap
                pendingTapJob = launch {
                    delay(timeoutMillis)
                    if (pendingTap == tap) {
                        bindings.invoke(LauncherTrigger.HOME_TAP)
                        clearPendingTap()
                    }
                }
            }

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pressedCell = layoutMetrics.pixelToCell(down.position)
                val startCellOccupied = items.findOccupantAt(pressedCell) != null

                if (!policy.canStartBackgroundGesture) {
                    return@awaitEachGesture
                }

                val supportsDoubleTap = LauncherTrigger.HOME_DOUBLE_TAP in policy.enabledTriggers
                val doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis
                val doubleTapSlopPx = viewConfiguration.touchSlop * 2f
                val secondTapCandidate = pendingTap?.let { pending ->
                    val elapsedMillis = down.uptimeMillis - pending.uptimeMillis
                    val delta = down.position - pending.position
                    val withinTapDistance =
                        (delta.x * delta.x) + (delta.y * delta.y) <= (doubleTapSlopPx * doubleTapSlopPx)
                    val canUseAsSecondTap =
                        supportsDoubleTap &&
                                !startCellOccupied &&
                                elapsedMillis >= 0L &&
                                elapsedMillis <= doubleTapTimeoutMillis &&
                                withinTapDistance

                    if (canUseAsSecondTap) {
                        clearPendingTap()
                        true
                    } else {
                        flushPendingTapAsSingleTap()
                        false
                    }
                } ?: false

                val outcome = awaitBackgroundGestureOutcome(
                    pointerId = down.id,
                    startPosition = down.position,
                    touchSlopPx = viewConfiguration.touchSlop,
                    gestureThresholdPx = gestureThresholdPx,
                    longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis,
                    policy = policy,
                    bindings = bindings
                )

                when (outcome) {
                    null -> {
                        if (!startCellOccupied) {
                            bindings.onEmptyAreaLongPress(down.position)
                        }
                        awaitPointerUp(pointerId = down.id)
                    }

                    BackgroundGestureOutcome.Triggered -> {
                        consumeUntilPointerUp(pointerId = down.id)
                    }

                    BackgroundGestureOutcome.Released -> {
                        if (!startCellOccupied) {
                            if (supportsDoubleTap) {
                                if (secondTapCandidate) {
                                    bindings.invoke(LauncherTrigger.HOME_DOUBLE_TAP)
                                } else {
                                    schedulePendingTapResolution(
                                        tap = PendingTap(
                                        position = down.position,
                                        uptimeMillis = down.uptimeMillis
                                        ),
                                        timeoutMillis = doubleTapTimeoutMillis
                                    )
                                }
                            } else {
                                bindings.invoke(LauncherTrigger.HOME_TAP)
                            }
                        }
                    }

                    BackgroundGestureOutcome.Moved,
                    BackgroundGestureOutcome.Cancelled -> Unit
                }

                if (secondTapCandidate && outcome != BackgroundGestureOutcome.Released) {
                    bindings.invoke(LauncherTrigger.HOME_TAP)
                }
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitBackgroundGestureOutcome(
    pointerId: PointerId,
    startPosition: Offset,
    touchSlopPx: Float,
    gestureThresholdPx: Float,
    longPressTimeoutMillis: Long,
    policy: HomeBackgroundGesturePolicy,
    bindings: HomeBackgroundGestureBindings
): BackgroundGestureOutcome? {
    var exceededTouchSlop = false
    val slopOutcome = withTimeoutOrNull(longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId }
                ?: return@withTimeoutOrNull BackgroundGestureOutcome.Cancelled

            if (!change.pressed) {
                return@withTimeoutOrNull BackgroundGestureOutcome.Released
            }

            val totalDrag = change.position - startPosition
            val matchedTrigger = policy.matchingTrigger(
                dragOffset = totalDrag,
                minimumDistancePx = gestureThresholdPx
            )
            if (matchedTrigger != null) {
                bindings.invoke(matchedTrigger)
                return@withTimeoutOrNull BackgroundGestureOutcome.Triggered
            }

            if (totalDrag.exceedsTouchSlop(touchSlopPx = touchSlopPx)) {
                exceededTouchSlop = true
                break
            }
        }
        null
    }

    if (slopOutcome != null) {
        return slopOutcome
    }

    if (!exceededTouchSlop) {
        return null
    }

    var event = currentEvent
    var change = event.changes.firstOrNull { it.id == pointerId }
        ?: return BackgroundGestureOutcome.Cancelled
    var totalDrag = change.position - startPosition

    if (!policy.hasDirectionalMotion(totalDrag)) {
        return BackgroundGestureOutcome.Moved
    }

    while (true) {
        event = awaitPointerEvent()
        change = event.changes.firstOrNull { it.id == pointerId }
            ?: return BackgroundGestureOutcome.Cancelled

        if (!change.pressed) {
            return BackgroundGestureOutcome.Moved
        }

        totalDrag = change.position - startPosition

        if (!policy.hasDirectionalMotion(totalDrag)) {
            return BackgroundGestureOutcome.Moved
        }

        val matchedTrigger = policy.matchingTrigger(
            dragOffset = totalDrag,
            minimumDistancePx = gestureThresholdPx
        )
        if (matchedTrigger != null) {
            bindings.invoke(matchedTrigger)
            return BackgroundGestureOutcome.Triggered
        }
    }
}

private fun HomeBackgroundGesturePolicy.matchingTrigger(
    dragOffset: Offset,
    minimumDistancePx: Float
): LauncherTrigger? {
    return directionalTriggers.firstOrNull { trigger ->
        dragOffset.matchesTriggerDirection(
            trigger = trigger,
            minimumDistancePx = minimumDistancePx
        )
    }
}

private fun HomeBackgroundGesturePolicy.hasDirectionalMotion(
    dragOffset: Offset
): Boolean {
    return directionalTriggers.any { trigger ->
        dragOffset.matchesTriggerDirection(
            trigger = trigger,
            minimumDistancePx = 0f
        )
    }
}

private suspend fun AwaitPointerEventScope.awaitPointerUp(pointerId: PointerId) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return
        if (!change.pressed) return
    }
}

private suspend fun AwaitPointerEventScope.consumeUntilPointerUp(pointerId: PointerId) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return
        change.consume()
        if (!change.pressed) return
    }
}
