/**
 * GridConfig.kt - Centralized configuration for the home screen grid
 *
 * This file defines all configuration parameters for the draggable grid,
 * including layout dimensions, gesture thresholds, and visual effects.
 *
 * WHY CENTRALIZE CONFIGURATION?
 * - Consistency: All grid-related values defined in one place
 * - Maintainability: Change values once, affects all usages
 * - Testability: Easy to mock different configurations for testing
 * - Flexibility: Can support different grid configurations (phone vs tablet)
 *
 * CONFIGURATION CATEGORIES:
 * 1. Layout: columns, rows, cell sizing
 * 2. Gestures: drag thresholds, long-press timing
 * 3. Visual: scales, alphas, animations
 *
 * USAGE:
 * ```kotlin
 * val config = GridConfig()
 * // Use config.columns, config.dragThresholdPx, etc.
 * ```
 *
 * FUTURE EXTENSIBILITY:
 * - Tablet configurations with more columns
 * - User-customizable grid sizes
 * - Accessibility-friendly larger cells
 */

package com.milki.launcher.ui.interaction.grid

import com.milki.launcher.domain.homegraph.HomeGridDefaults

/**
 * Configuration for the home screen grid layout.
 *
 * This data class holds all configuration parameters needed for:
 * - Grid layout (columns, rows)
 * - Gesture detection (thresholds)
 * - Visual effects (scales, alphas)
 *
 * IMMUTABILITY:
 * All properties are val (immutable). To change configuration,
 * create a new instance with different values.
 *
 * DEFAULT VALUES:
 * The default values are optimized for typical phone screens.
 * For tablets or accessibility, create instances with different values.
 *
 * @property columns Number of columns in the grid (default: [HomeGridDefaults.COLUMNS])
 * @property extraRows Additional rows beyond the highest item for visual padding
 * @property dragThresholdPx Minimum movement in pixels to start drag after long-press
 * @property dragScale Scale factor applied to item being dragged
 * @property dragAlpha Alpha (opacity) of item being dragged
 * @property previewScale Scale factor for the finger-following preview
 * @property previewAlpha Alpha for the finger-following preview
 * @property dropHighlightAlpha Alpha for the drop target highlight
 * @property dropHighlightScale Scale for the drop target highlight
 * @property dragZIndex Z-index for dragged item (ensures it's on top)
 * @property previewZIndex Z-index for preview (ensures it's above everything)
 * @property shadowElevation Shadow elevation for dragged item
 */
data class GridConfig(
    val columns: Int = HomeGridDefaults.COLUMNS,
    val extraRows: Int = 4,
    val maxRows: Int = HomeGridDefaults.MAX_ROWS,
    
    // Gesture thresholds
    val dragThresholdPx: Float = 20f,
    
    // Drag visual effects
    val dragScale: Float = 1f,
    val dragAlpha: Float = 0.6f,
    
    // Preview visual effects (item following finger)
    val previewScale: Float = 1f,
    val previewAlpha: Float = 0.9f,
    
    // Drop target highlight
    val dropHighlightAlpha: Float = 0.3f,
    val dropHighlightScale: Float = 0.9f,
    
    // Z-index values for layering
    val dragZIndex: Float = 10f,
    val previewZIndex: Float = 100f,
    
    // Shadow for depth effect
    val shadowElevation: Float = 8f
) {
    companion object {
        /**
         * Default configuration for standard phone screens.
         * Uses the shared home-grid default column count.
         */
        val Default = GridConfig()
    }

    init {
        require(columns >= 1) { "columns must be at least 1, got $columns" }
        require(extraRows >= 0) { "extraRows must be non-negative, got $extraRows" }
        require(maxRows >= 1) { "maxRows must be at least 1, got $maxRows" }
        require(dragThresholdPx > 0) { "dragThresholdPx must be positive, got $dragThresholdPx" }
        require(dragScale > 0) { "dragScale must be positive, got $dragScale" }
        require(previewScale > 0) { "previewScale must be positive, got $previewScale" }
    }
}
