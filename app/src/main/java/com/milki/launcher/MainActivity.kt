/**
 * MainActivity.kt - The main entry point of the Milki Launcher
 */

package com.milki.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.HomeRepository
import com.milki.launcher.handlers.PermissionHandler
import com.milki.launcher.presentation.home.HomeViewModel
import com.milki.launcher.presentation.search.ActionExecutor
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.presentation.search.SearchViewModel
import com.milki.launcher.ui.screens.LauncherScreen
import com.milki.launcher.ui.screens.openPinnedItem
import com.milki.launcher.ui.theme.LauncherTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * MainActivity - The launcher's home screen Activity.
 */
class MainActivity : ComponentActivity() {

    private val searchViewModel: SearchViewModel by viewModel()
    private val homeViewModel: HomeViewModel by viewModel()
    private val contactsRepository: ContactsRepository by inject()
    private val homeRepository: HomeRepository by inject()

    private lateinit var permissionHandler: PermissionHandler
    private lateinit var actionExecutor: ActionExecutor

    private var wasAlreadyOnHomescreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeHandlers()

        setContent {
            val searchUiState by searchViewModel.uiState.collectAsStateWithLifecycle()
            val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current

            CompositionLocalProvider(
                LocalSearchActionHandler provides { action: SearchResultAction ->
                    actionExecutor.execute(action, permissionHandler::hasPermission)
                }
            ) {
                LauncherTheme {
                    LauncherScreen(
                        searchUiState = searchUiState,
                        homeUiState = homeUiState,
                        onShowSearch = { searchViewModel.showSearch() },
                        onQueryChange = { searchViewModel.onQueryChange(it) },
                        onDismissSearch = { searchViewModel.hideSearch() },
                        onPinnedItemClick = { item ->
                            openPinnedItem(item, context)
                        }
                    )
                }
            }
        }
    }

    private fun initializeHandlers() {
        permissionHandler = PermissionHandler(this, searchViewModel)
        permissionHandler.setup()
        
        actionExecutor = ActionExecutor(this, contactsRepository, homeRepository)
        
        actionExecutor.onRequestPermission = { permission ->
            when (permission) {
                android.Manifest.permission.READ_CONTACTS -> {
                    permissionHandler.requestContactsPermission()
                }
                android.Manifest.permission.CALL_PHONE -> {
                    permissionHandler.requestCallPermission()
                }
                android.Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE -> {
                    permissionHandler.requestFilesPermission()
                }
            }
        }
        
        actionExecutor.onCloseSearch = {
            searchViewModel.hideSearch()
        }
        
        actionExecutor.onSaveRecentApp = { componentName ->
            searchViewModel.saveRecentApp(componentName)
        }
        
        permissionHandler.onCallPermissionResult = { granted ->
            actionExecutor.onPermissionResult(granted)
        }
    }

    override fun onResume() {
        super.onResume()
        permissionHandler.updateStates()
        wasAlreadyOnHomescreen = true
    }

    override fun onStop() {
        super.onStop()
        wasAlreadyOnHomescreen = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (!wasAlreadyOnHomescreen) {
                searchViewModel.hideSearch()
            } else {
                val uiState = searchViewModel.uiState.value
                when {
                    !uiState.isSearchVisible -> searchViewModel.showSearch()
                    uiState.query.isNotEmpty() -> searchViewModel.clearQuery()
                    else -> searchViewModel.hideSearch()
                }
            }
        }
    }
}
