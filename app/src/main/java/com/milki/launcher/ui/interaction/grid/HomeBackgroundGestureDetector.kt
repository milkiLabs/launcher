package com.milki.launcher.ui.interaction.grid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import com.milki.launcher.domain.model.GridOccupancy
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.ui.interaction.PreTimeoutResult
import com.milki.launcher.ui.interaction.awaitPointerUp
import com.milki.launcher.ui.interaction.consumeUntilPointerUp
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.interaction.trackPointerUntilLongPressOrRelease
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    occupancy: GridOccupancy,
    layoutMetrics: AppDragDropLayoutMetrics,
    policy: HomeBackgroundGesturePolicy,
    gestureThresholdPx: Float,
    bindings: HomeBackgroundGestureBindings
): Modifier {
    return pointerInput(key, items, occupancy, layoutMetrics, policy, gestureThresholdPx, bindings) {
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
                val occupant = occupancy.occupantAt(pressedCell)
                val startCellOccupied = occupant != null

                // Popup widget icons handle their own swipe-up gesture to launch
                // the provider app. Suppress background directional gestures when
                // the touch starts on one so both don't fire simultaneously.
                val isPopupWidgetCell = (occupant as? HomeItem.WidgetItem)
                    ?.displayMode == com.milki.launcher.domain.model.WidgetDisplayMode.PopupIcon

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
                    bindings = bindings,
                    suppressDirectionalGestures = isPopupWidgetCell
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

/**
 * Resolution produced by the shared tracker for phase 1 (pre-slop) of a
 * background gesture.
 */
private sealed interface BackgroundPhase1Resolution {
    /** A terminal gesture outcome was reached before touch slop. */
    data class Terminal(val outcome: BackgroundGestureOutcome) : BackgroundPhase1Resolution

    /** Movement passed touch slop; hand off to post-slop tracking. */
    data object SlopExceeded : BackgroundPhase1Resolution
}

private suspend fun AwaitPointerEventScope.awaitBackgroundGestureOutcome(
    pointerId: PointerId,
    startPosition: Offset,
    touchSlopPx: Float,
    gestureThresholdPx: Float,
    longPressTimeoutMillis: Long,
    policy: HomeBackgroundGesturePolicy,
    bindings: HomeBackgroundGestureBindings,
    suppressDirectionalGestures: Boolean = false
): BackgroundGestureOutcome? {
    val result = trackPointerUntilLongPressOrRelease(
        pointerId = pointerId,
        longPressTimeoutMillis = longPressTimeoutMillis,
        onMove = { change ->
            val totalDrag = change.position - startPosition
            val matchedTrigger = policy.matchingTrigger(
                dragOffset = totalDrag,
                minimumDistancePx = gestureThresholdPx
            )
            when {
                matchedTrigger != null && suppressDirectionalGestures ->
                    BackgroundPhase1Resolution.Terminal(BackgroundGestureOutcome.Moved)

                matchedTrigger != null -> {
                    bindings.invoke(matchedTrigger)
                    BackgroundPhase1Resolution.Terminal(BackgroundGestureOutcome.Triggered)
                }

                totalDrag.exceedsTouchSlop(touchSlopPx = touchSlopPx) ->
                    BackgroundPhase1Resolution.SlopExceeded

                else -> null
            }
        },
        onLift = {
            BackgroundPhase1Resolution.Terminal(BackgroundGestureOutcome.Released)
        }
    )

    var exceededTouchSlop = false
    val phase1Outcome: BackgroundGestureOutcome? = when (result) {
        is PreTimeoutResult.Resolved -> when (val value = result.value) {
            is BackgroundPhase1Resolution.Terminal -> value.outcome
            BackgroundPhase1Resolution.SlopExceeded -> {
                exceededTouchSlop = true
                null
            }
        }

        // Finger released before timeout or slop.
        is PreTimeoutResult.Released -> when (val value = result.value) {
            is BackgroundPhase1Resolution.Terminal -> value.outcome
            // Unreachable at lift time (onLift only produces Terminal).
            BackgroundPhase1Resolution.SlopExceeded -> null
        }

        // Pointer disappeared (multi-touch, system cancel).
        PreTimeoutResult.Lost -> BackgroundGestureOutcome.Cancelled

        // Timeout fired → long press.
        is PreTimeoutResult.LongPress -> null
    }

    if (!exceededTouchSlop) {
        return phase1Outcome
    }

    var change = (result as PreTimeoutResult.Resolved).change
    var totalDrag = change.position - startPosition

    if (!policy.hasDirectionalMotion(totalDrag)) {
        return BackgroundGestureOutcome.Moved
    }

    while (true) {
        val event = awaitPointerEvent()
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
            if (suppressDirectionalGestures) {
                return BackgroundGestureOutcome.Moved
            }
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
