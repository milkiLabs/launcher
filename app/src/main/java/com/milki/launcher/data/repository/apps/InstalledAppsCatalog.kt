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
            val launcherQueryIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val activities = traceSection("launcher.appsCatalog.queryLauncherActivities") {
                packageManager.queryIntentActivitiesCompat(launcherQueryIntent)
            }

            // Resolve package timestamps once per package, then reuse for each launcher activity.
            val packageRecencyByPackageName = traceSection("launcher.appsCatalog.queryPackageRecency") {
                activities
                    .asSequence()
                    .map { it.activityInfo.packageName }
                    .distinct()
                    .associateWith { packageName ->
                        resolvePackageRecencyMillis(packageManager, packageName)
                    }
            }

            activities.map { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo

                val label = traceSection("launcher.appsCatalog.resolveLabel") {
                    resolveInfo.loadLabel(packageManager).toString()
                }

                AppInfo(
                    name = label,
                    packageName = activityInfo.packageName,
                    activityName = activityInfo.name,
                )
            }.sortedBy { app -> app.nameLower }
        }
    }

    private fun resolvePackageRecencyMillis(
        packageManager: PackageManager,
        packageName: String
    ): Long {
        return try {
            val packageInfo = packageManager.getPackageInfoCompat(packageName)
            maxOf(packageInfo.lastUpdateTime, packageInfo.firstInstallTime)
        } catch (_: PackageManager.NameNotFoundException) {
            0L
        }
    }
}
