package com.milki.launcher.app.activity

import android.app.Activity.RESULT_OK
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.milki.launcher.core.intent.toLauncherBenchmarkRequestOrNull
import com.milki.launcher.core.launcher.isAppDefaultLauncher
import com.milki.launcher.core.launcher.launchHomeRoleRequestIfNeeded
import com.milki.launcher.core.launcher.openDefaultLauncherSettingsFallback
import com.milki.launcher.core.perf.traceSection
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
import com.milki.launcher.presentation.settings.SettingsViewModel
import com.milki.launcher.ui.screens.settings.SettingsActions
import com.milki.launcher.ui.screens.settings.SettingsAdvancedActions
import com.milki.launcher.ui.screens.settings.SettingsFileSearchActions
import com.milki.launcher.ui.screens.settings.SettingsHomeScreenActions
import com.milki.launcher.ui.screens.settings.SettingsNavHost
import com.milki.launcher.ui.screens.settings.SettingsPrefixActions
import com.milki.launcher.ui.screens.settings.SettingsSourceActions
import com.milki.launcher.ui.theme.LauncherTheme
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

@Serializable
private sealed interface MainRoute : NavKey {
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
 */
class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModel()
    private val homeRepository: HomeRepository by inject()
    private val settingsRepository: SettingsReader by inject()
    private val actionShortcutRepository: ActionShortcutRepository by inject()
    private val widgetHost: WidgetHostPort by inject()

    private val searchViewModel: SearchViewModel by viewModel()
    private val appDrawerViewModel: AppDrawerViewModel by viewModel()
    private val settingsViewModel: SettingsViewModel by viewModel()
    private val appRepository: AppRepository by inject()
    private val contactsRepository: ContactsRepository by inject()
    private val filesRepository:
            com.milki.launcher.domain.repository.FilesRepository by inject()
    private val widgetPickerCatalogStore: WidgetPickerCatalogStore by inject()

    private lateinit var runtime: LauncherHostRuntime

    private var showSetDefaultLauncherPrompt by mutableStateOf(false)
    private var hasPromptedForDefaultInForegroundSession = false
    private var isDefaultLauncher by mutableStateOf(false)
    private var pendingWidgetPermissionResult: ((Boolean) -> Unit)? = null
    private var isRootAtHome: (() -> Boolean)? = null
    private var resetRootToHome: (() -> Unit)? = null

    private val requestWidgetBindPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            pendingWidgetPermissionResult?.invoke(result.resultCode == RESULT_OK)
            pendingWidgetPermissionResult = null
        }

    private val exportBackupLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                settingsViewModel.exportBackup(uri)
            }
        }

    private val importBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

                settingsViewModel.importBackup(uri) { bindRequest ->
                    val bindIntent =
                        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                            putExtra(
                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                bindRequest.appWidgetId
                            )
                            putExtra(
                                AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,
                                ComponentName(
                                    bindRequest.providerPackage,
                                    bindRequest.providerClass
                                )
                            )
                            putExtra(
                                AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE,
                                Process.myUserHandle()
                            )
                        }

                    awaitActivityResult(
                        launcher = requestWidgetBindPermissionLauncher,
                        intent = bindIntent
                    )
                }
            }
        }

    private val requestHomeRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val granted =
                result.resultCode == RESULT_OK || isAppDefaultLauncher(this)

            if (!granted) {
                openDefaultLauncherSettingsFallback()
            }

            refreshLauncherDefaultState()
            refreshDefaultLauncherPromptState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshLauncherDefaultState()

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
                runtime.handleInitialIntent(intent)
            }

            traceSection("launcher.startup.setContent") {
                setContent {
                    MainNavigationRoot()
                }
            }
        }
    }

    @Composable
    private fun MainNavigationRoot() {
        val rootBackStack = rememberNavBackStack(MainRoute.Home)

        DisposableEffect(rootBackStack) {
            isRootAtHome = {
                rootBackStack.lastOrNull() == MainRoute.Home
            }
            resetRootToHome = {
                while (rootBackStack.size > 1) {
                    rootBackStack.removeLastOrNull()
                }
                runtime.launcherNavigator.clearTransientRoutes()
            }

            onDispose {
                isRootAtHome = null
                resetRootToHome = null
            }
        }

        NavDisplay(
            backStack = rootBackStack,
            onBack = {
                if (rootBackStack.size > 1) {
                    rootBackStack.removeLastOrNull()
                }
            },
            entryProvider = { route ->
                when (route) {
                    MainRoute.Home ->
                        NavEntry(route) {
                            LauncherRootContent(
                                runtime = runtime,
                                onOpenSettings = {
                                    runtime.launcherNavigator.clearTransientRoutes()
                                    if (rootBackStack.lastOrNull() != MainRoute.Settings) {
                                        rootBackStack.add(MainRoute.Settings)
                                    }
                                },
                                showSetDefaultLauncherPrompt =
                                    showSetDefaultLauncherPrompt,
                                onSetDefaultLauncher = ::setAsDefaultLauncher,
                                onDismissSetDefaultLauncherPrompt = {
                                    showSetDefaultLauncherPrompt = false
                                },
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
                                onExitSettings = {
                                    rootBackStack.removeLastOrNull()
                                }
                            )
                        }

                    else -> error("Unknown main route: $route")
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
        val backupStatusMessage by
        settingsViewModel.backupStatusMessage.collectAsStateWithLifecycle()
        val importReport by
        settingsViewModel.lastImportReport.collectAsStateWithLifecycle()

        val settingsActions =
            remember(settingsViewModel) {
                SettingsActions(
                    onOpenDefaultLauncherSettings =
                        ::openDefaultLauncherSettings,
                    onSetSearchLayout = settingsViewModel::setSearchLayout,
                    homeScreen =
                        SettingsHomeScreenActions(
                            onSetTriggerAction =
                                settingsViewModel::setTriggerAction,
                            onSetTriggerOpenAppTarget =
                                settingsViewModel::setTriggerOpenAppTarget
                        ),
                    sources =
                        SettingsSourceActions(
                            onAddSource =
                                settingsViewModel::addSearchSource,
                            onUpdateSource =
                                settingsViewModel::updateSearchSource,
                            onDeleteSource =
                                settingsViewModel::deleteSearchSource,
                            onSetSourceEnabled =
                                settingsViewModel::setSearchSourceEnabled,
                            onSetSourceSuggestedAction =
                                settingsViewModel::setSearchSourceSuggestedAction,
                            onSetDefaultSource =
                                settingsViewModel::setDefaultSearchSource,
                            prefixes =
                                SettingsPrefixActions(
                                    onAddPrefix =
                                        settingsViewModel::addPrefix,
                                    onRemovePrefix =
                                        settingsViewModel::removePrefix,
                                    onResetPrefixes =
                                        settingsViewModel::resetPrefixes
                                )
                        ),
                    fileSearch =
                        SettingsFileSearchActions(
                            onToggleCategory =
                                settingsViewModel::toggleFileSearchCategory,
                            onAddCustomExtension =
                                settingsViewModel::addCustomFileExtension,
                            onRemoveCustomExtension =
                                settingsViewModel::removeCustomFileExtension
                        ),
                    advanced =
                        SettingsAdvancedActions(
                            onResetToDefaults =
                                settingsViewModel::resetToDefaults,
                            onExportBackup = {
                                val suggestedName =
                                    "launcher-backup-${System.currentTimeMillis()}.json"
                                exportBackupLauncher.launch(suggestedName)
                            },
                            onImportBackup = {
                                importBackupLauncher.launch(
                                    arrayOf("application/json", "*/*")
                                )
                            }
                        )
                )
            }

        LauncherTheme {
            SettingsNavHost(
                settings = settings,
                installedApps = installedApps,
                actionShortcuts = actionShortcuts,
                showSetDefaultLauncherOption = !isDefaultLauncher,
                backupStatusMessage = backupStatusMessage,
                importReport = importReport,
                onDismissImportReport =
                    settingsViewModel::clearLastImportReport,
                actions = settingsActions,
                onExitSettings = onExitSettings
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLauncherDefaultState()
        refreshDefaultLauncherPromptState()
        runtime.onResume()
    }

    override fun onPause() {
        super.onPause()
        runtime.onPause()
    }

    override fun onStart() {
        super.onStart()
        hasPromptedForDefaultInForegroundSession = false
        runtime.onStart()
    }

    override fun onStop() {
        super.onStop()
        runtime.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (isHomeIntent(intent) && isRootAtHome?.invoke() == false) {
            resetRootToHome?.invoke()
            return
        }

        if (shouldNormalizeRootToHome(intent)) {
            resetRootToHome?.invoke()
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
        showSetDefaultLauncherPrompt = false

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

    private fun refreshLauncherDefaultState() {
        isDefaultLauncher = isAppDefaultLauncher(this)
    }

    private fun refreshDefaultLauncherPromptState() {
        if (isAppDefaultLauncher(this)) {
            showSetDefaultLauncherPrompt = false
            return
        }

        if (!hasPromptedForDefaultInForegroundSession) {
            hasPromptedForDefaultInForegroundSession = true
            showSetDefaultLauncherPrompt = true
        }
    }

    private fun shouldNormalizeRootToHome(intent: Intent): Boolean {
        return intent.action == LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT ||
                intent.toLauncherBenchmarkRequestOrNull() != null
    }

    private fun isHomeIntent(intent: Intent): Boolean {
        return intent.action == Intent.ACTION_MAIN &&
                intent.hasCategory(Intent.CATEGORY_HOME)
    }

    private suspend fun awaitActivityResult(
        launcher: ActivityResultLauncher<Intent>,
        intent: Intent
    ): Boolean =
        suspendCancellableCoroutine { continuation ->
            pendingWidgetPermissionResult = { granted ->
                if (continuation.isActive) {
                    continuation.resume(granted)
                }
            }

            runCatching {
                launcher.launch(intent)
            }.onFailure {
                pendingWidgetPermissionResult = null
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }

            continuation.invokeOnCancellation {
                pendingWidgetPermissionResult = null
            }
        }
}
