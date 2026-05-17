package com.milki.launcher.core.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

internal object PermissionSettingsNavigator {
    fun manageStorageIntent(context: Context): Intent? {
        val packageIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        if (packageIntent.resolveActivity(context.packageManager) != null) return packageIntent

        val generalIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        return generalIntent.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    fun openApplicationDetailsSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(
                context,
                "Unable to open app settings on this device.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
