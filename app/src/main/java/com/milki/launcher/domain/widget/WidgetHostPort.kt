package com.milki.launcher.domain.widget

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import com.milki.launcher.domain.model.GridSpan

/**
 * WidgetHostPort.kt - Domain-facing port over the Android widget host
 *
 * This interface is the narrow seam between the UI/presentation layers and the
 * Android AppWidgetHost framework. It exposes exactly the operations the
 * presentation and UI layers need:
 *
 * - Host listening lifecycle (updateHostState)
 * - Widget ID allocation/deallocation (allocateWidgetId/deallocateWidgetId)
 * - Binding widgets (bindWidget/createBindPermissionIntent/needsConfigure)
 * - Configuration and bind permission flows (startConfigureActivityForResult)
 * - Host view creation and sizing (createHostView/updateWidgetSize)
 * - Provider lookups (getProviderInfo/findInstalledProvider/loadProviderLabel)
 *
 * FRAMEWORK TYPES:
 * AppWidgetProviderInfo/AppWidgetHostView are Android bindings that must flow
 * into Compose's AndroidView unchanged, so they intentionally appear in the
 * signatures. Everything else about the framework (AppWidgetHost, listening
 * registration, ID bookkeeping) stays behind the data-layer implementation.
 *
 * Catalog assembly (getInstalledProviders + span math for the picker) is NOT
 * part of this port; it lives in WidgetPickerCatalogStore in the data layer.
 */
interface WidgetHostPort {

    /**
     * Updates the visibility/state flags that decide whether the underlying
     * host listens for widget updates. The launcher should listen only while
     * its main surface is started, resumed, and not on a transient surface.
     */
    fun updateHostState(
        started: Boolean? = null,
        resumed: Boolean? = null,
        isNormal: Boolean? = null
    )

    /** Allocates a new unique widget ID from the system. */
    fun allocateWidgetId(): Int

    /** Releases a previously allocated widget ID back to the system. */
    fun deallocateWidgetId(widgetId: Int)

    /**
     * Binds an allocated widget ID to a provider.
     *
     * @return true when bound immediately; false when the user must first
     *         grant permission via [createBindPermissionIntent].
     */
    fun bindWidget(
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo,
        options: Bundle? = null
    ): Boolean

    /**
     * Creates the Intent for the system "Allow this widget?" dialog, needed
     * when [bindWidget] returns false.
     */
    fun createBindPermissionIntent(
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo,
        options: Bundle? = null
    ): Intent

    /**
     * Creates the same "Allow this widget?" Intent for a provider that is only
     * known by its component parts (e.g. restored from a backup), targeting the
     * calling user's profile.
     */
    fun createBindPermissionIntent(
        appWidgetId: Int,
        providerPackage: String,
        providerClass: String
    ): Intent

    /** Starts the provider's configuration activity for a result. */
    fun startConfigureActivityForResult(
        activity: Activity,
        appWidgetId: Int,
        requestCode: Int,
        options: Bundle? = null
    )

    /**
     * Creates the AppWidgetHostView rendering the widget's content, ready to
     * be wrapped in a Compose AndroidView.
     */
    fun createHostView(widgetId: Int, providerInfo: AppWidgetProviderInfo): AppWidgetHostView

    /**
     * Returns the provider info for a bound widget ID, or null when the ID is
     * unbound or the provider app was uninstalled.
     */
    fun getProviderInfo(widgetId: Int): AppWidgetProviderInfo?

    /** Finds an installed provider by component name (drag payload fallback). */
    fun findInstalledProvider(provider: ComponentName): AppWidgetProviderInfo?

    /** Returns all widget providers installed on the device (picker catalog). */
    fun getInstalledProviders(): List<AppWidgetProviderInfo>

    /** Resolves the user-facing label for a widget provider. */
    fun loadProviderLabel(providerInfo: AppWidgetProviderInfo): String

    /** Builds the initial options bundle used when binding a widget for a span. */
    fun createBindOptions(span: GridSpan): Bundle

    /** Updates a hosted widget with its exact rendered size. */
    fun updateWidgetSize(
        hostView: AppWidgetHostView,
        widthPx: Int,
        heightPx: Int
    )
}
