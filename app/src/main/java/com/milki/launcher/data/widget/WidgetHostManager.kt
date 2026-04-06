/**
 * WidgetHostManager.kt - Wrapper around Android's AppWidgetHost framework
 *
 * This class manages the lifecycle and operations for Android app widgets on the
 * home screen. It wraps two core Android classes:
 *
 * 1. AppWidgetHost     - The "host" that manages widget IDs and creates widget views.
 *                        Each launcher app creates exactly ONE AppWidgetHost instance.
 * 2. AppWidgetManager  - The system service that provides information about installed
 *                        widget providers (which apps offer widgets, what sizes they
 *                        support, whether they need configuration, etc.).
 *
 * WHY THIS WRAPPER EXISTS:
 * - Encapsulates the Android widget framework behind a clean API
 * - Manages the host lifecycle (startListening/stopListening) in one place
 * - Provides helper methods for the common operations (allocate, bind, create view)
 * - Makes it easy to inject via Koin and test in isolation
 *
 * LIFECYCLE REQUIREMENTS:
 * The launcher must start listening while its main surface is visible/resumed and
 * stop listening when it is not. This tells Android when to deliver widget updates.
 * Without this, widgets can appear blank or stale.
 *
 * WIDGET ID ALLOCATION:
 * Each widget on the home screen gets a unique integer ID from the system. These IDs
 * are persistent — they survive app restarts. The host allocates IDs and the system
 * tracks which provider is bound to each ID. If a widget is removed, its ID should be
 * deallocated so the system can reclaim it.
 *
 * THREAD SAFETY:
 * The underlying Android AppWidgetHost and AppWidgetManager are thread-safe.
 * This wrapper can be called from any thread.
 */

package com.milki.launcher.data.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetProviderInfo.RESIZE_HORIZONTAL
import android.appwidget.AppWidgetProviderInfo.RESIZE_VERTICAL
import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL
import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.WindowManager
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.widget.recommendWidgetPlacementSpan
import com.milki.launcher.ui.interaction.grid.GridConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlin.math.roundToInt

class WidgetHostManager(
    private val context: Context
) {
    companion object {
        /**
         * Unique host ID for this launcher's AppWidgetHost.
         *
         * WHY 100?
         * Each app can have multiple widget hosts (e.g. a lockscreen host and a
         * home screen host). The ID distinguishes them. The value itself is arbitrary
         * but must be consistent across app restarts. We use 100 to avoid collision
         * with any default values used by the framework (0 or 1).
         */
        private const val HOST_ID = 100

        private const val TAG = "WidgetHostManager"
    }

    /**
     * The AppWidgetHost instance that manages widget IDs and creates widget views.
     *
     * This is the launcher's connection to the Android widget framework. It:
     * - Allocates unique widget IDs for new widgets
     * - Creates AppWidgetHostView instances that render widget content
     * - Receives widget update callbacks from the system
     *
     * The host is created once and reused for the entire app lifetime.
     */
    private val appWidgetHost: AppWidgetHost = AppWidgetHost(context, HOST_ID)

    /**
     * The system-provided AppWidgetManager that queries installed widget providers.
     *
     * This is a system service (like LocationManager or NotificationManager) that
     * provides read-only information about which apps offer widgets and what
     * properties those widgets have (min size, resize rules, preview image, etc.).
     *
     * WHY PUBLIC:
     * Callers (e.g. HomeViewModel) need this to call loadLabel() on
     * AppWidgetProviderInfo when creating HomeItem.WidgetItem.
     */
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)

    /**
     * Convenience accessor for the system PackageManager.
     *
     * WHY PUBLIC:
     * AppWidgetProviderInfo.loadLabel() requires a PackageManager argument.
     * Callers like HomeViewModel don't have direct Context access, so they
     * go through this property instead.
     */
    private val packageManager: PackageManager = context.packageManager
    private val widgetCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activityStarted = false
    private var activityResumed = false
    private var stateIsNormal = false
    private var isListening = false
    private var widgetPickerCatalogCache: List<WidgetAppGroup>? = null
    private var widgetPickerCatalogLoad: Deferred<List<WidgetAppGroup>>? = null

    /**
     * Resolves the user-facing label for a widget provider.
     *
     * This keeps PackageManager usage encapsulated inside WidgetHostManager so
     * callers do not need direct access to packageManager internals.
     */
    fun loadProviderLabel(providerInfo: AppWidgetProviderInfo): String {
        return providerInfo.loadLabel(packageManager) ?: providerInfo.provider.shortClassName
    }

    fun setActivityStarted(started: Boolean) {
        activityStarted = started
        syncListeningState()
    }

    fun setActivityResumed(resumed: Boolean) {
        activityResumed = resumed
        syncListeningState()
    }

    fun setStateIsNormal(isNormal: Boolean) {
        stateIsNormal = isNormal
        syncListeningState()
    }

    private fun startListening() {
        try {
            appWidgetHost.startListening()
        } catch (e: Exception) {
            // startListening() can throw on some devices if the widget database is corrupted.
            // We catch and log rather than crashing the launcher.
            Log.e(TAG, "Failed to start widget host listening", e)
        }
    }

    private fun stopListening() {
        try {
            appWidgetHost.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop widget host listening", e)
        }
    }

    private fun syncListeningState() {
        val shouldListen = activityStarted && activityResumed && stateIsNormal
        if (shouldListen == isListening) return

        runCatching {
            if (shouldListen) {
                startListening()
            } else {
                stopListening()
            }
        }.onSuccess {
            isListening = shouldListen
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to sync listening state shouldListen=$shouldListen", throwable)
        }
    }

    /**
     * Allocates a new unique widget ID from the system.
     *
     * This ID is used to bind a widget provider to this specific widget instance.
     * Each widget on the home screen has its own unique ID. The ID persists across
     * app restarts — it's stored in the system's widget database.
     *
     * IMPORTANT: If the widget is never bound or is removed, the ID should be
     * deallocated via [deallocateWidgetId] to avoid leaking IDs.
     *
     * @return A new unique integer widget ID.
     */
    fun allocateWidgetId(): Int {
        return appWidgetHost.allocateAppWidgetId()
    }

    /**
     * Releases a previously allocated widget ID back to the system.
     *
     * Call this when a widget is removed from the home screen, or when a widget
     * binding fails and the allocated ID is no longer needed.
     *
     * @param widgetId The widget ID to release.
     */
    fun deallocateWidgetId(widgetId: Int) {
        try {
            appWidgetHost.deleteAppWidgetId(widgetId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deallocate widget ID $widgetId", e)
        }
    }

    fun bindWidget(
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo,
        options: Bundle? = null
    ): Boolean {
        return try {
            if (options != null) {
                appWidgetManager.bindAppWidgetIdIfAllowed(
                    appWidgetId,
                    providerInfo.profile,
                    providerInfo.provider,
                    options
                )
            } else {
                appWidgetManager.bindAppWidgetIdIfAllowed(
                    appWidgetId,
                    providerInfo.profile,
                    providerInfo.provider,
                    Bundle.EMPTY
                )
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to bind widgetId=$appWidgetId provider=${providerInfo.provider} profile=${providerInfo.profile}",
                e
            )
            false
        }
    }

    /**
     * Creates an Intent that launches the system's "Allow this widget?" permission dialog.
     *
     * This is needed when [bindWidget] returns false — the user must explicitly
     * grant permission for this launcher to host the widget.
     *
     * The caller should pass this Intent to an ActivityResultLauncher registered
     * for [AppWidgetManager.ACTION_APPWIDGET_BIND].
     *
     * @param appWidgetId   The allocated widget ID.
     * @param providerInfo  The provider to bind.
     * @return An Intent ready to be launched.
     */
    fun createBindPermissionIntent(
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo,
        options: Bundle? = null
    ): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, providerInfo.profile)
            if (options != null) {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
            }
        }
    }

    fun needsConfigure(appWidgetId: Int): Boolean {
        val providerInfo = getProviderInfo(appWidgetId) ?: return false
        return needsInitialConfigure(providerInfo)
    }

    /**
     * Starts the provider's configuration Activity using the host helper.
     *
     * This mirrors Launcher3's approach and is more reliable than launching the
     * configure Activity directly, especially for cross-profile or restricted providers.
     */
    fun startConfigureActivityForResult(
        activity: Activity,
        appWidgetId: Int,
        requestCode: Int,
        options: Bundle? = null
    ) {
        appWidgetHost.startAppWidgetConfigureActivityForResult(
            activity,
            appWidgetId,
            0,
            requestCode,
            options
        )
    }

    /**
     * Creates an AppWidgetHostView that renders the widget's content.
     *
     * This is the actual Android View that displays the widget. It's created from
     * the bound widget ID and its provider info. The view receives content updates
     * from the widget provider automatically (as long as startListening() has been called).
     *
     * The returned view should be wrapped in a Compose AndroidView for display
     * in the home screen grid.
     *
     * @param widgetId The bound widget ID.
     * @param providerInfo The AppWidgetProviderInfo for this widget (from getProviderInfo).
     * @return An AppWidgetHostView ready to be displayed.
     */
    fun createHostView(widgetId: Int, providerInfo: AppWidgetProviderInfo): android.appwidget.AppWidgetHostView {
        return appWidgetHost.createView(context, widgetId, providerInfo)
    }

    /**
     * Returns the AppWidgetProviderInfo for a bound widget ID.
     *
     * This contains metadata about the widget: its provider component, min/max sizes,
     * whether it has a configuration activity, its preview image, etc.
     *
     * @param widgetId The bound widget ID.
     * @return The provider info, or null if the widget ID is not bound or the provider
     *         app was uninstalled.
     */
    fun getProviderInfo(widgetId: Int): AppWidgetProviderInfo? {
        return appWidgetManager.getAppWidgetInfo(widgetId)
    }

    /**
     * Returns all available widget providers installed on the device.
     *
     * Each entry describes one widget type that an app offers. A single app can
     * offer multiple widget types (e.g., a weather app might offer a 1×1 small
     * widget, a 4×2 large widget, and a 4×4 full-size widget).
     *
     * The returned list is used by the Widget Picker BottomSheet to show available
     * widgets grouped by app.
     *
     * @return List of all installed AppWidgetProviderInfo objects.
     */
    fun getInstalledProviders(): List<AppWidgetProviderInfo> {
        return appWidgetManager.installedProviders
    }

    /**
     * Returns the last completed widget-picker catalog snapshot if one exists.
     *
     * The snapshot is cached at the process level so the widget picker can open
     * immediately instead of rebuilding the full provider/app-label list on the
     * first composition.
     */
    fun peekWidgetPickerCatalog(): List<WidgetAppGroup>? = synchronized(this) {
        widgetPickerCatalogCache
    }

    /**
     * Starts building the widget-picker catalog in the background if needed.
     *
     * This is safe to call eagerly from launcher startup; repeated calls reuse
     * the same in-flight work.
     */
    fun prewarmWidgetPickerCatalog() {
        getOrStartWidgetPickerCatalogLoad()
    }

    /**
     * Suspends until the widget-picker catalog is ready, reusing any existing
     * background preload instead of duplicating work on the UI thread.
     */
    suspend fun awaitWidgetPickerCatalog(): List<WidgetAppGroup> {
        val cachedCatalog = synchronized(this) { widgetPickerCatalogCache }
        if (cachedCatalog != null) {
            return cachedCatalog
        }

        return getOrStartWidgetPickerCatalogLoad().await()
    }

    /**
     * Finds a widget provider from installed providers by its component name.
     *
     * This is used when decoding a widget drag payload from ClipData fallback,
     * where we only have the provider component and must re-resolve full
     * AppWidgetProviderInfo at drop time.
     */
    fun findInstalledProvider(provider: ComponentName): AppWidgetProviderInfo? {
        return appWidgetManager.installedProviders.firstOrNull { it.provider == provider }
    }

    /**
     * Builds the initial options bundle used when binding a widget for a given span.
     *
     * This gives providers accurate size information from the start instead of
     * waiting for the first host-view layout pass.
     */
    fun createBindOptions(span: GridSpan): Bundle {
        val (widthPx, heightPx) = estimateWidgetSizePx(span)
        return createWidgetSizeOptions(widthPx = widthPx, heightPx = heightPx)
    }

    /**
     * Updates a hosted widget with its exact rendered size.
     */
    fun updateWidgetSize(
        hostView: AppWidgetHostView,
        widthPx: Int,
        heightPx: Int
    ) {
        val sizeOptions = createWidgetSizeOptions(widthPx = widthPx, heightPx = heightPx)
        val widthDp = pxToDp(widthPx)
        val heightDp = pxToDp(heightPx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hostView.updateAppWidgetSize(
                sizeOptions,
                listOf(SizeF(widthDp.toFloat(), heightDp.toFloat()))
            )
        } else {
            @Suppress("DEPRECATION")
            hostView.updateAppWidgetSize(
                sizeOptions,
                widthDp,
                heightDp,
                widthDp,
                heightDp
            )
        }
    }

    /**
     * Calculates the minimum span (in grid cells) for a widget provider.
     *
     * Android widget providers specify their minimum size in dp (density-independent pixels).
     * We convert that to grid cell counts by dividing by the approximate cell size.
     *
     * The calculation uses the formula from Android's own launcher:
     *   cells = ceil((minSizeDp - 2 * cellPaddingDp) / cellSizeDp)
     *
     * We use an approximation where each cell is roughly 70dp wide (for 4 columns
     * on a typical 280dp-wide usable area). This matches how most launchers calculate
     * widget cell counts.
     *
     * @param providerInfo The widget's provider info containing minWidth/minHeight.
     * @return A Pair of (minColumns, minRows) representing the minimum grid span.
     */
    fun calculateMinSpan(providerInfo: AppWidgetProviderInfo): Pair<Int, Int> {
        // On API 31+, use the targetCellWidth/Height which is more accurate.
        // On older APIs, calculate from minWidth/minHeight dp values.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val targetCols = providerInfo.targetCellWidth
            val targetRows = providerInfo.targetCellHeight
            if (targetCols > 0 && targetRows > 0) {
                return Pair(targetCols, targetRows)
            }
        }

        // Fallback: convert dp dimensions to cell count using the standard formula.
        // The formula is: cells = floor((size - 30) / 70) + 1
        // This approximation is used by AOSP launcher and works well for 4-column grids.
        val minCols = dpToCells(providerInfo.minWidth)
        val minRows = dpToCells(providerInfo.minHeight)
        return Pair(minCols, minRows)
    }

    /**
     * Calculates the minimum resize span for a widget that supports resizing.
     *
     * Some widgets can be resized smaller than their default size. The minimum
     * resize dimensions define how small the user can make the widget.
     *
     * If the widget doesn't support resizing, this returns the same as calculateMinSpan().
     *
     * @param providerInfo The widget's provider info.
     * @return A Pair of (minResizeColumns, minResizeRows).
     */
    fun calculateMinResizeSpan(providerInfo: AppWidgetProviderInfo): Pair<Int, Int> {
        val minResizeWidth = providerInfo.minResizeWidth
        val minResizeHeight = providerInfo.minResizeHeight
        if (minResizeWidth <= 0 || minResizeHeight <= 0) {
            return calculateMinSpan(providerInfo)
        }
        return Pair(dpToCells(minResizeWidth), dpToCells(minResizeHeight))
    }

    /**
     * Calculates the recommended placement span for a new widget.
     *
     * This intentionally differs from the provider's raw minimum/default size:
     * very large widgets are shrunk to a practical first placement so users do
     * not need to clear an oversized area before they can drop them.
     */
    fun calculateRecommendedPlacementSpan(
        providerInfo: AppWidgetProviderInfo,
        gridColumns: Int = GridConfig.Default.columns
    ): GridSpan {
        val (minCols, minRows) = calculateMinSpan(providerInfo)
        return recommendWidgetPlacementSpan(
            rawSpan = GridSpan(columns = minCols, rows = minRows),
            gridColumns = gridColumns
        )
    }

    /**
     * Clamps a requested widget resize to the provider's supported axes, minimum
     * size, and the currently visible home-grid bounds.
     */
    fun clampResizeSpan(
        providerInfo: AppWidgetProviderInfo?,
        currentSpan: GridSpan,
        requestedSpan: GridSpan,
        gridColumns: Int,
        maxRows: Int
    ): GridSpan {
        val supportsHorizontalResize = providerInfo != null &&
            (providerInfo.resizeMode and RESIZE_HORIZONTAL) != 0
        val supportsVerticalResize = providerInfo != null &&
            (providerInfo.resizeMode and RESIZE_VERTICAL) != 0

        val (minResizeCols, minResizeRows) = providerInfo?.let(::calculateMinResizeSpan)
            ?: (1 to 1)

        val targetColumns = if (supportsHorizontalResize) {
            requestedSpan.columns.coerceIn(minResizeCols, gridColumns.coerceAtLeast(minResizeCols))
        } else {
            currentSpan.columns
        }

        val targetRows = if (supportsVerticalResize) {
            requestedSpan.rows.coerceIn(minResizeRows, maxRows.coerceAtLeast(minResizeRows))
        } else {
            currentSpan.rows
        }

        return GridSpan(columns = targetColumns, rows = targetRows)
    }

    /**
     * Converts a dp dimension to a cell count using the AOSP launcher formula.
     *
     * The standard formula is: cells = floor((dp - 30) / 70) + 1
     * This ensures that:
     * - 40dp  → 1 cell
     * - 110dp → 2 cells
     * - 180dp → 3 cells
     * - 250dp → 4 cells
     *
     * @param dp The dimension in dp.
     * @return The number of cells, minimum 1.
     */
    private fun dpToCells(dp: Int): Int {
        return ((dp - 30) / 70 + 1).coerceAtLeast(1)
    }

    private fun getOrStartWidgetPickerCatalogLoad(): Deferred<List<WidgetAppGroup>> =
        synchronized(this) {
            widgetPickerCatalogCache?.let { cached ->
                return@synchronized CompletableDeferred(cached)
            }

            widgetPickerCatalogLoad?.let { existingLoad ->
                return@synchronized existingLoad
            }

            widgetCatalogScope.async {
                runCatching {
                    buildWidgetPickerCatalog()
                }.onFailure { throwable ->
                    Log.e(TAG, "Failed to build widget picker catalog", throwable)
                }.getOrElse { emptyList() }
            }.also { load ->
                widgetPickerCatalogLoad = load
                load.invokeOnCompletion {
                    synchronized(this@WidgetHostManager) {
                        if (widgetPickerCatalogLoad === load) {
                            widgetPickerCatalogLoad = null
                        }
                    }
                }
            }
        }

    private fun buildWidgetPickerCatalog(): List<WidgetAppGroup> {
        return getInstalledProviders()
            .map { info ->
                val recommendedSpan = calculateRecommendedPlacementSpan(info)
                val widgetLabel = loadProviderLabel(info)
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
            .also { catalog ->
                synchronized(this) {
                    widgetPickerCatalogCache = catalog
                }
            }
    }

    private fun needsInitialConfigure(providerInfo: AppWidgetProviderInfo): Boolean {
        if (providerInfo.configure == null) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return true
        }

        val featureFlags = providerInfo.widgetFeatures
        val isOptionalConfiguration =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (featureFlags and WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0 &&
                (featureFlags and WIDGET_FEATURE_RECONFIGURABLE) != 0

        return !isOptionalConfiguration
    }

    private fun createWidgetSizeOptions(widthPx: Int, heightPx: Int): Bundle {
        val widthDp = pxToDp(widthPx)
        val heightDp = pxToDp(heightPx)
        return Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat()))
                )
            }
        }
    }

    private fun estimateWidgetSizePx(span: GridSpan): Pair<Int, Int> {
        val displayMetrics = context.resources.displayMetrics
        val windowWidthPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = context.getSystemService(WindowManager::class.java)
            windowManager?.currentWindowMetrics?.bounds?.width() ?: displayMetrics.widthPixels
        } else {
            displayMetrics.widthPixels
        }

        val cellSizePx = windowWidthPx.toFloat() / GridConfig.Default.columns
        val widthPx = (cellSizePx * span.columns).roundToInt().coerceAtLeast(1)
        val heightPx = (cellSizePx * span.rows).roundToInt().coerceAtLeast(1)
        return widthPx to heightPx
    }

    private fun pxToDp(px: Int): Int {
        return (px / context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    }
}
