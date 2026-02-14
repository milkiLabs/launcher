/**
 * MainActivity.kt - The main entry point of the Milki Launcher with multi-mode search
 *
 * This Activity serves as the primary launcher home screen. It supports
 * multi-mode searching via prefix shortcuts:
 * - No prefix: Search installed apps
 * - "s ": Web search (opens browser)
 * - "c ": Contacts search (requires permission, shows contacts in-app)
 * - "y ": YouTube search (opens YouTube app or browser)
 *
 * The contacts search feature integrates with the device's contacts database
 * and requires READ_CONTACTS permission. When permission is granted, contacts
 * are displayed in the search dialog and clicking a contact initiates a phone call.
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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel

import com.milki.launcher.data.repository.ContactsRepository
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.Contact

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
 *
 * The Activity handles multiple search types via prefix shortcuts:
 * - Apps: Direct app launching
 * - Web: Opens browser with search query
 * - Contacts: Searches device contacts (requires permission)
 * - YouTube: Opens YouTube with search query
 */
class MainActivity : ComponentActivity() {

    // ========================================================================
    // ACTIVITY STATE
    // ========================================================================

    /**
     * Controls visibility of the search dialog.
     *
     * This state is defined at the Activity level (not in Compose)
     * so it can be modified from multiple methods including onNewIntent().
     */
    private var showSearch by mutableStateOf(false)

    /**
     * Stores the current search query text.
     *
     * Like showSearch, this is Activity-level state so it can be accessed from onNewIntent().
     */
    private var searchQuery by mutableStateOf("")

    /**
     * Tracks whether READ_CONTACTS permission is granted.
     * Updated when permission is requested and when activity resumes.
     */
    private var hasContactsPermission by mutableStateOf(false)

    // ========================================================================
    // REPOSITORIES
    // ========================================================================

    /**
     * Repository for accessing device contacts.
     * Initialized lazily when first needed.
     */
    private val contactsRepository by lazy { ContactsRepository(this) }

    // ========================================================================
    // PERMISSION LAUNCHER
    // ========================================================================

    /**
     * Activity result launcher for requesting contacts permission.
     * Using the modern Activity Result API instead of deprecated onRequestPermissionsResult.
     */
    private lateinit var contactsPermissionLauncher: ActivityResultLauncher<String>

    // ========================================================================
    // ACTIVITY LIFECYCLE
    // ========================================================================

    /**
     * @param savedInstanceState Bundle with saved state (not used - we use Compose state)
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize permission launcher before setting content
        setupPermissionLauncher()

        // Check initial permission state
        updateContactsPermissionState()

        setContent {
            /**
             * Get or create the LauncherViewModel instance.
             *
             * viewModel() is a Compose function that:
             * - Creates a new ViewModel if one doesn't exist
             * - Returns the existing ViewModel if it does (survives configuration changes)
             * - Automatically scopes it to this Activity
             */
            val viewModel: LauncherViewModel = viewModel()

            LauncherTheme {
                // LauncherScreen is our main UI composable.
                LauncherScreen(
                    showSearch = showSearch,
                    searchQuery = searchQuery,

                    // Callback: User tapped home screen to open search
                    onShowSearch = { showSearch = true },

                    // Callback: User dismissed search (or pressed back)
                    onHideSearch = {
                        showSearch = false
                        searchQuery = "" // Clear query when closing
                    },

                    // Callback: User typed in search field
                    onSearchQueryChange = { searchQuery = it },

                    // Data: All installed apps from ViewModel
                    installedApps = viewModel.installedApps,

                    // Data: Recently launched apps from ViewModel
                    recentApps = viewModel.recentApps,

                    // Callback: User selected an app to launch
                    onLaunchApp = { appInfo ->
                        launchApp(appInfo, viewModel)
                    },

                    // Callback: User selected web search
                    onSearchWeb = { query, engine ->
                        performWebSearch(query, engine)
                    },

                    // Callback: User selected YouTube search
                    onSearchYouTube = { query ->
                        performYouTubeSearch(query)
                    },

                    // Contacts search configuration
                    hasContactsPermission = hasContactsPermission,
                    onRequestContactsPermission = { requestContactsPermission() },
                    searchContacts = { query -> searchContacts(query) },
                    onCallContact = { contact -> callContact(contact) }
                )
            }
        }
    }

    /**
     * Called when the activity is resumed.
     * Updates permission state in case user changed it in system settings.
     */
    override fun onResume() {
        super.onResume()
        updateContactsPermissionState()
    }

    // ========================================================================
    // PERMISSION HANDLING
    // ========================================================================

    /**
     * Sets up the permission launcher using the modern Activity Result API.
     * This must be called before onCreate finishes.
     */
    private fun setupPermissionLauncher() {
        contactsPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            // Update permission state based on user response
            hasContactsPermission = isGranted

            if (isGranted) {
                // Permission granted - refresh search if contacts mode is active
                // The search will automatically refresh due to state change
            }
        }
    }

    /**
     * Updates the contacts permission state by checking with PackageManager.
     * Called during initialization and on resume.
     */
    private fun updateContactsPermissionState() {
        hasContactsPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Requests the READ_CONTACTS permission from the user.
     * Shows system permission dialog.
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
     *
     * @param intent The new Intent that was received
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // Only handle MAIN action (which comes from home button)
        if (intent.action == Intent.ACTION_MAIN) {
            when {
                // CASE 1: Search is currently closed
                // ACTION: Open the search dialog
                !showSearch -> showSearch = true

                // CASE 2: Search is open and has text
                // ACTION: Clear the search text (but keep dialog open)
                searchQuery.isNotEmpty() -> searchQuery = ""

                // CASE 3: Search is open and empty
                // ACTION: Close the search dialog
                else -> showSearch = false
            }
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    /**
     * Launch an app and update recent apps.
     *
     * This method extracts the app launching logic into a separate
     * private method for clarity. It handles:
     * 1. Clearing search state
     * 2. Closing search dialog
     * 3. Starting the app activity
     * 4. Saving to recent apps via ViewModel
     *
     * @param appInfo The app to launch
     * @param viewModel The ViewModel for saving recent apps
     */
    private fun launchApp(appInfo: AppInfo, viewModel: LauncherViewModel) {
        // Clear search state
        searchQuery = ""
        showSearch = false

        // Launch the app using its stored Intent
        // ?.let ensures we only start if launchIntent exists
        appInfo.launchIntent?.let { startActivity(it) }

        // Save this app to recent apps list via ViewModel
        // The ViewModel will update the Repository, which updates DataStore,
        // which triggers a Flow emission, which updates the UI automatically!
        viewModel.saveRecentApp(appInfo.packageName)
    }

    /**
     * Searches contacts using the ContactsRepository.
     *
     * This is a suspend function that performs the actual contact search
     * against the device's contacts database.
     *
     * @param query The search query string
     * @return List of matching contacts
     */
    private suspend fun searchContacts(query: String): List<Contact> {
        return if (hasContactsPermission) {
            contactsRepository.searchContacts(query)
        } else {
            emptyList()
        }
    }

    /**
     * Initiates a phone call to the selected contact.
     *
     * Uses the contact's primary phone number to place a call.
     * Requires CALL_PHONE permission (handled by system dialer).
     *
     * @param contact The contact to call
     */
    private fun callContact(contact: Contact) {
        val phoneNumber = contact.phoneNumbers.firstOrNull() ?: return

        // Close search dialog
        searchQuery = ""
        showSearch = false

        // Create intent to dial the number
        // Using ACTION_DIAL instead of ACTION_CALL to avoid needing CALL_PHONE permission
        // The system dialer will open with the number pre-filled
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }

        // Check if there's a dialer available
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    /**
     * Perform a web search using the specified search engine.
     *
     * Opens the default web browser with the search query.
     * Supported engines: "google", "duckduckgo"
     *
     * @param query The search query string
     * @param engine The search engine to use
     */
    private fun performWebSearch(query: String, engine: String) {
        // Clear search state and close dialog
        searchQuery = ""
        showSearch = false

        // Build the search URL based on engine
        val searchUrl = when (engine.lowercase()) {
            "google" -> "https://www.google.com/search?q=${Uri.encode(query)}"
            "duckduckgo" -> "https://duckduckgo.com/?q=${Uri.encode(query)}"
            else -> "https://www.google.com/search?q=${Uri.encode(query)}"
        }

        // Create intent to open browser
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))

        // Check if there's a browser available
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    /**
     * Perform a YouTube search.
     *
     * Tries multiple YouTube app packages in order of preference:
     * 1. ReVanced YouTube (app.revanced.android.youtube)
     * 2. Official YouTube (com.google.android.youtube)
     * 3. Browser fallback (youtube.com)
     *
     * Uses YouTube search URL with package restriction to force opening in app.
     *
     * @param query The video search query
     */
    private fun performYouTubeSearch(query: String) {
        // Clear search state and close dialog
        searchQuery = ""
        showSearch = false

        // Build YouTube search URL
        val youtubeUrl = "https://www.youtube.com/results?search_query=${Uri.encode(query)}"

        // List of YouTube app packages to try in order
        val youtubePackages = listOf(
            "app.revanced.android.youtube",  // ReVanced YouTube (preferred)
            "com.google.android.youtube"     // Official YouTube
        )

        // Try each YouTube app package in order
        for (packageName in youtubePackages) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl)).apply {
                `package` = packageName
                // Add FLAG_ACTIVITY_NEW_TASK to ensure proper task handling
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Check if this app is installed and can handle the intent
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return  // Successfully launched, we're done
            }
        }

        // No YouTube app found, fallback to browser
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl))

        // Check if there's a browser available
        if (webIntent.resolveActivity(packageManager) != null) {
            startActivity(webIntent)
        }
    }
}
