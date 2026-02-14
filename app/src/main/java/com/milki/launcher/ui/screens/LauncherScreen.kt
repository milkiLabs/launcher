/**
 * LauncherScreen.kt - Main home screen of the launcher
 * 
 * This file is part of the UI layer in Clean Architecture.
 * LauncherScreen is a "screen" composable that coordinates between
 * multiple components to create a complete user interface.
 * 
 * Unlike the smaller components (AppListItem, AppSearchDialog), a screen
 * composable often has access to more data and coordinates multiple pieces.
 * 
 * Location: ui/screens/LauncherScreen.kt
 * Architecture Layer: Presentation (Screens)
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

// Domain model import - screens work with domain models
import com.milki.launcher.domain.model.AppInfo

// UI components used by this screen
import com.milki.launcher.ui.components.AppSearchDialog

/**
 * LauncherScreen displays the main home screen of the launcher.
 * 
 * This screen shows:
 * 1. A black background covering the entire screen
 * 2. "Tap to search" hint text in the center
 * 3. The search dialog (conditionally shown based on showSearch)
 * 
 * The screen is responsible for:
 * - Laying out the background and hint
 * - Deciding whether to show the search dialog
 * - Passing callbacks down to child components
 * 
 * The screen is NOT responsible for:
 * - Managing the actual search state (that's in MainActivity)
 * - Loading app data (that's in ViewModel)
 * - Handling navigation (that's also in MainActivity)
 * 
 * This separation means we can:
 * - Test this screen independently
 * - Reuse this screen in different contexts if needed
 * - Change the background color without affecting search logic
 * 
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
    // ========================================================================
    // BACKGROUND LAYER
    // ========================================================================
    
    /**
     * Box is a layout container that stacks children.
     * We use it here to fill the entire screen with a black background.
     * 
     * contentAlignment = Alignment.Center centers the hint text.
     * 
     * clickable makes the entire background tappable.
     * When tapped, it opens the search dialog via onShowSearch callback.
     */
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onShowSearch() },
        contentAlignment = Alignment.Center
    ) {
        /**
         * Hint text shown when search is closed.
         * 
         * We use copy(alpha = 0.3f) to make it semi-transparent
         * so it's subtle but visible.
         */
        Text(
            text = "Tap to search",
            color = Color.White.copy(alpha = 0.3f),
            style = MaterialTheme.typography.bodyLarge
        )
    }
    
    // ========================================================================
    // SEARCH DIALOG (conditionally shown)
    // ========================================================================
    
    /**
     * Only show the search dialog when showSearch is true.
     * 
     * This is a conditional composable - when showSearch is false,
     * nothing is rendered here. When true, AppSearchDialog is composed.
     * 
     * We pass all the data and callbacks the dialog needs.
     * The dialog manages its own internal state (like focus) but
     * relies on the parent for the actual search query and app lists.
     */
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
