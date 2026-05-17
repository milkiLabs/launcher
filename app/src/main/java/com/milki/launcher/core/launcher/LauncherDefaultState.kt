package com.milki.launcher.core.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

/**
 * Resolves whether this app currently owns the HOME role.
 */
fun isAppDefaultLauncher(context: Context): Boolean {
    val roleManager = context.homeRoleManagerOrNull()
    if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
        return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    }

    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val defaultHome = context.packageManager.resolveActivity(homeIntent, 0)
    return defaultHome?.activityInfo?.packageName == context.packageName
}
