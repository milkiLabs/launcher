/**
 * AppListItem.kt - Reusable component for displaying an app in a list
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
import coil.compose.rememberAsyncImagePainter
import com.milki.launcher.domain.model.AppIconRequest
import com.milki.launcher.domain.model.AppInfo

/**
 * AppListItem displays a single row in the app list.
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        // Layout: [Icon] [12dp spacer] [App Name]
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
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
             * as text right next to it. Screen readers will read the name,
             * so adding a description for the icon would be redundant.
             */
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
             // the app's display name.
            Text(
                text = appInfo.name,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
