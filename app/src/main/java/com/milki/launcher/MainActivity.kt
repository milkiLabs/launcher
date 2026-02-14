/**
 * MainActivity.kt - The main entry point of the Milki Launcher
 */

package com.milki.launcher

import android.content.Intent
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import androidx.lifecycle.viewmodel.compose.viewModel

import com.milki.launcher.domain.model.AppInfo

import com.milki.launcher.ui.screens.LauncherScreen

import com.milki.launcher.ui.theme.LauncherTheme

/**
 * MainActivity 
 * 
 * As a launcher app, this Activity has special characteristics:
 * 1. It uses launchMode="singleTask" in AndroidManifest.xml
 * 2. It has intent filters for MAIN, HOME, DEFAULT, and LAUNCHER categories
 * 3. It appears as a home screen option in Android settings

 TODO: don't make it a launcher, instead make another activity (settings) launcher intent
 * 
 */
class MainActivity : ComponentActivity() {
    
    // ========================================================================
    // ACTIVITY STATE
    // ========================================================================
    
    /**
     * Controls visibility of the search dialog.
     * 
     * This state is defined at the Activity level (not in Compose)
     * so it can be modified from multiple methods including onNewIntent().
     */
    private var showSearch by mutableStateOf(false)
    
    /**
     * Stores the current search query text.
     * 
     * Like showSearch, this is Activity-level state so it can be accessed from onNewIntent().
     */
    private var searchQuery by mutableStateOf("")
    
    // ========================================================================
    // ACTIVITY LIFECYCLE
    // ========================================================================
    
    /**
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
             * - Returns the existing ViewModel if it does (survives configuration changes)
             * - Automatically scopes it to this Activity
             */
            val viewModel: LauncherViewModel = viewModel()
            
            LauncherTheme {
                 //  LauncherScreen is our main UI composable.
                LauncherScreen(
                    showSearch = showSearch,
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
     * This happens when: User presses home button (sends MAIN action)
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
