/**
 * ActionExecutor.kt - Central handler for all user actions
 *
 * This class centralizes all action execution logic:
 * - Permission checking and requesting
 * - App launching and pinning
 * - File opening and pinning
 * - URL handling
 * - Pending action storage
 *
 * ARCHITECTURE:
 * All actions flow through this single executor, ensuring consistent
 * behavior and centralizing side effects.
 */

package com.milki.launcher.presentation.search

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.milki.launcher.domain.model.*
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.HomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Represents a pending action that requires a permission.
 */
data class PendingPermissionAction(
    val action: SearchResultAction,
    val requiredPermission: String
)

/**
 * Executes all user actions, handling permissions if needed.
 *
 * @property context Android context for starting activities
 * @property contactsRepository Repository for contacts
 * @property homeRepository Repository for pinned items
 */
class ActionExecutor(
    private val context: Context,
    private val contactsRepository: ContactsRepository,
    private val homeRepository: HomeRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    var pendingAction: PendingPermissionAction? = null
        private set
    
    var onRequestPermission: ((String) -> Unit)? = null
    var onCloseSearch: (() -> Unit)? = null
    var onSaveRecentApp: ((String) -> Unit)? = null

    /**
     * Execute a SearchResultAction.
     */
    fun execute(
        action: SearchResultAction,
        hasPermission: (String) -> Boolean
    ) {
        val requiredPermission = action.requiredPermission()
        
        if (requiredPermission != null && !hasPermission(requiredPermission)) {
            pendingAction = PendingPermissionAction(action, requiredPermission)
            onRequestPermission?.invoke(requiredPermission)
        } else {
            executeAction(action)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        val pending = pendingAction
        pendingAction = null
        
        if (granted && pending != null) {
            executeAction(pending.action)
        }
    }

    private fun executeAction(action: SearchResultAction) {
        when (action) {
            is SearchResultAction.Tap -> handleTap(action)
            is SearchResultAction.DialContact -> handleDialContact(action)
            is SearchResultAction.OpenUrlInBrowser -> handleOpenUrlInBrowser(action)
            is SearchResultAction.PinApp -> handlePinApp(action)
            is SearchResultAction.PinFile -> handlePinFile(action)
            is SearchResultAction.UnpinItem -> handleUnpinItem(action)
            is SearchResultAction.OpenAppInfo -> handleOpenAppInfo(action)
            is SearchResultAction.RequestPermission -> handleRequestPermission(action)
        }
        
        if (action.shouldCloseSearch()) {
            onCloseSearch?.invoke()
        }
    }

    // ========================================================================
    // TAP ACTION HANDLERS
    // ========================================================================

    private fun handleTap(action: SearchResultAction.Tap) {
        when (val result = action.result) {
            is AppSearchResult -> launchApp(result)
            is WebSearchResult -> openWebSearch(result)
            is YouTubeSearchResult -> openYouTubeSearch(result)
            is UrlSearchResult -> openUrl(result)
            is ContactSearchResult -> callContact(result)
            is FileDocumentSearchResult -> openFile(result)
            is PermissionRequestResult -> {
                handleRequestPermission(SearchResultAction.RequestPermission(
                    result.permission,
                    result.providerPrefix
                ))
            }
        }
    }

    private fun launchApp(result: AppSearchResult) {
        result.appInfo.launchIntent?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        
        val componentName = android.content.ComponentName(
            result.appInfo.packageName,
            result.appInfo.activityName
        ).flattenToString()
        onSaveRecentApp?.invoke(componentName)
    }

    private fun openWebSearch(result: WebSearchResult) {
        openUrlInBrowser(result.url)
    }

    private fun openYouTubeSearch(result: YouTubeSearchResult) {
        val youtubeUrl = "https://www.youtube.com/results?search_query=${Uri.encode(result.query)}"
        
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val pm = context.packageManager
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
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

    private fun openUrl(result: UrlSearchResult) {
        result.handlerApp?.let { handler ->
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
        
        saveRecentContact(phone)
    }

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

    private fun handleDialContact(action: SearchResultAction.DialContact) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${action.phoneNumber}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            context.startActivity(intent)
        } catch (e: SecurityException) {
            Toast.makeText(context, "Call permission not granted", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "No phone app found", Toast.LENGTH_SHORT).show()
        }
        
        saveRecentContact(action.phoneNumber)
    }

    // ========================================================================
    // PIN ACTIONS
    // ========================================================================

    private fun handlePinApp(action: SearchResultAction.PinApp) {
        scope.launch {
            val pinnedApp = HomeItem.PinnedApp.fromAppInfo(action.appInfo)
            homeRepository.addPinnedItem(pinnedApp)
        }
        Toast.makeText(context, "${action.appInfo.name} pinned to home", Toast.LENGTH_SHORT).show()
    }

    private fun handlePinFile(action: SearchResultAction.PinFile) {
        scope.launch {
            val pinnedFile = HomeItem.PinnedFile.fromFileDocument(action.file)
            homeRepository.addPinnedItem(pinnedFile)
        }
        Toast.makeText(context, "${action.file.name} pinned to home", Toast.LENGTH_SHORT).show()
    }

    private fun handleUnpinItem(action: SearchResultAction.UnpinItem) {
        scope.launch {
            homeRepository.removePinnedItem(action.itemId)
        }
        Toast.makeText(context, "Removed from home screen", Toast.LENGTH_SHORT).show()
    }

    // ========================================================================
    // APP INFO HANDLER
    // ========================================================================

    private fun handleOpenAppInfo(action: SearchResultAction.OpenAppInfo) {
        val intent = Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${action.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // ========================================================================
    // OPEN IN BROWSER HANDLER
    // ========================================================================

    private fun handleOpenUrlInBrowser(action: SearchResultAction.OpenUrlInBrowser) {
        openUrlInBrowser(action.url)
    }

    // ========================================================================
    // PERMISSION REQUEST HANDLER
    // ========================================================================

    private fun handleRequestPermission(action: SearchResultAction.RequestPermission) {
        onRequestPermission?.invoke(action.permission)
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private fun saveRecentContact(phoneNumber: String) {
        scope.launch {
            contactsRepository.saveRecentContact(phoneNumber)
        }
    }
}
