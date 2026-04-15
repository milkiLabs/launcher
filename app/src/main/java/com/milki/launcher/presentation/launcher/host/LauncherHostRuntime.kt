package com.milki.launcher.presentation.launcher.host

import android.content.Intent
import android.content.pm.LauncherApps
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.milki.launcher.core.intent.LauncherBenchmarkTarget
import com.milki.launcher.core.intent.toLauncherBenchmarkRequestOrNull
import com.milki.launcher.core.perf.traceSection
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
 * STARTUP OPTIMIZATION:
 * Dependencies not needed for the first visible frame are accepted as provider
 * functions (lazy lambdas) so that Koin construction is deferred until actual use.
 * Only homeViewModel and widgetHostManager are resolved eagerly because they feed
 * the first frame and widget host lifecycle.
 */
internal class LauncherHostRuntime(
    private val activity: ComponentActivity,
    private val searchViewModelProvider: () -> SearchViewModel,
    private val homeViewModel: HomeViewModel,
    private val appDrawerViewModelProvider: () -> AppDrawerViewModel,
    private val appRepositoryProvider: () -> AppRepository,
    private val contactsRepositoryProvider: () -> ContactsRepository,
    private val homeRepository: HomeRepository,
    private val widgetHostManager: WidgetHostManager
) {
    @Volatile
    private var deferredStartupCompleted = false

    private lateinit var permissionHandler: PermissionHandler
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var permissionRequestCoordinator: PermissionRequestCoordinator
    private lateinit var pinShortcutRequestCoordinator: PinShortcutRequestCoordinator

    private val benchmarkHomeSeeder by lazy {
        LauncherBenchmarkHomeSeeder(
            appRepository = appRepositoryProvider(),
            homeRepository = homeRepository,
            ownPackageName = activity.packageName
        )
    }

    val surfaceStateCoordinator = SurfaceStateCoordinator(
        showSearch = { searchViewModelProvider().showSearch() },
        hideSearch = { searchViewModelProvider().hideSearch() },
        isSearchVisible = { searchViewModelProvider().uiState.value.isSearchVisible },
        isFolderOpen = { homeViewModel.openFolderItem.value != null },
        closeFolder = { homeViewModel.closeFolder() },
        onAppDrawerVisibilityChanged = { isVisible ->
            appDrawerViewModelProvider().setDrawerVisible(isVisible)
        }
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
        traceSection("launcher.startup.runtime.initialize") {
            initializePermissionHandler()
            initializeBackButtonBehavior()
            widgetPlacementCoordinator.initialize()
        }
    }

    /**
     * Runs startup work that can wait until the first frame is already visible.
     *
     * Permission and action handlers are initialized here instead of in [initialize]
     * because they require SearchViewModel and ContactsRepository which are not
     * needed for the first frame.
     */
    fun completeDeferredStartup(widgetPickerCatalogStore: WidgetPickerCatalogStore) {
        if (deferredStartupCompleted) {
            return
        }
        deferredStartupCompleted = true

        traceSection("launcher.startup.deferred") {
            initializeDeferredHandlers()
            homeViewModel.startDeferredStartupWork()
            widgetPickerCatalogStore.prewarm()
        }
    }

    /**
     * Executes a search result action using current permission state.
     */
    fun dispatchSearchResultAction(action: SearchResultAction) {
        if (!::actionExecutor.isInitialized) {
            initializeDeferredHandlers()
        }
        actionExecutor.execute(action, permissionHandler::hasPermission)
    }

    /**
     * Applies the policy that decides whether launching an action should dismiss search.
     */
    fun updateSearchClosePolicy(closeSearchOnLaunch: Boolean) {
        if (!::actionExecutor.isInitialized) return
        actionExecutor.shouldCloseSearchForAction = { action ->
            closeSearchOnLaunch && action.shouldCloseSearch()
        }
    }

    fun onResume() {
        widgetHostManager.setActivityResumed(true)
        widgetHostManager.setStateIsNormal(true)
        if (::actionExecutor.isInitialized) {
            permissionHandler.updateStates()
        }
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
        if (handlePinShortcutIntent(intent)) {
            return
        }

        if (handleBenchmarkIntent(intent)) {
            return
        }

    }

    fun onNewIntent(intent: Intent) {
        if (handlePinShortcutIntent(intent)) {
            return
        }

        if (handleBenchmarkIntent(intent)) {
            return
        }

        when {
            isLauncherHomeIntent(intent) -> surfaceStateCoordinator.handleHomeIntent()
        }
    }

    private fun handlePinShortcutIntent(intent: Intent): Boolean {
        if (intent.action != LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT) {
            return false
        }

        if (!::pinShortcutRequestCoordinator.isInitialized) {
            // Force-initialize handlers only when we actually received
            // a pin-shortcut confirmation intent.
            initializeDeferredHandlers()
        }
        return pinShortcutRequestCoordinator.handleIntent(intent)
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
            traceSection("launcher.benchmark.seedHome") {
                runBlocking(Dispatchers.IO) {
                    benchmarkHomeSeeder.seed()
                }
            }
        }

        if (target == LauncherBenchmarkTarget.DRAWER) {
            surfaceStateCoordinator.updateAppDrawerOpen(true)
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        return widgetPlacementCoordinator.onActivityResult(requestCode, resultCode)
    }

    private fun initializePermissionHandler() {
        if (::permissionHandler.isInitialized) return

        permissionHandler = PermissionHandler(
            activity = activity,
            permissionStateSink = object : com.milki.launcher.core.permission.PermissionStateSink {
                override fun updateContactsPermission(hasPermission: Boolean) {
                    searchViewModelProvider().updateContactsPermission(hasPermission)
                }

                override fun updateFilesPermission(hasPermission: Boolean) {
                    searchViewModelProvider().updateFilesPermission(hasPermission)
                }
            }
        )
        permissionHandler.setup()
    }

    private fun initializeDeferredHandlers() {
        if (::actionExecutor.isInitialized) return

        initializePermissionHandler()

        actionExecutor = ActionExecutor(
            activity,
            contactsRepositoryProvider(),
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
            searchViewModel = searchViewModelProvider()
        )
        permissionRequestCoordinator.bind()
        permissionHandler.updateStates()
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
        appDrawerViewModelProvider().updateQuery("")
        searchViewModelProvider().hideSearch()
        homeViewModel.closeFolder()
    }
}
