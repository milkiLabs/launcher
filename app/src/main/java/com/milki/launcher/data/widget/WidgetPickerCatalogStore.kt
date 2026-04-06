package com.milki.launcher.data.widget

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.milki.launcher.data.repository.apps.PackageChangeMonitor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
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
    private var catalogCache: List<WidgetAppGroup>? = null
    private var catalogLoad: Deferred<List<WidgetAppGroup>>? = null
    private var catalogVersion: Long = 0L

    init {
        scope.launch {
            packageChangeMonitor.events.collectLatest {
                invalidate(prewarmAfterInvalidation = true)
            }
        }
    }

    fun peek(): List<WidgetAppGroup>? = synchronized(this) {
        catalogCache
    }

    fun prewarm() {
        getOrStartLoad()
    }

    suspend fun await(): List<WidgetAppGroup> {
        val cachedCatalog = synchronized(this) { catalogCache }
        if (cachedCatalog != null) {
            return cachedCatalog
        }
        return getOrStartLoad().await()
    }

    fun invalidate(prewarmAfterInvalidation: Boolean = false) {
        val previousLoad = synchronized(this) {
            catalogVersion += 1L
            catalogCache = null
            catalogLoad.also {
                catalogLoad = null
            }
        }
        previousLoad?.cancel()
        if (prewarmAfterInvalidation) {
            prewarm()
        }
    }

    private fun getOrStartLoad(): Deferred<List<WidgetAppGroup>> =
        synchronized(this) {
            catalogCache?.let { cached ->
                return@synchronized CompletableDeferred(cached)
            }

            catalogLoad?.let { existingLoad ->
                return@synchronized existingLoad
            }

            val loadVersion = catalogVersion
            scope.async {
                runCatching {
                    buildCatalog()
                }.onFailure { throwable ->
                    Log.e(TAG, "Failed to build widget picker catalog", throwable)
                }.getOrElse { emptyList() }
            }.also { load ->
                catalogLoad = load
                scope.launch {
                    try {
                        val catalog = load.await()
                        synchronized(this@WidgetPickerCatalogStore) {
                            if (catalogLoad === load && catalogVersion == loadVersion) {
                                catalogCache = catalog
                            }
                        }
                    } finally {
                        synchronized(this@WidgetPickerCatalogStore) {
                            if (catalogLoad === load) {
                                catalogLoad = null
                            }
                        }
                    }
                }
            }
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
