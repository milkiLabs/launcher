/**
 * ActionHandler.kt - Handles all search actions by delegating to appropriate handlers
 *
 * This class is the heart of the launcher's action execution. When a user
 * taps a search result (app, contact, file, web link, etc.), the ViewModel
 * emits a SearchAction, and this handler executes it.
 *
 * FUTURE CUSTOMIZATION (via Settings):
 * - Browser preference: Chrome, Firefox, Brave, or custom package
 * - YouTube preference: Official YouTube, ReVanced, or browser fallback
 * - Search engine: Google, DuckDuckGo, Bing, etc.
 * - File handling: Default app chooser or always ask
 *
 * ARCHITECTURE:
 * ┌─────────────────┐    SearchAction   ┌─────────────────┐   execute()    ┌─────────────────┐
 * │ SearchViewModel │ ───────────────►  │ ActionHandler   │ ─────────────► │  System/App     │
 * │ (decides what)  │                   │ (does it)       │                │  (receives it)  │
 * └─────────────────┘                   └─────────────────┘                └─────────────────┘
 */

package com.milki.launcher.handlers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.milki.launcher.presentation.search.SearchAction
import com.milki.launcher.presentation.search.SearchViewModel
import com.milki.launcher.util.MimeTypeUtil

/**
 * Handles all SearchAction events by executing the appropriate system operations.
 *
 * Each SearchAction subtype has a corresponding handler method. The handler:
 * 1. Extracts necessary data from the action
 * 2. Creates the appropriate Intent
 * 3. Starts the activity (app, browser, dialer, etc.)
 * 4. Optionally saves state (like recent apps)
 *
 * CONTEXT VS ACTIVITY:
 * We only need Context for most operations, not the full Activity.
 * This makes the handler more flexible and easier to test.
 * The only reason we'd need Activity is for startActivityForResult,
 * but we use the Activity Result API for that (handled elsewhere).
 *
 * @property context The context used to start activities and access system services
 * @property searchViewModel The ViewModel to update state (like saving recent apps)
 */
class ActionHandler(
    private val context: Context,
    private val searchViewModel: SearchViewModel
) {
    // ========================================================================
    // MAIN ENTRY POINT
    // ========================================================================

    /**
     * Handles a SearchAction by dispatching to the appropriate handler method.
     *
     * This is the main entry point. The Activity calls this method when
     * observing the ViewModel's action flow. The when expression ensures
     * we handle all possible action types exhaustively.
     *
     * @param action The action to execute
     */
    fun handle(action: SearchAction) {
        when (action) {
            is SearchAction.LaunchApp -> handleLaunchApp(action)
            is SearchAction.OpenWebSearch -> handleOpenWebSearch(action)
            is SearchAction.OpenYouTubeSearch -> handleOpenYouTubeSearch(action)
            is SearchAction.OpenUrl -> handleOpenUrl(action)
            is SearchAction.CallContact -> handleCallContact(action)
            is SearchAction.OpenFile -> handleOpenFile(action)
            is SearchAction.RequestContactsPermission,
            is SearchAction.RequestFilesPermission,
            is SearchAction.CloseSearch,
            is SearchAction.ClearQuery -> {
            }
        }
    }

    // ========================================================================
    // APP LAUNCHING
    // ========================================================================

    /**
     * Launches an app and saves it to recent apps.
     *
     * @param action Contains the AppInfo with the launch intent
     */
    private fun handleLaunchApp(action: SearchAction.LaunchApp) {
        action.appInfo.launchIntent?.let { intent ->
            context.startActivity(intent)
        }
        searchViewModel.saveRecentApp(action.appInfo.packageName)
    }

    // ========================================================================
    // WEB BROWSING
    // ========================================================================

    /**
     * Opens a web search URL in the default browser.
     *
     *  The URL is already constructed by the ViewModel with the proper search engine
     * and query encoding.
     *
     * @param action Contains the URL to open
     */
    private fun handleOpenWebSearch(action: SearchAction.OpenWebSearch) {
        openUrlInBrowser(action.url)
    }

    /**
     * Opens a URL directly in the browser.
     *
     * Called when the user types a valid URL and taps the URL result.
     * This provides a shortcut for navigating to websites without
     * needing to use the "s " prefix for web search.
     *
     * @param action Contains the URL to open
     */
    private fun handleOpenUrl(action: SearchAction.OpenUrl) {
        openUrlInBrowser(action.url)
    }

    /**
     * Opens a URL in the default browser.
     *
     * Modern approach: We call startActivity() directly and catch
     * ActivityNotFoundException if no browser is installed. This is
     * preferred over resolveActivity() because:
     * - Avoids race conditions (app could uninstall between check and launch)
     * - Reduces PackageManager queries (no double lookup)
     * - Official Android recommendation
     *
     * @param url The URL to open (can be a web search URL or direct URL)
     */
    private fun openUrlInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No browser installed - show a toast to let the user know
            Toast.makeText(context, "No browser app found", Toast.LENGTH_SHORT).show()
        }
    }

    // ========================================================================
    // YOUTUBE HANDLING
    // ========================================================================

    /**
     * Opens a YouTube search, preferring the app over browser.
     *
     * This method tries multiple YouTube packages in order:
     * 1. ReVanced (popular modded YouTube client)
     * 2. Official YouTube app
     * 3. Browser fallback
     *
     * WHY THIS ORDER?
     * - ReVanced users typically prefer it over official YouTube
     * - Official YouTube is the standard fallback
     * - Browser is the universal fallback that always works
     *
     * CUSTOMIZATION POTENTIAL:
     * In the future, this list could be:
     * - Configurable via settings
     * - Sourced from a repository
     * - Based on installed apps
     *
     * @param action Contains the search query
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

            try {
                context.startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // This package isn't installed, try the next one
            }
        }

        // Fallback to browser if no YouTube app is installed
        openUrlInBrowser(youtubeUrl)
    }

    // ========================================================================
    // PHONE CALLING
    // ========================================================================

    /**
     * Opens the phone dialer with a phone number pre-filled.
     *
     * This does NOT make the call directly - it opens the dialer app
     * with the number filled in. The user still needs to press the
     * call button. This is the standard Android behavior for security.
     *
     * WHY ACTION_DIAL INSTEAD OF ACTION_CALL?
     * - ACTION_CALL would dial immediately (requires CALL_PHONE permission)
     * - ACTION_DIAL is safer and doesn't require special permission
     * - Users expect to see the number before calling
     *
     * @param action Contains the phone number to dial
     */
    private fun handleCallContact(action: SearchAction.CallContact) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${action.phoneNumber}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No dialer app installed (very rare on Android)
            Toast.makeText(context, "No phone app found", Toast.LENGTH_SHORT).show()
        }
    }

    // ========================================================================
    // FILE OPENING
    // ========================================================================

    /**
     * Opens a file with an appropriate app.
     *
     * This method creates an Intent to open the file using the system's
     * file association mechanism. The file will open in the default app
     * for its MIME type (e.g., PDF viewer for PDFs, word processor for docs).
     *
     * MIME TYPE DETERMINATION:
     * 1. First, we try to use the MIME type stored in the FileDocument
     *    (this might come from the MediaStore or file system)
     * 2. If that's empty, we guess from the file extension
     *
     * INTENT FLAGS:
     * - FLAG_GRANT_READ_URI_PERMISSION: Grants the receiving app temporary
     *   read access to the file via its content URI
     * - FLAG_ACTIVITY_NEW_TASK: Starts the activity in a new task
     *
     * CHOOSER DIALOG:
     * We use createChooser() so users can pick an app if multiple can
     * handle the file type. This is better than forcing a default.
     *
     * @param action Contains the file to open
     */
    private fun handleOpenFile(action: SearchAction.OpenFile) {
        val file = action.file

        val mimeType = if (file.mimeType.isNotBlank()) {
            file.mimeType
        } else {
            val extension = file.name.substringAfterLast('.', "").lowercase()
            MimeTypeUtil.getMimeTypeFromExtension(extension)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(file.uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooserIntent = Intent.createChooser(intent, "Open ${file.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "No app found to open ${file.name}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
