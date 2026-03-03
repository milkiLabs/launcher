package com.milki.launcher.ui.components.dragdrop

import android.content.ClipData
import android.content.ClipDescription
import android.view.DragEvent
import com.milki.launcher.domain.model.AppInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * ExternalDragPayloadCodec.kt - Single-purpose codec for launcher app drag payloads.
 *
 * WHY THIS FILE EXISTS:
 * External app drag/drop uses Android platform DragEvent payload transport.
 * Keeping all encode/decode/gating logic in one place makes behavior predictable,
 * reusable, and easy to test.
 */
object ExternalDragPayloadCodec {

    /**
     * Label used for launcher app payloads in ClipData.
     */
    const val APP_DRAG_CLIP_LABEL: String = "launcher_app_drag_payload"

    @Serializable
    private data class AppDragPayload(
        val name: String,
        val packageName: String,
        val activityName: String
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Creates ClipData for platform drag transfer.
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
     * Returns true when this drag event is likely a launcher app payload.
     *
     * This is intentionally tolerant for ACTION_DRAG_STARTED to avoid rejecting
     * valid cross-window drags too early on OEM variants.
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
     * Decodes AppInfo from localState or ClipData JSON payload.
     */
    fun decodeAppInfo(dragEvent: DragEvent): AppInfo? {
        val localStateAppInfo = dragEvent.localState as? AppInfo
        if (localStateAppInfo != null) {
            return localStateAppInfo.copy(launchIntent = null)
        }

        val descriptionLabel = dragEvent.clipDescription?.label?.toString()
        if (descriptionLabel != null && descriptionLabel != APP_DRAG_CLIP_LABEL) {
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
