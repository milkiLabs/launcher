/**
 * DragVisualEffects.kt - Animation and visual effects for drag operations
 *
 * This file provides composable functions and utilities for rendering
 * visual effects during drag operations. Separating visual effects enables:
 * - Reusability: Same effects can be used across different components
 * - Customization: Easy to modify or replace visual feedback
 * - Performance: Animations are optimized for smooth 60fps rendering
 */

package com.milki.launcher.ui.interaction.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * Animation specs for drag-related animations.
 *
 * Having these as constants ensures consistent animation feel
 * across all drag operations.
 */
object DragAnimationSpecs {
    /**
     * Spring animation for scale and alpha changes during drag.
     * Medium-low stiffness provides a responsive but not jittery feel.
     */
    val DragSpring: AnimationSpec<Float> = spring(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy
    )

    /**
     * Quick spring for immediate feedback.
     * Use for haptic-coordinated animations.
     */
    val QuickSpring: AnimationSpec<Float> = spring(
        stiffness = Spring.StiffnessMedium,
        dampingRatio = Spring.DampingRatioMediumBouncy
    )

    /**
     * Tween animation for drop animations.
     * Smooth easing for final positioning.
     */
    val DropTween: AnimationSpec<Float> = tween(
        durationMillis = 200,
        easing = FastOutSlowInEasing
    )

    /**
     * Duration for drop completion animation.
     */
    const val DropAnimationDurationMs = 200

    /**
     * Duration for swap animation.
     */
    const val SwapAnimationDurationMs = 250
}

/**
 * Data class holding animated values for drag visual effects.
 *
 * @property scale The current scale factor
 * @property alpha The current alpha value
 * @property zIndex The z-index for layering
 */
data class DragVisualValues(
    val scale: Float = 1f,
    val alpha: Float = 1f,
    val zIndex: Float = 0f
)

/**
 * Composable that provides all animated values for drag effects.
 *
 * This is more efficient than splitting scale and alpha into separate
 * Animatable instances per effect because it uses a single LaunchedEffect.
 *
 * @param isDragging Whether the item is currently being dragged
 * @param config Grid configuration
 * @return Animated visual values
 */
@Composable
fun animateDragVisuals(
    isDragging: Boolean,
    config: GridConfig = GridConfig.Default
): DragVisualValues {
    val scaleAnimatable = remember { Animatable(1f) }
    val alphaAnimatable = remember { Animatable(1f) }

    LaunchedEffect(isDragging) {
        scaleAnimatable.animateTo(
            targetValue = if (isDragging) config.dragScale else 1f,
            animationSpec = DragAnimationSpecs.DragSpring
        )
    }

    LaunchedEffect(isDragging) {
        alphaAnimatable.animateTo(
            targetValue = if (isDragging) config.dragAlpha else 1f,
            animationSpec = DragAnimationSpecs.DragSpring
        )
    }

    return DragVisualValues(
        scale = scaleAnimatable.value,
        alpha = alphaAnimatable.value,
        zIndex = if (isDragging) config.dragZIndex else 0f
    )
}