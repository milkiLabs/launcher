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
 * ARCHITECTURE DECISION:
 * This catalog intentionally does NOT load icons. Icon loading is the most
 * expensive part of app enumeration (~6s for 60 apps) and is handled by
 * dedicated icon-loading paths closer to the UI:
 * - HomeIconWarmupCoordinator: preloads icons for home screen items
 * - AppIcon composable: loads icons on-demand when they become visible
 *
 * This keeps catalog scans fast (~130ms) and avoids flooding IO threads
 * during startup with icon work that no consumer needs yet.
 */
internal class InstalledAppsCatalog(
    private val application: Application,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

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

            activities.map { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo
                val packageName = activityInfo.packageName

                val label = traceSection("launcher.appsCatalog.resolveLabel") {
                    resolveInfo.loadLabel(packageManager).toString()
                }

                val installedOrUpdatedAtMillis = packageTimestampCache.getOrPut(packageName) {
                    resolveRecencyTimestampMillis(
                        packageManager = packageManager,
                        packageName = packageName
                    )
                }

                AppInfo(
                    name = label,
                    packageName = packageName,
                    activityName = activityInfo.name,
                    installedOrUpdatedAtMillis = installedOrUpdatedAtMillis
                )
            }.sortedBy { app -> app.nameLower }
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
