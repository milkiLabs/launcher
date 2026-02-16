package com.milki.launcher.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

/**
 * Utility class for handling permission checks, specifically for file access.
 * 
 * Centralizes the logic for checking storage permissions which changed significantly
 * between Android 10 and Android 11.
 */
object PermissionUtil {

    /**
     * Checks if the app has the necessary permission to access all files.
     * 
     * Permission requirements differ by Android version:
     * - Android 11+ (API 30+): Requires MANAGE_EXTERNAL_STORAGE (checked via Environment.isExternalStorageManager())
     * - Android 10 and below: Requires READ_EXTERNAL_STORAGE runtime permission
     * 
     * @param context The context used to check for permissions.
     * @return true if the permission is granted, false otherwise.
     */
    fun hasFilesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Check for MANAGE_EXTERNAL_STORAGE
            // This is a special permission granted via Settings
            Environment.isExternalStorageManager()
        } else {
            // Android 10 and below: Check for READ_EXTERNAL_STORAGE
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
