package com.milki.launcher.presentation.launcher.host

import android.content.Intent
import android.content.pm.LauncherApps
import androidx.activity.ComponentActivity

import androidx.lifecycle.lifecycleScope
import com.milki.launcher.core.intent.BENCHMARK_DRAWER_SCROLL_SEQUENCE_DOWN_UP
import com.milki.launcher.core.intent.LauncherBenchmarkTarget
import com.milki.launcher.core.intent.toLauncherBenchmarkRequestOrNull
import com.milki.launcher.core.perf.traceSection
import com.milki.launcher.data.widget.WidgetPickerCatalogStore
import com.milki.launcher.domain.widget.WidgetHostPort
import com.milki.launcher.core.permission.PermissionHandler
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.HomeRepository
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.presentation.drawer.AppDrawerViewModel
import com.milki.launcher.presentation.home.HomeViewModel
import com.milki.launcher.presentation.launcher.PermissionRequestCoordinator
import com.milki.launcher.presentation.launcher.PinShortcutRequestCoordinator
import com.milki.launcher.presentation.launcher.NotificationShadeController
import com.milki.launcher.presentation.launcher.ScreenLockController
import com.milki.launcher.presentation.launcher.LauncherNavigator
import com.milki.launcher.presentation.launcher.LauncherRoute
import com.milki.launcher.presentation.launcher.WidgetPlacementCoordinator
import com.milki.launcher.presentation.search.ActionExecutor
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.presentation.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Owns MainActivity orchestration that is not UI rendering.
 *
 * STARTUP OPTIMIZATION:
 * Dependencies not needed for the first visible frame are accepted as provider
 * functions (lazy lambdas) so that Koin construction is deferred until actual use.
 * Only homeViewModel and widgetHost are resolved eagerly because they feed
 * the first frame and widget host lifecycle.
 */
internal class LauncherHostRuntime(
    private val activity: ComponentActivity,
    private val searchViewModelProvider: () -> SearchViewModel,
    private val homeViewModel: HomeViewModel,
    private val appDrawerViewModelProvider: () -> AppDrawerViewModel,
    private val appRepositoryProvider: () -> AppRepository,
    private val contactsRepositoryProvider: () -> ContactsRepository,
    private val filesRepositoryProvider: () -> com.milki.launcher.domain.repository.FilesRepository,
    private val homeRepository: HomeRepository,
    private val widgetHost: WidgetHostPort
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

    private val notificationShadeController by lazy {
        NotificationShadeController(activity)
    }

    private val screenLockController = ScreenLockController(activity)

    val launcherNavigator = LauncherNavigator(
        closeFolder = { homeViewModel.closeFolder() },
        openFolder = { folderId -> homeViewModel.openFolder(folderId) },
        openNotificationShade = {
            notificationShadeController.expand()
        },
        lockScreen = {
            screenLockController.lock()
        },
        onAppDrawerVisibilityChanged = { isVisible ->
            appDrawerViewModelProvider().setDrawerVisible(isVisible)
        }
    )

    val widgetPlacementCoordinator: WidgetPlacementCoordinator = WidgetPlacementCoordinator(
        activity = activity,
        homeViewModel = homeViewModel,
        widgetHost = widgetHost
    )

    /**
     * Initializes runtime collaborators and host callbacks that must be registered once.
     */
    fun initialize() {
        traceSection("launcher.startup.runtime.initialize") {
            initializePermissionHandler()
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

    fun onResume() {
        widgetHost.updateHostState(resumed = true, isNormal = true)
        if (::actionExecutor.isInitialized) {
            permissionHandler.updateStates()
        }
        launcherNavigator.onResume()
    }

    fun onPause() {
        widgetHost.updateHostState(resumed = false)
    }

    fun onStart() {
        widgetHost.updateHostState(started = true)
    }

    fun onStop() {
        widgetHost.updateHostState(started = false)
        launcherNavigator.onStop()
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
            isLauncherHomeIntent(intent) -> launcherNavigator.handleHomeIntent()
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
            seedHome = request.seedHome,
            drawerQuery = request.drawerQuery,
            drawerScrollSequence = request.drawerScrollSequence
        )
        return true
    }

    private fun applyBenchmarkRequest(
        target: LauncherBenchmarkTarget,
        seedHome: Boolean,
        drawerQuery: String?,
        drawerScrollSequence: String?
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
            launcherNavigator.navigate(LauncherRoute.AppDrawer)
            if (drawerQuery != null) {
                appDrawerViewModelProvider().updateQuery(drawerQuery)
            }
            if (drawerScrollSequence == BENCHMARK_DRAWER_SCROLL_SEQUENCE_DOWN_UP) {
                appDrawerViewModelProvider().triggerBenchmarkScrollSequenceDownUp()
            }
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
                override fun updateContactsPermission(
                    state: com.milki.launcher.domain.model.PermissionAccessState
                ) {
                    searchViewModelProvider().updateContactsPermission(state)
                }

                override fun updateFilesPermission(
                    state: com.milki.launcher.domain.model.PermissionAccessState
                ) {
                    searchViewModelProvider().updateFilesPermission(state)
                }
            }
        )
        permissionHandler.setup()
    }

    private fun initializeDeferredHandlers() {
        if (::actionExecutor.isInitialized) return

        initializePermissionHandler()

        permissionRequestCoordinator = PermissionRequestCoordinator(
            permissionHandler = permissionHandler,
            onCloseSearch = {
                if (launcherNavigator.isSearchOpen) {
                    launcherNavigator.pop()
                }
            },
            actionExecutorProvider = { actionExecutor }
        )

        actionExecutor = ActionExecutor(
            activity,
            contactsRepositoryProvider(),
            filesRepositoryProvider(),
            homeViewModel,
            activity.lifecycleScope,
            permissionRequester = permissionRequestCoordinator::requestPermission,
            closeSearch = permissionRequestCoordinator::closeSearch,
            saveRecentApp = { componentName ->
                searchViewModelProvider().saveRecentApp(componentName)
            },
            openAppWidgets = { appName ->
                launcherNavigator.navigate(LauncherRoute.WidgetPicker(appName))
            }
        )

        pinShortcutRequestCoordinator = PinShortcutRequestCoordinator(
            context = activity,
            homePinning = homeViewModel,
            scope = activity.lifecycleScope
        )

        permissionRequestCoordinator.bind()
        permissionHandler.updateStates()
    }


    private fun isLauncherHomeIntent(intent: Intent): Boolean {
        return intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
    }

    private fun resetTransientSurfacesForBenchmark() {
        launcherNavigator.clearTransientRoutes()
        appDrawerViewModelProvider().updateQuery("")
    }
}
