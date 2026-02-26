/**
 * DragVisualEffects.kt - Animation and visual effects for drag operations
 *
 * This file provides composable functions and utilities for rendering
 * visual effects during drag operations. Separating visual effects enables:
 * - Reusability: Same effects can be used across different components
 * - Customization: Easy to modify or replace visual feedback
 * - Performance: Animations are optimized for smooth 60fps rendering
 *
 * VISUAL EFFECTS PROVIDED:
 * 1. Dragging item: Scales up and becomes semi-transparent
 * 2. Preview: Ghost image following the finger
 * 3. Drop highlight: Shows where item will land
 * 4. Swap animation: Smooth transition when items swap
 *
 * ANIMATION PRINCIPLES:
 * - Use spring animations for natural feel
 * - Keep animations short (150-300ms)
 * - Provide visual feedback for all interactions
 * - Respect user's animation preferences
 *
 * USAGE:
 * ```kotlin
 * // In your composable:
 * val animatedScale by animateDragScale(isDragging, config)
 * val animatedAlpha by animateDragAlpha(isDragging, config)
 * 
 * Box(
 *     modifier = Modifier
 *         .graphicsLayer {
 *             scaleX = animatedScale
 *             scaleY = animatedScale
 *             this.alpha = animatedAlpha
 *         }
 * )
 * ```
 */

package com.milki.launcher.ui.components.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

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
 * Composable that provides animated scale for drag effects.
 *
 * @param isDragging Whether the item is currently being dragged
 * @param config Grid configuration with drag scale values
 * @return Animated scale value
 */
@Composable
fun animateDragScale(
    isDragging: Boolean,
    config: GridConfig = GridConfig.Default
): Float {
    val animatable = remember { Animatable(1f) }
    
    LaunchedEffect(isDragging) {
        animatable.animateTo(
            targetValue = if (isDragging) config.dragScale else 1f,
            animationSpec = DragAnimationSpecs.DragSpring
        )
    }
    
    return animatable.value
}

/**
 * Composable that provides animated alpha for drag effects.
 *
 * @param isDragging Whether the item is currently being dragged
 * @param config Grid configuration with drag alpha values
 * @return Animated alpha value
 */
@Composable
fun animateDragAlpha(
    isDragging: Boolean,
    config: GridConfig = GridConfig.Default
): Float {
    val animatable = remember { Animatable(1f) }
    
    LaunchedEffect(isDragging) {
        animatable.animateTo(
            targetValue = if (isDragging) config.dragAlpha else 1f,
            animationSpec = DragAnimationSpecs.DragSpring
        )
    }
    
    return animatable.value
}

/**
 * Data class holding animated values for drag visual effects.
 *
 * Use this to apply multiple effects consistently.
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
 * This is more efficient than calling animateDragScale and animateDragAlpha
 * separately because it uses a single LaunchedEffect.
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

/**
 * Calculates the preview offset for the finger-following drag preview.
 *
 * The preview should follow the finger with the item centered on the touch point.
 *
 * @param basePosition The starting grid position in pixels
 * @param dragOffset The current drag offset in pixels
 * @param cellWidthPx The width of a grid cell in pixels
 * @param cellHeightPx The height of a grid cell in pixels
 * @return The pixel offset for the preview
 */
fun calculatePreviewOffset(
    basePosition: Offset,
    dragOffset: Offset,
    cellWidthPx: Float,
    cellHeightPx: Float
): IntOffset {
    val previewX = basePosition.x + dragOffset.x
    val previewY = basePosition.y + dragOffset.y
    
    return IntOffset(
        x = previewX.roundToInt(),
        y = previewY.roundToInt()
    )
}

/**
 * Calculates the drop target highlight offset.
 *
 * @param targetPosition The target grid position
 * @param cellWidthPx The width of a grid cell in pixels
 * @param cellHeightPx The height of a grid cell in pixels
 * @return The pixel offset for the highlight
 */
fun calculateDropHighlightOffset(
    targetPosition: GridCalculator,
    column: Int,
    row: Int
): IntOffset {
    return IntOffset(
        x = (column * targetPosition.cellWidthPx).roundToInt(),
        y = (row * targetPosition.cellHeightPx).roundToInt()
    )
}

/**
 * Extension to apply drag visual effects to a Modifier.
 *
 * @param values The animated visual values to apply
 */
fun Modifier.dragVisualEffects(values: DragVisualValues): Modifier {
    return this
        .zIndex(values.zIndex)
        .graphicsLayer {
            scaleX = values.scale
            scaleY = values.scale
            alpha = values.alpha
        }
}

/**
 * Extension to apply preview visual effects to a Modifier.
 *
 * @param config Grid configuration with preview scale and alpha
 */
fun Modifier.previewVisualEffects(config: GridConfig = GridConfig.Default): Modifier {
    return this
        .graphicsLayer {
            scaleX = config.previewScale
            scaleY = config.previewScale
            alpha = config.previewAlpha
            shadowElevation = config.shadowElevation
        }
}

/**
 * Extension to apply drop highlight visual effects to a Modifier.
 *
 * @param config Grid configuration with highlight scale and alpha
 */
fun Modifier.dropHighlightEffects(config: GridConfig = GridConfig.Default): Modifier {
    return this
        .alpha(config.dropHighlightAlpha)
        .graphicsLayer {
            scaleX = config.dropHighlightScale
            scaleY = config.dropHighlightScale
        }
}

/**
 * Helper to create IntOffset from Offset.
 */
fun Offset.toIntOffset(): IntOffset {
    return IntOffset(x.roundToInt(), y.roundToInt())
}
