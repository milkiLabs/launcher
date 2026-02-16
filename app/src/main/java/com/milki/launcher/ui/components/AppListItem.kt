/**
 * AppListItem.kt - Reusable component for displaying an app in a list
 */

package com.milki.launcher.ui.components

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
             * Display the app icon using our reusable AppIcon component.
             * 
             * AppIcon handles all the complexity of:
             * - Creating the AppIconRequest model
             * - Setting up the async image painter with Coil
             * - Loading the icon from PackageManager via AppIconFetcher
             * - Caching for performance
             * 
             * This avoids code duplication with AppGridItem and ensures
             * consistent icon loading behavior across the app.
             * 
             * We just need to provide the package name and size (40.dp for list items).
             */
            AppIcon(
                packageName = appInfo.packageName,
                size = 40.dp
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
