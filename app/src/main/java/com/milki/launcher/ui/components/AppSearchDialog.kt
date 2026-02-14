/**
 * AppSearchDialog.kt - Full-featured search dialog component
 * 
 * This file is part of the UI layer in Clean Architecture.
 * This component is self-contained and manages the search dialog UI,
 * including the search field, app list, filtering logic, and keyboard handling.
 * 
 * The dialog receives data and callbacks from the parent but manages
 * its own internal state (like focus management and filtering).
 * 
 * Location: ui/components/AppSearchDialog.kt
 * Architecture Layer: Presentation (UI components)
 */

package com.milki.launcher.ui.components

// Android & Compose imports
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Domain model import
import com.milki.launcher.domain.model.AppInfo

// Coroutines for async operations
import kotlinx.coroutines.delay

/**
 * AppSearchDialog displays a modal dialog with app search functionality.
 * 
 * Features:
 * - Search text field with auto-focus and keyboard handling
 * - Smart filtering (exact match > startsWith > contains)
 * - Shows recent apps when search is empty
 * - Empty state when no apps match
 * - "Done" button on keyboard launches first app
 * - Back button closes the dialog
 * 
 * This component is self-contained. It receives data via parameters
 * and notifies the parent of user actions via callbacks.
 * 
 * @param searchQuery Current search text (controlled from parent)
 * @param onSearchQueryChange Called when user types (parent updates state)
 * @param installedApps List of all installed apps for searching
 * @param recentApps List of recently launched apps
 * @param onDismiss Called when dialog should close
 * @param onLaunchApp Called when user selects an app to launch
 */
@Composable
fun AppSearchDialog(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    installedApps: List<AppInfo>,
    recentApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit
) {
    // ========================================================================
    // STATE MANAGEMENT
    // ========================================================================
    
    /**
     * FocusRequester allows us to programmatically control focus.
     * We use this to automatically open the keyboard when dialog opens.
     */
    val focusRequester = remember { FocusRequester() }
    
    /**
     * Filtered apps list using remember for performance.
     * 
     * remember caches the result and only recomputes when keys change.
     * Keys are: searchQuery, installedApps, recentApps
     * 
     * This prevents filtering from running on every recomposition
     * (which would happen hundreds of times and cause lag).
     */
    val filteredApps = remember(searchQuery, installedApps, recentApps) {
        filterApps(searchQuery, installedApps, recentApps)
    }
    
    // ========================================================================
    // BACK BUTTON HANDLING
    // ========================================================================
    
    /**
     * BackHandler intercepts the Android back button.
     * Without this, pressing back would exit the launcher entirely.
     * With this, pressing back closes the search dialog.
     */
    BackHandler { onDismiss() }
    
    // ========================================================================
    // DIALOG UI
    // ========================================================================
    
    Dialog(
        // Called when user taps outside dialog or presses back
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            // Don't use platform default width (too narrow)
            usePlatformDefaultWidth = false,
            // Ensure dialog respects system windows (status bar, nav bar)
            decorFitsSystemWindows = true
        )
    ) {
        /**
         * Surface provides Material Design container with styling.
         * It handles background color, elevation, and shape.
         */
        Surface(
            modifier = Modifier
                // Width: 90% of screen
                .fillMaxWidth(0.9f)
                // Height: 80% of screen
                .fillMaxHeight(0.8f)
                // Pad for on-screen keyboard
                .imePadding()
                // Pad for navigation bar (gesture area)
                .navigationBarsPadding()
                // Pad for status bar (time, battery)
                .statusBarsPadding(),
            // Rounded corners for modern look
            shape = RoundedCornerShape(16.dp),
            // Use theme surface color (adapts to light/dark mode)
            color = MaterialTheme.colorScheme.surface,
            // Elevation creates shadow/depth
            tonalElevation = 8.dp
        ) {
            /**
             * Column stacks the search field and app list vertically.
             */
            Column(modifier = Modifier.fillMaxSize()) {
                
                // ============================================================
                // SEARCH TEXT FIELD
                // ============================================================
                SearchTextField(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    focusRequester = focusRequester,
                    onLaunchFirstApp = {
                        // Launch first app when "Done" pressed on keyboard
                        filteredApps.firstOrNull()?.let { onLaunchApp(it) }
                    }
                )
                
                // ============================================================
                // CONTENT AREA (App List or Empty State)
                // ============================================================
                if (filteredApps.isEmpty()) {
                    // No apps to show
                    EmptyState(searchQuery = searchQuery)
                } else {
                    // Show scrollable list of apps
                    AppList(
                        apps = filteredApps,
                        onLaunchApp = onLaunchApp
                    )
                }
            }
        }
    }
    
    // ========================================================================
    // AUTO-FOCUS EFFECT
    // ========================================================================
    
    /**
     * LaunchedEffect runs a coroutine when the composable enters composition.
     * The 'Unit' key means this runs once (when dialog first opens).
     * 
     * We add a small delay to ensure the UI is fully rendered,
     * then request focus on the text field to open the keyboard.
     */
    LaunchedEffect(Unit) {
        delay(10)
        focusRequester.requestFocus()
    }
}

/**
 * SearchTextField - The search input field at the top of the dialog.
 * 
 * This is a private composable extracted for cleaner code organization.
 * Being private means it can only be used within this file.
 * 
 * @param searchQuery Current text value
 * @param onSearchQueryChange Callback when text changes
 * @param focusRequester Used to auto-focus the field
 * @param onLaunchFirstApp Callback when "Done" pressed on keyboard
 */
@Composable
private fun SearchTextField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onLaunchFirstApp: () -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            // Attach focus requester for programmatic focus control
            .focusRequester(focusRequester),
        
        // Placeholder text shown when empty
        placeholder = { Text("Search apps...") },
        
        // Prevent multiline input
        singleLine = true,
        
        // Keyboard configuration
        keyboardOptions = KeyboardOptions(
            // Show "Done" button instead of newline
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            // When "Done" pressed, launch first matching app
            onDone = { onLaunchFirstApp() }
        ),
        
        // Clear button (X) shown when there's text
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search"
                    )
                }
            }
        }
    )
}

/**
 * AppList - Displays a scrollable list of apps.
 * 
 * Uses LazyColumn for efficient scrolling with many items.
 * LazyColumn only composes visible items, saving memory.
 * 
 * @param apps List of apps to display (already filtered)
 * @param onLaunchApp Callback when user taps an app
 */
@Composable
private fun AppList(
    apps: List<AppInfo>,
    onLaunchApp: (AppInfo) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        /**
         * items() creates a list item for each app.
         * 
         * key = { app.packageName } helps Compose identify items efficiently
         * contentType = { "app_item" } helps Compose optimize recomposition
         */
        items(
            items = apps,
            key = { it.packageName },
            contentType = { "app_item" }
        ) { app ->
            // Reuse our AppListItem component
            AppListItem(
                appInfo = app,
                onClick = { onLaunchApp(app) }
            )
        }
    }
}

/**
 * EmptyState - Displayed when no apps match the search or no recent apps.
 * 
 * Shows different messages based on whether user is searching or not.
 * 
 * @param searchQuery Current search text (determines which message to show)
 */
@Composable
private fun EmptyState(searchQuery: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (searchQuery.isBlank()) {
                "No recent apps"  // Search empty, no recent apps to show
            } else {
                "No apps found"   // Searching but no matches
            },
            // Use secondary color for less emphasis than primary content
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Filter apps using a smart matching algorithm.
 * 
 * This is a pure function with no side effects - given the same inputs,
 * it always returns the same output. Pure functions are:
 * - Easy to test
 * - Easy to reason about
 * - Thread-safe
 * - Can be cached/memoized
 * 
 * Priority order for results:
 * 1. Exact matches (highest priority)
 * 2. Starts with matches (medium priority)
 * 3. Contains matches (lowest priority)
 * 
 * When search is empty, returns recent apps instead of all apps.
 * 
 * @param searchQuery The user's search text
 * @param installedApps All installed apps (for when user is searching)
 * @param recentApps Recently launched apps (for when search is empty)
 * @return Filtered and prioritized list of apps
 */
private fun filterApps(
    searchQuery: String,
    installedApps: List<AppInfo>,
    recentApps: List<AppInfo>
): List<AppInfo> {
    // When search is empty, show recent apps
    if (searchQuery.isBlank()) {
        return recentApps
    }
    
    val queryLower = searchQuery.trim().lowercase()
    
    // Three lists for different match priorities
    val exactMatches = mutableListOf<AppInfo>()
    val startsWithMatches = mutableListOf<AppInfo>()
    val containsMatches = mutableListOf<AppInfo>()
    
    // Categorize each app based on match type
    installedApps.forEach { app ->
        when {
            // Exact match: name or package equals query exactly
            app.nameLower == queryLower || app.packageLower == queryLower -> {
                exactMatches.add(app)
            }
            // Starts with: name or package starts with query
            app.nameLower.startsWith(queryLower) || app.packageLower.startsWith(queryLower) -> {
                startsWithMatches.add(app)
            }
            // Contains: name or package contains query anywhere
            app.nameLower.contains(queryLower) || app.packageLower.contains(queryLower) -> {
                containsMatches.add(app)
            }
        }
    }
    
    // Combine in priority order using the + operator (creates new list)
    return exactMatches + startsWithMatches + containsMatches
}
