/**
 * Type.kt - Typography definitions for the Milki Launcher
 * 
 * This file defines the text styles (typography) used throughout the app.
 * Material Design 3 provides a comprehensive typography scale with 15 styles,
 * from large display text to small labels.
 * 
 * We define a custom Typography object that configures these styles
 * for our app's needs. Each style specifies:
 * - Font family
 * - Font weight (normal, bold, etc.)
 * - Font size
 * - Line height (spacing between lines)
 * - Letter spacing
 * 
 * For detailed documentation, see: docs/Theme.md
 */

package com.milki.launcher.ui.theme

// ============================================================================
// IMPORTS
// ============================================================================
// Typography is the Material Design 3 class for defining text styles
import androidx.compose.material3.Typography

// TextStyle defines the visual attributes of text
import androidx.compose.ui.text.TextStyle

// FontFamily specifies which font to use
import androidx.compose.ui.text.font.FontFamily

// FontWeight controls text thickness (Normal, Bold, etc.)
import androidx.compose.ui.text.font.FontWeight

// sp (scalable pixels) is the unit for text sizes
// Unlike dp, sp scales with the user's font size preference
import androidx.compose.ui.unit.sp

// ============================================================================
// TYPOGRAPHY DEFINITION
// ============================================================================
/**
 * Typography configuration for the launcher.
 * 
 * Material Design 3 defines 15 typography styles:
 * 1. displayLarge - Hero text (57sp)
 * 2. displayMedium - Large headlines (45sp)
 * 3. displaySmall - Medium headlines (36sp)
 * 4. headlineLarge - Large section headers (32sp)
 * 5. headlineMedium - Medium section headers (28sp)
 * 6. headlineSmall - Small section headers (24sp)
 * 7. titleLarge - Large titles (22sp)
 * 8. titleMedium - Medium titles (16sp)
 * 9. titleSmall - Small titles (14sp)
 * 10. bodyLarge - Large body text (16sp) ← We configure this
 * 11. bodyMedium - Medium body text (14sp)
 * 12. bodySmall - Small body text (12sp)
 * 13. labelLarge - Large labels (14sp)
 * 14. labelMedium - Medium labels (12sp)
 * 15. labelSmall - Small labels (11sp)
 * 
 * For our minimalist launcher, we primarily use bodyLarge for app names.
 */
val Typography = Typography(
    // ========================================================================
    // BODY LARGE - Main text style
    // ========================================================================
    // We configure bodyLarge as our primary text style for app names.
    // This is used in the app list to display app names.
    bodyLarge = TextStyle(
        // Use the system default font (Roboto on most Android devices).
        // This ensures consistency with the system UI and respects
        // user font preferences.
        fontFamily = FontFamily.Default,
        
        // Normal weight (400) for regular text.
        // Other options: FontWeight.Bold (700), Light (300), Medium (500)
        fontWeight = FontWeight.Normal,
        
        // Font size: 16sp
        // sp = scalable pixels, respects user's font size preference
        // 16sp is the standard body text size in Material Design
        fontSize = 16.sp,
        
        // Line height: 24sp (1.5x font size)
        // Provides comfortable spacing between lines of text
        // Important for readability, especially in longer text
        lineHeight = 24.sp,
        
        // Letter spacing: 0.5sp
        // Slight spacing between letters for body text
        // Improves readability at smaller sizes
        letterSpacing = 0.5.sp
    )
    
    // ========================================================================
    // OTHER TEXT STYLES (Commented Out)
    // ========================================================================
    // You can uncomment and customize these as needed:
    
    /*
    // Title Large - For screen titles
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    
    // Label Small - For captions, timestamps
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)

// ============================================================================
// TYPOGRAPHY USAGE IN COMPOSE
// ============================================================================
// Access typography in your composables:
//
// Text(
//     text = "App Name",
//     style = MaterialTheme.typography.bodyLarge
// )
//
// MaterialTheme.typography provides all 15 text styles.
// They automatically adapt to light/dark theme.
