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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.SwipeUpAction
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.SettingsRepository
import com.milki.launcher.handlers.PermissionHandler
import com.milki.launcher.presentation.drawer.AppDrawerViewModel
import com.milki.launcher.presentation.home.HomeViewModel
import com.milki.launcher.presentation.main.HomeButtonPolicy
import com.milki.launcher.presentation.main.PermissionRequestCoordinator
import com.milki.launcher.presentation.main.SearchSessionController
import com.milki.launcher.presentation.search.ActionExecutor
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.presentation.search.SearchViewModel
import com.milki.launcher.ui.screens.DrawerActions
import com.milki.launcher.ui.screens.FolderActions
import com.milki.launcher.ui.screens.HomeActions
import com.milki.launcher.ui.screens.LauncherScreen
import com.milki.launcher.ui.screens.LauncherActions
import com.milki.launcher.ui.screens.MenuActions
import com.milki.launcher.ui.screens.SearchActions
import com.milki.launcher.ui.screens.WidgetActions
import com.milki.launcher.ui.screens.openPinnedItem
import com.milki.launcher.ui.theme.LauncherTheme
import com.milki.launcher.domain.model.HomeItem
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
     * AppDrawerViewModel manages drawer app list + sort mode state.
     */
    private val appDrawerViewModel: AppDrawerViewModel by viewModel()

    /**
     * ContactsRepository for contact-related operations.
     * Provided by Koin DI.
     */
    private val contactsRepository: ContactsRepository by inject()

    /**
     * Settings repository used for reading swipe-up action configuration.
     */
    private val settingsRepository: SettingsRepository by inject()

    /**
     * WidgetHostManager wraps Android's AppWidgetHost framework.
     *
     * It manages the lifecycle of hosted widgets (startListening/stopListening),
     * allocates widget IDs, binds widget providers, and creates widget views.
     * Provided as a singleton by Koin DI.
     */
    private val widgetHostManager: WidgetHostManager by inject()

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
     * Coordinator that wires permission request flow between ActionExecutor and PermissionHandler.
     */
    private lateinit var permissionRequestCoordinator: PermissionRequestCoordinator

    /**
     * Stateless policy used to decide what a home-button press should do.
     */
    private val homeButtonPolicy = HomeButtonPolicy()

    /**
     * Controller that applies search/menu state transitions chosen by policy.
     */
    private lateinit var searchSessionController: SearchSessionController

    /**
        * Tracks whether launcher is currently considered the active homescreen surface.
        *
        * Lifecycle semantics:
        * - Set to true in onResume() when launcher enters foreground.
        * - Set to false in onStop() when launcher leaves foreground.
        *
        * This flag feeds HomeButtonPolicy so repeated home-button presses while already
        * on launcher can trigger search/menu behavior, while first return to launcher
        * resets transient UI instead.
     */
    private var wasAlreadyOnHomescreen = false

    /**
     * Whether the homescreen empty-area long-press dropdown menu is currently open.
     *
     * This is controlled by LauncherScreen and read by onNewIntent() so the
     * home button can close the menu first before opening search.
     */
    private var isHomescreenMenuOpen by mutableStateOf(false)

    /**
     * Whether the app drawer full-screen overlay is visible.
     */
    private var isAppDrawerOpen by mutableStateOf(false)

    /**
     * Whether the widget picker bottom sheet is visible.
     *
     * Set to true when the user selects "Widgets" from the long-press menu.
     * The widget picker bottom sheet displays all available widgets grouped by app.
     */
    private var isWidgetPickerOpen by mutableStateOf(false)

    /**
     * Activity result launcher for the widget bind permission dialog.
     *
     * WHY THIS LAUNCHER EXISTS:
     * When the user tries to add a widget, the system may require explicit permission
     * to bind that widget to our launcher. If AppWidgetManager.bindAppWidgetIdIfAllowed()
     * returns false, we launch the system's permission dialog using this launcher.
     *
     * The dialog shows something like "Allow Milki Launcher to use this widget?"
     * and returns RESULT_OK or RESULT_CANCELED.
     *
     * FLOW:
     * 1. User drags widget from picker
     * 2. We allocate a widget ID and try to bind
     * 3. If bind fails → launch this permission dialog  
     * 4. If user grants → continue with widget placement
     * 5. If user denies → deallocate the widget ID, show toast
     */
    private lateinit var widgetBindLauncher: ActivityResultLauncher<Intent>

    /**
     * Activity result launcher for widget configuration activities.
     *
     * WHY THIS LAUNCHER EXISTS:
     * Some widgets have a configuration activity (e.g., a clock widget that lets
     * the user pick a style, or a weather widget that asks for a location).
     * After binding the widget, we check if it has a configure activity and launch it.
     *
     * The configuration activity returns RESULT_OK when the user finishes configuration,
     * or RESULT_CANCELED if they back out.
     *
     * FLOW:
     * 1. Widget is bound successfully
     * 2. We check providerInfo.configure — if non-null, launch this activity
     * 3. If user completes config → persist widget to DataStore
     * 4. If user cancels → deallocate widget ID, don't place widget
     */
    private lateinit var widgetConfigureLauncher: ActivityResultLauncher<Intent>

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        searchSessionController = SearchSessionController(searchViewModel)
        initializeHandlers()
        initializeBackButtonBehavior()
        initializeWidgetLaunchers()

        setContent {
            // Collect state from ViewModels
            val searchUiState by searchViewModel.uiState.collectAsStateWithLifecycle()
            val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
            val appDrawerUiState by appDrawerViewModel.uiState.collectAsStateWithLifecycle()
            val launcherSettings by settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = LauncherSettings()
            )
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
                        actions = LauncherActions(
                            search = SearchActions(
                                onQueryChange = { searchViewModel.onQueryChange(it) },
                                onDismissSearch = { searchViewModel.hideSearch() }
                            ),
                            menu = MenuActions(
                                onOpenSettings = {
                                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                                },
                                onHomescreenMenuOpenChange = { isOpen ->
                                    isHomescreenMenuOpen = isOpen
                                }
                            ),
                            drawer = DrawerActions(
                                onAppDrawerOpenChange = { isOpen ->
                                    isAppDrawerOpen = isOpen
                                },
                                onDrawerSortModeSelected = appDrawerViewModel::setSortMode
                            ),
                            home = HomeActions(
                                onHomeSwipeUp = {
                                    if (launcherSettings.swipeUpAction == SwipeUpAction.OPEN_APP_DRAWER) {
                                        isHomescreenMenuOpen = false
                                        searchViewModel.hideSearch()
                                        homeViewModel.closeFolder()
                                        isAppDrawerOpen = true
                                    }
                                },
                                onPinnedItemClick = { item ->
                                    // Folder icons open the FolderPopupDialog.
                                    // All other item types are launched directly.
                                    if (item is HomeItem.FolderItem) {
                                        homeViewModel.openFolder(item.id)
                                    } else {
                                        openPinnedItem(item, context)
                                    }
                                },
                                onPinnedItemLongPress = { _ ->
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
                            ),
                            folder = FolderActions(
                                onCreateFolder = { item1, item2, atPosition ->
                                    // Two non-folder icons were dropped on each other.
                                    // Both icons are removed from the grid and a new FolderItem
                                    // is created at atPosition containing both as children.
                                    homeViewModel.createFolder(item1, item2, atPosition)
                                },
                                onAddItemToFolder = { folderId, item ->
                                    // A non-folder icon was dropped onto an existing folder.
                                    // The icon is moved inside the folder's children list.
                                    homeViewModel.addItemToFolder(folderId, item)
                                },
                                onMergeFolders = { sourceFolderId, targetFolderId ->
                                    // A folder icon was dropped onto another folder.
                                    // All children of the source folder are appended to the
                                    // target folder, then the source folder is deleted.
                                    homeViewModel.mergeFolders(sourceFolderId, targetFolderId)
                                },
                                onFolderClose = {
                                    // User tapped the scrim or pressed back inside the popup.
                                    homeViewModel.closeFolder()
                                },
                                onFolderRename = { folderId, newName ->
                                    homeViewModel.renameFolder(folderId, newName)
                                },
                                onFolderItemClick = { item ->
                                    // Tap on an icon inside the folder popup — launch it.
                                    // FolderItem cannot appear here because nesting is not
                                    // supported, but the when in openPinnedItem is exhaustive.
                                    openPinnedItem(item, context)
                                },
                                onFolderItemRemove = { folderId, itemId ->
                                    // "Remove from folder" context-menu action.
                                    // Cleanup policy fires inside the repo: if ≤1 child remains
                                    // the folder is unwrapped/deleted and the popup closes.
                                    homeViewModel.removeItemFromFolder(folderId, itemId)
                                },
                                onFolderItemReorder = { folderId, newChildren ->
                                    // User reordered icons inside the folder popup via drag.
                                    homeViewModel.reorderFolderItems(folderId, newChildren)
                                },
                                onExtractItemFromFolder = { folderId, itemId, targetPosition ->
                                    // User dragged an icon out of the folder popup and released
                                    // it over an empty home grid cell. Move the item from the
                                    // folder to the resolved grid cell. Folder cleanup policy applies.
                                    homeViewModel.extractItemFromFolder(folderId, itemId, targetPosition)
                                },
                                onMoveFolderItemToFolder = { sourceFolderId, itemId, targetFolderId ->
                                    // User dragged an icon from one folder popup and dropped it
                                    // onto a different folder icon. Move between folders without
                                    // placing the item on the grid (avoids position collision).
                                    homeViewModel.moveItemBetweenFolders(
                                        sourceFolderId = sourceFolderId,
                                        itemId = itemId,
                                        targetFolderId = targetFolderId
                                    )
                                },
                                onFolderChildDroppedOnItem = { sourceFolderId, childItem, occupantItem, atPosition ->
                                    // User dragged a folder child and dropped it directly onto
                                    // a NON-FOLDER home icon.  This should work just like dragging
                                    // two normal grid icons together: the two items are merged into
                                    // a brand new folder at that grid cell.
                                    //
                                    // The ViewModel handles both steps in a single serialized
                                    // mutation:
                                    //   1. Remove childItem from its source folder (cleanup policy applies).
                                    //   2. Create a new folder with childItem + occupantItem at atPosition.
                                    homeViewModel.extractFolderChildOntoItem(
                                        sourceFolderId = sourceFolderId,
                                        childItem = childItem,
                                        occupantItem = occupantItem,
                                        atPosition = atPosition
                                    )
                                }
                            ),
                            widget = WidgetActions(
                                onWidgetPickerOpenChange = { isOpen ->
                                    isWidgetPickerOpen = isOpen
                                },
                                onRemoveWidget = { widgetId, _ ->
                                    homeViewModel.removeWidget(
                                        widgetId = widgetId,
                                        widgetHostManager = widgetHostManager
                                    )
                                },
                                onResizeWidget = { widgetId, newSpan ->
                                    homeViewModel.resizeWidget(widgetId, newSpan)
                                },
                                onWidgetDroppedToHome = { providerInfo, span, dropPosition ->
                                    // The user dragged a widget from the picker and dropped
                                    // it on a specific cell. Begin the bind → configure → place
                                    // flow using the actual drop position instead of auto-placement.
                                    val command = homeViewModel.startWidgetPlacement(
                                        providerInfo = providerInfo,
                                        targetPosition = dropPosition,
                                        span = span,
                                        widgetHostManager = widgetHostManager
                                    )
                                    executeWidgetPlacementCommand(command)
                                }
                            )
                        ),
                        isHomescreenMenuOpen = isHomescreenMenuOpen,
                        isAppDrawerOpen = isAppDrawerOpen,
                        appDrawerUiState = appDrawerUiState,
                        isWidgetPickerOpen = isWidgetPickerOpen,
                        widgetHostManager = widgetHostManager
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

                    // If a folder popup is open, close it first.
                    // The next back press will then handle search or be consumed as usual.
                    if (homeViewModel.uiState.value.openFolderItem != null) {
                        homeViewModel.closeFolder()
                        return
                    }

                    // If app drawer is open, close it before handling search/back behavior.
                    if (isAppDrawerOpen) {
                        isAppDrawerOpen = false
                        return
                    }

                    // If widget picker is open, close it before handling search/back behavior.
                    if (isWidgetPickerOpen) {
                        isWidgetPickerOpen = false
                        return
                    }

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

        // Initialize and bind permission orchestration.
        permissionRequestCoordinator = PermissionRequestCoordinator(
            permissionHandler = permissionHandler,
            actionExecutor = actionExecutor,
            searchViewModel = searchViewModel
        )
        permissionRequestCoordinator.bind()
    }

    /**
     * Initializes the Activity result launchers for widget operations.
     *
     * These launchers handle two async flows that require an Activity result:
     * 1. Widget bind permission — when the system needs user consent to bind a widget
     * 2. Widget configuration — when a widget has a configure Activity (e.g., pick clock style)
     *
     * WHY IN onCreate:
     * ActivityResultLauncher must be registered before the Activity reaches STARTED state.
     * Registering in onCreate guarantees this. Registering later (e.g., in a callback)
     * would throw an IllegalStateException.
     */
    private fun initializeWidgetLaunchers() {
        // Launcher for the system's "allow widget binding?" permission dialog.
        // This is triggered when bindAppWidgetIdIfAllowed() returns false.
        widgetBindLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val command = homeViewModel.handleWidgetBindResult(
                resultCode = result.resultCode,
                widgetHostManager = widgetHostManager
            )
            executeWidgetPlacementCommand(command)
        }

        // Launcher for widget configuration activities.
        // Some widgets (e.g., clock style picker) require user configuration before use.
        widgetConfigureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val command = homeViewModel.handleWidgetConfigureResult(
                resultCode = result.resultCode,
                widgetHostManager = widgetHostManager
            )
            executeWidgetPlacementCommand(command)
        }
    }

    /**
     * Executes a widget-placement command emitted by HomeViewModel.
     *
     * Keeping this in one method ensures all bind/configure launches are routed
     * consistently and keeps widget flow code out of unrelated callbacks.
     */
    private fun executeWidgetPlacementCommand(
        command: HomeViewModel.WidgetPlacementCommand
    ) {
        when (command) {
            is HomeViewModel.WidgetPlacementCommand.LaunchBindPermission -> {
                widgetBindLauncher.launch(command.intent)
            }
            is HomeViewModel.WidgetPlacementCommand.LaunchConfigure -> {
                widgetConfigureLauncher.launch(command.intent)
            }
            HomeViewModel.WidgetPlacementCommand.NoOp -> Unit
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

    /**
     * Called when the Activity becomes visible to the user.
     *
     * We use onStart (not onResume) for widget host listening because:
     * - The widget host should be active whenever the Activity is visible
     * - onStart/onStop pairs match the visible lifecycle, which is what
     *   AppWidgetHost expects for its listening lifecycle
     */
    override fun onStart() {
        super.onStart()
        widgetHostManager.startListening()
    }

    override fun onStop() {
        super.onStop()
        wasAlreadyOnHomescreen = false
        isAppDrawerOpen = false
        isWidgetPickerOpen = false
        // Stop listening for widget updates when the launcher is not visible.
        // This saves battery by telling the system to stop sending widget
        // update broadcasts to this host.
        widgetHostManager.stopListening()
        // Close any open folder popup whenever the launcher leaves the foreground
        // (e.g. user launched an app from search, switched to recents, etc.).
        // Without this the popup is still "open" in the ViewModel when the user
        // returns, and the folder dialog would reappear immediately on re-entry.
        homeViewModel.closeFolder()
    }

    /**
     * Handles new Intents sent to this Activity.
     *
     * This is called when:
     * - User presses home button while launcher is running
     * - Another app launches an Intent targeting this Activity
     *
     * HOME BUTTON BEHAVIOR:
    * - Input state is captured (homescreen/menu/search/query).
    * - HomeButtonPolicy resolves one deterministic decision.
    * - SearchSessionController applies the resulting transition.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // If app drawer is open, close it and consume this home press entirely.
            // This mirrors layered-dismiss behavior used by folder popup and menus.
            if (isAppDrawerOpen) {
                isAppDrawerOpen = false
                return
            }

            // If widget picker is open, close it and consume this home press.
            if (isWidgetPickerOpen) {
                isWidgetPickerOpen = false
                return
            }

            // If a folder popup is open, close it and consume this home press entirely.
            // The normal policy (open search, clear query, etc.) only runs on the NEXT
            // home press — matching the same layered-dismiss pattern used for the
            // homescreen menu and the search dialog.
            if (homeViewModel.uiState.value.openFolderItem != null) {
                homeViewModel.closeFolder()
                return
            }

            val uiState = searchViewModel.uiState.value
            val decision = homeButtonPolicy.resolve(
                HomeButtonPolicy.InputState(
                    isAlreadyOnHomescreen = wasAlreadyOnHomescreen,
                    isHomescreenMenuOpen = isHomescreenMenuOpen,
                    isSearchVisible = uiState.isSearchVisible,
                    hasSearchQuery = uiState.query.isNotEmpty()
                )
            )

            searchSessionController.applyHomeButtonDecision(
                decision = decision,
                closeHomescreenMenu = ::closeHomescreenMenu
            )
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
