package com.milki.launcher.data.widget

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.milki.launcher.data.repository.apps.PackageChangeMonitor
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.widget.WidgetHostPort
import com.milki.launcher.domain.widget.calculateMinWidgetSpan
import com.milki.launcher.domain.widget.recommendWidgetPlacementSpan
import com.milki.launcher.ui.interaction.grid.GridConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
 * out of [WidgetHostPort] implementations, which stay focused on Android
 * widget-host framework operations.
 */
class WidgetPickerCatalogStore(
    context: Context,
    private val widgetHost: WidgetHostPort,
    packageChangeMonitor: PackageChangeMonitor
) {
    companion object {
        private const val TAG = "WidgetPickerCatalog"

        /**
         * Batch installs/uninstalls emit one package event per package. Waiting
         * before rebuilding lets bursts coalesce into a single catalog rebuild
         * instead of N full icon+label IPC sweeps.
         */
        private const val PACKAGE_EVENT_DEBOUNCE_MS = 500L
    }

    private val packageManager: PackageManager = context.packageManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadMutex = Mutex()

    // Single source of truth. Null means "not loaded yet".
    private val catalog = MutableStateFlow<List<WidgetAppGroup>?>(null)

    init {
        scope.launch {
            packageChangeMonitor.events.collectLatest {
                delay(PACKAGE_EVENT_DEBOUNCE_MS)
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
        return widgetHost.getInstalledProviders()
            .map { info ->
                val (minCols, minRows) = calculateMinWidgetSpan(info)
                val recommendedSpan = recommendWidgetPlacementSpan(
                    rawSpan = GridSpan(columns = minCols, rows = minRows),
                    gridColumns = GridConfig.Default.columns
                )
                val widgetLabel = widgetHost.loadProviderLabel(info)
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
                        packageManager.getApplicationIcon(info.provider.packageName).constantState
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
