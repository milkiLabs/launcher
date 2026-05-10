package com.milki.launcher.data.repository.apps

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import com.milki.launcher.core.perf.traceSection
import com.milki.launcher.domain.model.AppInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans launcher activities and maps them into AppInfo models.
 *
 * COLD START OPTIMIZATION:
 * Label resolution via PackageManager IPC dominates cold start time
 * (~673ms for 28 apps). On the first load after process creation, this
 * catalog reads labels from a SharedPreferences cache instead of PM IPC.
 * Cache misses (new installs) fall back to PM. After resolution, the
 * cache is updated so the next cold start benefits.
 *
 * Subsequent loads (triggered by package change broadcasts) always resolve
 * fresh from PM because the PM process is warm by then, and the cache is
 * updated with fresh values.
 *
 * ARCHITECTURE DECISION:
 * This catalog intentionally does NOT load icons. Icon loading is the most
 * expensive part of app enumeration (~6s for 60 apps) and is handled by
 * dedicated icon-loading paths closer to the UI:
 * - HomeIconWarmupCoordinator: preloads icons for home screen items
 * - AppIcon composable: loads icons on-demand when they become visible
 *
 * This keeps catalog scans fast and avoids flooding IO threads
 * during startup with icon work that no consumer needs yet.
 */
internal class InstalledAppsCatalog(
    private val application: Application,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val labelCache = AppLabelCache(application)
    private var isFirstLoad = true

    suspend fun loadInstalledApps(): List<AppInfo> {
        return withContext(dispatcher) {
            val packageManager = application.packageManager
            val packageTimestampCache = mutableMapOf<String, Long>()
            val launcherQueryIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val activities = traceSection("launcher.appsCatalog.queryLauncherActivities") {
                packageManager.queryIntentActivitiesCompat(launcherQueryIntent)
            }

            // On first load: read cached labels to skip expensive PM IPC.
            // On subsequent loads (package changes): resolve fresh from PM.
            val useLabelCache = isFirstLoad
            isFirstLoad = false
            val cachedLabels = if (useLabelCache) labelCache.readAll() else emptyMap()

            var hadCacheMiss = false

            val apps = activities.map { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo
                val packageName = activityInfo.packageName
                val activityName = activityInfo.name

                val cachedLabel = if (useLabelCache) {
                    cachedLabels[AppLabelCache.cacheKey(packageName, activityName)]
                } else {
                    null
                }

                val label = cachedLabel
                    ?: traceSection("launcher.appsCatalog.resolveLabel") {
                        resolveInfo.loadLabel(packageManager).toString()
                    }.also { hadCacheMiss = true }

                val installedOrUpdatedAtMillis = packageTimestampCache.getOrPut(packageName) {
                    resolveRecencyTimestampMillis(
                        packageManager = packageManager,
                        packageName = packageName
                    )
                }

                AppInfo(
                    name = label,
                    packageName = packageName,
                    activityName = activityName,
                    installedOrUpdatedAtMillis = installedOrUpdatedAtMillis
                )
            }.sortedBy { app -> app.nameLower }

            // Persist labels so the next cold start benefits.
            if (hadCacheMiss || cachedLabels.size != apps.size) {
                labelCache.writeAll(apps)
            }

            apps
        }
    }

    private fun resolveRecencyTimestampMillis(
        packageManager: PackageManager,
        packageName: String
    ): Long {
        return try {
            val packageInfo = packageManager.getPackageInfoCompat(packageName)
            maxOf(packageInfo.firstInstallTime, packageInfo.lastUpdateTime)
        } catch (_: PackageManager.NameNotFoundException) {
            0L
        }
    }
}
