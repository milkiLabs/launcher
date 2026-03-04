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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.data.icon.AppIconMemoryCache
import com.milki.launcher.ui.components.dragdrop.ExternalDragPayloadCodec.ExternalDragItem
import com.milki.launcher.ui.theme.IconSize

typealias AppExternalDragDropPayload = ExternalDragPayloadCodec

/**
 * Generic alias for the shared external drag payload item type.
 */
typealias ExternalDragDropItem = ExternalDragItem

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
    val dragHostView = resolveExternalDragHostCandidates(hostView).firstOrNull() ?: hostView
    val packageManager = hostView.context.packageManager
    val iconDrawable = AppIconMemoryCache.getOrLoad(
        packageName = appInfo.packageName,
        packageManager = packageManager
    )
    val density = dragHostView.context.resources.displayMetrics.density
    val shadowSizePx = (dragShadowSize.value * density).toInt().coerceAtLeast(1)

    val clipData = AppExternalDragDropPayload.createClipData(ExternalDragItem.App(appInfo))
    val dragShadowBuilder = AppIconDragShadowBuilder(
        iconDrawable = iconDrawable,
        shadowSizePx = shadowSizePx
    )

    return startExternalDragWithFallbackHosts(
        hostView = hostView,
        clipData = clipData,
        localState = ExternalDragItem.App(appInfo),
        dragShadowBuilder = dragShadowBuilder,
        failureLogLabel = "app:${appInfo.packageName}/${appInfo.activityName}"
    )
}

/**
 * Starts an external file drag operation.
 *
 * File rows do not have package-based icons, so we use the host view shadow.
 */
fun startExternalFileDrag(
    hostView: View,
    fileDocument: FileDocument
): Boolean {
    val density = hostView.context.resources.displayMetrics.density
    val shadowSizePx = (IconSize.appList.value * density).toInt().coerceAtLeast(1)
    val clipData = AppExternalDragDropPayload.createClipData(ExternalDragItem.File(fileDocument))
    val fileDrawable = ContextCompat.getDrawable(hostView.context, android.R.drawable.ic_menu_agenda)
    val dragShadowBuilder = if (fileDrawable != null) {
        AppIconDragShadowBuilder(
            iconDrawable = fileDrawable,
            shadowSizePx = shadowSizePx
        )
    } else {
        View.DragShadowBuilder(hostView)
    }
    return startExternalDragWithFallbackHosts(
        hostView = hostView,
        clipData = clipData,
        localState = ExternalDragItem.File(fileDocument),
        dragShadowBuilder = dragShadowBuilder,
        failureLogLabel = "file:${fileDocument.name}"
    )
}

/**
 * Starts an external contact drag operation.
 *
 * Contact rows also use the host view drag shadow for a lightweight, generic
 * implementation that works across all prefix result rows.
 */
fun startExternalContactDrag(
    hostView: View,
    contact: Contact
): Boolean {
    val density = hostView.context.resources.displayMetrics.density
    val shadowSizePx = (IconSize.appList.value * density).toInt().coerceAtLeast(1)
    val clipData = AppExternalDragDropPayload.createClipData(ExternalDragItem.Contact(contact))
    val contactDrawable = ContextCompat.getDrawable(hostView.context, android.R.drawable.ic_menu_myplaces)
    val dragShadowBuilder = if (contactDrawable != null) {
        AppIconDragShadowBuilder(
            iconDrawable = contactDrawable,
            shadowSizePx = shadowSizePx
        )
    } else {
        View.DragShadowBuilder(hostView)
    }
    return startExternalDragWithFallbackHosts(
        hostView = hostView,
        clipData = clipData,
        localState = ExternalDragItem.Contact(contact),
        dragShadowBuilder = dragShadowBuilder,
        failureLogLabel = "contact:${contact.id}/${contact.displayName}"
    )
}

/**
 * Shared host-selection and drag start logic for all external payload types.
 */
private fun startExternalDragWithFallbackHosts(
    hostView: View,
    clipData: android.content.ClipData,
    localState: Any,
    dragShadowBuilder: View.DragShadowBuilder,
    failureLogLabel: String
): Boolean {
    val candidateHosts = resolveExternalDragHostCandidates(hostView)

    val primaryFlags = View.DRAG_FLAG_GLOBAL
    val fallbackFlags = 0

    for (candidate in candidateHosts) {
        if (candidate.startDragAndDrop(clipData, dragShadowBuilder, localState, primaryFlags)) {
            return true
        }
    }

    for (candidate in candidateHosts) {
        if (candidate.startDragAndDrop(clipData, dragShadowBuilder, localState, fallbackFlags)) {
            return true
        }
    }

    Log.w("AppExternalDragDrop", "Failed to start external drag for $failureLogLabel")
    return false
}

/**
 * Resolves candidate drag host views using one canonical ordering strategy.
 *
 * ORDERING POLICY:
 * 1) Activity decor view (most stable across dialog/window transitions)
 * 2) Root view
 * 3) Original source host view
 *
 * Only attached views are returned.
 */
private fun resolveExternalDragHostCandidates(hostView: View): List<View> {
    val activityDecorView = hostView.context.findActivity()?.window?.decorView
    val rootView = hostView.rootView

    return buildList {
        if (activityDecorView != null) add(activityDecorView)
        if (rootView !== activityDecorView) add(rootView)
        if (hostView !== activityDecorView && hostView !== rootView) add(hostView)
    }.filter { candidate -> candidate.isAttachedToWindow }
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
    onItemDropped: (item: ExternalDragDropItem, localOffset: Offset) -> Boolean,
    onDragStarted: (() -> Unit)? = null,
    onDragMoved: ((localOffset: Offset, item: ExternalDragDropItem?) -> Unit)? = null,
    onDragEnded: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentOnItemDroppedState = rememberUpdatedState(onItemDropped)
    val currentOnDragStartedState = rememberUpdatedState(onDragStarted)
    val currentOnDragMovedState = rememberUpdatedState(onDragMoved)
    val currentOnDragEndedState = rememberUpdatedState(onDragEnded)

    val coordinator = remember { DefaultExternalAppDragDropCoordinator() }
    val listener = remember(coordinator) {
        coordinator.createListener(
            object : ExternalAppDragDropCoordinator.TargetCallbacks {
                override fun onStarted() {
                    currentOnDragStartedState.value?.invoke()
                }

                override fun onMoved(localOffset: Offset, item: ExternalDragDropItem?) {
                    currentOnDragMovedState.value?.invoke(localOffset, item)
                }

                override fun onDropped(item: ExternalDragDropItem, localOffset: Offset): Boolean {
                    return currentOnItemDroppedState.value(item, localOffset)
                }

                override fun onEnded(result: Boolean) {
                    currentOnDragEndedState.value?.invoke()
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
