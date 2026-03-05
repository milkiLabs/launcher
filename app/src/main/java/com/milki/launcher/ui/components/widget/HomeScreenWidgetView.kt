/**
 * HomeScreenWidgetView.kt - Composable that renders an Android AppWidget on the home screen
 *
 * This composable bridges the gap between Android's View-based widget system and
 * Jetpack Compose. Android app widgets (e.g. a clock widget, weather widget) are
 * rendered as AppWidgetHostView — a traditional Android View. To display them inside
 * our Compose-based home screen grid, we wrap them in an AndroidView composable.
 *
 * HOW ANDROID WIDGETS WORK:
 * 1. Each widget has a unique integer ID (appWidgetId) allocated by AppWidgetHost
 * 2. The widget's content is provided by a RemoteViews object from the widget provider app
 * 3. AppWidgetHost.createView() creates an AppWidgetHostView that:
 *    - Renders the RemoteViews content
 *    - Automatically receives updates when the provider pushes new RemoteViews
 *    - Handles click events defined in the RemoteViews (PendingIntents)
 *
 * WHY AndroidView IS NEEDED:
 * Compose doesn't have a native way to display RemoteViews. AndroidView is the
 * official interop mechanism for embedding Android Views inside Compose layouts.
 * The View is created once and its size is updated when the widget's span changes.
 *
 * SIZE MANAGEMENT:
 * The widget view must fill its allocated grid cells exactly. We receive the pixel
 * dimensions (widthPx × heightPx) from the grid layout code and apply them to the
 * AndroidView. The AppWidgetHostView is also told the size via updateAppWidgetSize()
 * so the provider can send appropriately-sized RemoteViews.
 */

package com.milki.launcher.ui.components.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.milki.launcher.data.widget.WidgetHostManager
import kotlin.math.abs

/**
 * Touch-aware wrapper for hosted widget views that provides reliable long-press
 * detection across the entire widget surface.
 *
 * WHY THIS EXISTS:
 * AppWidgetHostView is a ViewGroup that may route touch events to deeply nested
 * RemoteViews children. Parent Compose gesture detectors and plain long-click
 * listeners can become unreliable because child views consume events first.
 *
 * By detecting long-press in dispatchTouchEvent() at the wrapper level, we see
 * every pointer stream before child consumption, which makes long-press behavior
 * deterministic.
 */
private class WidgetLongPressFrameLayout(context: Context) : FrameLayout(context) {

    /** Callback invoked exactly once per gesture when long-press is recognized. */
    var onWidgetLongPress: (() -> Unit)? = null

    /** Callback invoked when finger lifts after long-press without drag. */
    var onWidgetLongPressRelease: (() -> Unit)? = null

    /** Callback invoked when drag starts after a recognized long-press. */
    var onWidgetDragStart: (() -> Unit)? = null

    /** Callback invoked during drag after long-press with per-event delta. */
    var onWidgetDrag: ((Offset) -> Unit)? = null

    /** Callback invoked when an active widget drag ends with finger up. */
    var onWidgetDragEnd: (() -> Unit)? = null

    /** Callback invoked when an active widget drag is cancelled. */
    var onWidgetDragCancel: (() -> Unit)? = null

    /** Platform long-press timeout used to match Android gesture expectations. */
    private val longPressTimeoutMs: Long = ViewConfiguration.getLongPressTimeout().toLong()

    /** Move tolerance before we cancel long-press detection. */
    private val touchSlopPx: Int = ViewConfiguration.get(context).scaledTouchSlop

    /** Initial pointer coordinates for movement threshold checks. */
    private var downX = 0f
    private var downY = 0f

    /** True while the current gesture is still eligible for long-press. */
    private var isLongPressCandidate = false

    /** Ensures callback fires at most once per pointer-down sequence. */
    private var hasFiredLongPress = false

    /** True once long-press has transitioned into drag mode. */
    private var isDragActive = false

    /** Last pointer coordinates used to compute drag delta. */
    private var lastX = 0f
    private var lastY = 0f

    /** Total drag distance used to gate drag start by touch slop. */
    private var accumulatedDragX = 0f
    private var accumulatedDragY = 0f

    /** Runnable posted on ACTION_DOWN; fires callback if gesture remains valid. */
    private val longPressRunnable = Runnable {
        if (isLongPressCandidate && !hasFiredLongPress) {
            hasFiredLongPress = true
            onWidgetLongPress?.invoke()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                accumulatedDragX = 0f
                accumulatedDragY = 0f
                isLongPressCandidate = true
                hasFiredLongPress = false
                isDragActive = false
                removeCallbacks(longPressRunnable)
                postDelayed(longPressRunnable, longPressTimeoutMs)
            }

            MotionEvent.ACTION_MOVE -> {
                if (isLongPressCandidate) {
                    val movedTooFar =
                        abs(event.x - downX) > touchSlopPx || abs(event.y - downY) > touchSlopPx
                    if (movedTooFar) {
                        isLongPressCandidate = false
                        removeCallbacks(longPressRunnable)
                    }
                }

                // Long-press already fired: this move may transition into drag mode.
                if (hasFiredLongPress) {
                    val deltaX = event.x - lastX
                    val deltaY = event.y - lastY
                    lastX = event.x
                    lastY = event.y

                    if (!isDragActive) {
                        accumulatedDragX += deltaX
                        accumulatedDragY += deltaY
                        val crossedDragThreshold =
                            abs(accumulatedDragX) > touchSlopPx || abs(accumulatedDragY) > touchSlopPx
                        if (crossedDragThreshold) {
                            isDragActive = true
                            onWidgetDragStart?.invoke()
                        }
                    }

                    if (isDragActive) {
                        onWidgetDrag?.invoke(Offset(deltaX, deltaY))
                        // While dragging we consume at wrapper level so the inner
                        // widget does not receive click/up and trigger accidental actions.
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val wasDragActive = isDragActive
                val hadLongPress = hasFiredLongPress

                isLongPressCandidate = false
                isDragActive = false
                removeCallbacks(longPressRunnable)

                when (event.actionMasked) {
                    MotionEvent.ACTION_UP -> {
                        if (wasDragActive) {
                            onWidgetDragEnd?.invoke()
                            return true
                        }
                        if (hadLongPress) {
                            onWidgetLongPressRelease?.invoke()
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        if (wasDragActive) {
                            onWidgetDragCancel?.invoke()
                            return true
                        }
                        if (hadLongPress) {
                            onWidgetLongPressRelease?.invoke()
                        }
                    }
                }
            }
        }

        // Always continue normal event dispatch to child widget views so taps
        // and widget-defined PendingIntent interactions still work.
        return super.dispatchTouchEvent(event)
    }
}

/**
 * Renders an Android AppWidget inside a Compose layout.
 *
 * This composable creates (or reuses) an AppWidgetHostView for the given
 * [appWidgetId] and sizes it to fill [widthPx] × [heightPx] pixels.
 *
 * LIFECYCLE:
 * - The AppWidgetHostView is created once via [WidgetHostManager.createHostView]
 *   and cached via `remember(appWidgetId)`.
 * - When the widget is removed from the Compose tree (e.g. scrolled off-screen
 *   or deleted), the DisposableEffect removes the view from its parent to prevent
 *   "View already has a parent" crashes if it's re-added later.
 *
 * @param appWidgetId       The bound widget ID from AppWidgetHost
 * @param widgetHostManager The manager that created and owns the host
 * @param widthPx           The desired width in pixels (typically span.columns × cellWidthPx)
 * @param heightPx          The desired height in pixels (typically span.rows × cellHeightPx)
 * @param onWidgetLongPress Called when the user long-presses anywhere on the widget view.
 *                          This is used by the home grid to show the widget context menu
 *                          reliably even when AppWidgetHostView consumes touch events.
 * @param modifier          Modifier for the AndroidView container
 */
@Composable
fun HomeScreenWidgetView(
    appWidgetId: Int,
    widgetHostManager: WidgetHostManager,
    widthPx: Int,
    heightPx: Int,
    onWidgetLongPress: () -> Unit = {},
    onWidgetLongPressRelease: () -> Unit = {},
    onWidgetDragStart: () -> Unit = {},
    onWidgetDrag: (Offset) -> Unit = {},
    onWidgetDragEnd: () -> Unit = {},
    onWidgetDragCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val currentOnWidgetLongPress = rememberUpdatedState(onWidgetLongPress)
    val currentOnWidgetLongPressRelease = rememberUpdatedState(onWidgetLongPressRelease)
    val currentOnWidgetDragStart = rememberUpdatedState(onWidgetDragStart)
    val currentOnWidgetDrag = rememberUpdatedState(onWidgetDrag)
    val currentOnWidgetDragEnd = rememberUpdatedState(onWidgetDragEnd)
    val currentOnWidgetDragCancel = rememberUpdatedState(onWidgetDragCancel)

    // Create the AppWidgetHostView once for this widget ID.
    // If the widget's provider is uninstalled, getProviderInfo returns null
    // and we can't create a view — show nothing in that case.
    val hostView: AppWidgetHostView? = remember(appWidgetId) {
        try {
            val providerInfo = widgetHostManager.getProviderInfo(appWidgetId)
            if (providerInfo != null) {
                widgetHostManager.createHostView(appWidgetId, providerInfo)
            } else {
                Log.w("HomeScreenWidgetView", "No provider info for widget $appWidgetId — provider may be uninstalled")
                null
            }
        } catch (e: Exception) {
            Log.e("HomeScreenWidgetView", "Failed to create host view for widget $appWidgetId", e)
            null
        }
    }

    if (hostView == null) return

    // Convert pixel dimensions to dp for the size update bundle.
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }

    AndroidView(
        factory = { context ->
            // Wrap in a dedicated touch-aware FrameLayout so long-press detection
            // is reliable even when AppWidgetHostView children consume events.
            WidgetLongPressFrameLayout(context).apply {
                // Remove from any previous parent (safety net).
                (hostView.parent as? android.view.ViewGroup)?.removeView(hostView)
                addView(hostView, FrameLayout.LayoutParams(widthPx, heightPx))
            }
        },
        update = { frameLayout ->
            // Update the widget's layout size when the span changes (e.g. after resize).
            val layoutParams = hostView.layoutParams
            if (layoutParams != null) {
                layoutParams.width = widthPx
                layoutParams.height = heightPx
                hostView.layoutParams = layoutParams
            }

            // Tell the widget provider about the new size so it can send
            // appropriately-sized RemoteViews.
            val sizeBundle = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp.value.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp.value.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp.value.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp.value.toInt())
            }
            hostView.updateAppWidgetSize(sizeBundle, widthDp.value.toInt(), heightDp.value.toInt(), widthDp.value.toInt(), heightDp.value.toInt())

            // Keep callback updated for the current composition lambda.
            if (frameLayout is WidgetLongPressFrameLayout) {
                frameLayout.onWidgetLongPress = {
                    currentOnWidgetLongPress.value.invoke()
                }
                frameLayout.onWidgetLongPressRelease = {
                    currentOnWidgetLongPressRelease.value.invoke()
                }
                frameLayout.onWidgetDragStart = {
                    currentOnWidgetDragStart.value.invoke()
                }
                frameLayout.onWidgetDrag = { delta ->
                    currentOnWidgetDrag.value.invoke(delta)
                }
                frameLayout.onWidgetDragEnd = {
                    currentOnWidgetDragEnd.value.invoke()
                }
                frameLayout.onWidgetDragCancel = {
                    currentOnWidgetDragCancel.value.invoke()
                }
            }
        },
        modifier = modifier
    )

    // Clean up: remove the host view from its parent when this composable leaves
    // the composition. This prevents "View already has a parent" crashes.
    DisposableEffect(appWidgetId) {
        onDispose {
            (hostView.parent as? android.view.ViewGroup)?.removeView(hostView)
        }
    }
}
