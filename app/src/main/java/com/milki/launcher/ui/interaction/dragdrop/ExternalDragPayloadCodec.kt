package com.milki.launcher.ui.interaction.dragdrop

import android.content.ClipData
import android.content.ClipDescription
import android.content.ComponentName
import android.net.Uri
import android.view.DragEvent
import android.appwidget.AppWidgetProviderInfo
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.GridSpan
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
     */
    sealed class ExternalDragItem {
        data class App(val appInfo: AppInfo) : ExternalDragItem()
        data class File(val fileDocument: FileDocument) : ExternalDragItem()
        data class Contact(val contact: com.milki.launcher.domain.model.Contact) : ExternalDragItem()

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
         * [AppWidgetProviderInfo] is a Parcelable but not easily JSON-serializable,
         * so we rely on the fast localState path exclusively.
         *
         * @property providerInfo  The widget provider the user selected.
         *                         This may be null when decoded from ClipData fallback.
         * @property providerComponent  Stable provider identity used for ClipData fallback
         *                              decode and later provider re-resolution.
         * @property span          The default grid span (columns × rows) for this widget.
         */
        data class Widget(
            val providerInfo: AppWidgetProviderInfo?,
            val providerComponent: ComponentName,
            val span: GridSpan
        ) : ExternalDragItem()
    }

    @Serializable
    private sealed class ExternalPayloadDto {
        abstract val type: String

        @Serializable
        data class AppPayload(
            override val type: String = "app",
            val name: String,
            val packageName: String,
            val activityName: String
        ) : ExternalPayloadDto()

        @Serializable
        data class FilePayload(
            override val type: String = "file",
            val id: Long,
            val name: String,
            val mimeType: String,
            val size: Long,
            val dateModified: Long,
            val uri: String,
            val folderPath: String
        ) : ExternalPayloadDto()

        @Serializable
        data class ContactPayload(
            override val type: String = "contact",
            val id: Long,
            val displayName: String,
            val phoneNumbers: List<String>,
            val emails: List<String>,
            val photoUri: String?,
            val lookupKey: String
        ) : ExternalPayloadDto()

        @Serializable
        data class WidgetPayload(
            override val type: String = "widget",
            val providerPackage: String,
            val providerClass: String,
            val spanColumns: Int,
            val spanRows: Int
        ) : ExternalPayloadDto()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Creates ClipData for platform drag transfer from any supported drag item.
     */
    fun createClipData(item: ExternalDragItem): ClipData {
        val payloadText = when (item) {
            is ExternalDragItem.App -> {
                json.encodeToString(
                    ExternalPayloadDto.AppPayload(
                        name = item.appInfo.name,
                        packageName = item.appInfo.packageName,
                        activityName = item.appInfo.activityName
                    )
                )
            }

            is ExternalDragItem.File -> {
                val file = item.fileDocument
                json.encodeToString(
                    ExternalPayloadDto.FilePayload(
                        id = file.id,
                        name = file.name,
                        mimeType = file.mimeType,
                        size = file.size,
                        dateModified = file.dateModified,
                        uri = file.uri.toString(),
                        folderPath = file.folderPath
                    )
                )
            }

            is ExternalDragItem.Contact -> {
                val contact = item.contact
                json.encodeToString(
                    ExternalPayloadDto.ContactPayload(
                        id = contact.id,
                        displayName = contact.displayName,
                        phoneNumbers = contact.phoneNumbers,
                        emails = contact.emails,
                        photoUri = contact.photoUri,
                        lookupKey = contact.lookupKey
                    )
                )
            }

            is ExternalDragItem.FolderChild -> {
                // FolderChild is decoded entirely from localState (the object is passed
                // directly and read back in decodeDragItem before any JSON parsing).
                // ClipData requires a non-empty text, so we use the child item's id as
                // a minimal stable identifier; the actual payload is never decoded from it.
                item.childItem.id
            }

            is ExternalDragItem.Widget -> {
                // Widget payloads must have a ClipData fallback because localState
                // can be unavailable on some OEM/global drag paths.
                json.encodeToString(
                    ExternalPayloadDto.WidgetPayload(
                        providerPackage = item.providerComponent.packageName,
                        providerClass = item.providerComponent.className,
                        spanColumns = item.span.columns,
                        spanRows = item.span.rows
                    )
                )
            }
        }

        return ClipData.newPlainText(DRAG_CLIP_LABEL, payloadText)
    }

    /**
     * Convenience overload retained for app-only call sites.
     */
    fun createClipData(appInfo: AppInfo): ClipData = createClipData(ExternalDragItem.App(appInfo))

    /**
     * Returns true when this drag event is likely a launcher payload.
     *
     * This is intentionally tolerant for ACTION_DRAG_STARTED to avoid rejecting
     * valid cross-window drags too early on OEM variants.
     */
    fun isLikelyLauncherPayload(dragEvent: DragEvent): Boolean {
        val description = dragEvent.clipDescription
        if (description == null) {
            return dragEvent.localState is ExternalDragItem ||
                dragEvent.localState is AppInfo ||
                dragEvent.localState is FileDocument ||
                dragEvent.localState is Contact
        }

        val label = description.label?.toString()
        if (label == DRAG_CLIP_LABEL || label == LEGACY_APP_DRAG_CLIP_LABEL) {
            return true
        }

        return description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
    }

    /**
     * App-only compatibility helper.
     */
    fun isLikelyAppPayload(dragEvent: DragEvent): Boolean = isLikelyLauncherPayload(dragEvent)

    /**
     * Decodes any supported drag payload from localState or ClipData JSON.
     */
    fun decodeDragItem(dragEvent: DragEvent): ExternalDragItem? {
        val localStateItem = when (val localState = dragEvent.localState) {
            is ExternalDragItem -> localState
            is AppInfo -> ExternalDragItem.App(localState.copy(launchIntent = null))
            is FileDocument -> ExternalDragItem.File(localState)
            is Contact -> ExternalDragItem.Contact(localState)
            else -> null
        }

        if (localStateItem != null) {
            return localStateItem
        }

        val descriptionLabel = dragEvent.clipDescription?.label?.toString()
        // IMPORTANT:
        // Some OEM/platform drag paths may alter or drop custom ClipDescription
        // labels when crossing windows. We therefore do NOT hard-reject unknown
        // labels here. Instead, we attempt to parse the payload text and only
        // return a drag item when the JSON shape matches our known launcher types.
        // Non-launcher/plain-text drags naturally decode to null below.

        val clipData = dragEvent.clipData ?: return null
        if (clipData.itemCount <= 0) return null

        val rawText = clipData.getItemAt(0).text?.toString() ?: return null

        if (descriptionLabel == LEGACY_APP_DRAG_CLIP_LABEL) {
            return runCatching {
                val payload = json.decodeFromString(ExternalPayloadDto.AppPayload.serializer(), rawText)
                ExternalDragItem.App(
                    AppInfo(
                        name = payload.name,
                        packageName = payload.packageName,
                        activityName = payload.activityName,
                        launchIntent = null
                    )
                )
            }.getOrNull()
        }

        return runCatching {
            val dto = json.parseToJsonElement(rawText).jsonObject
            when (dto["type"]?.jsonPrimitive?.contentOrNull) {
                "app" -> {
                    val payload = json.decodeFromString(ExternalPayloadDto.AppPayload.serializer(), rawText)
                    ExternalDragItem.App(
                        AppInfo(
                            name = payload.name,
                            packageName = payload.packageName,
                            activityName = payload.activityName,
                            launchIntent = null
                        )
                    )
                }

                "file" -> {
                    val payload = json.decodeFromString(ExternalPayloadDto.FilePayload.serializer(), rawText)
                    ExternalDragItem.File(
                        FileDocument(
                            id = payload.id,
                            name = payload.name,
                            mimeType = payload.mimeType,
                            size = payload.size,
                            dateModified = payload.dateModified,
                            uri = Uri.parse(payload.uri),
                            folderPath = payload.folderPath
                        )
                    )
                }

                "contact" -> {
                    val payload = json.decodeFromString(ExternalPayloadDto.ContactPayload.serializer(), rawText)
                    ExternalDragItem.Contact(
                        Contact(
                            id = payload.id,
                            displayName = payload.displayName,
                            phoneNumbers = payload.phoneNumbers,
                            emails = payload.emails,
                            photoUri = payload.photoUri,
                            lookupKey = payload.lookupKey
                        )
                    )
                }

                "widget" -> {
                    val payload = json.decodeFromString(ExternalPayloadDto.WidgetPayload.serializer(), rawText)
                    ExternalDragItem.Widget(
                        providerInfo = null,
                        providerComponent = ComponentName(payload.providerPackage, payload.providerClass),
                        span = GridSpan(columns = payload.spanColumns, rows = payload.spanRows)
                    )
                }

                else -> null
            }
        }.getOrNull()
    }

    /**
     * App-only compatibility decode helper.
     */
    fun decodeAppInfo(dragEvent: DragEvent): AppInfo? {
        return (decodeDragItem(dragEvent) as? ExternalDragItem.App)?.appInfo
    }
}
