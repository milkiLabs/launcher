/**
 * Theme.kt - Material Design 3 theme configuration for the Milki Launcher
 * 
 * This file defines the LauncherTheme composable that provides Material Design 3
 * colors and typography to all UI components in the app.
 * 
 * Key features:
 * - Light and dark color schemes
 * - Dynamic colors support (Android 12+)
 * - Automatic system theme detection
 * - Custom typography integration
 * 
 * Usage: Wrap your content in LauncherTheme { ... }
 * 
 * For detailed documentation, see: docs/Theme.md
 */

package com.milki.launcher.ui.theme

// ============================================================================
// IMPORTS - Android Framework
// ============================================================================
// Activity class (imported but not used in current implementation)
import android.app.Activity

// Build provides Android version information for feature detection
import android.os.Build

// ============================================================================
// IMPORTS - Compose Foundation (Theme Detection)
// ============================================================================
// isSystemInDarkTheme returns whether the device is in dark mode
import androidx.compose.foundation.isSystemInDarkTheme

// ============================================================================
// IMPORTS - Material Design 3
// ============================================================================
// MaterialTheme provides the theme context for all Material components
import androidx.compose.material3.MaterialTheme

// darkColorScheme creates a color scheme for dark mode
import androidx.compose.material3.darkColorScheme

// Dynamic color schemes extract colors from the user's wallpaper (Android 12+)
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

// lightColorScheme creates a color scheme for light mode
import androidx.compose.material3.lightColorScheme

// ============================================================================
// IMPORTS - Compose Runtime
// ============================================================================
// Composable marks functions that define UI
import androidx.compose.runtime.Composable

// LocalContext provides access to the current Android Context
import androidx.compose.ui.platform.LocalContext

// ============================================================================
// COLOR SCHEMES
// ============================================================================
/**
 * Dark color scheme using our dark theme colors.
 * 
 * Material 3 color scheme includes many roles:
 * - primary, onPrimary, primaryContainer, onPrimaryContainer
 * - secondary, onSecondary, secondaryContainer, onSecondaryContainer
 * - tertiary, onTertiary, tertiaryContainer, onTertiaryContainer
 * - background, onBackground
 * - surface, onSurface, surfaceVariant, onSurfaceVariant
 * - error, onError, errorContainer, onErrorContainer
 * - outline, outlineVariant
 * - scrim, inverseSurface, inverseOnSurface, inversePrimary
 * 
 * We define the main colors here; others use Material defaults.
 */
private val DarkColorScheme = darkColorScheme(
    // Primary colors - main brand color
    primary = Purple80,
    
    // Secondary colors - accent color
    secondary = PurpleGrey80,
    
    // Tertiary colors - additional accent
    tertiary = Pink80
)

/**
 * Light color scheme using our light theme colors.
 * Uses the same structure as DarkColorScheme but with colors suited for
 * light backgrounds.
 */
private val LightColorScheme = lightColorScheme(
    // Primary colors
    primary = Purple40,
    
    // Secondary colors
    secondary = PurpleGrey40,
    
    // Tertiary colors
    tertiary = Pink40

    // You can override additional colors here.
    // Uncomment to customize:
    /*
    // Background colors
    background = Color(0xFFFFFBFE),  // Almost white
    onBackground = Color(0xFF1C1B1F),  // Dark text on background
    
    // Surface colors (cards, sheets)
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    
    // Primary text colors
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    */
)

// ============================================================================
// THEME COMPOSABLE
// ============================================================================
/**
 * LauncherTheme applies Material Design 3 theming to content.
 * 
 * This composable wraps your UI and provides:
 * - Color scheme (adapts to light/dark mode)
 * - Typography (text styles)
 * - Dynamic colors (on Android 12+, extracts from wallpaper)
 * 
 * Usage:
 * ```kotlin
 * setContent {
 *     LauncherTheme {
 *         // Your UI here
 *         MainScreen()
 *     }
 * }
 * ```
 * 
 * Inside the theme block, access colors and typography via:
 * - MaterialTheme.colorScheme.primary
 * - MaterialTheme.typography.bodyLarge
 * 
 * @param darkTheme Whether to use dark colors. Defaults to system setting.
 * @param dynamicColor Whether to use dynamic colors (Android 12+). Defaults to true.
 * @param content The UI content to apply theming to
 */
@Composable
fun LauncherTheme(
    // darkTheme parameter defaults to the system's current theme setting.
    // Users can override this to force light or dark mode.
    darkTheme: Boolean = isSystemInDarkTheme(),
    
    // Dynamic color is available on Android 12+ (API 31).
    // When enabled, colors are extracted from the user's wallpaper
    // for a personalized experience.
    dynamicColor: Boolean = true,
    
    // content is a composable lambda (trailing lambda syntax).
    // This is the UI that will be wrapped in our theme.
    content: @Composable () -> Unit
) {
    // ====================================================================
    // COLOR SCHEME SELECTION
    // ====================================================================
    // We use a when expression to select the appropriate color scheme
    // based on device capabilities and user preferences.
    val colorScheme = when {
        
        // ---------------------------------------------------------------
        // PRIORITY 1: Dynamic Colors (Android 12+)
        // ---------------------------------------------------------------
        // Dynamic colors extract a color palette from the user's wallpaper
        // and apply it to the app. This creates a personalized experience
        // that matches the system theme.
        //
        // Requirements:
        // - dynamicColor parameter is true (default)
        // - Device runs Android 12+ (API 31+)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Get the current context to access dynamic color APIs
            val context = LocalContext.current
            
            // Generate dynamic color scheme based on current theme
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }

        // ---------------------------------------------------------------
        // PRIORITY 2: Static Dark Theme
        // ---------------------------------------------------------------
        // If dynamic colors aren't available or disabled, use our static
        // color scheme based on the theme setting.
        darkTheme -> DarkColorScheme
        
        // ---------------------------------------------------------------
        // PRIORITY 3: Static Light Theme (Default)
        // ---------------------------------------------------------------
        // Default to light theme if not dark mode.
        else -> LightColorScheme
    }

    // ====================================================================
    // APPLY MATERIAL THEME
    // ====================================================================
    // MaterialTheme is the root composable that provides theme values
    // to all Material components below it in the composition tree.
    //
    // It provides:
    // - colorScheme: All theme colors
    // - typography: All text styles
    // - shapes: Corner shapes for components
    //
    // Child composables can access these via MaterialTheme.colorScheme, etc.
    MaterialTheme(
        // The color scheme (dynamic or static) we selected above
        colorScheme = colorScheme,
        
        // Our custom typography configuration from Type.kt
        typography = Typography,
        
        // The content to apply theming to
        content = content
    )
}

// ============================================================================
// THEME CUSTOMIZATION EXAMPLES
// ============================================================================
// Force dark mode:
// LauncherTheme(darkTheme = true) { ... }
//
// Force light mode:
// LauncherTheme(darkTheme = false) { ... }
//
// Disable dynamic colors:
// LauncherTheme(dynamicColor = false) { ... }
//
// Access theme values in composables:
// val primaryColor = MaterialTheme.colorScheme.primary
// val bodyStyle = MaterialTheme.typography.bodyLarge
