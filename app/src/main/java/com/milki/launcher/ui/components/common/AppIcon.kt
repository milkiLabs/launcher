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
 * 2. Tries to read icon from launcher memory cache synchronously
 * 3. If cache miss, loads icon on a background coroutine and caches it
 * 4. Displays the icon using an ImageView-backed AndroidView
 *
 * PERFORMANCE:
 * - In-memory cache avoids per-item image-pipeline overhead
 * - Repository preloads icons during app discovery for near-instant first render
 * - Cache misses are loaded off the main thread, then reused everywhere
 */

package com.milki.launcher.ui.components.common

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.milki.launcher.data.icon.AppIconMemoryCache
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
    val context = LocalContext.current

    /**
     * Immediate cache read for first composition.
     *
     * WHY THIS IS IMPORTANT FOR A LAUNCHER:
     * Users expect icons to appear instantly. We read from our process-wide
     * memory cache synchronously here. In most cases this is already populated
     * by repository preloading, so the icon is available in the first frame.
     */
    var iconDrawable by remember(packageName) {
        mutableStateOf(AppIconMemoryCache.get(packageName))
    }

    /**
     * Background fallback load for cache misses.
     *
     * If this specific icon wasn't preloaded yet, we load it on IO and store it
     * in memory cache for all future consumers. Once loaded, state updates and
     * this composable re-renders with the real icon.
     */
    LaunchedEffect(packageName, iconDrawable) {
        if (iconDrawable == null) {
            iconDrawable = withContext(Dispatchers.IO) {
                AppIconMemoryCache.getOrLoad(
                    packageName = packageName,
                    packageManager = context.packageManager
                )
            }
        }
    }

    /**
     * Rendering policy for cache-miss frames.
     *
     * We intentionally render no drawable while loading instead of showing
     * the generic Android default icon, which caused a visible flash when
     * returning home after process/cache cold paths.
     */
    val iconToDisplay = iconDrawable

    /**
     * Render via ImageView-backed AndroidView.
     *
     * WHY ANDROIDVIEW:
     * - We already have a Drawable from PackageManager/cache.
     * - ImageView can display Drawables directly without conversion overhead.
     * - This keeps the icon path simple and avoids extra image-pipeline layers.
     */
    AndroidView(
        modifier = modifier.then(Modifier.size(size)),
        factory = { imageViewContext ->
            ImageView(imageViewContext).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(iconToDisplay)
            }
        },
        update = { imageView ->
            imageView.setImageDrawable(iconToDisplay)
        }
    )
}
