/**
 * AppGridItem.kt - Compact grid item component for displaying apps in a grid layout
 *
 * This component is designed for displaying apps in a space-efficient grid format.
 * Unlike AppListItem which shows apps horizontally (icon + name side by side),
 * AppGridItem shows them vertically (icon on top, name below) to maximize
 * the number of apps visible on screen.
 *
 * USAGE:
 * This is used in the search dialog when displaying recent apps or search results.
 * The grid layout allows showing 8 apps (2 rows × 4 columns) in a compact space.
 *
 * PERFORMANCE CONSIDERATIONS:
 * - Uses rememberAsyncImagePainter for efficient async image loading
 * - Coil handles caching automatically
 * - Text is limited to 2 lines with ellipsis to prevent layout shifts
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.milki.launcher.domain.model.AppIconRequest
import com.milki.launcher.domain.model.AppInfo

/**
 * AppGridItem displays a single app in a compact grid format.
 *
 * Layout:
 * ```
 * ┌─────────────┐
 * │   [ICON]    │  <- 48dp circular icon
 * │   App Name  │  <- 2 lines max, centered
 * └─────────────┘
 * ```
 *
 * @param appInfo The app to display (from domain model)
 * @param onClick Called when user taps this item
 * @param modifier Optional modifier for external customization
 *
 * Example usage:
 * ```kotlin
 * AppGridItem(
 *     appInfo = app,
 *     onClick = { launchApp(app) },
 *     modifier = Modifier.weight(1f)
 * )
 * ```
 */
@Composable
fun AppGridItem(
    appInfo: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    /**
     * Surface provides the clickable area and ripple effect.
     * We use a rounded corner shape for a modern look.
     */
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        /**
         * Column arranges icon above text.
         * We center everything horizontally for a clean look.
         */
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            /**
             * Icon container with circular clip.
             *
             * The Box creates a circular background behind the icon.
             * This provides visual consistency and makes the tap target clearer.
             * The circular shape is common in modern launchers.
             */
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                /**
                 * Load the app icon using Coil with our custom AppIconFetcher.
                 *
                 * rememberAsyncImagePainter creates a painter that:
                 * 1. Loads the image asynchronously (doesn't block UI)
                 * 2. Caches the loaded image for performance
                 * 3. Shows a placeholder while loading
                 * 4. Handles errors gracefully
                 *
                 * We pass AppIconRequest which contains the package name.
                 * Our custom AppIconFetcher knows how to load icons from PackageManager.
                 */
                val painter = rememberAsyncImagePainter(
                    model = AppIconRequest(appInfo.packageName)
                )

                /**
                 * Image displays the loaded icon.
                 *
                 * contentDescription is null because the app name is displayed
                 * as text right below it. Screen readers will read the name,
                 * so adding a description for the icon would be redundant.
                 *
                 * We match the Box size (56.dp) to fill the circular container.
                 */
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                )
            }

            /**
             * App name text below the icon.
             *
             * - maxLines = 2: Allows wrapping for long names
             * - overflow = TextOverflow.Ellipsis: Shows "..." for very long names
             * - textAlign = TextAlign.Center: Centers text under the icon
             *
             * This ensures consistent grid item heights regardless of app name length.
             */
            Text(
                text = appInfo.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}
