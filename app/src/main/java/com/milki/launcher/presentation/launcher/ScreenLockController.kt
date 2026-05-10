package com.milki.launcher.presentation.launcher

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.milki.launcher.core.deviceadmin.LauncherDeviceAdminReceiver

/**
 * Manages screen locking via [DevicePolicyManager].
 *
 * FLOW:
 * 1. On first invocation, if Device Admin is not enabled, the user is prompted
 *    to enable it via the system Settings screen.
 * 2. Once enabled, subsequent calls to [lock] immediately turn off the screen.
 *
 * This mirrors [NotificationShadeController] in purpose: a thin wrapper around
 * a platform capability that requires special permission.
 */
internal class ScreenLockController(
    private val activity: ComponentActivity
) {
    private val devicePolicyManager: DevicePolicyManager =
        activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName =
        ComponentName(activity, LauncherDeviceAdminReceiver::class.java)

    /**
     * Launcher for the Device Admin enable request.
     *
     * Registered eagerly so it is ready before any user gesture triggers it.
     * The result callback re-attempts the lock if the user granted admin.
     */
    private val enableAdminLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            // After returning from the admin-enable screen, check if admin
            // was actually activated and lock if so.
            if (isAdminActive()) {
                lockNow()
            }
        }

    /**
     * Returns true if Device Admin is already enabled for this app.
     */
    fun isAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    /**
     * Locks the screen if Device Admin is enabled.
     * If not enabled, launches the system Device Admin activation prompt.
     *
     * @return true if the screen was locked immediately, false if a
     *         permission request was launched instead.
     */
    fun lock(): Boolean {
        return if (isAdminActive()) {
            lockNow()
            true
        } else {
            requestAdminPermission()
            false
        }
    }

    private fun lockNow() {
        devicePolicyManager.lockNow()
    }

    private fun requestAdminPermission() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enable device admin to allow the launcher to lock the screen on double-tap."
            )
        }
        enableAdminLauncher.launch(intent)
    }
}
