/**
 * HomeItem.kt - Data model for items pinned to the home screen
 *
 * This sealed class represents different types of items that can be pinned
 * to the launcher's home screen for quick access.
 *
 * WHY SEALED CLASS?
 * - Type-safe: Each item type has its own specific data
 * - Exhaustive when expressions: Compiler ensures we handle all types
 * - Clean serialization: Each subtype can define its own serialization logic
 *
 * ITEM TYPES:
 * 1. PinnedApp - Regular installed app
 * 2. PinnedFile - File (PDF, image, etc.) for quick opening
 * 3. AppShortcut - System shortcut from another app (e.g., WhatsApp chat)
 */

package com.milki.launcher.domain.model

import android.content.pm.ShortcutInfo

/**
 * Represents an item pinned to the home screen.
 *
 * All items have a unique ID used for:
 * - Identifying items in the list
 * - Removing specific items
 * - Preventing duplicates
 *
 * SERIALIZATION:
 * Each subclass provides toStorageString() and companion fromStorageString()
 * for persisting to DataStore.
 */
sealed class HomeItem {

    abstract val id: String

    /**
     * Converts the item to a storage-friendly string format.
     * Format is prefixed with item type for deserialization.
     */
    abstract fun toStorageString(): String

    /**
     * A pinned installed application.
     *
     * @property id Unique identifier: "app:{packageName}/{activityName}"
     * @property packageName The app's package name (e.g., "com.whatsapp")
     * @property activityName The specific activity to launch
     * @property label Display name shown under the icon
     */
    data class PinnedApp(
        override val id: String,
        val packageName: String,
        val activityName: String,
        val label: String
    ) : HomeItem() {

        /**
         * Storage format: "app|{packageName}|{activityName}|{label}"
         * Uses pipe (|) as separator since it's unlikely to be in app names.
         */
        override fun toStorageString(): String {
            return "app|$packageName|$activityName|$label"
        }

        companion object {
            /**
             * Creates a PinnedApp from an AppInfo instance.
             * Generates a consistent ID from package and activity name.
             */
            fun fromAppInfo(appInfo: AppInfo): PinnedApp {
                val id = "app:${appInfo.packageName}/${appInfo.activityName}"
                return PinnedApp(
                    id = id,
                    packageName = appInfo.packageName,
                    activityName = appInfo.activityName,
                    label = appInfo.name
                )
            }

            /**
             * Parses a storage string back into a PinnedApp.
             * Returns null if the format is invalid.
             */
            fun fromStorageString(str: String): PinnedApp? {
                val parts = str.split("|")
                if (parts.size != 4 || parts[0] != "app") return null
                return PinnedApp(
                    id = "app:${parts[1]}/${parts[2]}",
                    packageName = parts[1],
                    activityName = parts[2],
                    label = parts[3]
                )
            }
        }
    }

    /**
     * A pinned file (PDF, image, document, etc.).
     *
     * @property id Unique identifier: "file:{uri}"
     * @property uri Content URI of the file (e.g., "content://...")
     * @property name Display name (filename without path)
     * @property mimeType MIME type for opening with correct app
     * @property size File size in bytes (for display purposes)
     */
    data class PinnedFile(
        override val id: String,
        val uri: String,
        val name: String,
        val mimeType: String,
        val size: Long = 0
    ) : HomeItem() {

        /**
         * Storage format: "file|{uri}|{name}|{mimeType}|{size}"
         * Note: URIs can contain special characters, so we use a format
         * that splits from the end to handle pipes in URIs (unlikely but possible).
         */
        override fun toStorageString(): String {
            return "file|$uri|$name|$mimeType|$size"
        }

        companion object {
            /**
             * Creates a PinnedFile from a FileDocument instance.
             */
            fun fromFileDocument(file: FileDocument): PinnedFile {
                return PinnedFile(
                    id = "file:${file.uri}",
                    uri = file.uri.toString(),
                    name = file.name,
                    mimeType = file.mimeType,
                    size = file.size
                )
            }

            /**
             * Parses a storage string back into a PinnedFile.
             * Returns null if the format is invalid.
             */
            fun fromStorageString(str: String): PinnedFile? {
                val parts = str.split("|")
                if (parts.size < 5 || parts[0] != "file") return null
                // Rejoin URI parts in case URI contained pipes
                val uri = parts.subList(1, parts.size - 3).joinToString("|")
                return PinnedFile(
                    id = "file:$uri",
                    uri = uri,
                    name = parts[parts.size - 3],
                    mimeType = parts[parts.size - 2],
                    size = parts[parts.size - 1].toLongOrNull() ?: 0
                )
            }
        }
    }

    /**
     * A shortcut from another app (e.g., WhatsApp chat shortcut).
     *
     * These are created by other apps using ShortcutManager and can be
     * pinned to the launcher home screen.
     *
     * @property id Unique identifier: "shortcut:{packageName}/{shortcutId}"
     * @property packageName The app that published this shortcut
     * @property shortcutId The shortcut's unique ID within the app
     * @property shortLabel Short display name
     * @property longLabel Longer display name (may be same as shortLabel)
     */
    data class AppShortcut(
        override val id: String,
        val packageName: String,
        val shortcutId: String,
        val shortLabel: String,
        val longLabel: String = shortLabel
    ) : HomeItem() {

        /**
         * Storage format: "shortcut|{packageName}|{shortcutId}|{shortLabel}|{longLabel}"
         */
        override fun toStorageString(): String {
            return "shortcut|$packageName|$shortcutId|$shortLabel|$longLabel"
        }

        companion object {
            /**
             * Creates an AppShortcut from Android's ShortcutInfo.
             */
            fun fromShortcutInfo(info: ShortcutInfo): AppShortcut {
                return AppShortcut(
                    id = "shortcut:${info.`package`}/${info.id}",
                    packageName = info.`package` ?: "",
                    shortcutId = info.id ?: "",
                    shortLabel = info.shortLabel?.toString() ?: "",
                    longLabel = info.longLabel?.toString() ?: info.shortLabel?.toString() ?: ""
                )
            }

            /**
             * Parses a storage string back into an AppShortcut.
             * Returns null if the format is invalid.
             */
            fun fromStorageString(str: String): AppShortcut? {
                val parts = str.split("|")
                if (parts.size != 5 || parts[0] != "shortcut") return null
                return AppShortcut(
                    id = "shortcut:${parts[1]}/${parts[2]}",
                    packageName = parts[1],
                    shortcutId = parts[2],
                    shortLabel = parts[3],
                    longLabel = parts[4]
                )
            }
        }
    }

    companion object {
        /**
         * Parses any storage string into the appropriate HomeItem subtype.
         * Returns null if the format is unrecognized or invalid.
         */
        fun fromStorageString(str: String): HomeItem? {
            return when {
                str.startsWith("app|") -> PinnedApp.fromStorageString(str)
                str.startsWith("file|") -> PinnedFile.fromStorageString(str)
                str.startsWith("shortcut|") -> AppShortcut.fromStorageString(str)
                else -> null
            }
        }
    }
}
