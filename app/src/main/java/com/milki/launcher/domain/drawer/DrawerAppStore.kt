package com.milki.launcher.domain.drawer

import com.milki.launcher.domain.model.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Canonical store for drawer app snapshots.
 *
 * Supports deferred updates so expensive list rebuilds can be paused during
 * transitional UI periods.
 */
class DrawerAppStore {

    fun interface Listener {
        fun onAppsUpdated(apps: List<AppInfo>, flags: DrawerModelFlags)
    }

    private val listeners = linkedSetOf<Listener>()
    private val deferFlags = linkedSetOf<String>()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private var lookup = DrawerAppLookup(emptyList())
    private var deferred: DrawerDeferredUpdates? = null

    fun setApps(apps: List<AppInfo>, flags: DrawerModelFlags) {
        val normalized = apps
            .distinctBy { "${it.packageName}/${it.activityName}" }
            .sortedWith(compareBy<AppInfo> { it.name.lowercase() }
                .thenBy { it.packageName }
                .thenBy { it.activityName })

        if (deferFlags.isNotEmpty()) {
            deferred = DrawerDeferredUpdates(
                apps = normalized,
                flags = flags.copy(deferredReason = deferFlags.joinToString(","))
            )
            return
        }

        commit(normalized, flags)
    }

    fun enableDefer(flag: String) {
        deferFlags.add(flag)
    }

    fun disableDefer(flag: String) {
        deferFlags.remove(flag)
        if (deferFlags.isEmpty()) {
            val pending = deferred
            if (pending != null) {
                deferred = null
                commit(pending.apps, pending.flags)
            }
        }
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun lookup(packageName: String, activityName: String? = null): AppInfo? {
        return lookup.find(packageName = packageName, activityName = activityName)
    }

    private fun commit(apps: List<AppInfo>, flags: DrawerModelFlags) {
        lookup = DrawerAppLookup(apps)
        _apps.value = apps
        listeners.forEach { it.onAppsUpdated(apps, flags) }
    }
}
