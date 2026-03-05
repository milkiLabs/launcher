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
 * AppWidgetHost.startListening() must be called when the launcher Activity is visible,
 * and stopListening() when it's not. This tells Android to start/stop sending widget
 * update broadcasts to this host. Without this, widgets won't update their content.
 *
 * The caller (MainActivity) is responsible for calling startListening() in onStart()
 * and stopListening() in onStop().
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

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

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
    val appWidgetHost: AppWidgetHost = AppWidgetHost(context, HOST_ID)

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
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)

    /**
     * Convenience accessor for the system PackageManager.
     *
     * WHY PUBLIC:
     * AppWidgetProviderInfo.loadLabel() requires a PackageManager argument.
     * Callers like HomeViewModel don't have direct Context access, so they
     * go through this property instead.
     */
    val packageManager: android.content.pm.PackageManager = context.packageManager

    /**
     * Starts listening for widget updates from the system.
     *
     * MUST be called when the launcher Activity becomes visible (in onStart()).
     * After this call, the system will send widget update broadcasts to this host,
     * and any AppWidgetHostView created by this host will start receiving content
     * updates from their respective widget providers.
     *
     * If this is not called, widgets will appear blank or show stale content.
     */
    fun startListening() {
        try {
            appWidgetHost.startListening()
        } catch (e: Exception) {
            // startListening() can throw on some devices if the widget database is corrupted.
            // We catch and log rather than crashing the launcher.
            Log.e(TAG, "Failed to start widget host listening", e)
        }
    }

    /**
     * Stops listening for widget updates from the system.
     *
     * MUST be called when the launcher Activity is no longer visible (in onStop()).
     * This tells the system to stop sending widget update broadcasts, which saves
     * battery and CPU when the launcher is in the background.
     */
    fun stopListening() {
        try {
            appWidgetHost.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop widget host listening", e)
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

    /**
     * Attempts to bind a widget ID to a specific widget provider.
     *
     * "Binding" means associating the allocated widget ID with the app's widget
     * provider class. After binding, the system knows which app should provide
     * content for this widget.
     *
     * PERMISSION FLOW:
     * On first bind for a given provider, this may return false because the user
     * hasn't granted the BIND_APPWIDGET permission yet. When this happens, the
     * caller should launch the system's bind permission dialog using
     * Intent(AppWidgetManager.ACTION_APPWIDGET_BIND) with the widget ID and provider.
     *
     * @param widgetId The allocated widget ID to bind.
     * @param provider The ComponentName of the widget provider (package + class).
     * @return true if binding succeeded immediately (permission already granted);
     *         false if the user needs to grant permission first.
     */
    fun bindWidget(widgetId: Int, provider: ComponentName): Boolean {
        return appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider)
    }

    /**
     * Convenience overload that takes an [AppWidgetProviderInfo] instead of a
     * raw [ComponentName].  Extracts the provider component automatically.
     */
    fun bindWidget(appWidgetId: Int, providerInfo: AppWidgetProviderInfo): Boolean {
        return bindWidget(appWidgetId, providerInfo.provider)
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
        providerInfo: AppWidgetProviderInfo
    ): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
        }
    }

    /**
     * Creates an Intent for the widget's configure Activity, if one exists.
     *
     * Some widgets require initial user configuration (e.g., pick a city for weather,
     * select a clock style). The configure Activity is declared in the widget's
     * AppWidgetProviderInfo metadata.
     *
     * @param appWidgetId The widget ID that the configure Activity should set up.
     * @return An Intent to launch the configure Activity, or null if the widget
     *         has no configure Activity.
     */
    fun createConfigureIntent(appWidgetId: Int): Intent? {
        val providerInfo = getProviderInfo(appWidgetId) ?: return null
        val configureActivity = providerInfo.configure ?: return null

        return Intent().apply {
            component = configureActivity
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
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
}
