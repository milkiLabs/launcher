
package com.milki.launcher.domain.model

import android.content.pm.ShortcutInfo
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
@Immutable
enum class WidgetDisplayMode {
    Inline,
    PopupIcon
}

@Serializable
@Immutable
sealed class HomeItem {

    abstract val id: String

        abstract val position: GridPosition

        abstract fun withPosition(newPosition: GridPosition): HomeItem

        @Serializable
    @Immutable
    data class PinnedApp(
        override val id: String,
        val packageName: String,
        val activityName: String,
        val label: String,
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem() {

                override fun withPosition(newPosition: GridPosition): PinnedApp {
            return copy(position = newPosition)
        }

        companion object {
                        fun fromAppInfo(appInfo: AppInfo): PinnedApp {
                val id = "app:${appInfo.packageName}/${appInfo.activityName}"
                return PinnedApp(
                    id = id,
                    packageName = appInfo.packageName,
                    activityName = appInfo.activityName,
                    label = appInfo.name,
                    position = GridPosition.DEFAULT
                )
            }
        }
    }

        @Serializable
    @Immutable
    data class PinnedFile(
        override val id: String,
        val uri: String,
        val name: String,
        val mimeType: String,
        val size: Long = 0,
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem() {

                override fun withPosition(newPosition: GridPosition): PinnedFile {
            return copy(position = newPosition)
        }

        companion object {
                        fun fromFileDocument(file: FileDocument): PinnedFile {
                return PinnedFile(
                    id = "file:${file.uri}",
                    uri = file.uri.toString(),
                    name = file.name,
                    mimeType = file.mimeType,
                    size = file.size,
                    position = GridPosition.DEFAULT
                )
            }
        }
    }

        @Serializable
    @Immutable
    data class PinnedContact(
        override val id: String,
        val contactId: Long,
        val lookupKey: String,
        val displayName: String,
        val primaryPhone: String?,
        val photoUri: String?,
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem() {

                override fun withPosition(newPosition: GridPosition): PinnedContact {
            return copy(position = newPosition)
        }

        companion object {
                        fun fromContact(contact: Contact): PinnedContact {
                val contactKey = if (contact.lookupKey.isNotBlank()) contact.lookupKey else contact.id.toString()
                return PinnedContact(
                    id = "contact:${contact.id}:$contactKey",
                    contactId = contact.id,
                    lookupKey = contact.lookupKey,
                    displayName = contact.displayName,
                    primaryPhone = contact.phoneNumbers.firstOrNull(),
                    photoUri = contact.photoUri,
                    position = GridPosition.DEFAULT
                )
            }
        }
    }

        @Serializable
    @Immutable
    data class AppShortcut(
        override val id: String,
        val packageName: String,
        val shortcutId: String,
        val shortLabel: String,
        val longLabel: String = shortLabel,
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem() {

                override fun withPosition(newPosition: GridPosition): AppShortcut {
            return copy(position = newPosition)
        }

        companion object {
                        fun fromShortcutInfo(info: ShortcutInfo): AppShortcut {
                return AppShortcut(
                    id = "shortcut:${info.`package`}/${info.id}",
                    packageName = info.`package`,
                    shortcutId = info.id,
                    shortLabel = info.shortLabel?.toString() ?: "",
                    longLabel = info.longLabel?.toString() ?: info.shortLabel?.toString() ?: "",
                    position = GridPosition.DEFAULT
                )
            }
        }
    }

        @Serializable
    @Immutable
    data class ActionShortcut(
        override val id: String,
        val label: String,
        val destinationUri: String,
        val packageName: String? = null,
        val packageLabel: String? = null,
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem() {

        override fun withPosition(newPosition: GridPosition): ActionShortcut {
            return copy(position = newPosition)
        }

        companion object {
            val DefaultDocsShortcut = ActionShortcut(
                id = "action:milki_docs",
                label = "Milki docs",
                destinationUri = "https://milkilabs.github.io/launcher/guide/overview.html"
            )

            fun create(
                label: String,
                destinationUri: String,
                packageName: String? = null,
                packageLabel: String? = null
            ): ActionShortcut {
                val normalizedLabel = label.trim().ifBlank { "Shortcut" }
                val normalizedUri = destinationUri.trim()
                val scopedPackage = packageName?.takeIf { it.isNotBlank() }
                return ActionShortcut(
                    id = "action:${UUID.randomUUID()}",
                    label = normalizedLabel,
                    destinationUri = normalizedUri,
                    packageName = scopedPackage,
                    packageLabel = packageLabel?.takeIf { it.isNotBlank() },
                    position = GridPosition.DEFAULT
                )
            }
        }
    }

        @Serializable
    @Immutable
    data class WidgetItem(
        override val id: String,
        val appWidgetId: Int,
        val providerPackage: String,
        val providerClass: String,
        val label: String,
        override val position: GridPosition = GridPosition.DEFAULT,
        val span: GridSpan = GridSpan.SINGLE,
        val displayMode: WidgetDisplayMode = WidgetDisplayMode.Inline
    ) : HomeItem() {

        val homeGridSpan: GridSpan
            get() = when (displayMode) {
                WidgetDisplayMode.Inline -> span
                WidgetDisplayMode.PopupIcon -> GridSpan.SINGLE
            }

                override fun withPosition(newPosition: GridPosition): WidgetItem {
            return copy(position = newPosition)
        }

                fun withSpan(newSpan: GridSpan): WidgetItem {
            return copy(span = newSpan)
        }

        fun withDisplayMode(newDisplayMode: WidgetDisplayMode): WidgetItem {
            return copy(displayMode = newDisplayMode)
        }

        companion object {
                        fun create(
                appWidgetId: Int,
                providerPackage: String,
                providerClass: String,
                label: String,
                position: GridPosition = GridPosition.DEFAULT,
                span: GridSpan = GridSpan.SINGLE,
                displayMode: WidgetDisplayMode = WidgetDisplayMode.Inline
            ): WidgetItem {
                return WidgetItem(
                    id = "widget:$appWidgetId",
                    appWidgetId = appWidgetId,
                    providerPackage = providerPackage,
                    providerClass = providerClass,
                    label = label,
                    position = position,
                    span = span,
                    displayMode = displayMode
                )
            }
        }
    }

        @Serializable
    @Immutable
    data class FolderItem(
        override val id: String,
        val name: String = "Folder",
        val children: List<HomeItem> = emptyList(),
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem() {

                override fun withPosition(newPosition: GridPosition): FolderItem {
            return copy(position = newPosition)
        }

        companion object {

                        fun create(
                item1: HomeItem,
                item2: HomeItem,
                atPosition: GridPosition
            ): FolderItem {
                val id = "folder:${UUID.randomUUID()}"

                return FolderItem(
                    id = id,
                    name = "Folder",
                    children = listOf(
                        item1.withPosition(GridPosition.DEFAULT),
                        item2.withPosition(GridPosition.DEFAULT)
                    ),
                    position = atPosition
                )
            }
        }
    }

    companion object {
                val json: Json = Json {
            classDiscriminator = "type"
            encodeDefaults = true
        }
    }
}

val HomeItem.homeGridSpan: GridSpan
    get() = (this as? HomeItem.WidgetItem)?.homeGridSpan ?: GridSpan.SINGLE
