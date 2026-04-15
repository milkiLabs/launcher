package com.milki.launcher.presentation.launcher.host

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.milki.launcher.core.intent.LauncherBenchmarkTarget
import com.milki.launcher.core.intent.toLauncherBenchmarkRequestOrNull
import com.milki.launcher.data.widget.WidgetPickerCatalogStore
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.core.permission.PermissionHandler
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.HomeRepository
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.presentation.drawer.AppDrawerViewModel
import com.milki.launcher.presentation.home.HomeViewModel
import com.milki.launcher.presentation.launcher.PermissionRequestCoordinator
import com.milki.launcher.presentation.launcher.PinShortcutRequestCoordinator
import com.milki.launcher.presentation.launcher.SurfaceStateCoordinator
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
    @Volatile
    private var deferredStartupCompleted = false

    private lateinit var permissionHandler: PermissionHandler
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var permissionRequestCoordinator: PermissionRequestCoordinator
    private lateinit var pinShortcutRequestCoordinator: PinShortcutRequestCoordinator

    private val benchmarkHomeSeeder = LauncherBenchmarkHomeSeeder(
        appRepository = appRepository,
        homeRepository = homeRepository,
        ownPackageName = activity.packageName
    )

    val surfaceStateCoordinator = SurfaceStateCoordinator(
        showSearch = { searchViewModel.showSearch() },
        hideSearch = { searchViewModel.hideSearch() },
        isSearchVisible = { searchViewModel.uiState.value.isSearchVisible },
        isFolderOpen = { homeViewModel.openFolderItem.value != null },
        closeFolder = { homeViewModel.closeFolder() },
        onAppDrawerVisibilityChanged = appDrawerViewModel::setDrawerVisible
    )

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
     * Runs startup work that can wait until the first frame is already visible.
     */
    fun completeDeferredStartup(widgetPickerCatalogStore: WidgetPickerCatalogStore) {
        if (deferredStartupCompleted) {
            return
        }
        deferredStartupCompleted = true

        homeViewModel.startDeferredStartupWork()
        widgetPickerCatalogStore.prewarm()
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
        surfaceStateCoordinator.onResume()
    }

    fun onPause() {
        widgetHostManager.setActivityResumed(false)
    }

    fun onStart() {
        widgetHostManager.setActivityStarted(true)
    }

    fun onStop() {
        widgetHostManager.setActivityStarted(false)
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
            isLauncherHomeIntent(intent) -> surfaceStateCoordinator.handleHomeIntent()
        }
    }

    private fun handleBenchmarkIntent(intent: Intent): Boolean {
        val request = intent.toLauncherBenchmarkRequestOrNull() ?: return false

        applyBenchmarkRequest(
            target = request.target,
            seedHome = request.seedHome
        )
        return true
    }

    private fun applyBenchmarkRequest(
        target: LauncherBenchmarkTarget,
        seedHome: Boolean
    ) {
        resetTransientSurfacesForBenchmark()

        if (seedHome) {
            runBlocking(Dispatchers.IO) {
                benchmarkHomeSeeder.seed()
            }
        }

        if (target == LauncherBenchmarkTarget.DRAWER) {
            surfaceStateCoordinator.updateAppDrawerOpen(true)
        }
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
                    surfaceStateCoordinator.handleBackPressed()
                }
            }
        )
    }

    private fun isLauncherHomeIntent(intent: Intent): Boolean {
        return intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
    }

    private fun resetTransientSurfacesForBenchmark() {
        surfaceStateCoordinator.dismissContextMenus()
        surfaceStateCoordinator.updateAppDrawerOpen(false)
        surfaceStateCoordinator.updateHomescreenMenuOpen(false)
        surfaceStateCoordinator.updateWidgetPickerOpen(false)
        appDrawerViewModel.updateQuery("")
        searchViewModel.hideSearch()
        homeViewModel.closeFolder()
    }
}
