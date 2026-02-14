/**
 * LauncherScreen.kt - Main home screen of the launcher
 */

package com.milki.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.ui.components.AppSearchDialog

/**
 * @param showSearch Whether the search dialog is currently visible
 * @param searchQuery Current text in the search field
 * @param onShowSearch Called when user taps the home screen background
 * @param onHideSearch Called when search dialog should close
 * @param onSearchQueryChange Called when user types in search field
 * @param installedApps List of all installed apps (from ViewModel)
 * @param recentApps List of recently launched apps (from ViewModel)
 * @param onLaunchApp Called when user selects an app from the list
 */
@Composable
fun LauncherScreen(
    showSearch: Boolean,
    searchQuery: String,
    onShowSearch: () -> Unit,
    onHideSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    installedApps: List<AppInfo>,
    recentApps: List<AppInfo>,
    onLaunchApp: (AppInfo) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onShowSearch() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Tap to search",
            color = Color.White.copy(alpha = 0.3f),
            style = MaterialTheme.typography.bodyLarge
        )
    }
    
      // Only show the search dialog when showSearch is true.
    if (showSearch) {
        AppSearchDialog(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            installedApps = installedApps,
            recentApps = recentApps,
            onDismiss = onHideSearch,
            onLaunchApp = onLaunchApp
        )
    }
}
