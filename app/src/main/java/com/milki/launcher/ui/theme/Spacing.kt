package com.milki.launcher.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing tokens on a 4dp grid. Use these instead of raw `dp` literals.
 */
object Spacing {
    val none: Dp = 0.dp
    val hairline: Dp = 1.dp
    val extraSmall: Dp = 2.dp
    val small: Dp = 4.dp
    val smallMedium: Dp = 8.dp
    val medium: Dp = 12.dp
    val mediumLarge: Dp = 16.dp
    val large: Dp = 24.dp
    val extraLarge: Dp = 32.dp
}

/**
 * Standard icon sizes.
 */
object IconSize {
    val extraSmall: Dp = 16.dp
    val small: Dp = 20.dp
    val standard: Dp = 24.dp
    val large: Dp = 32.dp
    val appList: Dp = 40.dp
    val appLarge: Dp = 48.dp

    /** Home grid cells that fit icon + label in one row height. */
    val appHomeCompact: Dp = 56.dp
}

/**
 * Standard corner radii.
 */
object CornerRadius {
    val extraSmall: Dp = 2.dp
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val large: Dp = 16.dp
    val extraLarge: Dp = 24.dp
}
