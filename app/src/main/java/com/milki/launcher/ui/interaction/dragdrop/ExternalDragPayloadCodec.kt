package com.milki.launcher.ui.interaction.dragdrop

import android.content.ClipData
import android.content.ClipDescription
import android.content.ComponentName
import android.view.DragEvent
import android.appwidget.AppWidgetProviderInfo
import com.milki.launcher.core.util.lenientJson
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes [ComponentName] as its flattened "pkg/class" string form.
 */
private object ComponentNameSerializer : KSerializer<ComponentName> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("android.content.ComponentName", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ComponentName) {
        encoder.encodeString(value.flattenToString())
    }

    override fun deserialize(decoder: Decoder): ComponentName {
        val flat = decoder.decodeString()
        return ComponentName.unflattenFromString(flat)
            ?: throw kotlinx.serialization.SerializationException(
                "Malformed ComponentName: $flat"
            )
    }
}

/**
 * ExternalDragPayloadCodec.kt - Shared codec for launcher external drag payloads.
 *
 * WHY THIS FILE EXISTS:
 * External drag/drop uses Android platform DragEvent payload transport.
 * Keeping all encode/decode/gating logic in one place makes behavior predictable,
 * reusable, and easy to test.
 */
object ExternalDragPayloadCodec {

    /**
     * Unified label used for launcher drag payloads in ClipData.
     */
    const val DRAG_CLIP_LABEL: String = "launcher_drag_payload"

    /**
     * Legacy label kept for backward compatibility with older drag sessions.
     *
     * This ensures we can still decode events that were started by older
     * launcher code paths while we migrate to the unified payload contract.
     */
    const val LEGACY_APP_DRAG_CLIP_LABEL: String = "launcher_app_drag_payload"

    /**
     * Type-safe representation of all externally draggable launcher entities.
     *
     * This allows one drag/drop pipeline to carry app, file, and contact data.
     *
     * The hierarchy is polymorphic-serializable: the codec encodes/decodes the
     * sealed type directly with a "type" class discriminator, so adding a new
     * payload kind only requires adding a subclass here.
     */
    @Serializable
    sealed class ExternalDragItem {
        @Serializable
        @SerialName("app")
        data class App(val appInfo: AppInfo) : ExternalDragItem()

        @Serializable
        @SerialName("file")
        data class File(val fileDocument: FileDocument) : ExternalDragItem()

        @Serializable
        @SerialName("contact")
        data class Contact(val contact: com.milki.launcher.domain.model.Contact) :
            ExternalDragItem()

        @Serializable
        @SerialName("shortcut")
        data class Shortcut(val shortcut: HomeItem.AppShortcut) : ExternalDragItem()

        @Serializable
        @SerialName("actionShortcut")
        data class ActionShortcut(val shortcut: HomeItem.ActionShortcut) : ExternalDragItem()

        /**
         * An item being dragged OUT of a folder popup onto the home screen.
         *
         * WHY A SEPARATE TYPE:
         * When the user drags an icon out of the folder popup, the drop handler
         * needs to know both the item AND which folder it came from so it can
         * call [HomeRepository.extractItemFromFolder] instead of the regular
         * pin/move path.
         *
         * HOW IT TRAVELS:
         * Passed entirely via [android.view.DragEvent.localState].  Since the
         * folder popup and the home grid live in the SAME window (same Activity),
         * localState is always available and ClipData JSON is never needed for
         * decoding.  [ExternalDragPayloadCodec.decodeDragItem] returns it
         * directly because the `is ExternalDragItem` branch fires first.
         *
         * @property folderId  The [HomeItem.FolderItem.id] the item is being extracted from.
         * @property childItem The actual item being dragged out.
         */
        @Serializable
        @SerialName("folderChild")
        data class FolderChild(
            val folderId: String,
            val childItem: com.milki.launcher.domain.model.HomeItem
        ) : ExternalDragItem()

        /**
         * A widget being dragged from the Widget Picker BottomSheet to the home grid.
         *
         * HOW IT TRAVELS:
         * Like [FolderChild], this is carried entirely via [DragEvent.localState]
         * because the BottomSheet and home grid live in the same Activity process.
         * [AppWidgetProviderInfo] is a Parcelable that cannot be JSON-serialized,
         * so it is [Transient] and only travels on the localState path; ClipData
         * fallback decode re-resolves the provider from [providerComponent].
         *
         * @property providerInfo  The widget provider the user selected.
         *                         Always null when decoded from ClipData fallback.
         * @property providerComponent  Stable provider identity used for ClipData fallback
         *                              decode and later provider re-resolution.
         * @property span          The default grid span (columns × rows) for this widget.
         */
        @Serializable
        @SerialName("widget")
        data class Widget(
            @Transient val providerInfo: AppWidgetProviderInfo? = null,
            @Serializable(with = ComponentNameSerializer::class)
            val providerComponent: ComponentName,
            val span: GridSpan = GridSpan.SINGLE,
            val displayMode: com.milki.launcher.domain.model.WidgetDisplayMode =
                com.milki.launcher.domain.model.WidgetDisplayMode.Inline
        ) : ExternalDragItem()
    }

    private val json = lenientJson { classDiscriminator = "type" }

    /**
     * Creates ClipData for platform drag transfer from any supported drag item.
     */
    fun createClipData(item: ExternalDragItem): ClipData {
        return ClipData.newPlainText(DRAG_CLIP_LABEL, CodecSupport.encodePayloadText(item))
    }

    /**
     * Returns true when this drag event is likely a launcher payload.
     *
     * This is intentionally tolerant for ACTION_DRAG_STARTED to avoid rejecting
     * valid cross-window drags too early on OEM variants.
     */
    fun isLikelyLauncherPayload(dragEvent: DragEvent): Boolean {
        val description = dragEvent.clipDescription
        val label = description?.label?.toString()

        return when {
            description == null -> CodecSupport.hasSupportedLocalState(dragEvent.localState)
            label == DRAG_CLIP_LABEL || label == LEGACY_APP_DRAG_CLIP_LABEL -> true
            else -> description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
        }
    }

    /**
     * Decodes any supported drag payload from localState or ClipData JSON.
     */
    fun decodeDragItem(dragEvent: DragEvent): ExternalDragItem? {
        val descriptionLabel = dragEvent.clipDescription?.label?.toString()
        val rawText = CodecSupport.firstPayloadText(dragEvent.clipData)

        return CodecSupport.decodeLocalStateItem(dragEvent.localState)
            ?: CodecSupport.decodeClipPayload(descriptionLabel = descriptionLabel, rawText = rawText)
    }

    private object CodecSupport {
        fun encodePayloadText(item: ExternalDragItem): String {
            return when (item) {
                // FolderChild travels as a bare item id: it is always decoded via
                // localState, and the ClipData text only serves as a human-readable
                // hint for external drop targets.
                is ExternalDragItem.FolderChild -> item.childItem.id
                else -> json.encodeToString(ExternalDragItem.serializer(), item)
            }
        }

        fun hasSupportedLocalState(localState: Any?): Boolean {
            return when (localState) {
                is ExternalDragItem,
                is AppInfo,
                is FileDocument,
                is Contact,
                is HomeItem.AppShortcut,
                is HomeItem.ActionShortcut -> true

                else -> false
            }
        }

        fun decodeLocalStateItem(localState: Any?): ExternalDragItem? {
            return when (localState) {
                is ExternalDragItem -> localState
                is AppInfo -> ExternalDragItem.App(localState)
                is FileDocument -> ExternalDragItem.File(localState)
                is Contact -> ExternalDragItem.Contact(localState)
                is HomeItem.AppShortcut -> ExternalDragItem.Shortcut(localState)
                is HomeItem.ActionShortcut -> ExternalDragItem.ActionShortcut(localState)
                else -> null
            }
        }

        fun decodeClipPayload(
            descriptionLabel: String?,
            rawText: String?
        ): ExternalDragItem? {
            if (rawText == null) return null
            return if (descriptionLabel == LEGACY_APP_DRAG_CLIP_LABEL) {
                decodeLegacyAppPayload(rawText)
            } else {
                decodeStructuredPayload(rawText)
            }
        }

        fun firstPayloadText(clipData: ClipData?): String? {
            return clipData
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.text
                ?.toString()
        }

        /**
         * Payload format written by launcher versions predating the unified
         * contract: a flat app descriptor with no "type" discriminator.
         */
        @Serializable
        private data class LegacyAppPayload(
            val name: String,
            val packageName: String,
            val activityName: String = packageName
        )

        private fun decodeLegacyAppPayload(rawText: String): ExternalDragItem.App? {
            return runCatching {
                val payload = json.decodeFromString(LegacyAppPayload.serializer(), rawText)
                ExternalDragItem.App(
                    AppInfo(
                        name = payload.name,
                        packageName = payload.packageName,
                        activityName = payload.activityName
                    )
                )
            }.getOrNull()
        }

        private fun decodeStructuredPayload(rawText: String): ExternalDragItem? {
            return runCatching {
                json.decodeFromString(ExternalDragItem.serializer(), rawText)
            }.getOrNull()
        }
    }
}
