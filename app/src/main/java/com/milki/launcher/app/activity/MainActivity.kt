package com.milki.launcher.app.activity

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.milki.launcher.core.launcher.isAppDefaultLauncher
import com.milki.launcher.core.launcher.launchHomeRoleRequestIfNeeded
import com.milki.launcher.core.launcher.openDefaultLauncherSettingsFallback
import com.milki.launcher.core.launcher.syncLauncherIconVisibility
import com.milki.launcher.core.perf.traceSection
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.data.widget.WidgetPickerCatalogStore
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.HomeRepository
import com.milki.launcher.domain.repository.SettingsRepository
import com.milki.launcher.presentation.drawer.AppDrawerViewModel
import com.milki.launcher.presentation.home.HomeViewModel
import com.milki.launcher.presentation.search.SearchViewModel
import com.milki.launcher.presentation.launcher.host.LauncherHostRuntime
import com.milki.launcher.presentation.launcher.host.LauncherRootContent
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Android host shell for launcher home.
 *
 * STARTUP OPTIMIZATION:
 * Only dependencies that feed the first visible frame are resolved eagerly
 * (HomeViewModel, HomeRepository, WidgetHostManager). Everything else is wrapped
 * in provider lambdas so Koin construction is deferred until first actual use.
 *
 * Architecture split:
 * - [LauncherHostRuntime] owns lifecycle side effects and coordinator orchestration.
 * - [LauncherRootContent] collects ViewModel state and renders Compose UI.
 */
class MainActivity : ComponentActivity() {

    // ── Eagerly resolved: needed for the first visible frame ──────────
    private val homeViewModel: HomeViewModel by viewModel()
    private val homeRepository: HomeRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val widgetHostManager: WidgetHostManager by inject()

    // ── Lazily resolved: not needed for the first frame ───────────────
    private val searchViewModel: SearchViewModel by viewModel()
    private val appDrawerViewModel: AppDrawerViewModel by viewModel()
    private val appRepository: AppRepository by inject()
    private val contactsRepository: ContactsRepository by inject()
    private val widgetPickerCatalogStore: WidgetPickerCatalogStore by inject()

    private lateinit var runtime: LauncherHostRuntime
    private var showSetDefaultLauncherPrompt by mutableStateOf(false)
    private var hasPromptedForDefaultInForegroundSession = false

    private val requestHomeRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val granted =
                result.resultCode == RESULT_OK ||
                        isAppDefaultLauncher(this)
            if (!granted) {
                openDefaultLauncherSettingsFallback()
            }
            refreshDefaultLauncherPromptState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncLauncherIconVisibility(this)

        traceSection("launcher.startup.mainActivity.onCreate") {
            traceSection("launcher.startup.runtime.setup") {
                runtime = LauncherHostRuntime(
                    activity = this,
                    searchViewModelProvider = { searchViewModel },
                    homeViewModel = homeViewModel,
                    appDrawerViewModelProvider = { appDrawerViewModel },
                    appRepositoryProvider = { appRepository },
                    contactsRepositoryProvider = { contactsRepository },
                    homeRepository = homeRepository,
                    widgetHostManager = widgetHostManager
                )
                runtime.initialize()
                runtime.handleInitialIntent(intent)
            }

            traceSection("launcher.startup.setContent") {
                setContent {
                    LauncherRootContent(
                        runtime = runtime,
                        onOpenSettings = ::openSettings,
                        showSetDefaultLauncherPrompt = showSetDefaultLauncherPrompt,
                        onSetDefaultLauncher = ::setAsDefaultLauncher,
                        onDismissSetDefaultLauncherPrompt = {
                            showSetDefaultLauncherPrompt = false
                        },
                        searchViewModelProvider = { searchViewModel },
                        homeViewModel = homeViewModel,
                        appDrawerViewModelProvider = { appDrawerViewModel },
                        settingsRepository = settingsRepository,
                        widgetHostManager = widgetHostManager,
                        obtainWidgetPickerCatalogStore = { widgetPickerCatalogStore }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncLauncherIconVisibility(this)
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
        runtime.onNewIntent(intent)
    }

    @Deprecated("Required for AppWidgetHost configuration flows")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (runtime.onActivityResult(requestCode, resultCode)) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun openSettings() {
        val settingsIntent = Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(settingsIntent)
    }

    private fun setAsDefaultLauncher() {
        showSetDefaultLauncherPrompt = false
        if (launchHomeRoleRequestIfNeeded(requestHomeRoleLauncher)) {
            return
        }

        openDefaultLauncherSettingsFallback()
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
}
