/**
 * PermissionUtil.kt - Centralized permission checking utilities
 *
 * This utility object provides static methods for checking permission states.
 * It's used by PermissionHandler and can be used anywhere in the app that
 * needs to check permissions without requesting them.
 *
 * WHY A UTILITY OBJECT?
 * - No state to maintain - just pure permission checks
 * - Can be called from anywhere (Activity, Service, BroadcastReceiver)
 * - Keeps version-specific logic in one place
 * - Easy to test and mock
 *
 * PERMISSION TYPES:
 * - Contacts: READ_CONTACTS (standard runtime permission)
 * - Files: Varies by Android version (see hasFilesPermission docs)
 */

package com.milki.launcher.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

/**
 * Utility object for checking permission states.
 *
 * All methods are stateless - they simply check the current permission
 * state without modifying anything. This makes them safe to call from
 * any thread (though they typically need to be called from the main thread
 * to update UI).
 */
object PermissionUtil {

    /**
     * Checks if the app has permission to read contacts.
     *
     * This is a standard runtime permission that works the same on all
     * Android versions. The user sees a dialog asking for permission.
     *
     * @param context The context used to check for permission
     * @return true if READ_CONTACTS permission is granted, false otherwise
     */
    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the app has the necessary permission to access all files.
     *
     * Permission requirements differ by Android version:
     * - Android 11+ (API 30+): Requires MANAGE_EXTERNAL_STORAGE
     *   Checked via Environment.isExternalStorageManager()
     *   This is a special permission granted via Settings
     *
     * - Android 10 and below: Requires READ_EXTERNAL_STORAGE
     *   Standard runtime permission granted via dialog
     *
     * WHY THE DIFFERENCE?
     * Android 11 introduced "Scoped Storage" for better privacy.
     * Apps can no longer freely access all files on the device.
     * For apps that need broad file access (like launchers searching files),
     * MANAGE_EXTERNAL_STORAGE is required, which the user grants in Settings.
     *
     * @param context The context used to check for permissions
     * @return true if the permission is granted, false otherwise
     */
    fun hasFilesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
