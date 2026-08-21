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
import android.provider.ContactsContract
import android.widget.Toast
import com.milki.launcher.core.intent.launchSafe
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.model.AppSearchResult
import com.milki.launcher.domain.model.ContactSearchResult
import com.milki.launcher.domain.model.FileDocumentSearchResult
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.PermissionRequestResult
import com.milki.launcher.domain.model.PhoneNumberSearchResult
import com.milki.launcher.domain.model.UrlSearchResult
import com.milki.launcher.domain.model.WebSearchResult
import com.milki.launcher.domain.model.YouTubeSearchResult
import com.milki.launcher.presentation.home.HomePinningController
import com.milki.launcher.core.intent.openFile
import com.milki.launcher.core.intent.launchApp
import com.milki.launcher.core.intent.launchAppShortcut
import com.milki.launcher.core.intent.openUrlDestination
import kotlinx.coroutines.CoroutineScope
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
 * @property filesRepository Repository for files
 * @property homePinning Controller for pinning/unpinning items on the home grid
 * @property scope CoroutineScope tied to the caller's lifecycle (e.g., Activity's lifecycleScope).
 *                 This ensures all coroutines are cancelled when the lifecycle owner is destroyed,
 *                 preventing memory leaks and ensuring proper structured concurrency.
 * @property permissionRequester Requests an Android runtime permission from the host.
 * @property closeSearch Hides the search surface.
 * @property saveRecentApp Records a launched app component as recently used.
 * @property openAppWidgets Opens the widget picker for the given app.
 *
 * All host dependencies are constructor-injected so the executor is fully
 * functional immediately after construction; there is no late callback binding
 * and no risk of firing into a not-yet-wired callback.
 */
class ActionExecutor(
    private val context: Context,
    private val contactsRepository: ContactsRepository,
    private val filesRepository: com.milki.launcher.domain.repository.FilesRepository,
    private val homePinning: HomePinningController,
    private val scope: CoroutineScope,
    private val permissionRequester: (String) -> Unit,
    private val closeSearch: () -> Unit,
    private val saveRecentApp: (String) -> Unit,
    private val openAppWidgets: (String) -> Unit
) {

    var pendingAction: PendingPermissionAction? = null
        private set

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
            permissionRequester(requiredPermission)
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
            is SearchResultAction.DialPhoneNumber -> handleDialPhoneNumber(action)
            is SearchResultAction.SavePhoneNumber -> handleSavePhoneNumber(action)
            is SearchResultAction.OpenUrlInBrowser -> handleOpenUrlInBrowser(action)
            is SearchResultAction.OpenUrlInExternalBrowser -> handleOpenUrlInExternalBrowser(action)
            is SearchResultAction.ComposeEmail -> handleComposeEmail(action)
            is SearchResultAction.PinFile -> handlePinFile(action)
            is SearchResultAction.PinContact -> handlePinContact(action)
            is SearchResultAction.UnpinItem -> handleUnpinItem(action)
            is SearchResultAction.OpenAppInfo -> handleOpenAppInfo(action)
            is SearchResultAction.UninstallApp -> handleUninstallApp(action)
            is SearchResultAction.OpenAppWidgets -> handleOpenAppWidgets(action)
            is SearchResultAction.LaunchAppShortcut -> handleLaunchAppShortcut(action)
            is SearchResultAction.RequestPermission -> handleRequestPermission(action)
        }
        
        if (action.shouldCloseSearch()) {
            closeSearch()
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
            is PhoneNumberSearchResult -> {
                handleDialPhoneNumber(SearchResultAction.DialPhoneNumber(result.phoneNumber))
            }
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
        val success = launchApp(
            context = context,
            appInfo = result.appInfo,
            onRecentAppSaved = saveRecentApp
        )
        
        if (!success) {
            Toast.makeText(context, "App not found: ${result.appInfo.name}", Toast.LENGTH_SHORT).show()
        }
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

        if (!context.launchSafe("YouTube search", intent)) {
            openUrlInBrowser(youtubeUrl)
        }
    }

    private fun openUrl(result: UrlSearchResult) {
        openUrlDestination(
            context = context,
            url = result.url,
            preferredPackageName = result.handlerApp?.packageName,
            onFailure = {
                Toast.makeText(context, "No browser app found", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun openUrlInBrowser(url: String) {
        openUrlDestination(
            context = context,
            url = url,
            onFailure = {
                Toast.makeText(context, "No browser app found", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun openUrlInExternalBrowser(url: String) {
        val uri = Uri.parse(url)
        val pinnedIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        resolveDefaultBrowser()?.let { pinnedIntent.setPackage(it) }

        val chooserIntent = Intent.createChooser(
            Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            "Open with"
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        context.launchSafe(
            "external browser",
            listOf(pinnedIntent, chooserIntent),
            failureMessage = "No browser app found"
        )
    }
    
    private fun resolveDefaultBrowser(): String? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val resolveInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        } catch (e: Exception) {
            null
        }
        return resolveInfo?.activityInfo?.packageName
    }

    private fun callContact(result: ContactSearchResult) {
        val phone = result.contact.phoneNumbers.firstOrNull() ?: return

        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = phoneUri(phone)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.launchSafe("dialer", intent, failureMessage = "No phone app found")

        saveRecentContact(phone)
    }

    private fun openFile(result: FileDocumentSearchResult) {
        val file = result.file
        openFile(context, Uri.parse(file.uri), file.mimeType, file.name)
        saveRecentFile(file.id)
    }

    // ========================================================================
    // DIAL HANDLERS
    // ========================================================================

    private fun handleDialContact(action: SearchResultAction.DialContact) {
        executeDirectCall(action.phoneNumber)
    }

    private fun handleDialPhoneNumber(action: SearchResultAction.DialPhoneNumber) {
        executeDirectCall(action.phoneNumber)
    }

    private fun executeDirectCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = phoneUri(phoneNumber)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.launchSafe(
            "direct call",
            intent,
            onFailure = { error ->
                val message = if (error is SecurityException) {
                    "Call permission not granted"
                } else {
                    "No phone app found"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        )

        saveRecentContact(phoneNumber)
    }

    private fun handleSavePhoneNumber(action: SearchResultAction.SavePhoneNumber) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, action.phoneNumber)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.launchSafe(
            "contacts insert",
            intent,
            failureMessage = "No contacts app found"
        )
    }

    // ========================================================================
    // PIN ACTIONS
    // ========================================================================

    private fun handlePinFile(action: SearchResultAction.PinFile) {
        homePinning.pinFile(action.file)
        Toast.makeText(context, "${action.file.name} pinned to home", Toast.LENGTH_SHORT).show()
    }

    private fun handlePinContact(action: SearchResultAction.PinContact) {
        homePinning.pinContact(action.contact)
        Toast.makeText(context, "${action.contact.displayName} pinned to home", Toast.LENGTH_SHORT).show()
    }

    private fun handleUnpinItem(action: SearchResultAction.UnpinItem) {
        homePinning.unpinItem(action.itemId)
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
        context.launchSafe("app info screen", intent, failureMessage = "Unable to open app info")
    }

    private fun handleUninstallApp(action: SearchResultAction.UninstallApp) {
        val intent = Intent(
            Intent.ACTION_DELETE,
            Uri.parse("package:${action.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.launchSafe("app uninstall screen", intent, failureMessage = "Unable to uninstall app")
    }

    private fun handleOpenAppWidgets(action: SearchResultAction.OpenAppWidgets) {
        openAppWidgets(action.appName)
    }

    private fun handleLaunchAppShortcut(action: SearchResultAction.LaunchAppShortcut) {
        if (!launchAppShortcut(context, action.shortcut)) {
            Toast.makeText(context, "Shortcut unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    // ========================================================================
    // OPEN IN BROWSER HANDLER
    // ========================================================================

    private fun handleOpenUrlInBrowser(action: SearchResultAction.OpenUrlInBrowser) {
        openUrlInBrowser(action.url)
    }

    private fun handleOpenUrlInExternalBrowser(action: SearchResultAction.OpenUrlInExternalBrowser) {
        openUrlInExternalBrowser(action.url)
    }

    // ========================================================================
    // CLIPBOARD SUGGESTION ACTION HANDLERS
    // ========================================================================

    /**
     * Opens email compose screen with recipient pre-filled.
     */
    private fun handleComposeEmail(action: SearchResultAction.ComposeEmail) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${Uri.encode(action.emailAddress)}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.launchSafe("email compose", intent, failureMessage = "No email app found")
    }

    // ========================================================================
    // PERMISSION REQUEST HANDLER
    // ========================================================================

    private fun handleRequestPermission(action: SearchResultAction.RequestPermission) {
        permissionRequester(action.permission)
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private fun saveRecentContact(phoneNumber: String) {
        scope.launch {
            contactsRepository.saveRecentContact(phoneNumber)
        }
    }

    private fun saveRecentFile(fileId: Long) {
        scope.launch {
            filesRepository.saveRecentFile(fileId)
        }
    }

    private fun phoneUri(phoneNumber: String): Uri =
        Uri.fromParts("tel", phoneNumber, null)
}
