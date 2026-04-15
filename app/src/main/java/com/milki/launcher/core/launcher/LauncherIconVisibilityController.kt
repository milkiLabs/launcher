package com.milki.launcher.core.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

private const val MAIN_ACTIVITY_LAUNCHER_ALIAS =
    "com.milki.launcher.app.activity.MainActivityLauncherAlias"

/**
 * Keeps the app icon hidden while this app is the active default launcher.
 */
fun syncLauncherIconVisibility(context: Context) {
    val packageManager = context.packageManager
    val launcherAliasComponent = ComponentName(context.packageName, MAIN_ACTIVITY_LAUNCHER_ALIAS)
    val shouldShowIcon = !isAppDefaultLauncher(context)
    val targetState = if (shouldShowIcon) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } else {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    val currentState = packageManager.getComponentEnabledSetting(launcherAliasComponent)
    val alreadyApplied = when (currentState) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> shouldShowIcon
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> !shouldShowIcon
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> shouldShowIcon
        else -> false
    }
    if (alreadyApplied) {
        return
    }

    packageManager.setComponentEnabledSetting(
        launcherAliasComponent,
        targetState,
        PackageManager.DONT_KILL_APP
    )
}