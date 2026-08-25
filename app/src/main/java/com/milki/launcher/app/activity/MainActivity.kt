package com.milki.launcher.app.activity

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import com.milki.launcher.core.intent.isHomeIntent
import com.milki.launcher.core.intent.shouldNormalizeRootToHome
import com.milki.launcher.core.launcher.isAppDefaultLauncher
import com.milki.launcher.core.launcher.launchHomeRoleRequestIfNeeded
import com.milki.launcher.core.launcher.openDefaultLauncherSettingsFallback
import com.milki.launcher.core.perf.traceSection
import com.milki.launcher.data.contextmenu.AppContextDataCache
import com.milki.launcher.data.icon.AppIconMemoryCache
import com.milki.launcher.data.widget.WidgetPickerCatalogStore
import com.milki.launcher.domain.repository.ActionShortcutRepository
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.HomeRepository
import com.milki.launcher.domain.repository.SettingsReader
import com.milki.launcher.domain.widget.WidgetHostPort
import com.milki.launcher.presentation.drawer.AppDrawerViewModel
import com.milki.launcher.presentation.home.HomeViewModel
import com.milki.launcher.presentation.launcher.host.LauncherHostRuntime
import com.milki.launcher.presentation.launcher.host.LauncherRootContent
import com.milki.launcher.presentation.search.SearchViewModel
import com.milki.launcher.presentation.settings.BackupImportExportCoordinator
import com.milki.launcher.presentation.settings.DefaultLauncherPromoter
import com.milki.launcher.presentation.settings.SettingsViewModel
import com.milki.launcher.ui.screens.settings.SettingsNavHost
import com.milki.launcher.ui.screens.settings.rememberSettingsActions
import com.milki.launcher.ui.components.common.LocalAppContextDataCache
import com.milki.launcher.ui.components.common.LocalAppIconMemoryCache
import com.milki.launcher.ui.theme.LauncherTheme
import kotlinx.serialization.Serializable
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

@Serializable
internal sealed interface MainRoute : NavKey {
    @Serializable
    data object Home : MainRoute

    @Serializable
    data object Settings : MainRoute
}

/**
 * Single Android host for the launcher and settings.
 *
 * The root Navigation 3 stack is saveable, while launcher overlays are managed
 * separately by the transient launcher navigator.
 *
 * Focused responsibilities are delegated to collaborators:
 * - [DefaultLauncherPromoter] owns default-launcher detection and the
 *   once-per-session "set as default" prompt (survives config changes).
 * - [BackupImportExportCoordinator] owns backup import/export activity-result
 *   plumbing, including the widget-bind permission flow.
 */
class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModel()
    private val homeRepository: HomeRepository by inject()
    private val settingsRepository: SettingsReader by inject()
    private val actionShortcutRepository: ActionShortcutRepository by inject()
    private val widgetHost: WidgetHostPort by inject()

    private val searchViewModel: SearchViewModel by viewModel(
        parameters = { parametersOf(runtime.launcherNavigator.searchVisibilityFlow) }
    )
    private val appDrawerViewModel: AppDrawerViewModel by viewModel()
    private val settingsViewModel: SettingsViewModel by viewModel()
    private val defaultLauncherPromoter: DefaultLauncherPromoter by viewModel()
    private val appRepository: AppRepository by inject()
    private val contactsRepository: ContactsRepository by inject()
    private val filesRepository:
            com.milki.launcher.domain.repository.FilesRepository by inject()
    private val widgetPickerCatalogStore: WidgetPickerCatalogStore by inject()
    private val appIconMemoryCache: AppIconMemoryCache by inject()
    private val contextDataCache: AppContextDataCache by inject()

    private lateinit var runtime: LauncherHostRuntime
    private lateinit var backupCoordinator: BackupImportExportCoordinator
    private lateinit var rootNavigation: RootNavigationController

    private val requestHomeRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val granted =
                result.resultCode == RESULT_OK || isAppDefaultLauncher(this)

            if (!granted) {
                openDefaultLauncherSettingsFallback()
            }

            defaultLauncherPromoter.refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backupCoordinator =
            BackupImportExportCoordinator(
                activity = this,
                settingsViewModel = settingsViewModel,
                widgetHost = widgetHost
            )
        backupCoordinator.initialize()

        traceSection("launcher.startup.mainActivity.onCreate") {
            traceSection("launcher.startup.runtime.setup") {
                runtime =
                    LauncherHostRuntime(
                        activity = this,
                        searchViewModelProvider = { searchViewModel },
                        homeViewModel = homeViewModel,
                        appDrawerViewModelProvider = { appDrawerViewModel },
                        appRepositoryProvider = { appRepository },
                        contactsRepositoryProvider = { contactsRepository },
                        filesRepositoryProvider = { filesRepository },
                        homeRepository = homeRepository,
                        widgetHost = widgetHost
                    )
                runtime.initialize()
                rootNavigation = RootNavigationController(
                    initial = MainRoute.Home,
                    onResetExtras = { runtime.launcherNavigator.clearTransientRoutes() }
                )
                restoreRootBackStack(savedInstanceState)
                runtime.handleInitialIntent(intent)
            }

            traceSection("launcher.startup.setContent") {
                setContent {
                    CompositionLocalProvider(
                        LocalAppIconMemoryCache provides appIconMemoryCache,
                        LocalAppContextDataCache provides contextDataCache
                    ) {
                        MainNavigationRoot()
                    }
                }
            }
        }
    }

    @Composable
    private fun MainNavigationRoot() {
        val rootBackStack = rootNavigation.backStack
        val showSetDefaultLauncherPrompt by
        defaultLauncherPromoter.showSetDefaultLauncherPrompt
            .collectAsStateWithLifecycle()

        NavDisplay(
            backStack = rootBackStack,
            onBack = { rootNavigation.pop() },
            entryProvider = { route ->
                when (route) {
                    MainRoute.Home ->
                        NavEntry(route) {
                            LauncherRootContent(
                                runtime = runtime,
                                onOpenSettings = {
                                    runtime.launcherNavigator.clearTransientRoutes()
                                    rootNavigation.pushIfAbsent(MainRoute.Settings)
                                },
                                showSetDefaultLauncherPrompt =
                                    showSetDefaultLauncherPrompt,
                                onSetDefaultLauncher = ::setAsDefaultLauncher,
                                onDismissSetDefaultLauncherPrompt =
                                    defaultLauncherPromoter::dismissPrompt,
                                searchViewModelProvider = { searchViewModel },
                                homeViewModel = homeViewModel,
                                appDrawerViewModelProvider = { appDrawerViewModel },
                                settingsRepository = settingsRepository,
                                appRepositoryProvider = { appRepository },
                                actionShortcutRepository =
                                    actionShortcutRepository,
                                widgetHost = widgetHost,
                                obtainWidgetPickerCatalogStore = {
                                    widgetPickerCatalogStore
                                }
                            )
                        }

                    MainRoute.Settings ->
                        NavEntry(route) {
                            SettingsRootContent(
                                onExitSettings = { rootNavigation.pop() }
                            )
                        }
                }
            }
        )
    }

    @Composable
    private fun SettingsRootContent(onExitSettings: () -> Unit) {
        val settings by
        settingsViewModel.settings.collectAsStateWithLifecycle()
        val installedApps by
        settingsViewModel.installedApps.collectAsStateWithLifecycle()
        val actionShortcuts by
        settingsViewModel.actionShortcuts.collectAsStateWithLifecycle()
        val backupStatusMessage = remember { mutableStateOf<String?>(null) }
        LaunchedEffect(settingsViewModel) {
            settingsViewModel.backupStatusEvents.collect { event ->
                backupStatusMessage.value = event.message
            }
        }
        val importReport by
        settingsViewModel.lastImportReport.collectAsStateWithLifecycle()
        val importFileAccessPrompt by
        settingsViewModel.importFileAccessPrompt.collectAsStateWithLifecycle()
        val isDefaultLauncher by
        defaultLauncherPromoter.isDefaultLauncher.collectAsStateWithLifecycle()

        val settingsActions =
            rememberSettingsActions(
                settingsViewModel = settingsViewModel,
                onOpenDefaultLauncherSettings = ::openDefaultLauncherSettings,
                onExportBackup = backupCoordinator::launchExport,
                onImportBackup = backupCoordinator::launchImport
            )

        LauncherTheme {
            SettingsNavHost(
                settings = settings,
                installedApps = installedApps,
                actionShortcuts = actionShortcuts,
                showSetDefaultLauncherOption = !isDefaultLauncher,
                backupStatusMessage = backupStatusMessage.value,
                importReport = importReport,
                onDismissImportReport =
                    settingsViewModel::clearLastImportReport,
                importFileAccessPrompt = importFileAccessPrompt,
                onGrantImportFileAccess = {
                    val prompt = importFileAccessPrompt
                    backupCoordinator.requestMissingImportAccess(
                        needsFileAccess = (prompt?.pinnedFileCount ?: 0) > 0,
                        needsContactsAccess = (prompt?.pinnedContactCount ?: 0) > 0
                    ) {
                        settingsViewModel.continuePendingImportAfterFileAccessPrompt()
                    }
                },
                onSkipImportFileAccess =
                    settingsViewModel::continuePendingImportAfterFileAccessPrompt,
                actions = settingsActions,
                onExitSettings = onExitSettings
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(
            KEY_ROOT_BACK_STACK,
            ArrayList(rootNavigation.backStack.map { it.routeKey })
        )
    }

    private fun restoreRootBackStack(savedInstanceState: Bundle?) {
        val keys = savedInstanceState?.getStringArrayList(KEY_ROOT_BACK_STACK) ?: return
        val restored = keys.mapNotNull(::mainRouteFromKey)
        rootNavigation.backStack.clear()
        rootNavigation.backStack.addAll(restored.ifEmpty { listOf(MainRoute.Home) })
    }

    override fun onResume() {
        super.onResume()
        defaultLauncherPromoter.refresh()
        runtime.onResume()
    }

    override fun onPause() {
        super.onPause()
        runtime.onPause()
    }

    override fun onStart() {
        super.onStart()
        defaultLauncherPromoter.onForegroundSessionStarted()
        runtime.onStart()
    }

    override fun onStop() {
        super.onStop()
        runtime.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.isHomeIntent() && !rootNavigation.isAtHome()) {
            rootNavigation.resetToHome()
            return
        }

        if (intent.shouldNormalizeRootToHome()) {
            rootNavigation.resetToHome()
        }

        runtime.onNewIntent(intent)
    }

    @Deprecated("Required for AppWidgetHost configuration flows")
    @Suppress("DEPRECATION")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (runtime.onActivityResult(requestCode, resultCode)) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun setAsDefaultLauncher() {
        runtime.launcherNavigator.clearTransientRoutes()
        defaultLauncherPromoter.dismissPrompt()

        if (launchHomeRoleRequestIfNeeded(requestHomeRoleLauncher)) {
            return
        }

        openDefaultLauncherSettingsFallback()
    }

    private fun openDefaultLauncherSettings() {
        runtime.launcherNavigator.clearTransientRoutes()

        if (launchHomeRoleRequestIfNeeded(requestHomeRoleLauncher)) {
            return
        }

        openDefaultLauncherSettingsFallback()
    }

    private companion object {
        const val KEY_ROOT_BACK_STACK = "root_back_stack"
    }
}
