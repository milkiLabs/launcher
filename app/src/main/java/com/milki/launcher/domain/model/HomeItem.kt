
package com.milki.launcher.domain.model

import androidx.compose.runtime.Immutable
import com.milki.launcher.core.util.strictJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

    fun withPosition(newPosition: GridPosition): HomeItem = when (this) {
        is PinnedApp -> copy(position = newPosition)
        is PinnedFile -> copy(position = newPosition)
        is PinnedContact -> copy(position = newPosition)
        is AppShortcut -> copy(position = newPosition)
        is ActionShortcut -> copy(position = newPosition)
        is WidgetItem -> copy(position = newPosition)
        is FolderItem -> copy(position = newPosition)
    }

    @Serializable
    @Immutable
    data class PinnedApp(
        override val id: String,
        val packageName: String,
        val activityName: String,
        val label: String,
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem() {

                companion object {
                        fun fromAppInfo(appInfo: AppInfo): PinnedApp {
                val id = ItemId.app(appInfo.packageName, appInfo.activityName)
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

                companion object {
                        fun fromFileDocument(file: FileDocument): PinnedFile {
                return PinnedFile(
                    id = ItemId.file(file.uri),
                    uri = file.uri,
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

                companion object {
                        fun fromContact(contact: Contact): PinnedContact {
                val contactKey = if (contact.lookupKey.isNotBlank()) contact.lookupKey else contact.id.toString()
                return PinnedContact(
                    id = ItemId.contact(contact.id, contactKey),
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

                companion object {
            fun fromShortcutInfo(
                packageName: String,
                shortcutId: String,
                shortLabel: String,
                longLabel: String
            ): AppShortcut {
                return AppShortcut(
                    id = ItemId.shortcut(packageName, shortcutId),
                    packageName = packageName,
                    shortcutId = shortcutId,
                    shortLabel = shortLabel,
                    longLabel = longLabel,
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

        companion object {
            val DefaultDocsShortcut = ActionShortcut(
                id = ItemId.action("milki_docs"),
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
                    id = ItemId.actionRandom(),
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
                    id = ItemId.widget(appWidgetId),
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
        val name: String = DEFAULT_NAME,
        val children: List<HomeItem> = emptyList(),
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem() {

                companion object {

                    const val DEFAULT_NAME = "Folder"

                    fun create(
                item1: HomeItem,
                item2: HomeItem,
                atPosition: GridPosition
            ): FolderItem {
                val id = ItemId.folder()

                return FolderItem(
                    id = id,
                    name = DEFAULT_NAME,
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
                val json: Json = strictJson { classDiscriminator = "type" }
    }
}

val HomeItem.homeGridSpan: GridSpan
    get() = (this as? HomeItem.WidgetItem)?.homeGridSpan ?: GridSpan.SINGLE
