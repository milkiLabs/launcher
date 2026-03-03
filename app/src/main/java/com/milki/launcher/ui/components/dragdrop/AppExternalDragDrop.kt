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
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.DragEvent
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * MIME label used for app drag payloads.
 *
 * We still use plain text MIME for compatibility, but the payload itself is
 * structured JSON so decoding remains safe and explicit.
 */
private const val APP_DRAG_CLIP_LABEL = "launcher_app_drag_payload"

/**
 * Payload DTO used for cross-surface app drag and drop.
 *
 * We intentionally avoid including launch Intent because intents are not
 * serialization-friendly and are not required for pin/move logic.
 */
@Serializable
private data class AppDragPayload(
    val name: String,
    val packageName: String,
    val activityName: String
)

/**
 * Codec + utility helpers for app drag payloads.
 */
object AppExternalDragDropPayload {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Creates ClipData that can be transferred using Android drag and drop.
     */
    fun createClipData(appInfo: AppInfo): ClipData {
        val payload = AppDragPayload(
            name = appInfo.name,
            packageName = appInfo.packageName,
            activityName = appInfo.activityName
        )

        val serializedPayload = json.encodeToString(payload)
        return ClipData.newPlainText(APP_DRAG_CLIP_LABEL, serializedPayload)
    }

    /**
     * Fast payload gate used during ACTION_DRAG_STARTED.
     *
     * WHY THIS CHECK EXISTS:
     * Android dispatches drag events for many different drag sources.
     * If we return true for unrelated payloads, our home-surface drop overlay
     * becomes "active" and can show misleading highlight feedback.
     *
    * We verify that payload can be decoded into AppInfo from either:
    * 1) event.localState (same-process drag handoff)
    * 2) ClipData JSON payload (cross-surface transfer)
     *
     * This keeps the overlay strict and avoids false-positive highlights.
     */
    fun hasAppPayload(dragEvent: DragEvent): Boolean {
        return decodeAppInfo(dragEvent) != null
    }

    /**
     * Lightweight pre-check for ACTION_DRAG_STARTED acceptance.
     *
     * WHY THIS EXISTS:
     * Some Android variants do not provide fully decodable ClipData at
     * ACTION_DRAG_STARTED for global cross-window drags, even though later
     * drag events contain enough information. If we reject too early, DROP is
     * never delivered and the drag snaps back to source.
     *
     * This method answers: "Is this event likely one of our app drags?"
     * and allows lazy decode on DRAG_LOCATION/DROP.
     */
    fun isLikelyAppPayload(dragEvent: DragEvent): Boolean {
        val description = dragEvent.clipDescription
        if (description == null) {
            return dragEvent.localState is AppInfo
        }

        if (description.label?.toString() == APP_DRAG_CLIP_LABEL) {
            return true
        }

        return description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
    }

    /**
     * Attempts to decode AppInfo from a drag event payload.
     */
    fun decodeAppInfo(dragEvent: DragEvent): AppInfo? {
        /**
         * Preferred same-process path.
         *
         * WHY THIS FIRST:
         * - localState avoids serialization/deserialization completely.
         * - Some OEMs are less consistent about clipDescription/clip text during
         *   global drags, but localState remains stable within the same process.
         */
        val localStateAppInfo = dragEvent.localState as? AppInfo
        if (localStateAppInfo != null) {
            return localStateAppInfo.copy(launchIntent = null)
        }

        val descriptionLabel = dragEvent.clipDescription?.label?.toString()
        if (descriptionLabel != null && descriptionLabel != APP_DRAG_CLIP_LABEL) {
            // Preserve strictness when a non-matching explicit label is present.
            return null
        }

        val clipData = dragEvent.clipData ?: return null
        if (clipData.itemCount <= 0) return null

        val rawText = clipData.getItemAt(0).text?.toString() ?: return null

        return runCatching {
            val payload = json.decodeFromString(AppDragPayload.serializer(), rawText)
            AppInfo(
                name = payload.name,
                packageName = payload.packageName,
                activityName = payload.activityName,
                launchIntent = null
            )
        }.getOrNull()
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
 * This uses Android's View drag listener so it works regardless of Compose
 * foundation drag-and-drop API availability.
 *
 * IMPORTANT IMPLEMENTATION NOTE — LISTENER STABILITY:
 * The OnDragListener must remain stable across recompositions. Android only
 * sends ACTION_DRAG_STARTED once per drag session. If the listener is
 * recreated mid-drag (e.g. because a parent recomposed and callback lambdas
 * changed), the new listener never receives ACTION_DRAG_STARTED, so its
 * cached payload (activeDragAppInfo) is null and all subsequent events
 * (DRAG_LOCATION, DROP) silently fail.
 *
 * To prevent this we:
 * 1) Use rememberUpdatedState for every callback so the listener closure
 *    always invokes the latest version without needing new keys.
 * 2) Store activeDragAppInfo in a remember { mutableStateOf } so the value
 *    survives recomposition.
 * 3) Create the listener exactly once with a keyless remember { }.
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
    /**
     * rememberUpdatedState keeps a State<T> that always holds the latest
     * lambda reference supplied by the parent. The OnDragListener reads
     * through these State wrappers, so it always calls the current version
     * of each callback — even though the listener itself is never recreated.
     */
    val currentOnAppDropped by rememberUpdatedState(onAppDropped)
    val currentOnDragStarted by rememberUpdatedState(onDragStarted)
    val currentOnDragMoved by rememberUpdatedState(onDragMoved)
    val currentOnDragEnded by rememberUpdatedState(onDragEnded)

    /**
     * Drag payload cached for the active drag lifecycle.
     *
     * WHY A mutableStateOf INSTEAD OF A PLAIN var:
     * A plain local var is scoped to one invocation of the composable function.
     * If the parent recomposes, a new local var is created, but the listener
     * closure still references the old one — or worse, the listener itself is
     * recreated and the old var is garbage-collected.
     *
     * mutableStateOf inside remember { } creates a single, long-lived holder
     * that both the listener and the composable can share across recompositions.
     *
     * WHY CACHE AT ALL:
     * - Avoid repeated JSON decode work on every ACTION_DRAG_LOCATION
     * - Keep all callbacks consistent with the same decoded app payload
     * - Ensure ACTION_DROP can still succeed even if some OEM sends slightly
     *   different event data at drop time
     */
    val activeDragAppInfo = remember { mutableStateOf<AppInfo?>(null) }

    /**
     * The listener is created once and never replaced. It reads callbacks
     * through rememberUpdatedState refs and payload through mutableStateOf,
     * so it stays current without needing recreation.
     */
    val listener = remember {
        View.OnDragListener { dragTargetView, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    /**
                     * Validate that the incoming drag payload belongs to us.
                     * If the label or JSON doesn't match, reject the drag so
                     * this overlay does not interfere with unrelated drags.
                     */
                    if (!AppExternalDragDropPayload.isLikelyAppPayload(event)) {
                        activeDragAppInfo.value = null
                        Log.d("AppExternalDragDrop", "ACTION_DRAG_STARTED ignored: payload not likely app drag")
                        return@OnDragListener false
                    }

                    /**
                     * Always notify drag start so UI surfaces can reset stale target
                     * state and enable drop-highlight mode even when payload decode is
                     * deferred until later events.
                     */
                    currentOnDragStarted?.invoke()

                    activeDragAppInfo.value = AppExternalDragDropPayload.decodeAppInfo(event)
                    val dragAppInfo = activeDragAppInfo.value
                    if (dragAppInfo != null) {
                        Log.d(
                            "AppExternalDragDrop",
                            "ACTION_DRAG_STARTED accepted for ${dragAppInfo.packageName}/${dragAppInfo.activityName}"
                        )
                    } else {
                        Log.d("AppExternalDragDrop", "ACTION_DRAG_STARTED accepted with deferred payload decode")
                    }
                    true
                }
                DragEvent.ACTION_DRAG_LOCATION -> {
                    val dragAppInfo = activeDragAppInfo.value
                        ?: AppExternalDragDropPayload.decodeAppInfo(event)

                    if (dragAppInfo == null) {
                        /**
                         * Keep returning true so this target remains active for the
                         * current drag lifecycle; payload may become decodable on DROP.
                         */
                        return@OnDragListener true
                    }

                    if (activeDragAppInfo.value == null) {
                        activeDragAppInfo.value = dragAppInfo
                        Log.d(
                            "AppExternalDragDrop",
                            "Deferred payload decode resolved on DRAG_LOCATION for ${dragAppInfo.packageName}/${dragAppInfo.activityName}"
                        )
                    }

                    val normalizedLocalOffset = normalizeDragEventOffset(
                        targetView = dragTargetView,
                        event = event
                    )
                    currentOnDragMoved?.invoke(normalizedLocalOffset, dragAppInfo)
                    true
                }
                DragEvent.ACTION_DROP -> {
                    val appInfo = activeDragAppInfo.value
                        ?: AppExternalDragDropPayload.decodeAppInfo(event)
                        ?: return@OnDragListener false

                    val normalizedLocalOffset = normalizeDragEventOffset(
                        targetView = dragTargetView,
                        event = event
                    )

                    Log.d(
                        "AppExternalDragDrop",
                        "ACTION_DROP received for ${appInfo.packageName}/${appInfo.activityName} raw=(${event.x},${event.y}) local=(${normalizedLocalOffset.x},${normalizedLocalOffset.y}) viewSize=(${dragTargetView.width},${dragTargetView.height})"
                    )

                    currentOnAppDropped(appInfo, normalizedLocalOffset)
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    Log.d(
                        "AppExternalDragDrop",
                        "ACTION_DRAG_ENDED result=${event.result} hadActivePayload=${activeDragAppInfo.value != null}"
                    )
                    /**
                     * Always notify drag end so UI state is reset even when payload was
                     * never decoded during this drag lifecycle.
                     */
                    currentOnDragEnded?.invoke()
                    activeDragAppInfo.value = null
                    true
                }
                DragEvent.ACTION_DRAG_ENTERED,
                DragEvent.ACTION_DRAG_EXITED -> true
                else -> false
            }
        }
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

/**
 * Converts DragEvent coordinates into coordinates local to [targetView].
 *
 * DEVICE COMPATIBILITY NOTE:
 * Some devices/reporting paths provide DragEvent x/y already local to the
 * target view, while others can behave like window/screen-relative values for
 * cross-window global drags. When we use raw values directly on those devices,
 * the home grid can clamp drops to a fallback bottom cell.
 *
 * STRATEGY:
 * 1) If raw coordinates are already inside target bounds, keep them unchanged.
 * 2) Otherwise, build two candidates by subtracting target position in window
 *    and on-screen coordinates.
 * 3) Choose the candidate that best fits inside target bounds.
 */
private fun normalizeDragEventOffset(targetView: View, event: DragEvent): Offset {
    val rawOffset = Offset(event.x, event.y)
    val viewWidth = targetView.width.toFloat().coerceAtLeast(1f)
    val viewHeight = targetView.height.toFloat().coerceAtLeast(1f)

    fun isInsideBounds(offset: Offset): Boolean {
        return offset.x in 0f..viewWidth && offset.y in 0f..viewHeight
    }

    if (isInsideBounds(rawOffset)) {
        return rawOffset
    }

    val locationInWindow = IntArray(2)
    targetView.getLocationInWindow(locationInWindow)
    val windowCandidate = Offset(
        x = event.x - locationInWindow[0].toFloat(),
        y = event.y - locationInWindow[1].toFloat()
    )

    val locationOnScreen = IntArray(2)
    targetView.getLocationOnScreen(locationOnScreen)
    val screenCandidate = Offset(
        x = event.x - locationOnScreen[0].toFloat(),
        y = event.y - locationOnScreen[1].toFloat()
    )

    if (isInsideBounds(windowCandidate)) return windowCandidate
    if (isInsideBounds(screenCandidate)) return screenCandidate

    fun outOfBoundsDistance(offset: Offset): Float {
        val dx = when {
            offset.x < 0f -> abs(offset.x)
            offset.x > viewWidth -> abs(offset.x - viewWidth)
            else -> 0f
        }
        val dy = when {
            offset.y < 0f -> abs(offset.y)
            offset.y > viewHeight -> abs(offset.y - viewHeight)
            else -> 0f
        }
        return dx + dy
    }

    return if (outOfBoundsDistance(windowCandidate) <= outOfBoundsDistance(screenCandidate)) {
        windowCandidate
    } else {
        screenCandidate
    }
}
