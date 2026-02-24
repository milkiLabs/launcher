/**
 * PermissionHandler.kt - Centralized permission management for the launcher
 *
 * This class encapsulates ALL permission-related logic 
 * It handles:
 * - Contacts permission
 * - Files/Storage permission
 *
 * USAGE:
 * 1. Create instance in onCreate(): permissionHandler = PermissionHandler(this, searchViewModel)
 * 2. Call setup() in onCreate() to register launchers
 * 3. Call updateStates() in onResume() to refresh permission states
 * 4. Call requestContactsPermission() or requestFilesPermission() when needed
 */

package com.milki.launcher.handlers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.milki.launcher.presentation.search.SearchViewModel
import com.milki.launcher.util.PermissionUtil

/**
 * Handles all permission requests and state management for the launcher.
 *
 * This class uses the Activity Result API (introduced in AndroidX) instead of
 * the deprecated onRequestPermissionsResult() callback approach. 
 *
 * @property activity The ComponentActivity that owns this handler
 * @property searchViewModel The ViewModel to update with permission states
 */
class PermissionHandler(
    private val activity: ComponentActivity,
    private val searchViewModel: SearchViewModel
) {
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
            searchViewModel.updateContactsPermission(isGranted)
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
            searchViewModel.updateFilesPermission(isGranted)
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
            updateFilesPermissionState()
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
        val hasPermission = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        searchViewModel.updateContactsPermission(hasPermission)
    }

    /**
     * Checks and updates the files/storage permission state.
     *
     * This delegates to PermissionUtil which handles the version-specific logic:
     * - Android 11+: Check Environment.isExternalStorageManager()
     * - Android 10-: Check READ_EXTERNAL_STORAGE permission
     */
    private fun updateFilesPermissionState() {
        val hasPermission = PermissionUtil.hasFilesPermission(activity)
        searchViewModel.updateFilesPermission(hasPermission)
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
     *
     * TODO: Add logic to detect "permanently denied" and guide user to Settings
     */
    fun requestContactsPermission() {
        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
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
            }
        }
    }

    /**
     * Shows the READ_EXTERNAL_STORAGE permission dialog (Android 10 and below).
     *
     * The result is handled by our callback registered in registerFilesLauncher().
     */
    private fun requestReadStoragePermission() {
        filesPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}
