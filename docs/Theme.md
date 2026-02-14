# Theme.kt, Color.kt, Type.kt - Detailed Documentation

## Overview

The theme files define the visual appearance of the Milki Launcher using Material Design 3. They control:
- **Colors**: The color palette for light and dark modes
- **Typography**: Font styles and sizes
- **Theme**: Combines colors and typography into a cohesive design system

These files follow Material Design 3 (M3) guidelines, which is Google's latest design system.

---

## Table of Contents

1. [What is Material Design?](#what-is-material-design)
2. [Material Design 2 vs 3](#material-design-2-vs-3)
3. [Color.kt - Color Definitions](#colorkt---color-definitions)
4. [Type.kt - Typography](#typekt---typography)
5. [Theme.kt - Theme Configuration](#themekt---theme-configuration)
6. [Dynamic Colors](#dynamic-colors)
7. [Color Scheme](#color-scheme)
8. [How to Use the Theme](#how-to-use-the-theme)
9. [Customization Guide](#customization-guide)

---

## What is Material Design?

### Design System Philosophy

Material Design is Google's design system that provides:
- **Consistency**: Apps look and feel consistent across Android
- **Guidelines**: Best practices for UI/UX design
- **Components**: Pre-built UI components (buttons, cards, etc.)
- **Theming**: Easy customization of colors, shapes, and typography

### Why Use Material Design?

**For Developers**:
- Faster development (pre-built components)
- Less design decisions to make
- Automatic dark mode support
- Accessibility built-in

**For Users**:
- Familiar interface
- Consistent experience across apps
- Professional appearance
- Good usability

### Material Design Versions

- **MD1 (2014)**: Original release
- **MD2 (2018)**: Refined, more flexible
- **MD3 (2021)**: Current version, dynamic colors, updated components

---

## Material Design 2 vs 3

### Key Differences

| Feature | MD2 | MD3 |
|---------|-----|-----|
| Color System | Primary, Secondary, Background | Primary, Secondary, Tertiary, Surface, Background |
| Dynamic Colors | Not supported | Supported (Android 12+) |
| Elevation | Shadows | Tonal elevation (color shifts) |
| Components | Rounded corners | More rounded, updated shapes |
| Typography | 6 text styles | 15 text styles |

### Our Project Uses MD3

```kotlin
// build.gradle.kts
implementation(libs.androidx.compose.material3)  // Material 3
```

---

## Color.kt - Color Definitions

```kotlin
package com.milki.launcher.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
```

### Understanding Color Values

**Hex Color Format**: `0xFF6650a4`
- `0x`: Hexadecimal prefix
- `FF`: Alpha (opacity) - 255 = fully opaque
- `66`: Red component (0-255)
- `50`: Green component (0-255)
- `a4`: Blue component (0-255)

### Named Colors

**Purple80, PurpleGrey80, Pink80**:
- Used in **dark theme**
- Higher numbers (80) indicate lighter, more saturated colors
- Better visibility on dark backgrounds

**Purple40, PurpleGrey40, Pink40**:
- Used in **light theme**
- Lower numbers (40) indicate darker, less saturated colors
- Better visibility on light backgrounds

### Why Different Colors for Light/Dark?

**Visual Perception**:
- Colors appear different on dark vs light backgrounds
- Need to adjust saturation and brightness
- Ensures good contrast and readability

**Example**:
```kotlin
// Light purple on dark background
val Purple80 = Color(0xFFD0BCFF)  // Light, bright

// Dark purple on light background  
val Purple40 = Color(0xFF6650a4)  // Dark, muted
```

### Color Palette Strategy

The colors follow Material Design's tonal palette:
- **Primary**: Main brand color (Purple)
- **Secondary**: Accent color (Purple Grey)
- **Tertiary**: Additional accent (Pink)

These three colors work together harmoniously.

---

## Type.kt - Typography

```kotlin
package com.milki.launcher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)
```

### What is Typography?

Typography is the art of arranging text to make it readable and appealing. In Material Design, it includes:
- Font families
- Font sizes
- Font weights (bold, light, etc.)
- Line heights
- Letter spacing

### Material 3 Typography Scale

MD3 defines 15 text styles:
1. `displayLarge` - Hero text (57sp)
2. `displayMedium` - Large headlines (45sp)
3. `displaySmall` - Medium headlines (36sp)
4. `headlineLarge` - Large section headers (32sp)
5. `headlineMedium` - Medium section headers (28sp)
6. `headlineSmall` - Small section headers (24sp)
7. `titleLarge` - Large titles (22sp)
8. `titleMedium` - Medium titles (16sp)
9. `titleSmall` - Small titles (14sp)
10. `bodyLarge` - Large body text (16sp) ← **We define this**
11. `bodyMedium` - Medium body text (14sp)
12. `bodySmall` - Small body text (12sp)
13. `labelLarge` - Large labels (14sp)
14. `labelMedium` - Medium labels (12sp)
15. `labelSmall` - Small labels (11sp)

### Our Typography Configuration

```kotlin
bodyLarge = TextStyle(
    fontFamily = FontFamily.Default,     // System default font
    fontWeight = FontWeight.Normal,       // Regular weight (400)
    fontSize = 16.sp,                     // 16 scalable pixels
    lineHeight = 24.sp,                   // 1.5x font size for readability
    letterSpacing = 0.5.sp                // Slight spacing for body text
)
```

**FontFamily.Default**:
- Uses the device's default font (Roboto on most Android devices)
- Ensures consistency with system UI
- Respects user font preferences

**FontWeight.Normal**:
- Regular weight (400)
- Other options: Bold (700), Light (300), Medium (500)

**sp (Scalable Pixels)**:
- Like dp but scales with user's text size preference
- Respects accessibility settings
- 16sp is standard body text size

**Line Height**:
- 24sp = 1.5 × 16sp
- Provides comfortable reading space
- Prevents text from feeling cramped

### When to Use Each Style

**bodyLarge** (our defined style):
- App names in the list
- Main text content
- Comfortable reading size

**Other styles you might add**:
- `titleLarge`: Search dialog title
- `labelSmall`: Helper text, timestamps
- `headlineMedium`: Empty state messages

---

## Theme.kt - Theme Configuration

```kotlin
package com.milki.launcher.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun LauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

### Color Schemes

#### Dark Color Scheme

```kotlin
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)
```

Uses our light, bright colors designed for dark backgrounds.

#### Light Color Scheme

```kotlin
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)
```

Uses our dark, muted colors designed for light backgrounds.

### Material 3 Color Roles

Material 3 defines many color roles:

**Primary Colors**:
- `primary`: Main brand color (buttons, active states)
- `onPrimary`: Text/icons on primary color
- `primaryContainer`: Container backgrounds
- `onPrimaryContainer`: Text on primary containers

**Secondary Colors**:
- `secondary`: Accent color (chips, toggles)
- `onSecondary`: Text/icons on secondary
- `secondaryContainer`: Secondary backgrounds
- `onSecondaryContainer`: Text on secondary containers

**Tertiary Colors**:
- `tertiary`: Additional accent (contrasting elements)
- Similar container variants

**Neutral Colors**:
- `background`: App background
- `onBackground`: Text on background
- `surface`: Cards, sheets backgrounds
- `onSurface`: Text on surface
- `surfaceVariant`: Alternative surface
- `outline`: Borders and dividers

**Error Colors**:
- `error`: Error states
- `onError`: Text on error
- `errorContainer`: Error backgrounds

### LauncherTheme Composable

```kotlin
@Composable
fun LauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
```

**Parameters**:
- `darkTheme`: Whether to use dark colors (defaults to system setting)
- `dynamicColor`: Whether to use dynamic colors on Android 12+ (defaults to true)
- `content`: The UI content to theme ( composable lambda)

### Theme Selection Logic

```kotlin
val colorScheme = when {
    // Priority 1: Dynamic colors (Android 12+)
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    // Priority 2: Static dark theme
    darkTheme -> DarkColorScheme
    
    // Priority 3: Static light theme (default)
    else -> LightColorScheme
}
```

**Three-tier selection**:
1. Dynamic colors (if enabled and Android 12+)
2. Static dark theme (if system is in dark mode)
3. Static light theme (default)

---

## Dynamic Colors

### What are Dynamic Colors?

Dynamic Colors (Material You) is a feature introduced in Android 12 that:
- Extracts colors from the user's wallpaper
- Generates a custom color palette
- Applies it to all apps that support it

### How It Works

```
User sets wallpaper
    ↓
Android extracts dominant colors
    ↓
Generates color palette (light and dark variants)
    ↓
Apps call dynamicDarkColorScheme() or dynamicLightColorScheme()
    ↓
App uses user's personalized colors
```

### Code Implementation

```kotlin
dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
    val context = LocalContext.current
    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}
```

**Build.VERSION_CODES.S**: API level 31 (Android 12)

**dynamicDarkColorScheme(context)**: Generates dark theme from wallpaper colors

**dynamicLightColorScheme(context)**: Generates light theme from wallpaper colors

### Benefits

1. **Personalization**: Matches user's wallpaper
2. **Consistency**: All apps use same palette
3. **Accessibility**: Colors meet contrast requirements
4. **No Design Work**: Automatically generated

### Fallback

On Android 11 and below:
- Dynamic colors not available
- Falls back to static color scheme
- App still looks good

---

## How to Use the Theme

### Wrapping Content

```kotlin
setContent {
    LauncherTheme {  // ← Wrap everything in theme
        // Your UI content here
        LauncherScreen(...)
    }
}
```

### Accessing Theme Values

**Colors**:
```kotlin
// In any @Composable function
val colorScheme = MaterialTheme.colorScheme

// Use colors
Box(
    modifier = Modifier.background(colorScheme.primary)
)
Text(
    text = "Hello",
    color = colorScheme.onSurface
)
```

**Typography**:
```kotlin
// In any @Composable function
Text(
    text = "App Name",
    style = MaterialTheme.typography.bodyLarge
)
```

### Examples from MainActivity

```kotlin
// Background color
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)  // Hardcoded for launcher
)

// Text with theme typography
Text(
    text = "Tap to search",
    color = Color.White.copy(alpha = 0.3f),
    style = MaterialTheme.typography.bodyLarge  // Uses our Typography
)

// Surface with theme colors
Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 8.dp
) { ... }
```

---

## Customization Guide

### Changing the Color Palette

**Step 1**: Update Color.kt
```kotlin
// Change to blue theme
val Blue80 = Color(0xFF9FC3FF)
val BlueGrey80 = Color(0xFFB5C9D8)
val Cyan80 = Color(0xFF8BE3F5)

val Blue40 = Color(0xFF3B6BA0)
val BlueGrey40 = Color(0xFF4A6572)
val Cyan40 = Color(0xFF00687A)
```

**Step 2**: Update Theme.kt
```kotlin
private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Cyan80
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = Cyan40
)
```

### Adding More Typography Styles

```kotlin
val Typography = Typography(
    bodyLarge = TextStyle(...),
    
    // Add these
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

### Creating a Custom Font Theme

```kotlin
// Import custom font
val CustomFont = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_bold, FontWeight.Bold),
    Font(R.font.roboto_medium, FontWeight.Medium)
)

// Use in Typography
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = CustomFont,  // ← Use custom font
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
```

### Force Light or Dark Mode

```kotlin
// Always use dark theme
LauncherTheme(darkTheme = true) {
    content()
}

// Always use light theme
LauncherTheme(darkTheme = false) {
    content()
}
```

### Disable Dynamic Colors

```kotlin
// Always use static colors
LauncherTheme(dynamicColor = false) {
    content()
}
```

---

## Best Practices

### Do's

1. **Use theme colors**: Don't hardcode colors (except for special cases)
2. **Respect system theme**: Default to `isSystemInDarkTheme()`
3. **Enable dynamic colors**: Let users personalize their experience
4. **Test both modes**: Ensure app looks good in light and dark
5. **Use appropriate typography**: Match text style to content hierarchy

### Don'ts

1. **Don't ignore accessibility**: Ensure sufficient contrast ratios
2. **Don't use too many colors**: Stick to the MD3 palette
3. **Don't hardcode sizes**: Use sp for text, dp for everything else
4. **Don't forget to test**: On different devices and Android versions

---

## Key Takeaways

1. **Material Design 3** provides a complete design system
2. **Color.kt** defines your color palette (light and dark variants)
3. **Type.kt** defines text styles using Material typography scale
4. **Theme.kt** combines colors and typography into a theme
5. **Dynamic Colors** personalize the app based on wallpaper (Android 12+)
6. **Always wrap content** in your theme composable
7. **Access theme values** through `MaterialTheme.colorScheme` and `MaterialTheme.typography`

The theme files are small but essential - they ensure your app looks professional, consistent, and respects user preferences.
