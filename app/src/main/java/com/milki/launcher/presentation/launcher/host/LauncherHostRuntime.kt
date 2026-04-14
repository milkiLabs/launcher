package com.milki.launcher.presentation.launcher.host

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.milki.launcher.core.intent.LauncherBenchmarkAction
import com.milki.launcher.core.intent.toLauncherBenchmarkActionOrNull
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.HomeRepository
import com.milki.launcher.core.permission.PermissionHandler
import com.milki.launcher.presentation.drawer.AppDrawerViewModel
import com.milki.launcher.presentation.home.HomeViewModel
import com.milki.launcher.presentation.launcher.PinShortcutRequestCoordinator
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

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
    private val appRepository: AppRepository,
    private val contactsRepository: ContactsRepository,
    private val homeRepository: HomeRepository,
    private val widgetHostManager: WidgetHostManager
) {
    private lateinit var permissionHandler: PermissionHandler
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var permissionRequestCoordinator: PermissionRequestCoordinator
    private lateinit var pinShortcutRequestCoordinator: PinShortcutRequestCoordinator

    private val searchSessionController = SearchSessionController(searchViewModel)
    private val benchmarkHomeSeeder = LauncherBenchmarkHomeSeeder(
        appRepository = appRepository,
        homeRepository = homeRepository,
        ownPackageName = activity.packageName
    )

    val surfaceStateCoordinator: SurfaceStateCoordinatorContract = SurfaceStateCoordinator(
        showSearch = { searchViewModel.showSearch() },
        hideSearch = { searchViewModel.hideSearch() },
        isFolderOpen = { homeViewModel.openFolderItem.value != null },
        closeFolder = { homeViewModel.closeFolder() },
        onAppDrawerVisibilityChanged = appDrawerViewModel::setDrawerVisible
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

    fun handleInitialIntent(intent: Intent) {
        if (pinShortcutRequestCoordinator.handleIntent(intent)) {
            return
        }

        if (handleBenchmarkIntent(intent)) {
            return
        }

    }

    fun onNewIntent(intent: Intent) {
        if (pinShortcutRequestCoordinator.handleIntent(intent)) {
            return
        }

        if (handleBenchmarkIntent(intent)) {
            return
        }

        when {
            isLauncherHomeIntent(intent) -> {
                homeIntentCoordinator.onHomeButtonPressed(
                    isSearchVisible = searchViewModel.uiState.value.isSearchVisible
                )
            }
        }
    }

    private fun handleBenchmarkIntent(intent: Intent): Boolean {
        return when (intent.toLauncherBenchmarkActionOrNull()) {
            LauncherBenchmarkAction.PREPARE_HOME -> {
                prepareHomeForBenchmark()
                true
            }

            LauncherBenchmarkAction.OPEN_DRAWER -> {
                showDrawerForBenchmark()
                true
            }

            LauncherBenchmarkAction.OPEN_HOME -> {
                showHomeForBenchmark()
                true
            }

            null -> false
        }
    }

    private fun showHomeForBenchmark() {
        resetTransientSurfacesForBenchmark()
        surfaceStateCoordinator.updateAppDrawerOpen(false)
    }

    private fun prepareHomeForBenchmark() {
        resetTransientSurfacesForBenchmark()

        runBlocking(Dispatchers.IO) {
            benchmarkHomeSeeder.seed()
        }

        surfaceStateCoordinator.updateAppDrawerOpen(false)
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

        pinShortcutRequestCoordinator = PinShortcutRequestCoordinator(
            context = activity,
            homeRepository = homeRepository,
            homeViewModel = homeViewModel,
            scope = activity.lifecycleScope
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
                if (surfaceStateCoordinator.isAppDrawerOpen) {
                    surfaceStateCoordinator.updateAppDrawerOpen(false)
                    return@HomeIntentCoordinator true
                }

                if (surfaceStateCoordinator.isWidgetPickerOpen) {
                    surfaceStateCoordinator.updateWidgetPickerOpen(false)
                    return@HomeIntentCoordinator true
                }

                surfaceStateCoordinator.consumeHomePressForLayeredSurface()
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

    private fun showDrawerForBenchmark() {
        resetTransientSurfacesForBenchmark()
        surfaceStateCoordinator.updateAppDrawerOpen(true)
    }

    private fun resetTransientSurfacesForBenchmark() {
        surfaceStateCoordinator.dismissContextMenus()
        surfaceStateCoordinator.updateHomescreenMenuOpen(false)
        surfaceStateCoordinator.updateWidgetPickerOpen(false)
        appDrawerViewModel.updateQuery("")
        searchViewModel.hideSearch()
        homeViewModel.closeFolder()
    }
}
