package com.milki.launcher.data.widget

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.milki.launcher.data.repository.apps.PackageChangeMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    private val loadMutex = Mutex()

    // Single source of truth. Null means "not loaded yet".
    private val catalog = MutableStateFlow<List<WidgetAppGroup>?>(null)

    init {
        scope.launch {
            packageChangeMonitor.events.collectLatest {
                refresh()
            }
        }
    }

    fun peek(): List<WidgetAppGroup>? = catalog.value

    fun prewarm() {
        scope.launch { getOrLoad() }
    }

    suspend fun await(): List<WidgetAppGroup> = getOrLoad()

    private suspend fun getOrLoad(): List<WidgetAppGroup> {
        catalog.value?.let { cached -> return cached }
        return loadMutex.withLock {
            catalog.value ?: loadCatalog().also { loaded -> catalog.value = loaded }
        }
    }

    private suspend fun refresh() {
        loadMutex.withLock {
            catalog.value = loadCatalog()
        }
    }

    private suspend fun loadCatalog(): List<WidgetAppGroup> = withContext(Dispatchers.IO) {
        try {
            buildCatalog()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Log.e(TAG, "Failed to build widget picker catalog", throwable)
            emptyList()
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
