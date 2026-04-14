package com.milki.launcher.data.repository

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import com.milki.launcher.data.repository.apps.InstalledAppsCatalog
import com.milki.launcher.data.repository.apps.PackageChangeMonitor
import com.milki.launcher.data.repository.apps.RecentAppsStore
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Production implementation of AppRepository.
 *
 * Responsibilities are intentionally split:
 * - InstalledAppsCatalog: launcher app enumeration and icon preloading.
 * - RecentAppsStore: recents persistence and flow mapping.
 * - PackageChangeMonitor: package add/remove/update broadcast signals.
 */
class AppRepositoryImpl(
    private val application: Application,
    private val packageChangeMonitor: PackageChangeMonitor
) : AppRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val installedAppsCatalog = InstalledAppsCatalog(application)
    private val recentAppsStore = RecentAppsStore(application)

    private val installedAppsSnapshot = MutableStateFlow<List<AppInfo>>(emptyList())

    private val recentApps = recentAppsStore.observeRecentComponentNames()
        .map(::resolveRecentApps)
        .stateIn(
        scope = repositoryScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private val refreshTriggers = packageChangeMonitor.events
        .onStart { emit(Unit) }
        .shareIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    init {
        repositoryScope.launch {
            refreshTriggers.collectLatest {
                refreshInstalledAppsSnapshot()
            }
        }
    }

    override fun observeInstalledApps(): Flow<List<AppInfo>> {
        return installedAppsSnapshot
    }

    override suspend fun getInstalledApps(): List<AppInfo> {
        return installedAppsCatalog.loadInstalledApps()
    }

    override fun getRecentApps(): Flow<List<AppInfo>> {
        return recentApps
    }

    override suspend fun saveRecentApp(componentName: String) {
        recentAppsStore.saveRecentApp(componentName)
    }

    /**
     * Resolve recent component names independently from full app-catalog scans.
     * This keeps search-open recents fast even while installed-app refresh is running.
     */
    private fun resolveRecentApps(recentComponentNames: List<String>): List<AppInfo> {
        if (recentComponentNames.isEmpty()) return emptyList()

        val packageManager = application.packageManager
        return recentComponentNames.mapNotNull { flattenedComponent ->
            val componentName = ComponentName.unflattenFromString(flattenedComponent)
                ?: return@mapNotNull null
            resolveComponentAppInfo(componentName, packageManager)
        }
    }

    private fun resolveComponentAppInfo(
        componentName: ComponentName,
        packageManager: PackageManager
    ): AppInfo? {
        val activityInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getActivityInfo(
                    componentName,
                    PackageManager.ComponentInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getActivityInfo(componentName, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }

        val appLabel = activityInfo.loadLabel(packageManager)?.toString()
            ?: componentName.packageName

        return AppInfo(
            name = appLabel,
            packageName = componentName.packageName,
            activityName = componentName.className
        )
    }

    private suspend fun refreshInstalledAppsSnapshot() {
        val latestApps = installedAppsCatalog.loadInstalledApps()
        installedAppsSnapshot.value = latestApps
        recentAppsStore.pruneUnavailable(latestApps)
    }
}
