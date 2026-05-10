package com.milki.launcher.data.widget

import android.content.Context
import android.content.pm.PackageManager
import com.milki.launcher.data.cache.AsyncSnapshotCache
import com.milki.launcher.data.repository.apps.PackageChangeMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Process-wide widget picker catalog cache.
 *
 * This keeps picker-specific catalog assembly and package-change invalidation
 * out of [WidgetHostManager], which stays focused on Android widget-host
 * framework operations.
 */
class WidgetPickerCatalogStore(
    context: Context,
    private val widgetHostManager: WidgetHostManager,
    packageChangeMonitor: PackageChangeMonitor
) {
    companion object {
        private const val TAG = "WidgetPickerCatalog"
    }

    private val packageManager: PackageManager = context.packageManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val catalogCache = AsyncSnapshotCache(
        scope = scope,
        tag = TAG,
        emptySnapshot = emptyList(),
        loader = ::buildCatalog
    )

    init {
        scope.launch {
            packageChangeMonitor.events.collectLatest {
                invalidate(prewarmAfterInvalidation = true)
            }
        }
    }

    fun peek(): List<WidgetAppGroup>? = catalogCache.peek()

    fun prewarm() {
        catalogCache.prewarm()
    }

    suspend fun await(): List<WidgetAppGroup> {
        return catalogCache.await()
    }

    fun invalidate(prewarmAfterInvalidation: Boolean = false) {
        catalogCache.invalidate(prewarmAfterInvalidation = prewarmAfterInvalidation)
    }

    private fun buildCatalog(): List<WidgetAppGroup> {
        return widgetHostManager.getInstalledProviders()
            .map { info ->
                val recommendedSpan = widgetHostManager.calculateRecommendedPlacementSpan(info)
                val widgetLabel = widgetHostManager.loadProviderLabel(info)
                val appLabel = try {
                    val appInfo = packageManager.getApplicationInfo(info.provider.packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    info.provider.packageName
                }
                WidgetPickerEntry(
                    providerInfo = info,
                    label = widgetLabel,
                    appLabel = appLabel,
                    appIcon = try {
                        packageManager.getApplicationIcon(info.provider.packageName)
                    } catch (_: Exception) {
                        null
                    },
                    span = recommendedSpan
                )
            }
            .groupBy { it.providerInfo.provider.packageName }
            .map { (packageName, widgets) ->
                WidgetAppGroup(
                    packageName = packageName,
                    appLabel = widgets.first().appLabel,
                    appIcon = widgets.first().appIcon,
                    widgets = widgets.sortedBy { it.label.lowercase() }
                )
            }
            .sortedBy { it.appLabel.lowercase() }
    }
}
