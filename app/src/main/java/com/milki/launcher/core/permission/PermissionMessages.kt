package com.milki.launcher.core.permission

import android.Manifest

internal object PermissionMessages {
    const val FILE_ACCESS_NOT_GRANTED = "File access was not granted. Open Settings to search files."
    const val FILE_ACCESS_SETTINGS_UNAVAILABLE = "Unable to open file access settings on this device."

    fun declinedMessage(permission: String): String {
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

    fun blockedMessage(permission: String): String {
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
