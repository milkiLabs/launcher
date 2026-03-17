package com.milki.launcher.domain.drawer

import com.milki.launcher.domain.model.AppInfo

/**
 * Immutable lookup index for drawer apps by package/activity key.
 */
class DrawerAppLookup(
    apps: List<AppInfo>
) {
    private val byComponent = apps.associateBy { componentKey(it.packageName, it.activityName) }

    fun find(packageName: String, activityName: String? = null): AppInfo? {
        if (activityName != null) {
            return byComponent[componentKey(packageName, activityName)]
        }

        return byComponent.values.firstOrNull { it.packageName == packageName }
    }

    private fun componentKey(packageName: String, activityName: String): String {
        return "$packageName/$activityName"
    }
}
