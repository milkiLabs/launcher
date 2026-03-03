/**
 * AppExternalDragDrop.kt - Platform drag/drop bridge for app payloads
 *
 * This file handles Android platform drag-and-drop payloads for dragging apps
 * from the search dialog and dropping them on launcher surfaces such as the
 * home grid.
 *
 * WHY THIS FILE EXISTS:
 * 1) Compose-level in-surface dragging and Android platform drag/drop are
 *    separate systems.
 * 2) Search dialog is rendered in a dialog window while home grid lives in the
 *    main launcher content, so cross-surface transfers should use platform DnD.
 * 3) We need a stable, explicit payload contract for app identity.
 */

package com.milki.launcher.ui.components.dragdrop

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.Dp
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.data.icon.AppIconMemoryCache
import com.milki.launcher.ui.theme.IconSize

typealias AppExternalDragDropPayload = ExternalDragPayloadCodec

/**
 * Starts an external app drag operation using a stable host view.
 *
 * This function is intentionally imperative so callers can invoke it from
 * existing gesture handlers (e.g., combinedClickable onLongClick) without
 * adding extra pointer-input layers that may interfere with clicks.
 *
 * @return true when platform drag started successfully.
 */
fun startExternalAppDrag(
    hostView: View,
    appInfo: AppInfo,
    dragShadowSize: Dp = IconSize.appList
): Boolean {
    /**
     * Choose a drag host view that outlives transient UI surfaces.
     *
     * IMPORTANT CONTEXT:
     * Search results may live inside a Dialog window. If we start platform drag
     * from a short-lived dialog root and then dismiss that dialog immediately,
     * some OEM implementations can cancel/unstabilize the drag stream.
     *
     * Using Activity decor view when available gives us the most stable host.
     * Fallback order keeps behavior safe even if no Activity can be resolved.
     */
    val activityDecorView = hostView.context.findActivity()?.window?.decorView
    val rootView = hostView.rootView

    /**
     * Candidate hosts ordered by reliability preference.
     *
     * WHY MULTIPLE HOSTS:
     * Some devices reject startDragAndDrop() from specific view roots depending
     * on current gesture ownership/window routing. Trying multiple attached
     * hosts avoids hard failure while preserving our preferred decor-root path.
     */
    val candidateHosts = buildList {
        if (activityDecorView != null) add(activityDecorView)
        if (rootView !== activityDecorView) add(rootView)
        if (hostView !== activityDecorView && hostView !== rootView) add(hostView)
    }.filter { candidate -> candidate.isAttachedToWindow }

    val dragHostView = candidateHosts.firstOrNull() ?: hostView
    val packageManager = hostView.context.packageManager
    val iconDrawable = AppIconMemoryCache.getOrLoad(
        packageName = appInfo.packageName,
        packageManager = packageManager
    )
    val density = dragHostView.context.resources.displayMetrics.density
    val shadowSizePx = (dragShadowSize.value * density).toInt().coerceAtLeast(1)

    val clipData = AppExternalDragDropPayload.createClipData(appInfo)
    val dragShadowBuilder = AppIconDragShadowBuilder(
        iconDrawable = iconDrawable,
        shadowSizePx = shadowSizePx
    )

    /**
     * First attempt uses DRAG_FLAG_GLOBAL so drag can cross from search dialog
     * window to the home-screen host window.
     *
     * Fallback attempt uses local flags in case a specific OEM rejects global
     * drag start from a given host view despite being attached.
     */
    val primaryFlags = View.DRAG_FLAG_GLOBAL
    val fallbackFlags = 0

    for (candidate in candidateHosts) {
        if (candidate.startDragAndDrop(clipData, dragShadowBuilder, appInfo, primaryFlags)) {
            return true
        }
    }

    for (candidate in candidateHosts) {
        if (candidate.startDragAndDrop(clipData, dragShadowBuilder, appInfo, fallbackFlags)) {
            return true
        }
    }

    Log.w(
        "AppExternalDragDrop",
        "Failed to start external drag for ${appInfo.packageName}/${appInfo.activityName}"
    )
    return false
}

/**
 * Resolve an Activity from any Context chain.
 *
 * We intentionally keep this local to drag/drop bridge code, because this
 * helper is only needed for selecting a stable drag host view.
 */
private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

/**
 * Drag shadow builder that draws only the app icon (not the entire host view).
 */
private class AppIconDragShadowBuilder(
    private val iconDrawable: Drawable,
    private val shadowSizePx: Int
) : View.DragShadowBuilder() {
    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(shadowSizePx, shadowSizePx)
        outShadowTouchPoint.set(shadowSizePx / 2, shadowSizePx / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        iconDrawable.setBounds(0, 0, shadowSizePx, shadowSizePx)
        iconDrawable.draw(canvas)
    }
}

/**
 * Transparent view overlay that accepts external app drag payload drops.
 *
 * This composable is intentionally tiny: it adapts Compose callback props to
 * the reusable [ExternalAppDragDropCoordinator] and hosts a transparent Android
 * View that receives platform drag events.
 */
@Composable
@SuppressLint("ClickableViewAccessibility")
fun AppExternalDropTargetOverlay(
    onAppDropped: (appInfo: AppInfo, localOffset: Offset) -> Boolean,
    onDragStarted: (() -> Unit)? = null,
    onDragMoved: ((localOffset: Offset, appInfo: AppInfo?) -> Unit)? = null,
    onDragEnded: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentOnAppDropped by rememberUpdatedState(onAppDropped)
    val currentOnDragStarted by rememberUpdatedState(onDragStarted)
    val currentOnDragMoved by rememberUpdatedState(onDragMoved)
    val currentOnDragEnded by rememberUpdatedState(onDragEnded)

    val coordinator = remember { DefaultExternalAppDragDropCoordinator() }
    val listener = remember(coordinator) {
        coordinator.createListener(
            object : ExternalAppDragDropCoordinator.TargetCallbacks {
                override fun onStarted() {
                    currentOnDragStarted?.invoke()
                }

                override fun onMoved(localOffset: Offset, appInfo: AppInfo?) {
                    currentOnDragMoved?.invoke(localOffset, appInfo)
                }

                override fun onDropped(appInfo: AppInfo, localOffset: Offset): Boolean {
                    return currentOnAppDropped(appInfo, localOffset)
                }

                override fun onEnded(result: Boolean) {
                    currentOnDragEnded?.invoke()
                }
            }
        )
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            View(context).apply {
                setOnDragListener(listener)
            }
        },
        update = { view ->
            view.setOnDragListener(listener)
        }
    )
}
