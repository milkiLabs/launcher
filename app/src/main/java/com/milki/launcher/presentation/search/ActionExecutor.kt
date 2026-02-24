/**
 * PermissionAwareAction.kt - Base class for actions requiring runtime permissions
 *
 * This file provides a pattern for handling actions that require Android
 * runtime permissions. It centralizes the permission check/request/execute flow.
 *
 * WHY THIS PATTERN:
 * Without this pattern, permission handling was scattered:
 * - CALL_PHONE: Handled in ViewModel with pending state
 * - READ_CONTACTS: Handled via PermissionRequestResult in search results
 * - READ_EXTERNAL_STORAGE: Handled via PermissionRequestResult
 *
 * This created inconsistency and made it hard to understand the flow.
 *
 * With PermissionAwareAction:
 * - All permission-requiring actions follow the same pattern
 * - Clear separation: check → request → execute
 * - Easy to add new permission-requiring actions
 *
 * FLOW:
 * 1. UI triggers SearchResultAction
 * 2. ActionExecutor checks if action requires permission
 * 3. If permission granted: execute immediately
 * 4. If permission not granted: store pending action, request permission
 * 5. On permission result: execute pending action if granted
 */

package com.milki.launcher.presentation.search

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.milki.launcher.domain.model.*
import com.milki.launcher.domain.repository.ContactsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Represents a pending action that requires a permission.
 *
 * This is stored when a user triggers an action that needs a permission
 * they haven't granted yet. Once they grant the permission, the action
 * is executed.
 *
 * @property action The original SearchResultAction that triggered this
 * @property requiredPermission The Android permission string needed
 */
data class PendingPermissionAction(
    val action: SearchResultAction,
    val requiredPermission: String
)

/**
 * Executes a SearchResultAction, handling permissions if needed.
 *
 * This class centralizes all action execution logic:
 * - Permission checking and requesting
 * - Pending action storage
 * - Actual action execution
 * - Fallback handling
 *
 * @property context Android context for starting activities
 * @property contactsRepository Repository for contacts (for saving recent contacts)
 */
class ActionExecutor(
    private val context: Context,
    private val contactsRepository: ContactsRepository
) {
    /**
     * Coroutine scope for background operations like saving recent contacts.
     * Uses SupervisorJob so failures don't cancel other operations.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * The currently pending action waiting for permission.
     * Null if no action is pending.
     */
    var pendingAction: PendingPermissionAction? = null
        private set
    
    /**
     * Callback to request a permission from the UI layer.
     * Set by the Activity when creating the executor.
     */
    var onRequestPermission: ((String) -> Unit)? = null
    
    /**
     * Callback to close the search dialog.
     * Set by the Activity when creating the executor.
     */
    var onCloseSearch: (() -> Unit)? = null
    
    /**
     * Callback to save a recent app.
     * Set by the Activity when creating the executor.
     */
    var onSaveRecentApp: ((String) -> Unit)? = null

    /**
     * Execute a SearchResultAction.
     *
     * This is the main entry point. It checks if the action requires
     * permission and either executes immediately or requests permission.
     *
     * @param action The action to execute
     * @param hasPermission Function to check if a permission is granted
     */
    fun execute(
        action: SearchResultAction,
        hasPermission: (String) -> Boolean
    ) {
        val requiredPermission = action.requiredPermission()
        
        if (requiredPermission != null && !hasPermission(requiredPermission)) {
            // Permission required but not granted - store pending and request
            pendingAction = PendingPermissionAction(action, requiredPermission)
            onRequestPermission?.invoke(requiredPermission)
        } else {
            // No permission needed or already granted - execute immediately
            executeAction(action)
        }
    }

    /**
     * Handle permission result.
     *
     * Called by the Activity when the user responds to a permission request.
     * If granted and there's a pending action, executes it.
     *
     * @param granted Whether the permission was granted
     */
    fun onPermissionResult(granted: Boolean) {
        val pending = pendingAction
        pendingAction = null
        
        if (granted && pending != null) {
            executeAction(pending.action)
        }
    }

    /**
     * Execute an action without permission checking.
     *
     * This is called internally after permission checks pass.
     * It dispatches to the appropriate handler method.
     */
    private fun executeAction(action: SearchResultAction) {
        when (action) {
            is SearchResultAction.Tap -> handleTap(action)
            is SearchResultAction.DialContact -> handleDialContact(action)
            is SearchResultAction.OpenUrlInBrowser -> handleOpenUrlInBrowser(action)
            is SearchResultAction.RequestPermission -> handleRequestPermission(action)
        }
        
        if (action.shouldCloseSearch()) {
            onCloseSearch?.invoke()
        }
    }

    // ========================================================================
    // TAP ACTION HANDLERS
    // ========================================================================

    /**
     * Handle a tap on a search result.
     *
     * Dispatches to the appropriate handler based on result type.
     */
    private fun handleTap(action: SearchResultAction.Tap) {
        when (val result = action.result) {
            is AppSearchResult -> launchApp(result)
            is WebSearchResult -> openWebSearch(result)
            is YouTubeSearchResult -> openYouTubeSearch(result)
            is UrlSearchResult -> openUrl(result)
            is ContactSearchResult -> callContact(result)
            is FileDocumentSearchResult -> openFile(result)
            is PermissionRequestResult -> {
                // This shouldn't happen - RequestPermission action should be used
                // But handle it gracefully
                handleRequestPermission(SearchResultAction.RequestPermission(
                    result.permission,
                    result.providerPrefix
                ))
            }
        }
    }

    /**
     * Launch an app.
     */
    private fun launchApp(result: AppSearchResult) {
        result.appInfo.launchIntent?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        
        // Save to recent apps
        val componentName = android.content.ComponentName(
            result.appInfo.packageName,
            result.appInfo.activityName
        ).flattenToString()
        onSaveRecentApp?.invoke(componentName)
    }

    /**
     * Open a web search URL.
     */
    private fun openWebSearch(result: WebSearchResult) {
        openUrlInBrowser(result.url)
    }

    /**
     * Open a YouTube search.
     */
    private fun openYouTubeSearch(result: YouTubeSearchResult) {
        // Try to find YouTube app, fallback to browser
        val youtubeUrl = "https://www.youtube.com/results?search_query=${Uri.encode(result.query)}"
        
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        // Try to find a YouTube app
        val pm = context.packageManager
        val resolved = pm.queryIntentActivities(intent, 0)
        val youtubePackage = resolved.firstOrNull {
            it.activityInfo.packageName.contains("youtube", ignoreCase = true)
        }?.activityInfo?.packageName
        
        if (youtubePackage != null) {
            intent.setPackage(youtubePackage)
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            openUrlInBrowser(youtubeUrl)
        }
    }

    /**
     * Open a URL result.
     */
    private fun openUrl(result: UrlSearchResult) {
        result.handlerApp?.let { handler ->
            // Open with specific app
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.url)).apply {
                `package` = handler.packageName
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                openUrlInBrowser(result.url)
            }
        } ?: openUrlInBrowser(result.url)
    }

    /**
     * Open a URL in the default browser.
     */
    private fun openUrlInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No browser app found", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Call a contact (opens dialer).
     */
    private fun callContact(result: ContactSearchResult) {
        val phone = result.contact.phoneNumbers.firstOrNull() ?: return
        
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phone")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No phone app found", Toast.LENGTH_SHORT).show()
        }
        
        // Save to recent contacts
        saveRecentContact(phone)
    }

    /**
     * Open a file.
     */
    private fun openFile(result: FileDocumentSearchResult) {
        val file = result.file
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(file.uri, file.mimeType.ifBlank { "application/octet-stream" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val chooserIntent = Intent.createChooser(intent, "Open ${file.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open ${file.name}", Toast.LENGTH_SHORT).show()
        }
    }

    // ========================================================================
    // DIAL CONTACT HANDLER
    // ========================================================================

    /**
     * Handle direct dial action.
     *
     * Makes a call directly using ACTION_CALL (requires CALL_PHONE permission).
     * Permission should already be checked before calling this.
     */
    private fun handleDialContact(action: SearchResultAction.DialContact) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${action.phoneNumber}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            context.startActivity(intent)
        } catch (e: SecurityException) {
            // This shouldn't happen since we check permission first
            Toast.makeText(context, "Call permission not granted", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "No phone app found", Toast.LENGTH_SHORT).show()
        }
        
        // Save to recent contacts
        saveRecentContact(action.phoneNumber)
    }

    // ========================================================================
    // OPEN IN BROWSER HANDLER
    // ========================================================================

    /**
     * Handle open URL in browser action.
     */
    private fun handleOpenUrlInBrowser(action: SearchResultAction.OpenUrlInBrowser) {
        openUrlInBrowser(action.url)
    }

    // ========================================================================
    // PERMISSION REQUEST HANDLER
    // ========================================================================

    /**
     * Handle a permission request action.
     *
     * This is triggered when user taps a PermissionRequestResult.
     * It requests the permission via the callback.
     */
    private fun handleRequestPermission(action: SearchResultAction.RequestPermission) {
        onRequestPermission?.invoke(action.permission)
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Save a phone number to recent contacts.
     */
    private fun saveRecentContact(phoneNumber: String) {
        scope.launch {
            contactsRepository.saveRecentContact(phoneNumber)
        }
    }
}
