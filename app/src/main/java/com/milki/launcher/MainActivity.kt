/**
 * MainActivity.kt - The main entry point of the Milki Launcher
 */

package com.milki.launcher

import android.content.Intent
import androidx.activity.viewModels
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milki.launcher.handlers.PermissionHandler
import com.milki.launcher.presentation.search.ActionExecutor
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.presentation.search.SearchViewModel
import com.milki.launcher.ui.screens.LauncherScreen
import com.milki.launcher.ui.theme.LauncherTheme

/**
 * MainActivity - The launcher's home screen Activity.
 *
 * As a launcher, this Activity stays alive in the background when the user
 * opens other apps. When the user presses home again, onNewIntent() is called
 * instead of onCreate() (because we use singleTask launch mode).
 */
class MainActivity : ComponentActivity() {

    // ========================================================================
    // DEPENDENCIES
    // ========================================================================

    /**
     * SearchViewModel instance obtained from the AppContainer.
     *
     * Created lazily to ensure AppContainer is initialized first.
     * This ViewModel manages all search state (query, results, visibility)
     * and emits actions for the Activity to execute.
     */
    private val searchViewModel: SearchViewModel by viewModels {
        (application as LauncherApplication).container.searchViewModelFactory
    }

    /**
     * PermissionHandler - Manages all permission requests.
     *
     * This handler is responsible for:
     * - Registering permission launchers
     * - Checking and updating permission states
     * - Requesting permissions from the user
     *
     * Initialized in onCreate() after the Activity is ready.
     */
    private lateinit var permissionHandler: PermissionHandler

    /**
     * ActionExecutor - Executes search result actions.
     *
     * This executor is responsible for:
     * - Launching apps
     * - Opening URLs and web searches
     * - Opening YouTube searches
     * - Making phone calls (direct and via dialer)
     * - Opening files
     * - Handling permission-requiring actions
     *
     * Provided to composables via CompositionLocalProvider.
     *
     * Initialized in onCreate() after the Activity is ready.
     */
    private lateinit var actionExecutor: ActionExecutor

    // ========================================================================
    // HOME BUTTON STATE TRACKING
    // ========================================================================

    /**
     * Tracks whether the user is currently on the homescreen.
     *
     * This flag is crucial for distinguishing between two scenarios:
     *
     * SCENARIO 1: User is on homescreen, presses home button
     * - wasAlreadyOnHomescreen == true
     * - Action: Toggle search (show/hide/clear)
     *
     * SCENARIO 2: User is in another app, presses home button
     * - wasAlreadyOnHomescreen == false
     * - Action: Just ensure search is hidden (user wants to go home)
     *
     * LIFECYCLE FLOW:
     * 1. User opens app from homescreen → onStop() sets flag to false
     * 2. User presses home → onNewIntent() fires (flag is false)
     * 3. onResume() sets flag to true
     * 4. User presses home again → onNewIntent() fires (flag is true)
     *
     * The key insight is that onNewIntent() fires BEFORE onResume(),
     * so we can check the flag to know the prior state.
     */
    private var wasAlreadyOnHomescreen = false

    // ========================================================================
    // ACTIVITY LIFECYCLE
    // ========================================================================

    /**
     * Setup order matters:
     * 1. Initialize handlers (they need to register launchers before use)
     * 2. Set content (Compose UI)
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeHandlers()

        setContent {
            val uiState by searchViewModel.uiState.collectAsStateWithLifecycle()

            /**
             * Provide the action handler via CompositionLocal.
             * Any descendant composable can access it via LocalSearchActionHandler.current.
             */
            CompositionLocalProvider(
                LocalSearchActionHandler provides { action: SearchResultAction ->
                    actionExecutor.execute(action, permissionHandler::hasPermission)
                }
            ) {
                LauncherTheme {
                    LauncherScreen(
                        uiState = uiState,
                        onShowSearch = { searchViewModel.showSearch() },
                        onQueryChange = { searchViewModel.onQueryChange(it) },
                        onDismissSearch = { searchViewModel.hideSearch() }
                    )
                }
            }
        }
    }

    /**
     * Initializes the permission handler and action executor.
     *
     * This is separated from onCreate() for clarity and potential future
     * customization (e.g., passing settings repository to ActionExecutor).
     */
    private fun initializeHandlers() {
        // Initialize permission handler first (needed by action executor)
        permissionHandler = PermissionHandler(this, searchViewModel)
        permissionHandler.setup()
        
        // Get contacts repository from the container
        val container = (application as LauncherApplication).container
        val contactsRepository = container.contactsRepository
        
        // Initialize action executor
        actionExecutor = ActionExecutor(this, contactsRepository)
        
        // Set up executor callbacks
        actionExecutor.onRequestPermission = { permission ->
            when (permission) {
                android.Manifest.permission.READ_CONTACTS -> {
                    permissionHandler.requestContactsPermission()
                }
                android.Manifest.permission.CALL_PHONE -> {
                    permissionHandler.requestCallPermission()
                }
                else -> {
                    // Handle other permissions if needed
                }
            }
        }
        
        actionExecutor.onCloseSearch = {
            searchViewModel.hideSearch()
        }
        
        actionExecutor.onSaveRecentApp = { componentName ->
            searchViewModel.saveRecentApp(componentName)
        }
        
        // Connect permission handler to action executor
        permissionHandler.onCallPermissionResult = { granted ->
            actionExecutor.onPermissionResult(granted)
        }
    }

    /**
     * We update permission states here because:
     * - User might have changed permissions in Settings
     * - Permission state can change while app is in background
     *
     * We also set the homescreen flag AFTER onNewIntent would have fired.
     * Android calls lifecycle methods in this order:
     * onNewIntent → onStart → onResume
     *
     * So when onNewIntent checks the flag, it sees the OLD state (correct).
     * Then onResume sets it to true for the NEXT home press.
     */
    override fun onResume() {
        super.onResume()
        permissionHandler.updateStates()
        wasAlreadyOnHomescreen = true
    }

    /**
     * We clear the homescreen flag so that when the user presses home
     * and onNewIntent fires, we know they were in another app.
     */
    override fun onStop() {
        super.onStop()
        wasAlreadyOnHomescreen = false
    }

    // ========================================================================
    // HOME BUTTON HANDLING
    // ========================================================================

    /**
     * Called when the Activity receives a new Intent while already running.
     *
     * BEHAVIOR ON HOME BUTTON PRESS:
     * 1. Returning from another app (!wasAlreadyOnHomescreen) -> Hide search, show homescreen.
     * 2. Already on homescreen (wasAlreadyOnHomescreen) -> Toggle search or clear query.
     *
     * Intent checks (ACTION_MAIN + CATEGORY_HOME):
     * Strictly filters for actual Home button presses, ignoring other system 
     * events or shortcuts that might broadcast ACTION_MAIN alone.
     *
     * Note: onNewIntent fires BEFORE onResume, allowing the `wasAlreadyOnHomescreen` 
     * flag to accurately reflect the prior state.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (!wasAlreadyOnHomescreen) {
                // User is returning from another app
                // Ensure clean state - just show the homescreen
                searchViewModel.hideSearch()
            } else {
                // User is already on the homescreen
                // Toggle search visibility
                val uiState = searchViewModel.uiState.value
                when {
                    !uiState.isSearchVisible -> searchViewModel.showSearch()
                    uiState.query.isNotEmpty() -> searchViewModel.clearQuery()
                    else -> searchViewModel.hideSearch()
                }
            }
        }
    }
}
