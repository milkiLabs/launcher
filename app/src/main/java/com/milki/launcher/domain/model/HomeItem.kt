/**
 * HomeItem.kt - Data model for items pinned to the home screen
 *
 * This sealed class represents different types of items that can be pinned
 * to the launcher's home screen for quick access.
 *
 * WHY SEALED CLASS?
 * - Type-safe: Each item type has its own specific data
 * - Exhaustive when expressions: Compiler ensures we handle all types
 * - Clean serialization: kotlinx.serialization handles polymorphic serialization
 *
 * ITEM TYPES:
 * 1. PinnedApp - Regular installed app
 * 2. PinnedFile - File (PDF, image, etc.) for quick opening
 * 3. AppShortcut - System shortcut from another app (e.g., WhatsApp chat)
 *
 * SERIALIZATION:
 * Uses kotlinx.serialization with polymorphic serialization for the sealed class.
 * Each item is serialized to JSON with a "type" discriminator field.
 *
 * EXAMPLE JSON OUTPUT:
 * {
 *   "type": "PinnedApp",
 *   "id": "app:com.whatsapp/.Main",
 *   "packageName": "com.whatsapp",
 *   "activityName": ".Main",
 *   "label": "WhatsApp",
 *   "position": { "row": 0, "column": 1 }
 * }
 *
 * WHY KOTLINX.SERIALIZATION?
 * - No fragile pipe-delimited parsing
 * - Handles special characters in labels/URIs correctly
 * - Single source of truth for serialization
 * - Type-safe deserialization
 */

package com.milki.launcher.domain.model

import android.content.pm.ShortcutInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
 * The @Serializable annotation enables automatic JSON serialization.
 * Polymorphic serialization is configured with a "type" discriminator.
 */
@Serializable
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
    @Serializable
    data class PinnedApp(
        override val id: String,
        val packageName: String,
        val activityName: String,
        val label: String,
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem() {

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
    @Serializable
    data class PinnedFile(
        override val id: String,
        val uri: String,
        val name: String,
        val mimeType: String,
        val size: Long = 0,
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem() {

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
    @Serializable
    data class AppShortcut(
        override val id: String,
        val packageName: String,
        val shortcutId: String,
        val shortLabel: String,
        val longLabel: String = shortLabel,
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem() {

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
        }
    }

    companion object {
        /**
         * Creates a default Json instance configured for HomeItem serialization.
         *
         * CONFIGURATION:
         * - classDiscriminator: Uses "type" field to identify subclasses
         * - encodeDefaults: Ensures default values are written to JSON
         *
         * This configuration provides type safety where each subclass is correctly identified.
         */
        val json: Json = Json {
            classDiscriminator = "type"
            encodeDefaults = true
        }
    }
}
