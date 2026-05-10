/**
 * Color.kt - Color palette definitions for the Milki Launcher
 * 
 * This file defines the color palette used throughout the app.
 * We define colors for both light and dark themes.
 * 
 * Material Design 3 uses a color system with:
 * - Primary: Main brand color (buttons, active states)
 * - Secondary: Accent color (chips, toggles)
 * - Tertiary: Additional accent (contrasting elements)
 * 
 * We define separate colors for light and dark themes because:
 * - Colors appear different on light vs dark backgrounds
 * - Need different saturation/brightness for readability
 * - Accessibility requirements differ
 * 
 * Color naming convention:
 * - XX80: Light theme colors (lighter, more saturated)
 * - XX40: Dark theme colors (darker, less saturated)
 * 
 * For detailed documentation, see: docs/Theme.md
 */

package com.milki.launcher.ui.theme

// ============================================================================
// IMPORTS
// ============================================================================
// Color class for defining colors in Jetpack Compose
import androidx.compose.ui.graphics.Color

// ============================================================================
// DARK THEME COLORS
// ============================================================================
// These colors are used in dark mode. They are lighter and more saturated
// to provide good contrast against dark backgrounds.

/**
 * Primary color for dark theme.
 * 
 * Used for: Buttons, active states, important UI elements
 * Hex: #D0BCFF (Light purple)
 * 
 * This is a light, bright purple that stands out well on dark backgrounds.
 * The "80" in the name indicates it's from the 80% lightness level of
 * the Material tonal palette.
 */
val Purple80 = Color(0xFFD0BCFF)

/**
 * Secondary color for dark theme.
 * 
 * Used for: Accent elements, chips, toggles, secondary buttons
 * Hex: #CCC2DC (Light purple-grey)
 * 
 * This complements the primary color and is used for less prominent
 * interactive elements.
 */
val PurpleGrey80 = Color(0xFFCCC2DC)

/**
 * Tertiary color for dark theme.
 * 
 * Used for: Contrasting accents, highlights, special indicators
 * Hex: #EFB8C8 (Light pink)
 * 
 * The tertiary color provides additional contrast and can be used for
 * elements that need to stand out from both primary and secondary.
 */
val Pink80 = Color(0xFFEFB8C8)

// ============================================================================
// LIGHT THEME COLORS
// ============================================================================
// These colors are used in light mode. They are darker and less saturated
// to provide good contrast against light backgrounds.

/**
 * Primary color for light theme.
 * 
 * Used for: Buttons, active states, important UI elements
 * Hex: #6650a4 (Dark purple)
 * 
 * This is a darker, muted purple that stands out well on light backgrounds.
 * The "40" in the name indicates it's from the 40% lightness level of
 * the Material tonal palette.
 */
val Purple40 = Color(0xFF6650a4)

/**
 * Secondary color for light theme.
 * 
 * Used for: Accent elements, chips, toggles, secondary buttons
 * Hex: #625b71 (Dark purple-grey)
 * 
 * This complements the primary color in light mode.
 */
val PurpleGrey40 = Color(0xFF625b71)

/**
 * Tertiary color for light theme.
 * 
 * Used for: Contrasting accents, highlights, special indicators
 * Hex: #7D5260 (Dark pink/burgundy)
 * 
 * The tertiary color provides additional contrast in light mode.
 */
val Pink40 = Color(0xFF7D5260)

// ============================================================================
// COLOR HEX FORMAT EXPLANATION
// ============================================================================
// Colors are defined in hexadecimal format: 0xAARRGGBB
// 
// 0x: Hexadecimal prefix
// AA: Alpha (opacity) - FF = fully opaque, 00 = fully transparent
// RR: Red component (00-FF)
// GG: Green component (00-FF)
// BB: Blue component (00-FF)
//
// Example: 0xFFD0BCFF
// - FF: Fully opaque
// - D0: Red = 208 (out of 255)
// - BC: Green = 188 (out of 255)
// - FF: Blue = 255 (out of 255)
// Result: A light purple color
