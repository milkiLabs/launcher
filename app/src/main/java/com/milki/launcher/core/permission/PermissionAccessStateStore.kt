package com.milki.launcher.core.permission

import android.content.Context

/**
 * Small persistent store for permission flows that now require Settings recovery.
 *
 * This lets us remember "Don't ask again" style states across process death
 * so the next user action can route straight to Settings instead of blindly
 * re-triggering the same denied path.
 */
internal interface PermissionAccessStateStore {
    fun requiresSettings(permission: String): Boolean
    fun markRequiresSettings(permission: String)
    fun clearRequiresSettings(permission: String)
}

internal class SharedPreferencesPermissionAccessStateStore(
    context: Context
) : PermissionAccessStateStore {
    private val preferences = context.getSharedPreferences(
        "permission_access_state",
        Context.MODE_PRIVATE
    )

    override fun requiresSettings(permission: String): Boolean {
        return preferences.getBoolean(permission, false)
    }

    override fun markRequiresSettings(permission: String) {
        preferences.edit().putBoolean(permission, true).apply()
    }

    override fun clearRequiresSettings(permission: String) {
        preferences.edit().remove(permission).apply()
    }
}
