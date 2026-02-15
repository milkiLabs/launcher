/**
 * MainActivity.kt - The main entry point of the Milki Launcher
 *
 * This Activity serves as the primary launcher home screen. It:
 * 1. Hosts the Compose UI (LauncherScreen)
 * 2. Observes SearchViewModel state
 * 3. Handles SearchAction events (navigation, calls, etc.)
 * 4. Manages permissions
 *
 * ARCHITECTURE:
 * This Activity follows the "dumb View" pattern:
 * - State comes from SearchViewModel
 * - Actions are emitted by SearchViewModel
 * - Activity just renders UI and executes actions
 *
 * This keeps the Activity thin and the logic testable in the ViewModel.
 *
 * LIFECYCLE:
 * 1. onCreate: Setup ViewModel, observe state, observe actions
 * 2. onResume: Check permission status
 * 3. onNewIntent: Handle home button press (toggle search)
 */

package com.milki.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.milki.launcher.presentation.search.SearchAction
import com.milki.launcher.presentation.search.SearchViewModel
import com.milki.launcher.presentation.search.shouldCloseSearch
import com.milki.launcher.ui.screens.LauncherScreen
import com.milki.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.launch

/**
 * MainActivity
 *
 * As a launcher app, this Activity has special characteristics:
 * 1. It uses launchMode="singleTask" in AndroidManifest.xml
 * 2. It has intent filters for MAIN, HOME, DEFAULT, and LAUNCHER categories
 * 3. It appears as a home screen option in Android settings
 */
class MainActivity : ComponentActivity() {

    // ========================================================================
    // VIEWMODEL
    // ========================================================================

    /**
     * SearchViewModel instance obtained from the AppContainer.
     * Created lazily to ensure AppContainer is initialized first.
     */
    private val searchViewModel: SearchViewModel by lazy {
        (application as LauncherApplication).container.searchViewModelFactory.create(SearchViewModel::class.java)
    }

    // ========================================================================
    // PERMISSION LAUNCHER
    // ========================================================================

    /**
     * Activity result launcher for requesting contacts permission.
     */
    private lateinit var contactsPermissionLauncher: ActivityResultLauncher<String>

    // ========================================================================
    // ACTIVITY LIFECYCLE
    // ========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupPermissionLauncher()
        observeActions()
        updateContactsPermissionState()

        setContent {
            val uiState by searchViewModel.uiState.collectAsState()

            LauncherTheme {
                LauncherScreen(
                    uiState = uiState,
                    onShowSearch = { searchViewModel.showSearch() },
                    onQueryChange = { searchViewModel.onQueryChange(it) },
                    onDismissSearch = { searchViewModel.hideSearch() },
                    onResultClick = { result -> searchViewModel.onResultClick(result) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateContactsPermissionState()
    }

    // ========================================================================
    // ACTION OBSERVATION
    // ========================================================================

    /**
     * Observe SearchAction events from the ViewModel.
     * Actions are one-time events (navigation, calls, etc.)
     */
    private fun observeActions() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchViewModel.action.collect { action ->
                    handleAction(action)
                }
            }
        }
    }

    /**
     * Handle a SearchAction event.
     * Dispatches to the appropriate handler method.
     */
    private fun handleAction(action: SearchAction) {
        when (action) {
            is SearchAction.LaunchApp -> handleLaunchApp(action)
            is SearchAction.OpenWebSearch -> handleOpenWebSearch(action)
            is SearchAction.OpenYouTubeSearch -> handleOpenYouTubeSearch(action)
            is SearchAction.CallContact -> handleCallContact(action)
            is SearchAction.RequestContactsPermission -> requestContactsPermission()
            is SearchAction.CloseSearch -> searchViewModel.hideSearch()
            is SearchAction.ClearQuery -> searchViewModel.clearQuery()
        }
    }

    // ========================================================================
    // ACTION HANDLERS
    // ========================================================================

    /**
     * Launch an app and save it to recent apps.
     */
    private fun handleLaunchApp(action: SearchAction.LaunchApp) {
        action.appInfo.launchIntent?.let { startActivity(it) }
        searchViewModel.saveRecentApp(action.appInfo.packageName)
    }

    /**
     * Open a web search in the browser.
     */
    private fun handleOpenWebSearch(action: SearchAction.OpenWebSearch) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    /**
     * Open a YouTube search.
     * Tries YouTube app first, then browser fallback.
     */
    private fun handleOpenYouTubeSearch(action: SearchAction.OpenYouTubeSearch) {
        val query = action.query
        val youtubeUrl = "https://www.youtube.com/results?search_query=${Uri.encode(query)}"

        val youtubePackages = listOf(
            "app.revanced.android.youtube",
            "com.google.android.youtube"
        )

        for (packageName in youtubePackages) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl)).apply {
                `package` = packageName
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return
            }
        }

        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl))
        if (webIntent.resolveActivity(packageManager) != null) {
            startActivity(webIntent)
        }
    }

    /**
     * Make a phone call to a contact.
     * Opens the dialer with the number pre-filled.
     */
    private fun handleCallContact(action: SearchAction.CallContact) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${action.phoneNumber}")
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    // ========================================================================
    // PERMISSION HANDLING
    // ========================================================================

    /**
     * Sets up the permission launcher using the Activity Result API.
     */
    private fun setupPermissionLauncher() {
        contactsPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            searchViewModel.updateContactsPermission(isGranted)
        }
    }

    /**
     * Updates the contacts permission state by checking with PackageManager.
     */
    private fun updateContactsPermissionState() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        searchViewModel.updateContactsPermission(hasPermission)
    }

    /**
     * Requests the READ_CONTACTS permission from the user.
     */
    private fun requestContactsPermission() {
        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    // ========================================================================
    // INTENT HANDLING
    // ========================================================================

    /**
     * onNewIntent is called when the Activity receives a new Intent
     * while already running (not being created fresh).
     *
     * This happens when: User presses home button (sends MAIN action)
     *
     * We use this to toggle the search dialog when home is pressed.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.action == Intent.ACTION_MAIN) {
            val uiState = searchViewModel.uiState.value

            when {
                !uiState.isSearchVisible -> searchViewModel.showSearch()
                uiState.query.isNotEmpty() -> searchViewModel.clearQuery()
                else -> searchViewModel.hideSearch()
            }
        }
    }
}
