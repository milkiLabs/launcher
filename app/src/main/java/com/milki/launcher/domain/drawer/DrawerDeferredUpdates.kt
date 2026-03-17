package com.milki.launcher.domain.drawer

import com.milki.launcher.domain.model.AppInfo

/**
 * Holds the most recent deferred apps snapshot while updates are paused.
 */
data class DrawerDeferredUpdates(
    val apps: List<AppInfo>,
    val flags: DrawerModelFlags
)
