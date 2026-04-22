package com.milki.launcher.core.deviceadmin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Minimal [DeviceAdminReceiver] that enables screen-lock via
 * [android.app.admin.DevicePolicyManager.lockNow].
 *
 * This receiver declares only the `force-lock` policy (see
 * `res/xml/device_admin_receiver.xml`). No other admin capabilities
 * are requested or used.
 */
class LauncherDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        // No-op: admin activated.
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // No-op: admin deactivated.
    }
}
