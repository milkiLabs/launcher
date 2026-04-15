package com.milki.launcher.app.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
 * Architecture split:
 * - [LauncherHostRuntime] owns lifecycle side effects and coordinator orchestration.
 * - [LauncherRootContent] collects ViewModel state and renders Compose UI.
 */
class MainActivity : ComponentActivity() {

    private val searchViewModel: SearchViewModel by viewModel()
    private val homeViewModel: HomeViewModel by viewModel()
    private val appDrawerViewModel: AppDrawerViewModel by viewModel()
    private val appRepository: AppRepository by inject()
    private val contactsRepository: ContactsRepository by inject()
    private val homeRepository: HomeRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val widgetHostManager: WidgetHostManager by inject()
    private val widgetPickerCatalogStore: WidgetPickerCatalogStore by inject()

    private lateinit var runtime: LauncherHostRuntime

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        traceSection("launcher.startup.mainActivity.onCreate") {
            traceSection("launcher.startup.runtime.setup") {
                runtime = LauncherHostRuntime(
                    activity = this,
                    searchViewModel = searchViewModel,
                    homeViewModel = homeViewModel,
                    appDrawerViewModel = appDrawerViewModel,
                    appRepository = appRepository,
                    contactsRepository = contactsRepository,
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
                        searchViewModel = searchViewModel,
                        homeViewModel = homeViewModel,
                        appDrawerViewModel = appDrawerViewModel,
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
        runtime.onResume()
    }

    override fun onPause() {
        super.onPause()
        runtime.onPause()
    }

    override fun onStart() {
        super.onStart()
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
}
