/**
 * MainActivity.kt - The main entry point of the Milki Launcher
 *
 * This is the launcher's primary Activity. As a launcher app, it has special
 * characteristics defined in AndroidManifest.xml:
 * - launchMode="singleTask" (only one instance exists)
 * - Intent filters for HOME and DEFAULT categories
 * - Appears as a home screen option in Android settings
 *
 * RESPONSIBILITIES (kept minimal):
 * 1. UI composition - Setting up the Compose content
 * 2. Lifecycle management - Handling onResume/onStop for home button detection
 * 3. Home button handling - Detecting home presses and toggling search
 * 4. Delegating to handlers - PermissionHandler and ActionHandler do the real work
 *
 * ARCHITECTURE:
 * MainActivity is intentionally lean. It delegates to:
 * - PermissionHandler: All permission requests and state
 * - ActionHandler: All search action execution (launch app, open URL, etc.)
 * - SearchViewModel: State management and business logic
 *
 * This separation makes the code easier to:
 * - Understand (each class has one job)
 * - Test (handlers can be unit tested independently)
 * - Extend (add settings without touching MainActivity)
 */

package com.milki.launcher

import androidx.activity.viewModels
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.milki.launcher.handlers.ActionHandler
import com.milki.launcher.handlers.PermissionHandler
import com.milki.launcher.presentation.search.SearchAction
import com.milki.launcher.presentation.search.SearchViewModel
import com.milki.launcher.ui.screens.LauncherScreen
import com.milki.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.launch

/**
 * MainActivity - The launcher's home screen Activity.
 *
 * This Activity is launched when:
 * - User presses the home button (if our app is set as default launcher)
 * - User taps our app icon from the app drawer
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
     * ActionHandler - Executes search actions.
     *
     * This handler is responsible for:
     * - Launching apps
     * - Opening URLs and web searches
     * - Opening YouTube searches
     * - Making phone calls
     * - Opening files
     *
     * Initialized in onCreate() after the Activity is ready.
     */
    private lateinit var actionHandler: ActionHandler

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
     * Called when the Activity is first created.
     *
     * Setup order matters:
     * 1. Initialize handlers (they need to register launchers before use)
     * 2. Observe actions (so we can respond to ViewModel events)
     * 3. Set content (Compose UI)
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeHandlers()
        observeActions()

        setContent {
            val uiState by searchViewModel.uiState.collectAsStateWithLifecycle()

            LauncherTheme {
                LauncherScreen(
                    uiState = uiState,
                    onShowSearch = { searchViewModel.showSearch() },
                    onQueryChange = { searchViewModel.onQueryChange(it) },
                    onDismissSearch = { searchViewModel.hideSearch() },
                    onResultClick = { result -> searchViewModel.onResultClick(result) }
                )
            }
        }
    }

    /**
     * Initializes the permission and action handlers.
     *
     * This is separated from onCreate() for clarity and potential future
     * customization (e.g., passing settings repository to ActionHandler).
     */
    private fun initializeHandlers() {
        permissionHandler = PermissionHandler(this, searchViewModel)
        permissionHandler.setup()

        actionHandler = ActionHandler(this, searchViewModel)
    }

    /**
     * Called when the Activity becomes visible and interactive.
     *
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
     * Called when the Activity is no longer visible.
     *
     * This happens when:
     * - User opens another app
     * - User opens Settings
     * - User opens the recent apps list
     *
     * We clear the homescreen flag so that when the user presses home
     * and onNewIntent fires, we know they were in another app.
     */
    override fun onStop() {
        super.onStop()
        wasAlreadyOnHomescreen = false
    }

    // ========================================================================
    // ACTION OBSERVATION
    // ========================================================================

    /**
     * Observes SearchAction events from the ViewModel.
     *
     * Actions are one-time events (navigation, calls, etc.) that the
     * ViewModel emits when the user interacts with search results.
     *
     * WHY USE REPEATONLIFECYLE WITH STARTED?
     * - STARTED ensures we only collect when the Activity is visible
     * - Automatically cancels collection when Activity stops
     * - Restarts collection if Activity is resumed
     * - Prevents memory leaks and race conditions
     */
    private fun observeActions() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchViewModel.action.collect { action ->
                    handleAction(action)
                }
            }
        }
    }

    /**
     * Handles a SearchAction event.
     *
     * Actions are split into two categories:
     * 1. System actions - Delegated to ActionHandler (launch app, open URL, etc.)
     * 2. Permission actions - Delegated to PermissionHandler
     * 3. Local actions - Handled directly by ViewModel (close search, clear query)
     *
     * @param action The action to execute
     */
    private fun handleAction(action: SearchAction) {
        when (action) {
            is SearchAction.RequestContactsPermission -> {
                permissionHandler.requestContactsPermission()
            }
            is SearchAction.RequestFilesPermission -> {
                permissionHandler.requestFilesPermission()
            }
            is SearchAction.CloseSearch -> {
                searchViewModel.hideSearch()
            }
            is SearchAction.ClearQuery -> {
                searchViewModel.clearQuery()
            }
            else -> {
                // All other actions (launch app, open URL, call, file, etc.)
                // are handled by ActionHandler
                actionHandler.handle(action)
            }
        }
    }

    // ========================================================================
    // HOME BUTTON HANDLING
    // ========================================================================

    /**
     * Called when the Activity receives a new Intent while already running.
     *
     * This happens when the user presses the home button while our launcher
     * is set as the default. Because we use singleTask launch mode, Android
     * doesn't create a new Activity - it sends a new Intent to the existing one.
     *
     * BEHAVIOR:
     * 1. User returning from another app (wasAlreadyOnHomescreen == false):
     *    → Close search dialog. User just wants to go "home".
     *
     * 2. User already on homescreen (wasAlreadyOnHomescreen == true):
     *    → Toggle search dialog:
     *      - If search is hidden → Show it
     *      - If search has text → Clear the text
     *      - If search is empty → Hide it
     *
     * INTENT.ACTION_MAIN + CATEGORY_HOME check:
      * We check for ACTION_MAIN (the standard launch intent) AND CATEGORY_HOME
      * to ensure this is specifically a home button press. While ACTION_MAIN
      * is commonly used for the home button, other system events can also
      * trigger ACTION_MAIN intents. Adding the CATEGORY_HOME check provides
      * an extra layer of safety to ensure we only respond to actual home
      * button presses and not other system events that might accidentally
      * send ACTION_MAIN.
      *
      * Examples of events that might send ACTION_MAIN but NOT CATEGORY_HOME:
      * - App shortcuts created by other apps
      * - Certain system broadcasts
      * - Intent filters from other apps that match the action
      *
      * By requiring both ACTION_MAIN and CATEGORY_HOME, we ensure our
      * launcher behavior is only triggered when the user intentionally
      * presses the home button.
      *
      * IMPORTANT: onNewIntent fires BEFORE onResume!
      * That's why our wasAlreadyOnHomescreen flag works correctly.
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
