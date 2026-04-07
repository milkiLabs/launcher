package com.milki.launcher.presentation.launcher

import android.content.Context
import android.widget.Toast

/**
 * Best-effort bridge for expanding the system notification shade from launcher.
 *
 * Some devices/ROMs block this hidden platform API for regular apps, so callers
 * get a Boolean result and users receive a small toast on failure.
 */
internal class NotificationShadeController(
    private val context: Context
) {

    fun expand(): Boolean {
        val expanded = runCatching {
            val statusBarManager = context.getSystemService(Context.STATUS_BAR_SERVICE)
                ?: throw IllegalStateException("Status bar service unavailable")
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            val expandNotificationsPanel =
                statusBarManagerClass.getMethod("expandNotificationsPanel")
            expandNotificationsPanel.invoke(statusBarManager)
            true
        }.getOrDefault(false)

        if (!expanded) {
            Toast.makeText(
                context,
                "Unable to expand notification shade on this device",
                Toast.LENGTH_SHORT
            ).show()
        }

        return expanded
    }
}
