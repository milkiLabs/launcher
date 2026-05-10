package com.milki.launcher.presentation.launcher

import android.content.Context


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
