/**
 * Spacing.kt - Centralized spacing constants for consistent UI layout
 *
 * WHY THIS EXISTS:
 * Throughout the app, we use the same spacing values repeatedly:
 * - 16.dp horizontal padding appears 6+ times
 * - 8.dp spacing appears 8+ times
 * - 12.dp padding appears 3+ times
 *
 * This leads to:
 * - Magic numbers scattered throughout the code
 * - Inconsistent spacing if values are slightly different
 * - Difficulty changing spacing globally
 * - No clear spacing system
 *
 * By centralizing spacing values, we ensure:
 * - Consistent spacing across the entire app
 * - Easy global adjustments (change once, affects everywhere)
 * - Clear spacing hierarchy (small, medium, large, etc.)
 * - Better maintainability
 *
 * USAGE:
 * ```kotlin
 * // Instead of:
 * Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
 *
 * // Use:
 * Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small)
 * ```
 *
 * DESIGN SYSTEM:
 * This follows Material Design's 8dp grid system where all spacing
 * is a multiple of 4dp or 8dp. This creates visual rhythm and consistency.
 */

package com.milki.launcher.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing object contains all spacing constants used throughout the app.
 *
 * NAMING CONVENTION:
 * - extraSmall (2dp): Minimal spacing, used for subtle separations
 * - small (4dp): Tight spacing within components
 * - smallMedium (8dp): Common spacing for related elements
 * - medium (12dp): Standard spacing between elements
 * - mediumLarge (16dp): Comfortable spacing, most common for padding
 * - large (24dp): Generous spacing for section separation
 * - extraLarge (32dp): Major section breaks
 *
 * USAGE GUIDELINES:
 * - Use extraSmall (2dp) for: Indicator bars, dividers
 * - Use small (4dp) for: Icon-to-text spacing in tight layouts
 * - Use smallMedium (8dp) for: Spacing between related items, vertical spacing in lists
 * - Use medium (12dp) for: Vertical padding in list items, spacing between groups
 * - Use mediumLarge (16dp) for: Horizontal screen padding, card padding
 * - Use large (24dp) for: Section headers, major groupings
 * - Use extraLarge (32dp) for: Top-level sections, dialog padding
 */
object Spacing {
    /**
     * 2dp - Minimal spacing
     * Used for: Indicator bars, subtle dividers
     * Example: The colored bar under the search field
     */
    val extraSmall: Dp = 2.dp

    /**
     * 4dp - Tight spacing
     * Used for: Small gaps within components
     * Example: Space between indicator bar and text field
     */
    val small: Dp = 4.dp

    /**
     * 8dp - Common small spacing
     * Used for: Related elements, vertical list spacing
     * Example: Space between icon and text in provider info
     */
    val smallMedium: Dp = 8.dp

    /**
     * 12dp - Standard element spacing
     * Used for: Vertical padding in list items
     * Example: AppListItem vertical padding, AppGridItem padding
     */
    val medium: Dp = 12.dp

    /**
     * 16dp - Most common padding
     * Used for: Horizontal screen padding, card padding
     * Example: Search field horizontal padding, list item horizontal padding
     */
    val mediumLarge: Dp = 16.dp

    /**
     * 24dp - Section spacing
     * Used for: Separating major sections
     * Example: Space between search field and results
     */
    val large: Dp = 24.dp

    /**
     * 32dp - Major section breaks
     * Used for: Top-level sections, dialog padding
     * Example: Large icon sizes, major visual breaks
     */
    val extraLarge: Dp = 32.dp
}

/**
 * IconSize object contains standard icon sizes used throughout the app.
 *
 * WHY THIS EXISTS:
 * Icon sizes were hardcoded throughout the app:
 * - 16.dp, 20.dp, 32.dp, 40.dp, 48.dp, 56.dp
 *
 * Centralizing these ensures:
 * - Consistent icon sizing
 * - Clear size hierarchy
 * - Easy global adjustments
 *
 * USAGE:
 * ```kotlin
 * // Instead of:
 * Icon(modifier = Modifier.size(16.dp))
 *
 * // Use:
 * Icon(modifier = Modifier.size(IconSize.extraSmall))
 * ```
 */
object IconSize {
    /**
     * 16dp - Extra small icons
     * Used for: Provider icons in search field, small decorative icons
     */
    val extraSmall: Dp = 16.dp

    /**
     * 20dp - Small icons
     * Used for: Trailing icons, secondary actions
     * Example: Call icon in contact results
     */
    val small: Dp = 20.dp

    /**
     * 24dp - Standard icons
     * Used for: Most icons in the app (Material Design standard)
     * Example: Search icon, close icon, navigation icons
     */
    val standard: Dp = 24.dp

    /**
     * 32dp - Large icons
     * Used for: Prominent icons, warning icons
     * Example: Warning icon in permission request
     */
    val large: Dp = 32.dp

    /**
     * 40dp - App icons in lists
     * Used for: App icons in list view
     * Example: AppListItem icon size
     */
    val appList: Dp = 40.dp

    /**
     * 48dp - Large app icons
     * Used for: Featured app icons, larger touch targets
     */
    val appLarge: Dp = 48.dp

    /**
     * 56dp - Extra large app icons
     * Used for: App icons in grid view
     * Example: AppGridItem icon size
     */
    val appGrid: Dp = 56.dp
}

/**
 * CornerRadius object contains standard corner radius values.
 *
 * WHY THIS EXISTS:
 * Corner radius values were hardcoded:
 * - 2.dp, 12.dp, 16.dp
 *
 * Centralizing ensures consistent rounded corners throughout the app.
 *
 * USAGE:
 * ```kotlin
 * // Instead of:
 * RoundedCornerShape(12.dp)
 *
 * // Use:
 * RoundedCornerShape(CornerRadius.medium)
 * ```
 */
object CornerRadius {
    /**
     * 2dp - Minimal rounding
     * Used for: Small elements like indicator bars
     */
    val extraSmall: Dp = 2.dp

    /**
     * 8dp - Small rounding
     * Used for: Buttons, small cards
     */
    val small: Dp = 8.dp

    /**
     * 12dp - Medium rounding
     * Used for: Grid items, list items
     * Example: AppGridItem shape
     */
    val medium: Dp = 12.dp

    /**
     * 16dp - Large rounding
     * Used for: Dialogs, large cards
     * Example: AppSearchDialog shape
     */
    val large: Dp = 16.dp

    /**
     * 24dp - Extra large rounding
     * Used for: Bottom sheets, major UI elements
     */
    val extraLarge: Dp = 24.dp
}
