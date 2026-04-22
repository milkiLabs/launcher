package com.milki.launcher.presentation.launcher

import android.content.Context

/**
 * Best-effort bridge for expanding the system notification shade from launcher.
 *
 * Some devices/ROMs block this hidden platform API for regular apps.
 */
internal class NotificationShadeController(
    private val context: Context
) {

    fun expand(): Boolean {
        return runCatching {
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            statusBarManagerClass
                .getMethod("expandNotificationsPanel")
                .invoke(context.getSystemService("statusbar"))
            true
        }.getOrDefault(false)
    }
}
