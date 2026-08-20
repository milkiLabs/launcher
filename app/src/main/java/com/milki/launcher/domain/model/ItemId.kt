package com.milki.launcher.domain.model

import java.util.UUID

/**
 * Single source of truth for [HomeItem] id formats and the `prefix:` contracts.
 *
 * Item ids have the shape `"<prefix>:<rest>"`; every construction site must go
 * through this factory so the format cannot drift between callers.
 */
object ItemId {

    const val APP_PREFIX = "app"
    const val FILE_PREFIX = "file"
    const val CONTACT_PREFIX = "contact"
    const val SHORTCUT_PREFIX = "shortcut"
    const val ACTION_PREFIX = "action"
    const val WIDGET_PREFIX = "widget"
    const val FOLDER_PREFIX = "folder"

    fun app(packageName: String, activityName: String): String =
        "$APP_PREFIX:$packageName/$activityName"

    fun file(uri: String): String =
        "$FILE_PREFIX:$uri"

    fun contact(contactId: Long, lookupKey: String): String =
        "$CONTACT_PREFIX:$contactId:$lookupKey"

    fun shortcut(packageName: String, shortcutId: String): String =
        "$SHORTCUT_PREFIX:$packageName/$shortcutId"

    fun action(key: String): String =
        "$ACTION_PREFIX:$key"

    fun actionRandom(): String =
        action(UUID.randomUUID().toString())

    fun widget(appWidgetId: Int): String =
        "$WIDGET_PREFIX:$appWidgetId"

    fun folder(): String =
        "$FOLDER_PREFIX:${UUID.randomUUID()}"

    /** Returns the fragment after the first `:` (the rest of the id), or null when [raw] has no prefix separator. */
    fun rest(raw: String): String? =
        raw.substringAfter(':', "").takeIf { it.isNotEmpty() }

    /** Returns the numeric widget id from a [WIDGET_PREFIX]-prefixed home item id, or null when absent. */
    fun widgetId(raw: String): Int? =
        rest(raw)?.toIntOrNull()
}