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
 * GRID POSITION:
 * Each item has a position property that determines where it appears on the
 * home screen grid. The grid is a 2D coordinate system (row, column) where:
 * - row 0 is at the top
 * - column 0 is at the left
 *
 * SERIALIZATION:
 * Each subclass provides toStorageString() and companion fromStorageString()
 * for persisting to DataStore.
 */
sealed class HomeItem {

    abstract val id: String

    /**
     * The grid position where this item is located on the home screen.
     *
     * This determines the cell (row, column) where the icon appears.
     * Multiple items should not share the same position.
     */
    abstract val position: GridPosition

    /**
     * Converts the item to a storage-friendly string format.
     * Format is prefixed with item type for deserialization.
     */
    abstract fun toStorageString(): String

    /**
     * Creates a copy of this item with a new grid position.
     * Used when the user drags an item to a new location.
     *
     * @param newPosition The new grid position for the item
     * @return A new HomeItem instance with the updated position
     */
    abstract fun withPosition(newPosition: GridPosition): HomeItem

    /**
     * A pinned installed application.
     *
     * @property id Unique identifier: "app:{packageName}/{activityName}"
     * @property packageName The app's package name (e.g., "com.whatsapp")
     * @property activityName The specific activity to launch
     * @property label Display name shown under the icon
     * @property position Grid position (row, column) on the home screen
     */
    data class PinnedApp(
        override val id: String,
        val packageName: String,
        val activityName: String,
        val label: String,
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem() {

        /**
         * Storage format: "app|{packageName}|{activityName}|{label}|{row},{column}"
         * Uses pipe (|) as separator since it's unlikely to be in app names.
         * Position is stored as "row,column" at the end.
         */
        override fun toStorageString(): String {
            return "app|$packageName|$activityName|$label|${position.toStorageString()}"
        }

        /**
         * Creates a copy of this PinnedApp with a new grid position.
         * Called when the user drags the app icon to a new location.
         */
        override fun withPosition(newPosition: GridPosition): PinnedApp {
            return copy(position = newPosition)
        }

        companion object {
            /**
             * Creates a PinnedApp from an AppInfo instance.
             * Generates a consistent ID from package and activity name.
             * Position defaults to (0, 0) - should be adjusted by caller if needed.
             */
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

            /**
             * Parses a storage string back into a PinnedApp.
             * Returns null if the format is invalid.
             *
             * SUPPORTED FORMATS:
             * - Legacy (4 parts): "app|packageName|activityName|label" -> position defaults to (0,0)
             * - New (5 parts): "app|packageName|activityName|label|row,column"
             */
            fun fromStorageString(str: String): PinnedApp? {
                val parts = str.split("|")
                if (parts.isEmpty() || parts[0] != "app") return null

                // Handle legacy format (4 parts) without position
                if (parts.size == 4) {
                    return PinnedApp(
                        id = "app:${parts[1]}/${parts[2]}",
                        packageName = parts[1],
                        activityName = parts[2],
                        label = parts[3],
                        position = GridPosition.DEFAULT
                    )
                }

                // Handle new format (5 parts) with position
                if (parts.size == 5) {
                    val position = GridPosition.fromStorageString(parts[4])
                        ?: GridPosition.DEFAULT
                    return PinnedApp(
                        id = "app:${parts[1]}/${parts[2]}",
                        packageName = parts[1],
                        activityName = parts[2],
                        label = parts[3],
                        position = position
                    )
                }

                return null
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
     * @property position Grid position (row, column) on the home screen
     */
    data class PinnedFile(
        override val id: String,
        val uri: String,
        val name: String,
        val mimeType: String,
        val size: Long = 0,
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem() {

        /**
         * Storage format: "file|{uri}|{name}|{mimeType}|{size}|{row},{column}"
         * Note: URIs can contain special characters, so we use a format
         * that splits from the end to handle pipes in URIs (unlikely but possible).
         */
        override fun toStorageString(): String {
            return "file|$uri|$name|$mimeType|$size|${position.toStorageString()}"
        }

        /**
         * Creates a copy of this PinnedFile with a new grid position.
         * Called when the user drags the file icon to a new location.
         */
        override fun withPosition(newPosition: GridPosition): PinnedFile {
            return copy(position = newPosition)
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
                    size = file.size,
                    position = GridPosition.DEFAULT
                )
            }

            /**
             * Parses a storage string back into a PinnedFile.
             * Returns null if the format is invalid.
             *
             * SUPPORTED FORMATS:
             * - Legacy (5 parts): "file|uri|name|mimeType|size" -> position defaults to (0,0)
             * - New (6 parts): "file|uri|name|mimeType|size|row,column"
             */
            fun fromStorageString(str: String): PinnedFile? {
                val parts = str.split("|")
                if (parts.isEmpty() || parts[0] != "file") return null

                // Handle legacy format (5 parts) without position
                if (parts.size == 5) {
                    val uri = parts[1]
                    return PinnedFile(
                        id = "file:$uri",
                        uri = uri,
                        name = parts[2],
                        mimeType = parts[3],
                        size = parts[4].toLongOrNull() ?: 0,
                        position = GridPosition.DEFAULT
                    )
                }

                // Handle new format (6 parts) with position
                if (parts.size == 6) {
                    val uri = parts[1]
                    val position = GridPosition.fromStorageString(parts[5])
                        ?: GridPosition.DEFAULT
                    return PinnedFile(
                        id = "file:$uri",
                        uri = uri,
                        name = parts[2],
                        mimeType = parts[3],
                        size = parts[4].toLongOrNull() ?: 0,
                        position = position
                    )
                }

                return null
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
     * @property position Grid position (row, column) on the home screen
     */
    data class AppShortcut(
        override val id: String,
        val packageName: String,
        val shortcutId: String,
        val shortLabel: String,
        val longLabel: String = shortLabel,
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem() {

        /**
         * Storage format: "shortcut|{packageName}|{shortcutId}|{shortLabel}|{longLabel}|{row},{column}"
         */
        override fun toStorageString(): String {
            return "shortcut|$packageName|$shortcutId|$shortLabel|$longLabel|${position.toStorageString()}"
        }

        /**
         * Creates a copy of this AppShortcut with a new grid position.
         * Called when the user drags the shortcut icon to a new location.
         */
        override fun withPosition(newPosition: GridPosition): AppShortcut {
            return copy(position = newPosition)
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
                    longLabel = info.longLabel?.toString() ?: info.shortLabel?.toString() ?: "",
                    position = GridPosition.DEFAULT
                )
            }

            /**
             * Parses a storage string back into an AppShortcut.
             * Returns null if the format is invalid.
             *
             * SUPPORTED FORMATS:
             * - Legacy (5 parts): "shortcut|packageName|shortcutId|shortLabel|longLabel" -> position defaults to (0,0)
             * - New (6 parts): "shortcut|packageName|shortcutId|shortLabel|longLabel|row,column"
             */
            fun fromStorageString(str: String): AppShortcut? {
                val parts = str.split("|")
                if (parts.isEmpty() || parts[0] != "shortcut") return null

                // Handle legacy format (5 parts) without position
                if (parts.size == 5) {
                    return AppShortcut(
                        id = "shortcut:${parts[1]}/${parts[2]}",
                        packageName = parts[1],
                        shortcutId = parts[2],
                        shortLabel = parts[3],
                        longLabel = parts[4],
                        position = GridPosition.DEFAULT
                    )
                }

                // Handle new format (6 parts) with position
                if (parts.size == 6) {
                    val position = GridPosition.fromStorageString(parts[5])
                        ?: GridPosition.DEFAULT
                    return AppShortcut(
                        id = "shortcut:${parts[1]}/${parts[2]}",
                        packageName = parts[1],
                        shortcutId = parts[2],
                        shortLabel = parts[3],
                        longLabel = parts[4],
                        position = position
                    )
                }

                return null
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
