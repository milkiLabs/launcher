package com.milki.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DismissDragSession {
    Undecided,
    Allowed,
    Blocked
}

@Stable
class LauncherSheetState(
    private val scope: CoroutineScope
) {
    /** The 0..1 fraction where 0f is fully expanded and 1f is fully hidden */
    private val _fraction = Animatable(1f)
    
    var maxOffsetPx by mutableFloatStateOf(1f)

    val currentOffsetPx: Float
        get() = _fraction.value * maxOffsetPx

    val expandedFraction: Float
        get() = 1f - _fraction.value

    val isHidden: Boolean
        get() = _fraction.targetValue == 1f

    private val animationSpec: AnimationSpec<Float> = spring(
        dampingRatio = 0.8f,
        stiffness = 300f
    )

    fun onDragDelta(deltaPx: Float) {
        if (maxOffsetPx <= 0) return
        val rawCurrent = _fraction.value * maxOffsetPx
        val newOffset = (rawCurrent + deltaPx).coerceIn(0f, maxOffsetPx)
        scope.launch {
            _fraction.snapTo(newOffset / maxOffsetPx)
        }
    }
    
    val isSettledHidden: Boolean
        get() = _fraction.value == 1f

    val isSettledExpanded: Boolean
        get() = _fraction.value == 0f

    /** Returns true if the drawer ended up expanded, false if hidden. */
    suspend fun onDragStopped(velocityPx: Float): Boolean {
        val targetFraction = if (velocityPx > 1000f) {
            1f // swipe down abruptly
        } else if (velocityPx < -1000f) {
            0f // swipe up abruptly
        } else if (_fraction.value > 0.5f) {
            1f // mostly hidden
        } else {
            0f // mostly expanded
        }
        
        _fraction.animateTo(
            targetValue = targetFraction,
            initialVelocity = velocityPx / maxOffsetPx,
            animationSpec = animationSpec
        )
        return targetFraction == 0f
    }

    suspend fun animateToHidden() {
        if (_fraction.value == 1f || _fraction.targetValue == 1f) return
        _fraction.animateTo(1f, animationSpec = animationSpec)
    }

    suspend fun animateToExpanded() {
        if (_fraction.value == 0f || _fraction.targetValue == 0f) return
        _fraction.animateTo(0f, animationSpec = animationSpec)
    }

    suspend fun snapToHidden() {
        _fraction.snapTo(1f)
    }

    suspend fun snapToExpanded() {
        _fraction.snapTo(0f)
    }
}

@Composable
fun rememberLauncherSheetState(): LauncherSheetState {
    val scope = rememberCoroutineScope()
    return remember { LauncherSheetState(scope) }
}

/**
 * A container that handles nested scrolling and behaves like a smooth launcher drawer.
 * When the drawer is visible, scrolling down on its layout pulls it away. 
 */
@Composable
fun LauncherSheet(
    state: LauncherSheetState,
    modifier: Modifier = Modifier,
    onDismissedByUser: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val currentOnDismissedByUser = rememberUpdatedState(onDismissedByUser)
    val dismissDragSession = remember { mutableStateOf(DismissDragSession.Undecided) }
    
    val nestedScrollConnection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (available.y < 0 && state.expandedFraction < 1f) {
                    // Pulling up while partially hidden re-expands the sheet first.
                    state.onDragDelta(available.y)
                    Offset(0f, available.y)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (
                    source == NestedScrollSource.UserInput &&
                    dismissDragSession.value == DismissDragSession.Undecided &&
                    state.expandedFraction == 1f &&
                    abs(consumed.y) > 0f
                ) {
                    // Child scroll consumed drag first, so this gesture is a scroll gesture.
                    dismissDragSession.value = DismissDragSession.Blocked
                }

                if (
                    source == NestedScrollSource.UserInput &&
                    dismissDragSession.value == DismissDragSession.Undecided &&
                    available.y > 0f &&
                    abs(consumed.y) == 0f
                ) {
                    // No child scroll consumption: drag started at top, allow dismiss.
                    dismissDragSession.value = DismissDragSession.Allowed
                }

                return if (
                    source == NestedScrollSource.UserInput &&
                    available.y > 0 &&
                    dismissDragSession.value == DismissDragSession.Allowed
                ) {
                    // Gesture started from top: pull sheet downward.
                    state.onDragDelta(available.y)
                    Offset(0f, available.y)
                } else if (available.y < 0 && state.expandedFraction < 1f) {
                    state.onDragDelta(available.y)
                    Offset(0f, available.y)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val result = if (state.expandedFraction < 1f) {
                    val keptExpanded = state.onDragStopped(available.y)
                    if (!keptExpanded) {
                        currentOnDismissedByUser.value()
                    }
                    available
                } else {
                    Velocity.Zero
                }
                dismissDragSession.value = DismissDragSession.Undecided
                return result
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                dismissDragSession.value = DismissDragSession.Undecided
                return Velocity.Zero
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxHeight = constraints.maxHeight.toFloat()
        LaunchedEffect(maxHeight) {
            if (maxHeight > 0) {
                state.maxOffsetPx = maxHeight
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, state.currentOffsetPx.roundToInt()) }
                .nestedScroll(nestedScrollConnection)
        ) {
            content()
        }
    }
}
