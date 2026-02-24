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
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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
import com.milki.launcher.util.MimeTypeUtil
import com.milki.launcher.util.PermissionUtil
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
    // HOME BUTTON STATE TRACKING
    // ========================================================================

    /**
     * Tracks whether the user is currently on the homescreen (activity is
     * in the foreground and not returning from another app).
     *
     * This flag is the key to correctly handling the home button:
     * - Set to FALSE in onStop() (user left the homescreen)
     * - Set to TRUE in onResume() (user is now on the homescreen)
     * - Checked in onNewIntent() to decide behavior
     *
     * When onNewIntent fires:
     * - If wasAlreadyOnHomescreen == true → user was on homescreen, toggle dialog
     * - If wasAlreadyOnHomescreen == false → user is returning from an app,
     *   ensure dialog is closed
     */
    private var wasAlreadyOnHomescreen = false

    // ========================================================================
    // PERMISSION LAUNCHER
    // ========================================================================

    /**
     * Activity result launcher for requesting contacts permission.
     */
    private lateinit var contactsPermissionLauncher: ActivityResultLauncher<String>

    /**
     * Activity result launcher for requesting files/storage permission.
     * Used on Android 10 and below for READ_EXTERNAL_STORAGE.
     */
    private lateinit var filesPermissionLauncher: ActivityResultLauncher<String>
    
    /**
     * Activity result launcher for requesting MANAGE_EXTERNAL_STORAGE permission.
     * Used on Android 11+ to open Settings for "All files access" permission.
     * This is a special permission that cannot be granted via normal runtime permissions.
     */
    private lateinit var manageStorageLauncher: ActivityResultLauncher<Intent>

    // ========================================================================
    // ACTIVITY LIFECYCLE
    // ========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupPermissionLaunchers()
        observeActions()
        updateContactsPermissionState()
        updateFilesPermissionState()

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
        updateFilesPermissionState()
        // Mark that the user is now on the homescreen.
        // This is set AFTER onNewIntent would have already fired
        // (Android calls onNewIntent before onResume), so the flag
        // is only true when the user is already sitting on the homescreen.
        wasAlreadyOnHomescreen = true
    }

    override fun onStop() {
        super.onStop()
        // User has left the homescreen (another app is in the foreground).
        // Clear the flag so that the next onNewIntent knows the user
        // is returning from an app, not already on the homescreen.
        wasAlreadyOnHomescreen = false
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
            is SearchAction.OpenUrl -> handleOpenUrl(action)
            is SearchAction.CallContact -> handleCallContact(action)
            is SearchAction.OpenFile -> handleOpenFile(action)
            is SearchAction.RequestContactsPermission -> requestContactsPermission()
            is SearchAction.RequestFilesPermission -> requestFilesPermission()
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
     * 
     * This delegates to openUrlInBrowser() to avoid code duplication.
     * Both web searches and direct URLs use the same intent mechanism.
     */
    private fun handleOpenWebSearch(action: SearchAction.OpenWebSearch) {
        openUrlInBrowser(action.url)
    }

    /**
     * Open a URL directly in the browser.
     * 
     * Called when the user types a valid URL and taps the URL result.
     * This delegates to openUrlInBrowser() to avoid code duplication.
     */
    private fun handleOpenUrl(action: SearchAction.OpenUrl) {
        openUrlInBrowser(action.url)
    }

    /**
     * Opens a URL in the default browser.
     * 
     * This is a shared helper method used by both handleOpenWebSearch()
     * and handleOpenUrl() to avoid code duplication. Both actions need
     * to open URLs in the browser using the same intent mechanism.
     * 
     * HOW IT WORKS:
     * 1. Creates an ACTION_VIEW intent with the URL
     * 2. Checks if any app can handle the intent (resolveActivity)
     * 3. Starts the activity if an app is available
     * 
     * INTENT DETAILS:
     * - ACTION_VIEW: Standard action for viewing content
     * - Uri.parse(url): Converts the URL string to a Uri object
     * - resolveActivity(): Checks if any app can handle this intent
     *   (prevents crashes if no browser is installed)
     * 
     * @param url The URL to open (can be a web search URL or direct URL)
     * 
     * Example URLs:
     * - Web search: "https://www.google.com/search?q=android"
     * - Direct URL: "https://www.example.com"
     */
    private fun openUrlInBrowser(url: String) {
        /**
         * Create an intent to view the URL.
         * 
         * ACTION_VIEW tells Android to open the URL in an appropriate app.
         * For http/https URLs, this will typically open the default browser.
         */
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        
        /**
         * Check if any app can handle this intent.
         * 
         * resolveActivity() returns null if no app can handle the intent.
         * This prevents crashes if the user has no browser installed
         * (rare but possible on custom ROMs or enterprise devices).
         */
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

    /**
     * Open a file/document with an appropriate app.
     * 
     * This method creates an Intent to open the file using the system's
     * file association mechanism. The file will open in the default app
     * for its MIME type (e.g., PDF viewer for PDFs, word processor for docs).
     * 
     * INTENT FLAGS:
     * - FLAG_GRANT_READ_URI_PERMISSION: Grants the receiving app temporary
     *   read access to the file via its content URI
     * - FLAG_ACTIVITY_NEW_TASK: Starts the activity in a new task (standard for launcher)
     */
    private fun handleOpenFile(action: SearchAction.OpenFile) {
        val file = action.file
        
        // Use the file's MIME type, or guess from extension
        val mimeType = if (file.mimeType.isNotBlank()) {
            file.mimeType
        } else {
            // Guess MIME type from file extension using utility
            val extension = file.name.substringAfterLast('.', "").lowercase()
            MimeTypeUtil.getMimeTypeFromExtension(extension)
        }
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(file.uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Try to start the activity
        // Use createChooser to let user pick an app if there are multiple options
        val chooserIntent = Intent.createChooser(intent, "Open ${file.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(chooserIntent)
        } catch (e: Exception) {
            // If no app can handle the file, show a toast or error message
            android.widget.Toast.makeText(
                this,
                "No app found to open ${file.name}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ========================================================================
    // PERMISSION HANDLING
    // ========================================================================

    /**
     * Sets up the permission launchers using the Activity Result API.
     * Each permission has its own launcher for clean separation of concerns.
     */
    private fun setupPermissionLaunchers() {
        // Launcher for contacts permission
        contactsPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            searchViewModel.updateContactsPermission(isGranted)
        }

        // Launcher for files/storage permission (Android 10 and below)
        filesPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            searchViewModel.updateFilesPermission(isGranted)
        }
        
        // Launcher for MANAGE_EXTERNAL_STORAGE permission (Android 11+)
        // This opens Settings and the result is checked in onResume
        manageStorageLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // The result is handled in onResume where we check the actual permission state
            updateFilesPermissionState()
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
     * Updates the files permission state.
     * 
     * Permission requirements differ by Android version:
     * - Android 11+ (API 30+): Requires MANAGE_EXTERNAL_STORAGE (checked via Environment.isExternalStorageManager())
     * - Android 10 and below: Requires READ_EXTERNAL_STORAGE runtime permission
     */
    private fun updateFilesPermissionState() {
        val hasPermission = PermissionUtil.hasFilesPermission(this)

        searchViewModel.updateFilesPermission(hasPermission)
    }

    /**
     * Requests the READ_CONTACTS permission from the user.
     */
    private fun requestContactsPermission() {
        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    /**
     * Requests the appropriate storage permission based on Android version.
     * 
     * On Android 11+ (API 30+):
     *   Opens Settings for MANAGE_EXTERNAL_STORAGE permission ("All files access").
     *   This is a special permission that requires user action in Settings.
     * 
     * On Android 10 and below:
     *   Requests READ_EXTERNAL_STORAGE runtime permission via dialog.
     */
    private fun requestFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Open Settings for MANAGE_EXTERNAL_STORAGE
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            
            // Try the specific intent first, fall back to general manage files intent
            if (intent.resolveActivity(packageManager) != null) {
                manageStorageLauncher.launch(intent)
            } else {
                // Fallback to general "All files access" settings
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                if (fallbackIntent.resolveActivity(packageManager) != null) {
                    manageStorageLauncher.launch(fallbackIntent)
                }
            }
        } else {
            // Android 10 and below: Request READ_EXTERNAL_STORAGE via dialog
            filesPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
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
     * BEHAVIOR:
     * 1. If user is RETURNING from another app (wasAlreadyOnHomescreen == false):
     *    → Always close the dialog. The user just wants to go home.
     * 2. If user is ALREADY on the homescreen (wasAlreadyOnHomescreen == true):
     *    → Toggle the search dialog (open if closed, clear query or close if open).
     *
     * NOTE: Android calls onNewIntent BEFORE onResume. So when returning from
     * an app, the lifecycle is: onNewIntent → onResume. The flag is still false
     * during onNewIntent, which is exactly what we want.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.action == Intent.ACTION_MAIN) {
            if (!wasAlreadyOnHomescreen) {
                // User is returning from another app.
                // Ensure the search dialog is closed.
                searchViewModel.hideSearch()
            } else {
                // User is already on the homescreen.
                // Toggle the search dialog.
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
