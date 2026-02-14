/**
 * MainActivity.kt - The main entry point and UI component of the Milki Launcher
 * 
 * This file contains the main Activity class and all the Compose UI functions that make up
 * the launcher's user interface. It's responsible for:
 * - Displaying the home screen
 * - Handling user interactions
 * - Managing the search dialog
 * - Launching apps when selected
 * - Coordinating with LauncherViewModel for data
 * 
 * For detailed documentation, see: docs/MainActivity.md
 */

package com.milki.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.milki.launcher.ui.theme.LauncherTheme

// ============================================================================
// MAIN ACTIVITY CLASS
// ============================================================================
/**
 * As a launcher app, this Activity has special characteristics:
 * 1. It uses launchMode="singleTask" in AndroidManifest.xml to ensure only one instance exists at a time
 * 2. It has intent filters for MAIN, HOME, DEFAULT, and LAUNCHER categories which makes it appear as a home screen option
 * 
 * The Activity manages two key state variables:
 * - showSearch: Controls whether the search dialog is visible
 * - searchQuery: Stores the current text in the search box
 * 
 * These state variables are defined at the Activity level (not in Compose)
 * so they survive the Activity lifecycle and can be modified from multiple
 * methods including onNewIntent().
 */
class MainActivity : ComponentActivity() {
    
    // ========================================================================
    // STATE VARIABLES
    // ========================================================================

    // Controls visibility of the search dialog.
    private var showSearch by mutableStateOf(false)
    
    
    // Stores the current search query text.
    private var searchQuery by mutableStateOf("")

    //  @param savedInstanceState Bundle containing saved state (not used here
    // since we use Compose state and ViewModel which survive rotation)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            // viewModel() creates or retrieves the LauncherViewModel instance.
            val viewModel: LauncherViewModel = viewModel()
            
            LauncherTheme {
                // LauncherScreen is our main composable that displays either:
                // 1. The black home screen with "Tap to search" hint
                // 2. The search dialog (when showSearch is true)
                LauncherScreen(
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    // Callback: Called when user taps home screen to open search
                    onShowSearch = { showSearch = true },
                    // Callback: Called when user dismisses search, We also clear the search query when closing
                    onHideSearch = { 
                        showSearch = false
                        searchQuery = ""
                    },
                    // Callback: Called when search text changes
                    // The 'it' parameter is the new text value
                    onSearchQueryChange = { searchQuery = it },

                    // Data: List of all installed apps from ViewModel
                    // This is a SnapshotStateList that updates automatically
                    installedApps = viewModel.installedApps,
                    // Data: List of recently launched apps from ViewModel
                    recentApps = viewModel.recentApps,
                    
                    // Callback: Called when user taps an app to launch it
                    // This handles starting the app and updating recent apps
                    onLaunchApp = { appInfo ->
                        // Close search dialog and clear query
                        searchQuery = ""
                        showSearch = false

                        // Launch the app using its stored Intent
                        // The ?.let ensures we only start if launchIntent exists
                        appInfo.launchIntent?.let { startActivity(it) }
                        
                        // Save this app to recent apps list via ViewModel
                        viewModel.saveRecentApp(appInfo.packageName)
                         
                        // TODO: does the activity gets destroyed? why 
                        
                    }
                )
            }
        }
    }

    /**
     * onNewIntent is called when the Activity receives a new Intent while
     * already running. This is different from onCreate which is called when
     * the Activity is first created.
     * 
     * @param intent The new Intent that was received
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        // Only handle MAIN action (which comes from home button press)
        if (intent.action == Intent.ACTION_MAIN) {
            when {
                // State 1: Search is currently closed
                // Action: Open the search dialog
                !showSearch -> showSearch = true
                // State 2: Search is open and has text
                // Action: Clear the search text (but keep dialog open)
                searchQuery.isNotEmpty() -> searchQuery = ""
                // State 3: Search is open and empty
                // Action: Close the search dialog
                else -> showSearch = false
            }
        }
    }
}

/**
 * LauncherScreen is the main UI composable that displays the home screen
 * and conditionally shows the search dialog.

 * @param showSearch Boolean controlling search dialog visibility
 * @param searchQuery Current search text
 * @param onShowSearch Callback when user taps home screen
 * @param onHideSearch Callback when user dismisses search
 * @param onSearchQueryChange Callback when search text changes
 * @param installedApps List of all installed apps for searching
 * @param recentApps List of recently launched apps
 * @param onLaunchApp Callback when user selects an app to launch
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
    
    //
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

    // When showSearch is true, we display the AppSearchDialog composable.
    // When false, nothing is rendered here.
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

/**
 * AppSearchDialog displays a modal dialog with:
 * - A search text field at the top
 * - A scrollable list of apps below
 * - Smart filtering based on search query
 * 
 * @param searchQuery Current search text
 * @param onSearchQueryChange Callback when text changes
 * @param installedApps List of all installed apps
 * @param recentApps List of recently launched apps
 * @param onDismiss Callback when dialog should close
 * @param onLaunchApp Callback when user selects an app
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
    
    // FocusRequester allows us to programmatically control focus.
    // We use this to automatically open the keyboard when the dialog opens.
    val focusRequester = remember { FocusRequester() }

    // ========================================================================
    // APP FILTERING LOGIC
    // ========================================================================
    // This is the core search functionality. We use remember() with keys
    // to cache the filtered list and only recompute when necessary.
    //
    // The keys are: searchQuery, installedApps, recentApps
    // This means the filtering only runs when:
    // 1. User types (searchQuery changes)
    // 2. App list is loaded/updated (installedApps changes)
    // 3. Recent apps change (recentApps changes)
    //
    // Without remember(), filtering would run on EVERY recomposition,
    // which would be hundreds of times and make the UI laggy.
    val filteredApps = remember(searchQuery, installedApps, recentApps) {
        
        // When search query is empty or whitespace only, show recent apps.
        if (searchQuery.isBlank()) {
            recentApps
        } else {
            // We implement a three-tier matching system:
            // 1. Exact matches (highest priority)
            // 2. Starts with matches (medium priority)
            // 3. Contains matches (lowest priority)
            
            val queryLower = searchQuery.trim().lowercase()
            
            // Create three lists for different match types
            val exactMatches = mutableListOf<AppInfo>()
            val startsWithMatches = mutableListOf<AppInfo>()
            val containsMatches = mutableListOf<AppInfo>()
            
            // Iterate through all installed apps
            installedApps.forEach { app ->
                // Check match type using pre-computed lowercase strings
                // AppInfo.nameLower and packageLower are lazy properties
                // that compute lowercase once and cache the result
                when {
                    // App name or package exactly equals the query
                    app.nameLower == queryLower || app.packageLower == queryLower -> {
                        exactMatches.add(app)
                    }
                    
                    // App name or package starts with the query
                    app.nameLower.startsWith(queryLower) || app.packageLower.startsWith(queryLower) -> {
                        startsWithMatches.add(app)
                    }
                    
                    // App name or package contains the query anywhere
                    app.nameLower.contains(queryLower) || app.packageLower.contains(queryLower) -> {
                        containsMatches.add(app)
                    }
                }
            }
            
            // Combine lists in priority order
            // This ensures exact matches appear first, then startsWith, then contains
            exactMatches + startsWithMatches + containsMatches
        }
    }

    // BackHandler intercepts the Android back button.
    // Without this, pressing back would exit the launcher entirely.
    // With this, pressing back closes the search dialog.
    // TODO: is this actually needed?
    BackHandler { onDismiss() }

    Dialog(
        // Called when user taps outside dialog or presses back
        onDismissRequest = onDismiss,
        
        // Configure dialog appearance and behavior
        properties = DialogProperties(
            // Don't use platform default width (which would be too narrow)
            // We'll specify our own width
            // TODO: why?
            usePlatformDefaultWidth = false,
            // Ensure dialog respects system windows (status bar, nav bar)
            decorFitsSystemWindows = true
        )
    ) {
        Surface(
            modifier = Modifier
                // Width is 90% of screen width
                .fillMaxWidth(0.9f)
                // Height is 80% of screen height
                .fillMaxHeight(0.8f)
                
                // Add padding for the on-screen keyboard (IME)
                // This ensures the dialog resizes when keyboard opens
                .imePadding()
                
                // Add padding for the navigation bar (gesture area)
                // Prevents dialog content from going under gesture navigation
                .navigationBarsPadding()
                
                // Add padding for the status bar (time, battery, etc.)
                // Ensures dialog doesn't overlap status bar
                .statusBarsPadding(),
            
            // Apply rounded corners. 
            shape = RoundedCornerShape(16.dp),
            
            // This adapts to light/dark mode automatically
            color = MaterialTheme.colorScheme.surface,
            
            // Add tonal elevation of 8dp
            // This creates a subtle visual hierarchy
            tonalElevation = 8.dp
        ) {
            // We use Column to stack: Search Field → App List
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ========================================================
                // SEARCH TEXT FIELD
                // ========================================================
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        // Attach focus requester for keyboard management
                        .focusRequester(focusRequester),
                    
                    placeholder = { Text("Search apps...") },
                    
                    singleLine = true,
                    
                    colors = OutlinedTextFieldDefaults.colors(),
                    
                    // Configure keyboard appearance and behavior
                    // TODO: for future delimieters, this shows the configured delimiter
                    // when delimiter is search, show the search instead of done?
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        // Show "Done" button on keyboard instead of newline
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    
                    // Configure keyboard actions
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        // When user presses "Done" button:
                        onDone = {
                            // Launch the first app in filtered list (if any)
                            filteredApps.firstOrNull()?.let { onLaunchApp(it) }
                        }
                    ),
                    
                    // Trailing icon (X button to clear search)
                    trailingIcon = {
                        // Only show clear button when there's text
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    }
                )

                // Show either the list of apps or an empty state message
                if (filteredApps.isEmpty()) {
                    // ----------------------------------------------------
                    // EMPTY STATE
                    // ----------------------------------------------------
                    // Displayed when no apps match the search or no recent apps
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            // Show different message based on whether searching
                            text = if (searchQuery.isBlank()) {
                                "No recent apps"  // Search empty, no recent apps
                            } else {
                                "No apps found"   // Searching but no matches
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    // ----------------------------------------------------
                    // APP LIST
                    // ----------------------------------------------------
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = filteredApps,
                            key = { app -> app.packageName },
                            contentType = { "app_item" }
                        ) { app ->
                            // AppListItem displays a single app row
                            AppListItem(
                                appInfo = app,
                                onClick = { onLaunchApp(app) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ========================================================================
    // AUTO-FOCUS EFFECT
    // ========================================================================
    // LaunchedEffect runs a coroutine when the composable enters composition.
    // The 'Unit' key means this only runs once (when dialog first opens).
    //
    // We use a small delay (10ms) to ensure the UI is ready, then request
    // focus on the text field. This automatically opens the keyboard.
    // TODO: find a cleaner way
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(10)
        focusRequester.requestFocus()
    }
}

/**
 * AppListItem displays a single row in the app list.
 * 
 * @param appInfo The AppInfo object containing app data (name, icon, etc.)
 * @param onClick Callback when user taps this item
 */
@Composable
fun AppListItem(
    appInfo: AppInfo,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        
        // Transparent background so we don't override list background
        color = Color.Transparent
    ) {
        // Layout: [Icon] [Spacer] [App Name]
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ================================================================
            // APP ICON
            // ================================================================
            // Load the app icon using Coil with our custom AppIconFetcher.
            // rememberAsyncImagePainter creates a painter that loads the image
            // asynchronously and caches it for performance.
            
            // We pass an AppIconRequest which contains the package name.
            // Our custom AppIconFetcher (registered in LauncherApplication)
            // knows how to load app icons from the PackageManager.
            val painter = rememberAsyncImagePainter(
                model = AppIconRequest(appInfo.packageName)
            )
            
            // Display the loaded icon using Image composable
            Image(
                // The painter handles the actual image loading and display
                painter = painter,
                
                // No content description needed since the app name is displayed
                // Icons are decorative in this context
                contentDescription = null,
                
                modifier = Modifier.size(40.dp)
            )
            
            // Add space between icon and app name
            Spacer(modifier = Modifier.width(12.dp))
            
            // Display the app's display name
            Text(
                text = appInfo.name,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
