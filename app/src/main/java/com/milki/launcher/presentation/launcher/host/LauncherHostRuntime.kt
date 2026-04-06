package com.milki.launcher.presentation.launcher.host

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.core.permission.PermissionHandler
import com.milki.launcher.presentation.drawer.AppDrawerViewModel
import com.milki.launcher.presentation.home.HomeViewModel
import com.milki.launcher.presentation.launcher.DrawerHomePressPolicy
import com.milki.launcher.presentation.launcher.HomeButtonPolicy
import com.milki.launcher.presentation.launcher.HomeIntentCoordinator
import com.milki.launcher.presentation.launcher.HomeIntentCoordinatorContract
import com.milki.launcher.presentation.launcher.PermissionRequestCoordinator
import com.milki.launcher.presentation.launcher.SearchSessionController
import com.milki.launcher.presentation.launcher.SurfaceStateCoordinator
import com.milki.launcher.presentation.launcher.SurfaceStateCoordinatorContract
import com.milki.launcher.presentation.launcher.WidgetPlacementCoordinator
import com.milki.launcher.presentation.search.ActionExecutor
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.presentation.search.SearchViewModel
import com.milki.launcher.presentation.search.shouldCloseSearch

/**
 * Owns MainActivity orchestration that is not UI rendering.
 *
 * This keeps Activity code focused on Android host responsibilities while this runtime
 * encapsulates callback wiring, policy coordinators, and lifecycle side effects.
 */
internal class LauncherHostRuntime(
    private val activity: ComponentActivity,
    private val searchViewModel: SearchViewModel,
    private val homeViewModel: HomeViewModel,
    private val appDrawerViewModel: AppDrawerViewModel,
    private val contactsRepository: ContactsRepository,
    private val widgetHostManager: WidgetHostManager
) {
    private val drawerHomePressPolicy = DrawerHomePressPolicy()
    private var shouldClearDrawerQueryOnHomePress = true
    private var shouldClearWidgetPickerQueryOnHomePress = true

    private lateinit var permissionHandler: PermissionHandler
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var permissionRequestCoordinator: PermissionRequestCoordinator

    private val searchSessionController = SearchSessionController(searchViewModel)

    val surfaceStateCoordinator: SurfaceStateCoordinatorContract = SurfaceStateCoordinator(
        showSearch = { searchViewModel.showSearch() },
        hideSearch = { searchViewModel.hideSearch() },
        isFolderOpen = { homeViewModel.uiState.value.openFolderItem != null },
        closeFolder = { homeViewModel.closeFolder() }
    )

    val homeIntentCoordinator: HomeIntentCoordinatorContract = createHomeIntentCoordinator()

    val widgetPlacementCoordinator: WidgetPlacementCoordinator = WidgetPlacementCoordinator(
        activity = activity,
        homeViewModel = homeViewModel,
        widgetHostManager = widgetHostManager
    )

    /**
     * Initializes runtime collaborators and host callbacks that must be registered once.
     */
    fun initialize() {
        initializeHandlers()
        initializeBackButtonBehavior()
        widgetPlacementCoordinator.initialize()
    }

    /**
     * Executes a search result action using current permission state.
     */
    fun dispatchSearchResultAction(action: SearchResultAction) {
        actionExecutor.execute(action, permissionHandler::hasPermission)
    }

    /**
     * Applies the policy that decides whether launching an action should dismiss search.
     */
    fun updateSearchClosePolicy(closeSearchOnLaunch: Boolean) {
        actionExecutor.shouldCloseSearchForAction = { action ->
            closeSearchOnLaunch && action.shouldCloseSearch()
        }
    }

    fun updateHomeButtonQueryClearPolicy(
        clearDrawerQuery: Boolean,
        clearWidgetPickerQuery: Boolean
    ) {
        shouldClearDrawerQueryOnHomePress = clearDrawerQuery
        shouldClearWidgetPickerQueryOnHomePress = clearWidgetPickerQuery
    }

    fun onResume() {
        widgetHostManager.setActivityResumed(true)
        widgetHostManager.setStateIsNormal(true)
        permissionHandler.updateStates()
        homeIntentCoordinator.onResume()
    }

    fun onPause() {
        widgetHostManager.setActivityResumed(false)
    }

    fun onStart() {
        widgetHostManager.setActivityStarted(true)
    }

    fun onStop() {
        widgetHostManager.setActivityStarted(false)
        homeIntentCoordinator.onStop()
        surfaceStateCoordinator.onStop()
    }

    fun onNewIntent(intent: Intent) {
        if (!isLauncherHomeIntent(intent)) return

        homeIntentCoordinator.onHomeButtonPressed(
            isSearchVisible = searchViewModel.uiState.value.isSearchVisible
        )
    }

    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        return widgetPlacementCoordinator.onActivityResult(requestCode, resultCode)
    }

    private fun initializeHandlers() {
        permissionHandler = PermissionHandler(
            activity = activity,
            permissionStateSink = object : com.milki.launcher.core.permission.PermissionStateSink {
                override fun updateContactsPermission(hasPermission: Boolean) {
                    searchViewModel.updateContactsPermission(hasPermission)
                }

                override fun updateFilesPermission(hasPermission: Boolean) {
                    searchViewModel.updateFilesPermission(hasPermission)
                }
            }
        )
        permissionHandler.setup()

        actionExecutor = ActionExecutor(
            activity,
            contactsRepository,
            homeViewModel,
            activity.lifecycleScope
        )

        permissionRequestCoordinator = PermissionRequestCoordinator(
            permissionHandler = permissionHandler,
            actionExecutor = actionExecutor,
            searchViewModel = searchViewModel
        )
        permissionRequestCoordinator.bind()
    }

    private fun initializeBackButtonBehavior() {
        activity.onBackPressedDispatcher.addCallback(
            activity,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    surfaceStateCoordinator.handleBackPressed(
                        isSearchVisible = searchViewModel.uiState.value.isSearchVisible
                    )
                }
            }
        )
    }

    private fun createHomeIntentCoordinator(): HomeIntentCoordinatorContract {
        return HomeIntentCoordinator(
            homeButtonPolicy = HomeButtonPolicy(),
            isHomescreenMenuOpen = { surfaceStateCoordinator.isHomescreenMenuOpen },
            consumeLayeredHomePress = {
                when (
                    drawerHomePressPolicy.resolve(
                        DrawerHomePressPolicy.InputState(
                            isDrawerOpen = surfaceStateCoordinator.isAppDrawerOpen,
                            hasDrawerQuery = appDrawerViewModel.uiState.value.query.isNotBlank(),
                            shouldClearDrawerQueryOnHomePress = shouldClearDrawerQueryOnHomePress
                        )
                    )
                ) {
                    DrawerHomePressPolicy.Decision.CLEAR_QUERY -> {
                        surfaceStateCoordinator.dismissContextMenus()
                        appDrawerViewModel.updateQuery("")
                        true
                    }

                    DrawerHomePressPolicy.Decision.CLOSE_DRAWER -> {
                        surfaceStateCoordinator.updateAppDrawerOpen(false)
                        true
                    }

                    DrawerHomePressPolicy.Decision.NONE -> {
                        if (surfaceStateCoordinator.isWidgetPickerOpen) {
                            if (
                                shouldClearWidgetPickerQueryOnHomePress &&
                                surfaceStateCoordinator.widgetPickerQuery.isNotBlank()
                            ) {
                                surfaceStateCoordinator.dismissContextMenus()
                                surfaceStateCoordinator.updateWidgetPickerQuery("")
                            } else {
                                surfaceStateCoordinator.updateWidgetPickerOpen(false)
                            }
                            return@HomeIntentCoordinator true
                        }

                        surfaceStateCoordinator.consumeHomePressForLayeredSurface()
                    }
                }
            },
            applyDecision = { decision ->
                searchSessionController.applyHomeButtonDecision(
                    decision = decision,
                    dismissContextMenus = {
                        surfaceStateCoordinator.dismissContextMenus()
                    },
                    closeHomescreenMenu = {
                        surfaceStateCoordinator.updateHomescreenMenuOpen(false)
                    }
                )
            }
        )
    }

    private fun isLauncherHomeIntent(intent: Intent): Boolean {
        return intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
    }
}