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
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.milki.launcher.domain.repository.ContactsRepository
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

    /**
     * Whether the homescreen empty-area long-press dropdown menu is currently open.
     *
     * This is controlled by LauncherScreen and read by onNewIntent() so the
     * home button can close the menu first before opening search.
     */
    private var isHomescreenMenuOpen by mutableStateOf(false)

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeHandlers()
        initializeBackButtonBehavior()

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
                        onOpenSettings = {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        },
                        isHomescreenMenuOpen = isHomescreenMenuOpen,
                        onHomescreenMenuOpenChange = { isOpen ->
                            isHomescreenMenuOpen = isOpen
                        },
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
                        },
                        onItemDroppedToHome = { item, position ->
                            /**
                             * User dropped an external payload onto the home grid.
                             *
                             * Behavior is centralized in HomeViewModel:
                             * - If item is not pinned yet: pin it first, then place at drop cell.
                             * - If item is already pinned: move existing icon to drop cell.
                             */
                            homeViewModel.pinOrMoveHomeItemToPosition(item, position)
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
     * Configures launcher-specific back button behavior.
     *
     * UX REQUIREMENT:
     * When the user is on the launcher home screen, pressing back should
     * keep them on home instead of navigating to previous apps / recents.
     *
     * IMPLEMENTATION NOTES:
     * - We register an always-enabled OnBackPressedCallback at Activity level.
     * - If search dialog is visible, back closes search (expected UX).
     * - If search is not visible, we intentionally consume back and do nothing.
     *
     * WHY ACTIVITY-LEVEL CALLBACK:
     * This guarantees the launcher remains a stable "root" surface.
     * Without this callback, Android's default back behavior may navigate
     * away from launcher into previous tasks.
     */
    private fun initializeBackButtonBehavior() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val uiState = searchViewModel.uiState.value

                    // If search is open, close it.
                    if (uiState.isSearchVisible) {
                        searchViewModel.hideSearch()
                        return
                    }

                    // Launcher home screen behavior:
                    // Consume back press and stay on home.
                }
            }
        )
    }

    /**
     * Initializes all handlers and sets up callbacks.
     */
    private fun initializeHandlers() {
        // Initialize permission handler
        permissionHandler = PermissionHandler(this, searchViewModel)
        permissionHandler.setup()
        
        // Initialize action executor
        actionExecutor = ActionExecutor(this, contactsRepository, homeViewModel, lifecycleScope)
        
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
                handleHomePressedAfterReturningToLauncher()
            } else {
                handleHomePressedWhileAlreadyOnLauncher()
            }
        }
    }

    /**
     * Handles home-button behavior when launcher is brought to foreground
     * from another app/task.
     *
     * UX RULE:
     * - Always reset transient overlays/search state.
     */
    private fun handleHomePressedAfterReturningToLauncher() {
        closeHomescreenMenu()
        searchViewModel.hideSearch()
    }

    /**
     * Handles home-button behavior when user is already on launcher.
     *
     * PRIORITY ORDER (explicit and deterministic):
     * 1. If homescreen long-press menu is open -> close it and consume this press.
     * 2. If search is hidden -> open search.
     * 3. If search is visible and query is non-empty -> clear query.
     * 4. If search is visible and query is empty -> hide search.
     *
     * This ordering prevents focus conflicts by ensuring overlay UI is dismissed
     * before search dialog transitions are attempted.
     */
    private fun handleHomePressedWhileAlreadyOnLauncher() {
        if (isHomescreenMenuOpen) {
            closeHomescreenMenu()
            return
        }

        val uiState = searchViewModel.uiState.value
        when {
            !uiState.isSearchVisible -> searchViewModel.showSearch()
            uiState.query.isNotEmpty() -> searchViewModel.clearQuery()
            else -> searchViewModel.hideSearch()
        }
    }

    /**
     * Closes the homescreen long-press dropdown menu.
     *
     * Keeping this as a dedicated helper avoids repeating direct state writes
     * in multiple intent/interaction paths.
     */
    private fun closeHomescreenMenu() {
        isHomescreenMenuOpen = false
    }
}
