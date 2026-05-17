package com.milki.launcher.core.launcher

import android.app.role.RoleManager
import android.content.Context
import android.os.Build

/**
 * Returns the [RoleManager] if the device API level supports it (Android Q+),
 * otherwise returns `null`.
 */
fun Context.homeRoleManagerOrNull(): RoleManager? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return null
    }
    return getSystemService(RoleManager::class.java)
}
