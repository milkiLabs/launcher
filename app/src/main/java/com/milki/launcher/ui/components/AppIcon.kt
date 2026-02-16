/**
 * AppIcon.kt - Reusable composable for displaying app icons
 *
 * This component centralizes the app icon loading logic to avoid duplication
 * across AppGridItem and AppListItem components.
 *
 * WHY THIS EXISTS:
 * Previously, both AppGridItem and AppListItem had identical code for loading
 * app icons using Coil's rememberAsyncImagePainter. This led to:
 * - Code duplication (same logic in multiple places)
 * - Maintenance burden (changes needed in multiple files)
 * - Inconsistency risk (one component might be updated but not the other)
 *
 * By extracting this into a shared component, we ensure:
 * - Single source of truth for icon loading
 * - Consistent behavior across all app displays
 * - Easier maintenance and updates
 *
 * USAGE:
 * ```kotlin
 * AppIcon(
 *     packageName = appInfo.packageName,
 *     size = 48.dp
 * )
 * ```
 *
 * HOW IT WORKS:
 * 1. Takes a package name and size as parameters
 * 2. Creates an AppIconRequest model for Coil
 * 3. Uses rememberAsyncImagePainter to load the icon asynchronously
 * 4. Displays the icon with the specified size
 *
 * PERFORMANCE:
 * - Coil handles caching automatically (memory + disk)
 * - Icons are loaded asynchronously (doesn't block UI thread)
 * - Our custom AppIconFetcher efficiently loads icons from PackageManager
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.milki.launcher.domain.model.AppIconRequest
import com.milki.launcher.ui.theme.IconSize

/**
 * AppIcon displays an app's icon loaded from its package name.
 *
 * This is a reusable component that handles all the complexity of:
 * - Creating the AppIconRequest model
 * - Setting up the async image painter
 * - Configuring the Image composable
 *
 * @param packageName The package name of the app (e.g., "com.android.chrome")
 * @param modifier Optional modifier for external customization (e.g., clip shape, padding)
 * @param size The size of the icon in dp (default: 40.dp)
 *
 * ACCESSIBILITY:
 * contentDescription is null because this icon should always be accompanied
 * by the app name as text. Screen readers will read the text, making a
 * separate description for the icon redundant and potentially annoying.
 *
 * Example usage:
 * ```kotlin
 * // In a list item
 * AppIcon(
 *     packageName = appInfo.packageName,
 *     size = 40.dp
 * )
 *
 * // In a grid item with circular clip
 * AppIcon(
 *     packageName = appInfo.packageName,
 *     size = 56.dp,
 *     modifier = Modifier.clip(CircleShape)
 * )
 * ```
 */
@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = IconSize.appList
) {
    /**
     * Create the async image painter.
     *
     * rememberAsyncImagePainter is a Coil function that:
     * 1. Takes a model (our AppIconRequest with the package name)
     * 2. Returns a Painter that loads the image asynchronously
     * 3. Handles caching automatically (memory + disk)
     * 4. Shows a placeholder while loading
     * 5. Handles errors gracefully
     *
     * The "remember" prefix means this painter is cached across recompositions.
     * If the packageName doesn't change, the same painter instance is reused.
     *
     * AppIconRequest is our custom model that works with AppIconFetcher.
     * The fetcher knows how to load app icons from Android's PackageManager.
     */
    val painter = rememberAsyncImagePainter(
        model = AppIconRequest(packageName)
    )

    /**
     * Display the icon using the Image composable.
     *
     * The Image composable:
     * - Takes the painter we created above
     * - Applies the size modifier to control dimensions
     * - Applies any additional modifiers passed by the caller
     *
     * contentDescription is null because:
     * - This icon is always shown with the app name as text
     * - Screen readers will read the text label
     * - Adding a description would be redundant
     * - This follows Android accessibility best practices
     */
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier.then(Modifier.size(size))
    )
}
