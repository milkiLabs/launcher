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
 * 3. PinnedContact - Contact shortcut for quick dial/open
 * 4. AppShortcut - System shortcut from another app (e.g., WhatsApp chat)
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
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

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
@Immutable
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
    @Immutable
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
    @Immutable
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
     * A pinned contact for quick access from the home grid.
     *
     * @property id Unique identifier: "contact:{contactId}:{lookupKey}"
     * @property contactId Stable-ish contact database ID
     * @property lookupKey Contact lookup key for resilient identification
     * @property displayName Contact display label
     * @property primaryPhone Primary phone number used for quick dial/open actions
     * @property photoUri Optional photo URI string for future avatar rendering
     * @property position Grid position (row, column) on the home screen
     */
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

        /**
         * Creates a copy of this contact with a new grid position.
         */
        override fun withPosition(newPosition: GridPosition): PinnedContact {
            return copy(position = newPosition)
        }

        companion object {
            /**
             * Creates a PinnedContact from a Contact domain model.
             */
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
    @Immutable
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
                    packageName = info.`package`,
                    shortcutId = info.id,
                    shortLabel = info.shortLabel?.toString() ?: "",
                    longLabel = info.longLabel?.toString() ?: info.shortLabel?.toString() ?: "",
                    position = GridPosition.DEFAULT
                )
            }
        }
    }

    // ========================================================================
    // WIDGET ITEM
    // ========================================================================

    /**
     * A home screen widget (Android AppWidget) that can span multiple grid cells.
     *
     * WHAT IS AN APP WIDGET?
     * Android app widgets are miniature application views that can be embedded in
     * the home screen. Examples include clocks, weather displays, music players,
     * and calendar views. They are provided by installed apps and rendered by the
     * launcher using Android's AppWidgetHost framework.
     *
     * MULTI-CELL SIZING:
     * Unlike all other HomeItem types which occupy exactly one grid cell (1×1),
     * a WidgetItem can span multiple columns and rows. The [span] property defines
     * how many cells the widget occupies. The [position] property defines the
     * top-left anchor cell.
     *
     * EXAMPLE: A 2-column × 3-row widget at position (1, 1):
     * The widget covers cells (1,1), (1,2), (2,1), (2,2), (3,1), (3,2).
     * No other items can be placed in those cells.
     *
     * WIDGET LIFECYCLE:
     * 1. User opens widget picker from home screen long-press menu
     * 2. User drags a widget from the picker onto the home grid
     * 3. System allocates a widget ID via AppWidgetHost
     * 4. System binds the widget to the chosen provider (may require permission)
     * 5. If the provider has a configuration activity, it is launched
     * 6. Widget is persisted in DataStore and rendered on the grid
     *
     * RESIZING:
     * Users can resize widgets by long-pressing → selecting "Resize" from the
     * context menu → dragging the resize handles. The resize respects:
     * - Provider's minimum and maximum size (from AppWidgetProviderInfo)
     * - Grid boundaries (no spanning outside the 4-column grid)
     * - Collision prevention (no overlapping other items)
     *
     * CLEANUP:
     * When a widget's provider app is uninstalled, the widget becomes orphaned.
     * The launcher detects this on app list refresh and removes the widget,
     * deallocating its widget ID.
     *
     * @property id Unique identifier: "widget:{appWidgetId}"
     * @property appWidgetId The integer ID assigned by Android's AppWidgetHost.
     *                       This ID is used to create the AppWidgetHostView that
     *                       renders the widget content.
     * @property providerPackage The package name of the app that provides this widget
     *                           (e.g., "com.google.android.deskclock" for the Clock widget).
     * @property providerClass The fully qualified class name of the AppWidgetProvider
     *                         (e.g., "com.google.android.deskclock.widget.DigitalWidget").
     * @property label Display name shown when the widget is selected or in the picker.
     * @property position Grid position of the widget's top-left anchor cell.
     * @property span How many columns and rows this widget occupies on the grid.
     *                Defaults to 1×1 (single cell), but widgets typically use larger spans.
     */
    @Serializable
    @Immutable
    data class WidgetItem(
        override val id: String,
        val appWidgetId: Int,
        val providerPackage: String,
        val providerClass: String,
        val label: String,
        override val position: GridPosition = GridPosition.DEFAULT,
        val span: GridSpan = GridSpan.SINGLE
    ) : HomeItem() {

        /**
         * Creates a copy of this WidgetItem with a new grid position.
         * Called when the user drags the widget to a new location on the home screen.
         */
        override fun withPosition(newPosition: GridPosition): WidgetItem {
            return copy(position = newPosition)
        }

        /**
         * Creates a copy of this WidgetItem with a new span (size).
         * Called when the user resizes the widget via drag handles.
         *
         * @param newSpan The new size (columns × rows) for this widget.
         * @return A new WidgetItem instance with the updated span.
         */
        fun withSpan(newSpan: GridSpan): WidgetItem {
            return copy(span = newSpan)
        }

        companion object {
            /**
             * Creates a WidgetItem from the Android widget binding information.
             *
             * This is called after the widget has been successfully allocated and bound
             * by the AppWidgetHost. The appWidgetId is the integer returned by
             * AppWidgetHost.allocateAppWidgetId().
             *
             * @param appWidgetId The allocated widget ID from AppWidgetHost.
             * @param providerPackage Package name of the widget provider app.
             * @param providerClass Class name of the AppWidgetProvider.
             * @param label Display label for the widget.
             * @param position Where on the grid to place the widget.
             * @param span How many cells the widget should occupy.
             * @return A new WidgetItem ready to be persisted and rendered.
             */
            fun create(
                appWidgetId: Int,
                providerPackage: String,
                providerClass: String,
                label: String,
                position: GridPosition = GridPosition.DEFAULT,
                span: GridSpan = GridSpan.SINGLE
            ): WidgetItem {
                return WidgetItem(
                    id = "widget:$appWidgetId",
                    appWidgetId = appWidgetId,
                    providerPackage = providerPackage,
                    providerClass = providerClass,
                    label = label,
                    position = position,
                    span = span
                )
            }
        }
    }

    // ========================================================================
    // FOLDER ITEM
    // ========================================================================

    /**
     * A folder that groups multiple pinned items together on the home screen.
     *
     * CREATION:
     * Folders are created automatically when the user drags one home screen
     * item onto another. Both items become children of the new folder.
     *
     * FOLDER LIFECYCLE:
     * - Two items dropped on each other → FolderItem created at that position
     * - An additional item dropped onto the folder → item added to children
     * - Two folders dropped on each other → children are merged
     * - Last child removed → folder is deleted automatically (cleanup policy)
     * - Only one child remains → folder is "unwrapped" back to a plain item
     *
     * SERIALIZATION:
     * FolderItem contains a [children] list of type List<HomeItem>.
     * Since HomeItem is a sealed class with @Serializable, kotlinx.serialization
     * handles the polymorphic children list automatically. Each child in the
     * JSON output will have its own "type" discriminator, exactly the same way
     * as top-level items stored in DataStore.
     *
     * EXAMPLE JSON:
     * ```
     * {
     *   "type": "FolderItem",
     *   "id": "folder:550e8400-...",
     *   "name": "Social",
     *   "children": [
     *     { "type": "PinnedApp", "id": "app:com.whatsapp/.Main", ... },
     *     { "type": "PinnedApp", "id": "app:org.telegram.messenger/.Main", ... }
     *   ],
     *   "position": { "row": 0, "column": 2 }
     * }
     * ```
     *
     * NESTING POLICY:
     * Folders cannot be placed inside other folders. This is enforced at the
     * repository level: any attempt to add a FolderItem as a child of another
     * FolderItem is silently ignored. Only the four leaf types (PinnedApp,
     * PinnedFile, PinnedContact, AppShortcut) can be folder children.
     *
     * @property id Unique identifier: "folder:{uuid}"
     * @property name User-visible folder name, shown below the folder icon and inside the popup.
     *                 Defaults to "Folder". The user can rename it by tapping the title inside
     *                 the folder popup dialog.
     * @property children The list of home items stored in this folder.
     *                     Order matters — it determines the grid display order inside the popup.
     *                     Children use positions only for ordering; their actual GridPosition
     *                     values are indices within the folder grid, not the home screen grid.
     * @property position Grid position (row, column) on the home screen where the folder icon sits.
     */
    @Serializable
    @Immutable
    data class FolderItem(
        override val id: String,
        val name: String = "Folder",
        val children: List<HomeItem> = emptyList(),
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem() {

        /**
         * Creates a copy of this FolderItem with a new grid position.
         * Called when the user drags the folder icon to a new location on the home screen.
         */
        override fun withPosition(newPosition: GridPosition): FolderItem {
            return copy(position = newPosition)
        }

        companion object {

            /**
             * Creates a new FolderItem from two existing home screen items.
             *
             * This is called when the user drags one icon onto another, triggering
             * folder creation. Both items are placed as children of the new folder.
             *
             * Child positions inside the folder use a flat sequential scheme:
             * - item1 goes to position (0,0) inside the folder grid
             * - item2 goes to position (0,1) inside the folder grid
             *
             * WHY RESET CHILD POSITIONS TO DEFAULT:
             * Children inside a folder are rendered in a LazyVerticalGrid that
             * positions them by list index, not by their GridPosition values.
             * Resetting them to DEFAULT avoids stale home-screen coordinates being
             * accidentally interpreted as folder-internal coordinates.
             *
             * @param item1 The first item (the one that was dragged onto item2)
             * @param item2 The second item (the one that was dropped on)
             * @param atPosition The home screen grid cell where the folder will appear.
             *                   This is the position previously occupied by item2.
             * @return A new FolderItem containing both items as children.
             */
            fun create(
                item1: HomeItem,
                item2: HomeItem,
                atPosition: GridPosition
            ): FolderItem {
                // Generate a UUID-based ID so folders are always unique, even if
                // the same two items are re-paired after a previous folder is deleted.
                val id = "folder:${UUID.randomUUID()}"

                return FolderItem(
                    id = id,
                    name = "Folder",
                    // Both children have their positions reset to DEFAULT because
                    // their display order inside the folder is determined by list index.
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
