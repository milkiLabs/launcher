package com.milki.launcher.data.repository

import android.app.Application
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
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
    application: Application
) : AppRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val installedAppsCatalog = InstalledAppsCatalog(application)
    private val recentAppsStore = RecentAppsStore(application)
    private val packageChangeMonitor = PackageChangeMonitor(application)

    private val installedAppsSnapshot = MutableStateFlow<List<AppInfo>>(emptyList())

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
        return recentAppsStore.observeRecentApps()
    }

    override suspend fun saveRecentApp(componentName: String) {
        recentAppsStore.saveRecentApp(componentName)
    }

    private suspend fun refreshInstalledAppsSnapshot() {
        val latestApps = installedAppsCatalog.loadInstalledApps()
        installedAppsSnapshot.value = latestApps
        recentAppsStore.pruneUnavailable(latestApps)
    }
}
