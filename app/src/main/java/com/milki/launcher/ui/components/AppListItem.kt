/**
 * AppListItem.kt - Reusable component for displaying an app in a list
 * 
 * This file is part of the UI layer in Clean Architecture.
 * UI components are reusable building blocks that know how to display data
 * but don't know where the data comes from or what happens when clicked.
 * 
 * This component follows the Single Responsibility Principle - it does ONE thing:
 * display an app row with an icon and name.
 * 
 * Location: ui/components/AppListItem.kt
 * Architecture Layer: Presentation (UI components)
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Coil for async image loading
import coil.compose.rememberAsyncImagePainter

// Domain model import
import com.milki.launcher.AppIconRequest
import com.milki.launcher.domain.model.AppInfo

/**
 * AppListItem displays a single row in the app list.
 * 
 * This is a reusable component that can be used anywhere you need to
 * display an app. It's completely self-contained and doesn't know about
 * the surrounding screen, dialog, or navigation.
 * 
 * What this component does:
 * - Displays the app icon (loaded asynchronously via Coil)
 * - Displays the app name
 * - Handles click events
 * 
 * What this component does NOT do:
 * - Know where the app data comes from
 * - Know what happens when clicked (just calls the callback)
 * - Manage any state
 * 
 * @param appInfo The app to display (from domain model)
 * @param onClick Called when user taps this item
 * @param modifier Optional modifier for external customization
 * 
 * Example usage:
 * ```kotlin
 * AppListItem(
 *     appInfo = app,
 *     onClick = { launchApp(app) }
 * )
 * ```
 */
@Composable
fun AppListItem(
    appInfo: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    /**
     * Surface provides a container with Material Design styling.
     * We use Color.Transparent so the background is determined by the parent
     * (could be in a list, dialog, etc.).
     */
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // clickable makes the entire row tappable
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        /**
         * Row arranges children horizontally.
         * Layout: [Icon] [12dp spacer] [App Name]
         * verticalAlignment centers items vertically in the row.
         */
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ===================================================================
            // APP ICON
            // ===================================================================
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
             * as text right next to it. Screen readers will read the name,
             * so adding a description for the icon would be redundant.
             */
            Image(
                painter = painter,
                contentDescription = null, // Decorative, name shown as text
                modifier = Modifier.size(40.dp)
            )
            
            // ===================================================================
            // SPACER
            // ===================================================================
            /**
             * Spacer creates empty space between the icon and text.
             * Using Spacer is preferred over padding for layout gaps
             * because it's more explicit and flexible.
             */
            Spacer(modifier = Modifier.width(12.dp))
            
            // ===================================================================
            // APP NAME
            // ===================================================================
            /**
             * Text displays the app's display name.
             * We use bodyLarge from the Material typography scale.
             * This ensures consistent text sizing across the app.
             */
            Text(
                text = appInfo.name,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
