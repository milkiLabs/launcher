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

package com.milki.launcher.ui.interaction.dragdrop

import android.appwidget.AppWidgetProviderInfo
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Paint
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
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.data.icon.AppIconMemoryCache
import com.milki.launcher.data.icon.ShortcutIconLoader
import com.milki.launcher.ui.interaction.dragdrop.ExternalDragPayloadCodec.ExternalDragItem
import com.milki.launcher.ui.theme.IconSize

typealias AppExternalDragDropPayload = ExternalDragPayloadCodec

/**
 * Generic alias for the shared external drag payload item type.
 */
typealias ExternalDragDropItem = ExternalDragItem

/**
 * In-memory cache for the last externally-dragged item.
 *
 * WHY THIS EXISTS:
 * When dragging across windows (search dialog → home grid, drawer → home grid),
 * Android's DragEvent.localState is unavailable to the receiving window, and
 * ClipData is only provided at ACTION_DROP (not ACTION_DRAG_LOCATION).  This
 * leaves ACTION_DRAG_LOCATION with no way to identify the dragged item,
 * preventing the drop-target highlight from showing a preview icon.
 *
 * Since all drag sources and targets live in the same process, this simple
 * cache bridges the gap: startExternal*Drag functions store the item before
 * calling View.startDragAndDrop(), and the coordinator reads it as a fallback.
 *
 * LIFECYCLE:
 * - Set   → in startExternal*Drag(), before platform drag starts
 * - Read  → in ExternalAppDragDropCoordinator at ACTION_DRAG_STARTED
 * - Clear → in ACTION_DRAG_ENDED, or on drag start failure
 */
object ExternalDragItemCache {
    var currentItem: ExternalDragDropItem? = null
        internal set

    internal fun clear() {
        currentItem = null
    }
}

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
    val dragItem = ExternalDragItem.App(appInfo)
    ExternalDragItemCache.currentItem = dragItem

    val dragHostView = resolveExternalDragHostCandidates(hostView).firstOrNull() ?: hostView
    val packageManager = hostView.context.packageManager
    val iconDrawable = AppIconMemoryCache.getOrLoad(
        packageName = appInfo.packageName,
        packageManager = packageManager
    )
    val density = dragHostView.context.resources.displayMetrics.density
    val shadowSizePx = (dragShadowSize.value * density).toInt().coerceAtLeast(1)

    val clipData = AppExternalDragDropPayload.createClipData(dragItem)
    val dragShadowBuilder = AppIconDragShadowBuilder(
        iconDrawable = iconDrawable,
        shadowSizePx = shadowSizePx
    )

    return startExternalDragWithFallbackHosts(
        hostView = hostView,
        clipData = clipData,
        localState = dragItem,
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
    val dragItem = ExternalDragItem.File(fileDocument)
    ExternalDragItemCache.currentItem = dragItem

    val density = hostView.context.resources.displayMetrics.density
    val shadowSizePx = (IconSize.appList.value * density).toInt().coerceAtLeast(1)
    val clipData = AppExternalDragDropPayload.createClipData(dragItem)
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
        localState = dragItem,
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
    val dragItem = ExternalDragItem.Contact(contact)
    ExternalDragItemCache.currentItem = dragItem

    val density = hostView.context.resources.displayMetrics.density
    val shadowSizePx = (IconSize.appList.value * density).toInt().coerceAtLeast(1)
    val clipData = AppExternalDragDropPayload.createClipData(dragItem)
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
        localState = dragItem,
        dragShadowBuilder = dragShadowBuilder,
        failureLogLabel = "contact:${contact.id}/${contact.displayName}"
    )
}

/**
 * Starts an external app-shortcut drag operation.
 */
fun startExternalShortcutDrag(
    hostView: View,
    shortcut: HomeItem.AppShortcut,
    dragShadowSize: Dp = IconSize.appList
): Boolean {
    val dragItem = ExternalDragItem.Shortcut(shortcut)
    ExternalDragItemCache.currentItem = dragItem

    val dragHostView = resolveExternalDragHostCandidates(hostView).firstOrNull() ?: hostView
    val density = dragHostView.context.resources.displayMetrics.density
    val shadowSizePx = (dragShadowSize.value * density).toInt().coerceAtLeast(1)
    val shortcutIcon = ShortcutIconLoader.getCached(shortcut)
    val fallbackIcon = ContextCompat.getDrawable(hostView.context, android.R.drawable.sym_def_app_icon)
    val iconDrawable = shortcutIcon ?: fallbackIcon
    val clipData = AppExternalDragDropPayload.createClipData(dragItem)
    val dragShadowBuilder = if (iconDrawable != null) {
        AppIconDragShadowBuilder(
            iconDrawable = iconDrawable,
            shadowSizePx = shadowSizePx
        )
    } else {
        View.DragShadowBuilder(hostView)
    }

    return startExternalDragWithFallbackHosts(
        hostView = hostView,
        clipData = clipData,
        localState = dragItem,
        dragShadowBuilder = dragShadowBuilder,
        failureLogLabel = "shortcut:${shortcut.packageName}/${shortcut.shortcutId}"
    )
}

/**
 * Starts an external drag for an item being pulled OUT of a folder popup.
 *
 * WHY THIS FUNCTION EXISTS:
 * When the user long-presses an icon inside [FolderPopupDialog] and the pointer
 * leaves the popup boundary, the dialog cannot hand a Compose-level drag to the
 * home grid because they live in different Compose surfaces.  Instead the folder
 * popup calls this function, which starts a platform [android.view.View.startDragAndDrop]
 * operation, and the [AppExternalDropTargetOverlay] on the home grid receives the
 * resulting [android.view.DragEvent].
 *
 * HOW THE PAYLOAD IS TAGGED:
 * The item is wrapped in an [ExternalDragItem.FolderChild] that carries both the
 * source [folderId] and the [item] itself.  This lets the drop handler call
 * [HomeRepository.extractItemFromFolder] rather than a generic pin/move operation.
 *
 * THE DRAG SHADOW:
 * Each item type gets a small icon-sized shadow via [AppIconDragShadowBuilder]:
 * - [HomeItem.PinnedApp]     → cached app icon (AppIconMemoryCache) or sym_def_app_icon
 * - [HomeItem.PinnedFile]    → ic_menu_agenda
 * - [HomeItem.PinnedContact] → ic_menu_myplaces
 * - [HomeItem.AppShortcut]   → sym_def_app_icon
 * - anything else            → ic_menu_add
 *
 * IMPORTANT: [View.DragShadowBuilder(hostView)] is intentionally NEVER used here.
 * [hostView] is [LocalView.current] — the full-screen Compose root.  The default
 * constructor renders the entire attached view as the shadow, which would show the
 * whole screen dragging under the finger.
 *
 * @param hostView  Any attached view in the same window (e.g. [LocalView.current]).
 * @param folderId  ID of the folder the item is leaving.
 * @param item      The [HomeItem] being dragged out.
 * @return true when the platform drag-and-drop session started successfully.
 */
fun startExternalFolderItemDrag(
    hostView: View,
    folderId: String,
    item: com.milki.launcher.domain.model.HomeItem,
    dragShadowSize: Dp = IconSize.appList
): Boolean {
    // ClipData is required by the platform API but we don't actually decode it —
    // we always read localState first in ExternalDragPayloadCodec.decodeDragItem.
    // We still provide a minimal plain-text clip so older API levels are happy.
    val clipData = android.content.ClipData.newPlainText(
        ExternalDragPayloadCodec.DRAG_CLIP_LABEL,
        item.id
    )

    val density = hostView.context.resources.displayMetrics.density
    val shadowSizePx = (dragShadowSize.value * density).toInt().coerceAtLeast(1)

    // Choose an appropriate drag shadow drawable based on the item type.
    //
    // IMPORTANT: Never use View.DragShadowBuilder(hostView) here.
    // hostView is LocalView.current — the full-screen Compose root view.
    // Passing it to the default DragShadowBuilder causes Android to render
    // the ENTIRE SCREEN as the drag shadow, which is the bug reported for PDF files.
    //
    // Instead, always use AppIconDragShadowBuilder with a specific Drawable so the
    // shadow is a small, item-representative icon.
    val shadowDrawable: Drawable? = when (item) {
        is com.milki.launcher.domain.model.HomeItem.PinnedApp -> {
            // Try the in-memory app icon cache first; fall back to the system default.
            AppIconMemoryCache.get(item.packageName)
                ?: ContextCompat.getDrawable(hostView.context, android.R.drawable.sym_def_app_icon)
        }
        is com.milki.launcher.domain.model.HomeItem.PinnedFile -> {
            // Use the same file icon that startExternalFileDrag uses.
            ContextCompat.getDrawable(hostView.context, android.R.drawable.ic_menu_agenda)
        }
        is com.milki.launcher.domain.model.HomeItem.PinnedContact -> {
            // Use the same contact icon that startExternalContactDrag uses.
            ContextCompat.getDrawable(hostView.context, android.R.drawable.ic_menu_myplaces)
        }
        is com.milki.launcher.domain.model.HomeItem.AppShortcut -> {
            // Generic app icon for shortcuts.
            ContextCompat.getDrawable(hostView.context, android.R.drawable.sym_def_app_icon)
        }
        else -> {
            // FolderItem (shouldn't be dragged out of itself) and any future types:
            // use a neutral document icon rather than the full-screen view shadow.
            ContextCompat.getDrawable(hostView.context, android.R.drawable.ic_menu_add)
        }
    }

    val dragShadowBuilder: View.DragShadowBuilder = if (shadowDrawable != null) {
        AppIconDragShadowBuilder(shadowDrawable, shadowSizePx)
    } else {
        // Last-resort fallback: an empty shadow (0×0) so at least no screen content leaks.
        object : View.DragShadowBuilder() {
            override fun onProvideShadowMetrics(outShadowSize: android.graphics.Point, outShadowTouchPoint: android.graphics.Point) {
                outShadowSize.set(shadowSizePx, shadowSizePx)
                outShadowTouchPoint.set(shadowSizePx / 2, shadowSizePx / 2)
            }
            override fun onDrawShadow(canvas: android.graphics.Canvas) { /* intentionally blank */ }
        }
    }

    // Wrap the item so the drop handler knows to call extractItemFromFolder.
    val payload = ExternalDragItem.FolderChild(folderId = folderId, childItem = item)
    ExternalDragItemCache.currentItem = payload

    return startExternalDragWithFallbackHosts(
        hostView = hostView,
        clipData = clipData,
        localState = payload,
        dragShadowBuilder = dragShadowBuilder,
        failureLogLabel = "folder-child:${folderId}/${item.id}"
    )
}

/**
 * Starts an external drag for a widget being dragged from the Widget Picker BottomSheet.
 *
 * WHY THIS FUNCTION EXISTS:
 * The Widget Picker is rendered in a ModalBottomSheet (a dialog window), but the
 * home grid drop target lives in the main Activity window. Just like search-dialog
 * app drags, we use Android platform View.startDragAndDrop() to cross the window
 * boundary. The drag shadow shows either the widget's preview image or the app icon.
 *
 * PAYLOAD:
 * We pass [ExternalDragItem.Widget] via [DragEvent.localState] for fast same-process
 * decoding, and also attach a ClipData JSON fallback (provider component + span)
 * so widget drags still decode correctly when localState is unavailable.
 *
 * @param hostView  Any attached View in the bottom sheet window (typically [LocalView.current]).
 * @param providerInfo  The widget provider selected by the user.
 * @param span          The default grid span for this widget.
 * @param dragShadowSize  Base one-cell size for drag shadow calculations.
 * @return true when the platform drag session started successfully.
 */
fun startExternalWidgetDrag(
    hostView: View,
    providerInfo: AppWidgetProviderInfo,
    span: GridSpan,
    dragShadowSize: Dp = IconSize.appGrid
): Boolean {
    val density = hostView.context.resources.displayMetrics.density
    val shadowCellSizePx = (dragShadowSize.value * density).toInt().coerceAtLeast(1)
    val shadowWidthPx = (shadowCellSizePx * span.columns).coerceAtLeast(1)
    val shadowHeightPx = (shadowCellSizePx * span.rows).coerceAtLeast(1)

    val payload = ExternalDragItem.Widget(
        providerInfo = providerInfo,
        providerComponent = providerInfo.provider,
        span = span
    )
    ExternalDragItemCache.currentItem = payload

    val clipData = AppExternalDragDropPayload.createClipData(payload)
    // Use a simple rectangle shadow for widgets (no stretched widget image).
    // This keeps startDragAndDrop reliable on OEM variants while preserving
    // the "box-like" drag visual requested by product UX.
    val dragShadowBuilder = AppPlainRectDragShadowBuilder(
        shadowWidthPx = shadowWidthPx,
        shadowHeightPx = shadowHeightPx
    )

    return startExternalDragWithFallbackHosts(
        hostView = hostView,
        clipData = clipData,
        localState = payload,
        dragShadowBuilder = dragShadowBuilder,
        failureLogLabel = "widget:${providerInfo.provider.flattenToShortString()}"
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
    ExternalDragItemCache.clear()
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
 * Drag shadow builder that draws a plain rectangular box for widget drags.
 *
 * WHY NOT EMPTY SHADOW:
 * Some OEM drag implementations are unreliable when the shadow draws nothing.
 * A lightweight transparent rectangle keeps drag initiation stable without rendering
 * a visible ghost that confuses the user (since we handle target highlights separately).
 */
private class AppPlainRectDragShadowBuilder(
    private val shadowWidthPx: Int,
    private val shadowHeightPx: Int
) : View.DragShadowBuilder() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.TRANSPARENT
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = android.graphics.Color.TRANSPARENT
    }

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(shadowWidthPx, shadowHeightPx)
        outShadowTouchPoint.set(shadowWidthPx / 2, shadowHeightPx / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val right = shadowWidthPx.toFloat()
        val bottom = shadowHeightPx.toFloat()
        canvas.drawRect(0f, 0f, right, bottom, fillPaint)
        canvas.drawRect(0f, 0f, right, bottom, strokePaint)
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
