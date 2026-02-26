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

package com.milki.launcher.ui.components.grid

import androidx.compose.ui.unit.dp

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
 * @property columns Number of columns in the grid (default: 4)
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
    val columns: Int = 4,
    val extraRows: Int = 4,
    val maxRows: Int = 100,
    
    // Gesture thresholds
    val dragThresholdPx: Float = 20f,
    
    // Drag visual effects
    val dragScale: Float = 1.15f,
    val dragAlpha: Float = 0.6f,
    
    // Preview visual effects (item following finger)
    val previewScale: Float = 1.2f,
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
         * Uses 4 columns which is common for launchers.
         */
        val Default = GridConfig()
        
        /**
         * Configuration optimized for tablets.
         * More columns to utilize the larger screen space.
         */
        val Tablet = GridConfig(
            columns = 6,
            extraRows = 6,
            dragThresholdPx = 25f
        )
        
        /**
         * Configuration with larger cells for accessibility.
         * Fewer columns means larger, easier-to-tap cells.
         */
        val Accessibility = GridConfig(
            columns = 3,
            dragThresholdPx = 30f,
            dragScale = 1.1f,
            previewScale = 1.15f
        )
    }
    
    /**
     * Validates the configuration and returns any issues found.
     *
     * This is useful for debugging configuration problems.
     * Returns an empty list if the configuration is valid.
     *
     * @return List of validation error messages, empty if valid
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (columns < 1) {
            errors.add("columns must be at least 1, got $columns")
        }
        if (extraRows < 0) {
            errors.add("extraRows must be non-negative, got $extraRows")
        }
        if (maxRows < 1) {
            errors.add("maxRows must be at least 1, got $maxRows")
        }
        if (dragThresholdPx <= 0) {
            errors.add("dragThresholdPx must be positive, got $dragThresholdPx")
        }
        if (dragScale <= 0) {
            errors.add("dragScale must be positive, got $dragScale")
        }
        if (previewScale <= 0) {
            errors.add("previewScale must be positive, got $previewScale")
        }
        
        return errors
    }
}
