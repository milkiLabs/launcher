/**
 * MainActivity.kt - The main entry point of the Milki Launcher
 *
 * This Activity serves as the launcher's home screen. It:
 * - Displays the pinned items grid
 * - Handles search functionality
 * - Manages permission requests
 * - Coordinates between ViewModels and UI
 *
 * ARCHITECTURE:
 * This Activity follows the MVVM pattern:
 * - ViewModels provide state via StateFlow
 * - UI renders state via Compose
 * - User actions flow through callbacks and LocalSearchActionHandler
 *
 * The Activity is kept minimal - most logic is in ViewModels and UseCases.
 */

package com.milki.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.HomeRepository
import com.milki.launcher.handlers.PermissionHandler
import com.milki.launcher.presentation.home.HomeViewModel
import com.milki.launcher.presentation.search.ActionExecutor
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.presentation.search.SearchViewModel
import com.milki.launcher.ui.screens.LauncherScreen
import com.milki.launcher.ui.screens.openPinnedItem
import com.milki.launcher.ui.theme.LauncherTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * MainActivity - The launcher's home screen Activity.
 *
 * This is the main entry point when the user presses the home button.
 * It displays the pinned items grid and provides access to search functionality.
 */
class MainActivity : ComponentActivity() {

    // ========================================================================
    // DEPENDENCY INJECTION
    // ========================================================================

    /**
     * SearchViewModel handles all search-related state and logic.
     * Provided by Koin DI.
     */
    private val searchViewModel: SearchViewModel by viewModel()

    /**
     * HomeViewModel handles the home screen pinned items state.
     * Provided by Koin DI.
     */
    private val homeViewModel: HomeViewModel by viewModel()

    /**
     * ContactsRepository for contact-related operations.
     * Provided by Koin DI.
     */
    private val contactsRepository: ContactsRepository by inject()

    /**
     * HomeRepository for pinned items persistence.
     * Provided by Koin DI.
     */
    private val homeRepository: HomeRepository by inject()

    // ========================================================================
    // HANDLERS
    // ========================================================================

    /**
     * PermissionHandler manages runtime permission requests.
     * Initialized in onCreate.
     */
    private lateinit var permissionHandler: PermissionHandler

    /**
     * ActionExecutor handles all SearchResultAction implementations.
     * Initialized in onCreate.
     */
    private lateinit var actionExecutor: ActionExecutor

    /**
     * Tracks whether we were already on the homescreen before onPause.
     * Used to determine behavior when home button is pressed.
     */
    private var wasAlreadyOnHomescreen = false

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeHandlers()

        setContent {
            // Collect state from ViewModels
            val searchUiState by searchViewModel.uiState.collectAsStateWithLifecycle()
            val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current

            /**
             * Provide the action handler via CompositionLocal.
             * This allows any child composable to emit actions without prop drilling.
             */
            CompositionLocalProvider(
                LocalSearchActionHandler provides { action: SearchResultAction ->
                    actionExecutor.execute(action, permissionHandler::hasPermission)
                }
            ) {
                LauncherTheme {
                    LauncherScreen(
                        searchUiState = searchUiState,
                        homeUiState = homeUiState,
                        onQueryChange = { searchViewModel.onQueryChange(it) },
                        onDismissSearch = { searchViewModel.hideSearch() },
                        onPinnedItemClick = { item ->
                            openPinnedItem(item, context)
                        },
                        onPinnedItemLongPress = { item ->
                            /**
                             * Long press without drag shows the action menu.
                             * The menu is shown by the PinnedItem composable itself,
                             * so we don't need to do anything here.
                             * This callback exists for potential future use (e.g., haptic feedback).
                             */
                        },
                        onPinnedItemMove = { itemId, newPosition ->
                            /**
                             * User has dragged an item to a new position.
                             * Delegate to HomeViewModel to update the position.
                             */
                            homeViewModel.moveItemToPosition(itemId, newPosition)
                        }
                    )
                }
            }
        }
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initializes all handlers and sets up callbacks.
     */
    private fun initializeHandlers() {
        // Initialize permission handler
        permissionHandler = PermissionHandler(this, searchViewModel)
        permissionHandler.setup()
        
        // Initialize action executor
        actionExecutor = ActionExecutor(this, contactsRepository, homeRepository)
        
        // Set up permission request callback
        actionExecutor.onRequestPermission = { permission ->
            when (permission) {
                android.Manifest.permission.READ_CONTACTS -> {
                    permissionHandler.requestContactsPermission()
                }
                android.Manifest.permission.CALL_PHONE -> {
                    permissionHandler.requestCallPermission()
                }
                android.Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE -> {
                    permissionHandler.requestFilesPermission()
                }
            }
        }
        
        // Set up search close callback
        actionExecutor.onCloseSearch = {
            searchViewModel.hideSearch()
        }
        
        // Set up recent app save callback
        actionExecutor.onSaveRecentApp = { componentName ->
            searchViewModel.saveRecentApp(componentName)
        }
        
        // Set up permission result callback
        permissionHandler.onCallPermissionResult = { granted ->
            actionExecutor.onPermissionResult(granted)
        }
    }

    // ========================================================================
    // ACTIVITY LIFECYCLE CALLBACKS
    // ========================================================================

    override fun onResume() {
        super.onResume()
        permissionHandler.updateStates()
        wasAlreadyOnHomescreen = true
    }

    override fun onStop() {
        super.onStop()
        wasAlreadyOnHomescreen = false
    }

    /**
     * Handles new Intents sent to this Activity.
     *
     * This is called when:
     * - User presses home button while launcher is running
     * - Another app launches an Intent targeting this Activity
     *
     * HOME BUTTON BEHAVIOR:
     * - If not already on homescreen: Hide search
     * - If already on homescreen with search open: Toggle search state
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (!wasAlreadyOnHomescreen) {
                searchViewModel.hideSearch()
            } else {
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
