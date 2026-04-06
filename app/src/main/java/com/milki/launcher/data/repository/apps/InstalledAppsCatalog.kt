package com.milki.launcher.data.repository.apps

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import com.milki.launcher.data.icon.AppIconMemoryCache
import com.milki.launcher.domain.model.AppInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Scans launcher activities and maps them into AppInfo models.
 */
internal class InstalledAppsCatalog(
    private val application: Application,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(8)
) {

    suspend fun loadInstalledApps(): List<AppInfo> {
        return withContext(dispatcher) {
            val packageManager = application.packageManager
            val launcherQueryIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val activities = packageManager.queryIntentActivitiesCompat(launcherQueryIntent)
            val preloadedIconPackages = ConcurrentHashMap.newKeySet<String>()

            activities.map { resolveInfo ->
                async {
                    val packageName = resolveInfo.activityInfo.packageName

                    if (preloadedIconPackages.add(packageName)) {
                        AppIconMemoryCache.preload(
                            packageName = packageName,
                            icon = resolveInfo.loadIcon(packageManager)
                        )
                    }

                    val componentName = ComponentName(
                        resolveInfo.activityInfo.packageName,
                        resolveInfo.activityInfo.name
                    )

                    AppInfo(
                        name = resolveInfo.loadLabel(packageManager).toString(),
                        packageName = componentName.packageName,
                        activityName = componentName.className
                    )
                }
            }.awaitAll()
                .sortedBy { app -> app.nameLower }
        }
    }
}
