package com.milki.launcher.core.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher

/**
 * Launches the Android HOME role request when supported and this app is not
 * currently the default launcher.
 */
fun ComponentActivity.launchHomeRoleRequestIfNeeded(
    launcher: ActivityResultLauncher<Intent>
): Boolean {
    val roleManager = homeRoleManagerOrNull() ?: return false
    val canRequestHomeRole =
        roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    if (!canRequestHomeRole) {
        return false
    }

    return runCatching {
        launcher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        true
    }.getOrDefault(false)
}

/**
 * Fallback flow for devices/ROMs where HOME role request is unavailable.
 */
fun ComponentActivity.openDefaultLauncherSettingsFallback(): Boolean {
    if (tryStartActivity(Intent(Settings.ACTION_HOME_SETTINGS))) {
        return true
    }

    if (tryStartActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) {
        return true
    }

    return tryStartActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    )
}

private fun ComponentActivity.homeRoleManagerOrNull(): RoleManager? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return null
    }
    return getSystemService(RoleManager::class.java)
}

private fun ComponentActivity.tryStartActivity(intent: Intent): Boolean {
    return runCatching {
        startActivity(intent)
        true
    }.getOrDefault(false)
}
