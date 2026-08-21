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
 * LAYERING:
 * UI/presentation code depends on the [WidgetHostPort] interface (domain layer),
 * not on this class directly. Data-layer collaborators (backup sanitizer, widget
 * picker catalog) may keep depending on this concretion.
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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.widget.WidgetHostPort

class WidgetHostManager(
    private val context: Context
) : WidgetHostPort {
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
    private var activityStarted = false
    private var activityResumed = false
    private var stateIsNormal = false
    private var isListening = false

    /**
     * Resolves the user-facing label for a widget provider.
     *
     * This keeps PackageManager usage encapsulated inside WidgetHostManager so
     * callers do not need direct access to packageManager internals.
     */
    override fun loadProviderLabel(providerInfo: AppWidgetProviderInfo): String {
        return providerInfo.loadLabel(packageManager) ?: providerInfo.provider.shortClassName
    }

    override fun updateHostState(
        started: Boolean?,
        resumed: Boolean?,
        isNormal: Boolean?
    ) {
        started?.let { activityStarted = it }
        resumed?.let { activityResumed = it }
        isNormal?.let { stateIsNormal = it }
        syncListeningState()
    }

    private fun syncListeningState() {
        val shouldListen = activityStarted && activityResumed && stateIsNormal
        if (shouldListen == isListening) return

        if (updateListeningRegistration(shouldListen)) {
            isListening = shouldListen
        }
    }

    private fun updateListeningRegistration(shouldListen: Boolean): Boolean {
        val action = if (shouldListen) "start" else "stop"
        val hostCommand = if (shouldListen) {
            appWidgetHost::startListening
        } else {
            appWidgetHost::stopListening
        }

        return try {
            hostCommand()
            true
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to $action widget host listening; keeping launcher alive despite framework error",
                e
            )
            false
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
    override fun allocateWidgetId(): Int {
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
    override fun deallocateWidgetId(widgetId: Int) {
        try {
            appWidgetHost.deleteAppWidgetId(widgetId)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to deallocate widget ID $widgetId", e)
        }
    }

    override fun bindWidget(
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo,
        options: Bundle?
    ): Boolean {
        return try {
            appWidgetManager.bindAppWidgetIdIfAllowed(
                appWidgetId,
                providerInfo.profile,
                providerInfo.provider,
                options ?: Bundle.EMPTY
            )
        } catch (e: IllegalArgumentException) {
            val providerSummary =
                "widgetId=$appWidgetId provider=${providerInfo.provider} profile=${providerInfo.profile}"
            Log.e(TAG, "Failed to bind $providerSummary", e)
            false
        } catch (e: SecurityException) {
            val providerSummary =
                "widgetId=$appWidgetId provider=${providerInfo.provider} profile=${providerInfo.profile}"
            Log.e(TAG, "Failed to bind $providerSummary", e)
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
    override fun createBindPermissionIntent(
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo,
        options: Bundle?
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

    /**
     * Starts the provider's configuration Activity using the host helper.
     *
     * This mirrors Launcher3's approach and is more reliable than launching the
     * configure Activity directly, especially for cross-profile or restricted providers.
     */
    override fun startConfigureActivityForResult(
        activity: Activity,
        appWidgetId: Int,
        requestCode: Int,
        options: Bundle?
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
    override fun createHostView(widgetId: Int, providerInfo: AppWidgetProviderInfo): AppWidgetHostView {
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
    override fun getProviderInfo(widgetId: Int): AppWidgetProviderInfo? {
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
    override fun getInstalledProviders(): List<AppWidgetProviderInfo> {
        return appWidgetManager.installedProviders
    }

    /**
     * Finds a widget provider from installed providers by its component name.
     *
     * This is used when decoding a widget drag payload from ClipData fallback,
     * where we only have the provider component and must re-resolve full
     * AppWidgetProviderInfo at drop time.
     */
    override fun findInstalledProvider(provider: ComponentName): AppWidgetProviderInfo? {
        return appWidgetManager.installedProviders.firstOrNull { it.provider == provider }
    }

    /**
     * Builds the initial options bundle used when binding a widget for a given span.
     *
     * This gives providers accurate size information from the start instead of
     * waiting for the first host-view layout pass.
     */
    override fun createBindOptions(span: GridSpan): Bundle {
        val (widthPx, heightPx) = estimateWidgetSizePx(context, span)
        return createWidgetSizeOptions(context, widthPx = widthPx, heightPx = heightPx)
    }

    /**
     * Updates a hosted widget with its exact rendered size.
     */
    override fun updateWidgetSize(
        hostView: AppWidgetHostView,
        widthPx: Int,
        heightPx: Int
    ) {
        val sizeOptions = createWidgetSizeOptions(context, widthPx = widthPx, heightPx = heightPx)
        val widthDp = pxToDp(context, widthPx)
        val heightDp = pxToDp(context, heightPx)
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
}
