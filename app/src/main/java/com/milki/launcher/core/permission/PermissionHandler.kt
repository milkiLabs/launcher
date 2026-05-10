/**
 * PermissionHandler.kt - Centralized permission management for the launcher
 *
 * This class encapsulates ALL permission-related logic 
 * It handles:
 * - Contacts permission (READ_CONTACTS)
 * - Call permission (CALL_PHONE) - for direct dialing
 * - Files/Storage permission
 *
 * USAGE:
 * 1. Create instance in onCreate(): permissionHandler = PermissionHandler(this, searchViewModel)
 * 2. Call setup() in onCreate() to register launchers
 * 3. Call updateStates() in onResume() to refresh permission states
 * 4. Call requestContactsPermission() or requestFilesPermission() when needed
 */

package com.milki.launcher.core.permission

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.milki.launcher.domain.model.PermissionAccessState

/**
 * Sink for propagating permission-state updates to higher layers.
 *
 * This keeps core permission logic independent from presentation classes.
 */
interface PermissionStateSink {
    fun updateContactsPermission(state: PermissionAccessState)
    fun updateFilesPermission(state: PermissionAccessState)
}

/**
 * Handles all permission requests and state management for the launcher.
 *
 * This class uses the Activity Result API (introduced in AndroidX) instead of
 * the deprecated onRequestPermissionsResult() callback approach. 
 *
 * @property activity The ComponentActivity that owns this handler
 * @property permissionStateSink Sink used to publish permission state updates
 */
class PermissionHandler(
    private val activity: ComponentActivity,
    private val permissionStateSink: PermissionStateSink
) {
    private val accessStateStore = SharedPreferencesPermissionAccessStateStore(activity)

    // ========================================================================
    // PERMISSION LAUNCHERS
    // ========================================================================

    /**
     * Launcher for requesting contacts permission.
     *
     * The launcher must be registered BEFORE any permission requests are made,
     * which is why we call setup() in onCreate().
     */
    private lateinit var contactsPermissionLauncher: ActivityResultLauncher<String>

    /**
     * Launcher for requesting call permission (CALL_PHONE).
     *
     * This permission is needed to make phone calls directly (ACTION_CALL)
     * instead of just opening the dialer (ACTION_DIAL).
     *
     * WHEN THIS IS USED:
     * When the user taps the dial icon on a contact result, we request this
     * permission if not already granted. Once granted, the call is made directly.
     */
    private lateinit var callPermissionLauncher: ActivityResultLauncher<String>

    /**
     * Launcher for requesting files/storage permission on Android 10 and below.
     *
     * On older Android versions, READ_EXTERNAL_STORAGE is a standard runtime
     * permission that shows a dialog. The user can grant or deny it.
     *
     * On Android 11+, we don't use this launcher - instead we open Settings
     * for MANAGE_EXTERNAL_STORAGE permission (see manageStorageLauncher below).
     */
    private lateinit var filesPermissionLauncher: ActivityResultLauncher<String>

    /**
     * Launcher for requesting MANAGE_EXTERNAL_STORAGE permission on Android 11+.
     *
     * This is a SPECIAL permission that requires opening the Settings app.
     * Unlike normal runtime permissions, this cannot be granted via a dialog.
     * The user must manually enable "Allow all the time" in Settings.
     *
     * WHY IS THIS DIFFERENT?
     * - Android 11 introduced Scoped Storage for privacy
     * - Apps that need broad file access (like file managers or launchers) must request this special permission
     * - Google Play has strict review process for apps using this permission
     */
    private lateinit var manageStorageLauncher: ActivityResultLauncher<Intent>
    
    // ========================================================================
    // CALLBACKS
    // ========================================================================
    
    /**
     * Callback invoked whenever ANY permission result is received by this handler.
     *
     * WHY GENERIC CALLBACK:
     * Historically we only exposed a call-permission callback. That made orchestration
     * logic more fragmented because each permission type had different callback paths.
     *
     * This generic callback allows the coordinator/state machine to observe a unified
     * stream of permission outcomes:
     * - READ_CONTACTS dialog result
     * - CALL_PHONE dialog result
     * - READ_EXTERNAL_STORAGE dialog result (Android 10 and below)
     * - MANAGE_EXTERNAL_STORAGE effective result after returning from Settings (Android 11+)
     *
     * @property permission The permission string associated with this result.
     * @property granted Whether the permission is currently granted.
     */
    var onPermissionResult: ((permission: String, granted: Boolean) -> Unit)? = null

    // ========================================================================
    // SETUP
    // ========================================================================

    /**
     * Registers all permission launchers.
     *
     * MUST be called in onCreate() before any permission requests.
     * The launchers are "registered" with the Activity's lifecycle, meaning
     * they'll automatically be cleaned up if the Activity is destroyed.
     *
     * REGISTRATION ORDER DOESN'T MATTER:
     * Each launcher is independent and can be called in any order.
     * We use lateinit because we can't initialize them in the constructor
     * (we need the Activity's registerForActivityResult method).
     */
    fun setup() {
        registerContactsLauncher()
        registerCallLauncher()
        registerFilesLauncher()
        registerManageStorageLauncher()
    }

    /**
     * Registers the contacts permission launcher.
     *
     * The callback receives true if granted, false if denied.
     * We immediately update the ViewModel with the result so the UI can react.
     */
    private fun registerContactsLauncher() {
        contactsPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            handleRuntimePermissionResult(
                permission = Manifest.permission.READ_CONTACTS,
                isGranted = isGranted,
                updateState = permissionStateSink::updateContactsPermission
            )
        }
    }

    /**
     * Registers the call permission launcher.
     *
     * This is used when the user taps the dial icon on a contact result.
     * If permission is granted, the pending action in ActionExecutor will be executed.
     * If denied, the pending action is cancelled.
     *
     * FLOW:
     * 1. User taps dial icon on contact
     * 2. ActionExecutor checks CALL_PHONE permission
     * 3. If not granted, stores pending action and requests permission
     * 4. This callback receives the result
     * 5. If granted, ActionExecutor executes the stored pending action
     * 6. If denied, ActionExecutor clears the pending action
     */
    private fun registerCallLauncher() {
        callPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            handleRuntimePermissionResult(
                permission = Manifest.permission.CALL_PHONE,
                isGranted = isGranted
            )
        }
    }

    /**
     * Registers the files permission launcher (for Android 10 and below).
     *
     * This launcher is only used on older Android versions. On Android 11+,
     * we use the manageStorageLauncher instead.
     */
    private fun registerFilesLauncher() {
        filesPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            handleRuntimePermissionResult(
                permission = Manifest.permission.READ_EXTERNAL_STORAGE,
                isGranted = isGranted,
                updateState = permissionStateSink::updateFilesPermission
            )
        }
    }

    /**
     * Registers the manage storage launcher (for Android 11+).
     *
     * Unlike normal permissions, we don't get a direct result - we have to
     * check the actual permission state when the user returns.
     *
     * That's why our callback calls updateFilesPermissionState() instead of
     * using the result directly. The Settings Intent doesn't return whether
     * permission was granted - we have to check it ourselves.
     */
    private fun registerManageStorageLauncher() {
        manageStorageLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            val hasPermission = PermissionUtil.hasFilesPermission(activity)
            handleManageStorageResult(hasPermission)
        }
    }

    // ========================================================================
    // STATE UPDATES
    // ========================================================================

    /**
     * Updates all permission states in the ViewModel.
     *
     * Call this in onResume() to ensure the UI always reflects the current
     * permission state. This is important because:
     * - User might have changed permissions in Settings
     * - User might have granted/denied permission in our dialog
     * - Permission state can change while app is in background
     */
    fun updateStates() {
        updateContactsPermissionState()
        updateFilesPermissionState()
    }

    /**
     * Checks and updates the contacts permission state.
     *
     * ContextCompat.checkSelfPermission() returns PERMISSION_GRANTED or PERMISSION_DENIED.
     */
    private fun updateContactsPermissionState() {
        permissionStateSink.updateContactsPermission(
            accessStateForRuntimePermission(Manifest.permission.READ_CONTACTS)
        )
    }

    private fun updateFilesPermissionState() {
        val state = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            accessStateForSpecialPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
        } else {
            accessStateForRuntimePermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        permissionStateSink.updateFilesPermission(state)
    }

    // ========================================================================
    // PERMISSION REQUESTS
    // ========================================================================

    /**
     * Requests contacts permission from the user.
     *
     * The result will be handled by our callback registered in registerContactsLauncher().
     *
     * If the user previously denied the permission and checked "Don't ask again",
     * the dialog won't show - the system will immediately call our callback
     * with false. In this case, we should direct the user to Settings.
     */
    fun requestContactsPermission() {
        requestRuntimePermission(
            permission = Manifest.permission.READ_CONTACTS,
            launcher = contactsPermissionLauncher,
            updateState = permissionStateSink::updateContactsPermission
        )
    }

    /**
     * Requests call permission from the user.
     *
     * This is called when the user taps the dial icon on a contact result
     * but doesn't have CALL_PHONE permission yet.
     *
     * The result will be handled by our callback registered in registerCallLauncher(),
     * which will execute the pending call if granted.
     */
    fun requestCallPermission() {
        requestRuntimePermission(
            permission = Manifest.permission.CALL_PHONE,
            launcher = callPermissionLauncher
        )
    }

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Requests the appropriate storage permission based on Android version.
     */
    fun requestFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestManageStoragePermission()
        } else {
            requestReadStoragePermission()
        }
    }

    /**
     * Opens Settings to request MANAGE_EXTERNAL_STORAGE (Android 11+).
     *
     * This is the "All files access" permission that lets apps read/write
     * to all files on the device. It's a sensitive permission that requires
     * user action in the Settings app.
     *
     * INTENT EXPLANATION:
     * - Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION:
     *   Opens Settings directly to our app's file access toggle
     *   (Preferred, but not available on all devices)
     *
     * - Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION:
     *   Opens the general "All files access" Settings page
     *   (Fallback for devices that don't support the app-specific intent)
     *
     * We check resolveActivity() to ensure an app can handle the intent
     * before launching. This prevents crashes on devices without Settings.
     */
    private fun requestManageStoragePermission() {
        if (PermissionUtil.hasFilesPermission(activity)) {
            accessStateStore.clearRequiresSettings(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
            permissionStateSink.updateFilesPermission(PermissionAccessState.GRANTED)
            onPermissionResult?.invoke(Manifest.permission.MANAGE_EXTERNAL_STORAGE, true)
            return
        }

        val appSpecificIntent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
        ).apply {
            data = Uri.parse("package:${activity.packageName}")
        }

        if (appSpecificIntent.resolveActivity(activity.packageManager) != null) {
            manageStorageLauncher.launch(appSpecificIntent)
        } else {
            val generalIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            if (generalIntent.resolveActivity(activity.packageManager) != null) {
                manageStorageLauncher.launch(generalIntent)
            } else {
                Toast.makeText(
                    activity,
                    "Unable to open file access settings on this device.",
                    Toast.LENGTH_SHORT
                ).show()
                onPermissionResult?.invoke(Manifest.permission.MANAGE_EXTERNAL_STORAGE, false)
            }
        }
    }

    /**
     * Shows the READ_EXTERNAL_STORAGE permission dialog (Android 10 and below).
     *
     * The result is handled by our callback registered in registerFilesLauncher().
     */
    private fun requestReadStoragePermission() {
        requestRuntimePermission(
            permission = Manifest.permission.READ_EXTERNAL_STORAGE,
            launcher = filesPermissionLauncher,
            updateState = permissionStateSink::updateFilesPermission
        )
    }

    private fun requestRuntimePermission(
        permission: String,
        launcher: ActivityResultLauncher<String>,
        updateState: ((PermissionAccessState) -> Unit)? = null
    ) {
        if (hasPermission(permission)) {
            accessStateStore.clearRequiresSettings(permission)
            updateState?.invoke(PermissionAccessState.GRANTED)
            onPermissionResult?.invoke(permission, true)
            return
        }

        if (accessStateStore.requiresSettings(permission)) {
            updateState?.invoke(PermissionAccessState.REQUIRES_SETTINGS)
            Toast.makeText(
                activity,
                blockedMessage(permission),
                Toast.LENGTH_LONG
            ).show()
            openApplicationDetailsSettings()
            onPermissionResult?.invoke(permission, false)
            return
        }

        launcher.launch(permission)
    }

    private fun handleRuntimePermissionResult(
        permission: String,
        isGranted: Boolean,
        updateState: ((PermissionAccessState) -> Unit)? = null
    ) {
        val resolvedState = PermissionOutcomeResolver.resolveRuntimeRequestResult(
            isGranted = isGranted,
            shouldShowRationale = activity.shouldShowRequestPermissionRationale(permission)
        )

        when (resolvedState) {
            PermissionAccessState.GRANTED -> {
                accessStateStore.clearRequiresSettings(permission)
            }

            PermissionAccessState.CAN_REQUEST -> {
                accessStateStore.clearRequiresSettings(permission)
                Toast.makeText(
                    activity,
                    declinedMessage(permission),
                    Toast.LENGTH_SHORT
                ).show()
            }

            PermissionAccessState.REQUIRES_SETTINGS -> {
                accessStateStore.markRequiresSettings(permission)
                Toast.makeText(
                    activity,
                    blockedMessage(permission),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        updateState?.invoke(resolvedState)
        onPermissionResult?.invoke(permission, isGranted)
    }

    private fun handleManageStorageResult(hasPermission: Boolean) {
        val state = if (hasPermission) {
            accessStateStore.clearRequiresSettings(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
            PermissionAccessState.GRANTED
        } else {
            accessStateStore.markRequiresSettings(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
            Toast.makeText(
                activity,
                "File access was not granted. Open Settings to search files.",
                Toast.LENGTH_LONG
            ).show()
            PermissionAccessState.REQUIRES_SETTINGS
        }

        permissionStateSink.updateFilesPermission(state)
        onPermissionResult?.invoke(Manifest.permission.MANAGE_EXTERNAL_STORAGE, hasPermission)
    }

    private fun accessStateForRuntimePermission(permission: String): PermissionAccessState {
        if (hasPermission(permission)) {
            accessStateStore.clearRequiresSettings(permission)
            return PermissionAccessState.GRANTED
        }

        return if (accessStateStore.requiresSettings(permission)) {
            PermissionAccessState.REQUIRES_SETTINGS
        } else {
            PermissionAccessState.CAN_REQUEST
        }
    }

    private fun accessStateForSpecialPermission(permission: String): PermissionAccessState {
        if (PermissionUtil.hasFilesPermission(activity)) {
            accessStateStore.clearRequiresSettings(permission)
            return PermissionAccessState.GRANTED
        }

        return if (accessStateStore.requiresSettings(permission)) {
            PermissionAccessState.REQUIRES_SETTINGS
        } else {
            PermissionAccessState.CAN_REQUEST
        }
    }

    private fun openApplicationDetailsSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null)
        )

        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(intent)
        } else {
            Toast.makeText(
                activity,
                "Unable to open app settings on this device.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun declinedMessage(permission: String): String {
        return when (permission) {
            Manifest.permission.READ_CONTACTS ->
                "Contacts permission declined. Contact search will stay unavailable."

            Manifest.permission.CALL_PHONE ->
                "Call permission declined. Contact taps still open the dialer."

            Manifest.permission.READ_EXTERNAL_STORAGE ->
                "Storage permission declined. File search will stay unavailable."

            else -> "Permission declined."
        }
    }

    private fun blockedMessage(permission: String): String {
        return when (permission) {
            Manifest.permission.READ_CONTACTS ->
                "Contacts permission is blocked. Open Settings to search contacts."

            Manifest.permission.CALL_PHONE ->
                "Call permission is blocked. Open Settings for direct calling."

            Manifest.permission.READ_EXTERNAL_STORAGE ->
                "Storage permission is blocked. Open Settings to search files."

            else -> "Permission is blocked. Enable it in app settings."
        }
    }
}
