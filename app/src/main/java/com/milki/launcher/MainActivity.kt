/**
 * MainActivity.kt - The main entry point of the Milki Launcher
 * 
 * This file contains the main Activity class which is the entry point of the app.
 * After SOLID refactoring, this class is drastically simplified:
 * 
 * BEFORE (524 lines):
 * - Activity lifecycle management
 * - UI state management
 * - 3 composable functions (LauncherScreen, AppSearchDialog, AppListItem)
 * - App filtering logic
 * - Navigation handling
 * 
 * AFTER (~85 lines):
 * - Activity lifecycle only (onCreate, onNewIntent)
 * - Minimal UI state (showSearch, searchQuery)
 * - Delegates ALL UI to LauncherScreen composable
 * - Delegates ALL data to LauncherViewModel
 * 
 * This follows the Single Responsibility Principle perfectly.
 * 
 * Architecture:
 * - Presentation Layer: MainActivity, LauncherScreen, UI components
 * - ViewModel Layer: LauncherViewModel (mediates between UI and data)
 * - Domain Layer: AppRepository interface, AppInfo model
 * - Data Layer: AppRepositoryImpl (PackageManager, DataStore)
 * 
 * For detailed documentation, see: docs/MainActivity.md
 */

package com.milki.launcher

// Android imports
import android.content.Intent
import android.os.Bundle

// Activity base class
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

// Compose State - for Activity-level state
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// ViewModel integration
import androidx.lifecycle.viewmodel.compose.viewModel

// Domain model - we only need this for the launchApp function
import com.milki.launcher.domain.model.AppInfo

// UI Screen - all UI is delegated here
import com.milki.launcher.ui.screens.LauncherScreen

// Theme
import com.milki.launcher.ui.theme.LauncherTheme

/**
 * MainActivity - The entry point and Activity container.
 * 
 * As a launcher app, this Activity has special characteristics:
 * 1. It uses launchMode="singleTask" in AndroidManifest.xml
 * 2. It has intent filters for MAIN, HOME, DEFAULT, and LAUNCHER categories
 * 3. It appears as a home screen option in Android settings
 * 
 * Key responsibilities after refactoring:
 * 1. Set up the Compose UI in onCreate()
 * 2. Manage minimal Activity-level state (showSearch, searchQuery)
 * 3. Handle home button presses via onNewIntent()
 * 4. Launch apps when user selects them
 * 
 * What it does NOT do:
 * - Define UI composables (delegated to LauncherScreen)
 * - Load app data (delegated to LauncherViewModel)
 * - Filter apps (delegated to AppSearchDialog)
 * - Manage recent apps (delegated to Repository via ViewModel)
 */
class MainActivity : ComponentActivity() {
    
    // ========================================================================
    // ACTIVITY STATE
    // ========================================================================
    
    /**
     * Controls visibility of the search dialog.
     * 
     * This state is defined at the Activity level (not in Compose)
     * so it survives configuration changes and can be modified from
     * multiple methods including onNewIntent().
     * 
     * We use mutableStateOf() which creates an observable Compose state.
     * When this value changes, Compose automatically recomposes.
     */
    private var showSearch by mutableStateOf(false)
    
    /**
     * Stores the current search query text.
     * 
     * Like showSearch, this is Activity-level state so it survives
     * configuration changes and can be accessed from onNewIntent().
     */
    private var searchQuery by mutableStateOf("")
    
    // ========================================================================
    // ACTIVITY LIFECYCLE
    // ========================================================================
    
    /**
     * onCreate is called when the Activity is first created.
     * 
     * This is where we:
     * 1. Set up the Compose UI using setContent()
     * 2. Get the ViewModel instance
     * 3. Delegate everything to LauncherScreen composable
     * 
     * @param savedInstanceState Bundle with saved state (not used - we use Compose state)
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            /**
             * Get or create the LauncherViewModel instance.
             * 
             * viewModel() is a Compose function that:
             * - Creates a new ViewModel if one doesn't exist
             * - Returns the existing ViewModel if it does (survives rotation!)
             * - Automatically scopes it to this Activity
             */
            val viewModel: LauncherViewModel = viewModel()
            
            /**
             * Apply the app's Material Design theme.
             * Theme is defined in ui/theme/Theme.kt
             */
            LauncherTheme {
                /**
                 * LauncherScreen is our main UI composable.
                 * 
                 * We pass all state and callbacks as parameters.
                 * This is called "state hoisting" - state lives in the parent
                 * (MainActivity) and is passed down to children.
                 * 
                 * Benefits:
                 * - LauncherScreen is reusable (doesn't depend on Activity)
                 * - State is centralized and easy to track
                 * - Testing is easier (can pass mock state)
                 */
                LauncherScreen(
                    // State: Whether search dialog is visible
                    showSearch = showSearch,
                    
                    // State: Current search text
                    searchQuery = searchQuery,
                    
                    // Callback: User tapped home screen to open search
                    onShowSearch = { showSearch = true },
                    
                    // Callback: User dismissed search (or pressed back)
                    onHideSearch = { 
                        showSearch = false
                        searchQuery = "" // Clear query when closing
                    },
                    
                    // Callback: User typed in search field
                    onSearchQueryChange = { searchQuery = it },
                    
                    // Data: All installed apps from ViewModel
                    installedApps = viewModel.installedApps,
                    
                    // Data: Recently launched apps from ViewModel
                    recentApps = viewModel.recentApps,
                    
                    // Callback: User selected an app to launch
                    onLaunchApp = { appInfo ->
                        launchApp(appInfo, viewModel)
                    }
                )
            }
        }
    }
    
    // ========================================================================
    // INTENT HANDLING
    // ========================================================================
    
    /**
     * onNewIntent is called when the Activity receives a new Intent
     * while already running (not being created fresh).
     * 
     * This happens when:
     * - User presses home button (sends MAIN action)
     * - User switches to this app from recents
     * 
     * We use this to toggle the search dialog when home is pressed.
     * 
     * @param intent The new Intent that was received
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        // Only handle MAIN action (which comes from home button)
        if (intent.action == Intent.ACTION_MAIN) {
            when {
                // CASE 1: Search is currently closed
                // ACTION: Open the search dialog
                !showSearch -> showSearch = true
                
                // CASE 2: Search is open and has text
                // ACTION: Clear the search text (but keep dialog open)
                searchQuery.isNotEmpty() -> searchQuery = ""
                
                // CASE 3: Search is open and empty
                // ACTION: Close the search dialog
                else -> showSearch = false
            }
        }
    }
    
    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================
    
    /**
     * Launch an app and update recent apps.
     * 
     * This method extracts the app launching logic into a separate
     * private method for clarity. It handles:
     * 1. Clearing search state
     * 2. Closing search dialog
     * 3. Starting the app activity
     * 4. Saving to recent apps via ViewModel
     * 
     * @param appInfo The app to launch
     * @param viewModel The ViewModel for saving recent apps
     */
    private fun launchApp(appInfo: AppInfo, viewModel: LauncherViewModel) {
        // Clear search state
        searchQuery = ""
        showSearch = false
        
        // Launch the app using its stored Intent
        // ?.let ensures we only start if launchIntent exists
        appInfo.launchIntent?.let { startActivity(it) }
        
        // Save this app to recent apps list via ViewModel
        // The ViewModel will update the Repository, which updates DataStore,
        // which triggers a Flow emission, which updates the UI automatically!
        viewModel.saveRecentApp(appInfo.packageName)
    }
}
