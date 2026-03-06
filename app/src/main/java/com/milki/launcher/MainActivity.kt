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
import com.milki.launcher.presentation.main.ActivityWidgetPlacementCoordinator
import com.milki.launcher.presentation.main.HomeButtonPolicy
import com.milki.launcher.presentation.main.HomeIntentCoordinator
import com.milki.launcher.presentation.main.HomeIntentCoordinatorContract
import com.milki.launcher.presentation.main.PermissionRequestCoordinator
import com.milki.launcher.presentation.main.SearchSessionController
import com.milki.launcher.presentation.main.SurfaceStateCoordinator
import com.milki.launcher.presentation.main.SurfaceStateCoordinatorContract
import com.milki.launcher.presentation.main.WidgetPlacementCoordinator
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
     * AppDrawerViewModel manages drawer app list and loading state.
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
     * Controller that applies search/menu state transitions chosen by policy.
     */
    private lateinit var searchSessionController: SearchSessionController

    /**
     * Coordinator responsible for layered surface open/close orchestration.
     */
    private lateinit var surfaceStateCoordinator: SurfaceStateCoordinatorContract

    /**
     * Coordinator that owns HOME-button policy orchestration.
     */
    private lateinit var homeIntentCoordinator: HomeIntentCoordinatorContract

    /**
     * Coordinator that owns widget bind/configure activity launcher orchestration.
     */
    private lateinit var widgetPlacementCoordinator: WidgetPlacementCoordinator

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        searchSessionController = SearchSessionController(searchViewModel)
        initializeHandlers()
        initializeCoordinators()
        initializeBackButtonBehavior()
        widgetPlacementCoordinator.initialize()

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
                                    surfaceStateCoordinator.updateHomescreenMenuOpen(isOpen)
                                }
                            ),
                            drawer = DrawerActions(
                                onAppDrawerOpenChange = { isOpen ->
                                    surfaceStateCoordinator.updateAppDrawerOpen(isOpen)
                                }
                            ),
                            home = HomeActions(
                                onHomeSwipeUp = {
                                    if (launcherSettings.swipeUpAction == SwipeUpAction.OPEN_APP_DRAWER) {
                                        surfaceStateCoordinator.openAppDrawerFromSwipeGesture()
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
                                    surfaceStateCoordinator.updateWidgetPickerOpen(isOpen)
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
                                    widgetPlacementCoordinator.execute(command)
                                }
                            )
                        ),
                        isHomescreenMenuOpen = surfaceStateCoordinator.isHomescreenMenuOpen,
                        isAppDrawerOpen = surfaceStateCoordinator.isAppDrawerOpen,
                        appDrawerUiState = appDrawerUiState,
                        isWidgetPickerOpen = surfaceStateCoordinator.isWidgetPickerOpen,
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

                    // Delegate layered back behavior to coordinator so Activity
                    // does not own per-surface close ordering logic.
                    surfaceStateCoordinator.handleBackPressed(
                        isSearchVisible = uiState.isSearchVisible
                    )
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
     * Initializes orchestration coordinators extracted from MainActivity.
     *
     * COORDINATORS CREATED HERE:
     * 1) SurfaceStateCoordinator
     *    - Owns layered surface visibility state and close ordering policies.
     *
     * 2) HomeIntentCoordinator
     *    - Owns HOME-button policy orchestration using HomeButtonPolicy + SearchSessionController.
     *
     * 3) WidgetPlacementCoordinator
     *    - Owns bind/configure ActivityResult launchers and command dispatching.
     */
    private fun initializeCoordinators() {
        surfaceStateCoordinator = SurfaceStateCoordinator(
            hideSearch = { searchViewModel.hideSearch() },
            isFolderOpen = { homeViewModel.uiState.value.openFolderItem != null },
            closeFolder = { homeViewModel.closeFolder() }
        )

        homeIntentCoordinator = HomeIntentCoordinator(
            homeButtonPolicy = HomeButtonPolicy(),
            isHomescreenMenuOpen = { surfaceStateCoordinator.isHomescreenMenuOpen },
            consumeLayeredHomePress = { surfaceStateCoordinator.consumeHomePressForLayeredSurface() },
            applyDecision = { decision ->
                searchSessionController.applyHomeButtonDecision(
                    decision = decision,
                    closeHomescreenMenu = {
                        surfaceStateCoordinator.updateHomescreenMenuOpen(false)
                    }
                )
            }
        )

        widgetPlacementCoordinator = ActivityWidgetPlacementCoordinator(
            activity = this,
            homeViewModel = homeViewModel,
            widgetHostManagerProvider = { widgetHostManager }
        )
    }

    // ========================================================================
    // ACTIVITY LIFECYCLE CALLBACKS
    // ========================================================================

    override fun onResume() {
        super.onResume()
        permissionHandler.updateStates()
        homeIntentCoordinator.onResume()
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
        homeIntentCoordinator.onStop()
        surfaceStateCoordinator.onStop()
        // Stop listening for widget updates when the launcher is not visible.
        // This saves battery by telling the system to stop sending widget
        // update broadcasts to this host.
        widgetHostManager.stopListening()
    }

    /**
     * Handles new Intents sent to this Activity.
     *
     * This is called when:
     * - User presses home button while launcher is running
     * - Another app launches an Intent targeting this Activity
     *
     * HOME BUTTON BEHAVIOR:
     * - Home-intent classification stays in MainActivity (host responsibility).
     * - Orchestration/policy execution is delegated to HomeIntentCoordinator.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (isLauncherHomeIntent(intent)) {
            val uiState = searchViewModel.uiState.value
            homeIntentCoordinator.onHomeButtonPressed(
                isSearchVisible = uiState.isSearchVisible,
                hasSearchQuery = uiState.query.isNotEmpty()
            )
        }
    }

    /**
     * Host-level helper that classifies whether an Intent is a HOME-button return.
     *
     * Keeping this check near onNewIntent maintains clear ownership:
     * MainActivity handles Android Intent mechanics,
     * HomeIntentCoordinator handles policy/orchestration once classified.
     */
    private fun isLauncherHomeIntent(intent: Intent): Boolean {
        return intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
    }
}
