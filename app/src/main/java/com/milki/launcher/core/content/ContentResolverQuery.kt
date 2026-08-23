package com.milki.launcher.core.content

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri

/** Builds a SQL "IN" placeholder list: "?,?,?" for [count] values. */
fun sqlInPlaceholders(count: Int): String = List(count) { "?" }.joinToString(",")

/**
 * Builds a SQL "[column] IN (?,...)" selection plus its args,
 * or null when [values] is empty.
 */
fun sqlInSelection(column: String, values: Collection<*>): Pair<String, Array<String>>? {
    if (values.isEmpty()) return null
    val selection = "$column IN (${sqlInPlaceholders(values.size)})"
    return selection to values.map { it.toString() }.toTypedArray()
}

/**
 * Queries [uri] and maps every row via [block], collecting non-null results.
 * The cursor is always closed.
 */
inline fun <T> ContentResolver.queryEach(
    uri: Uri,
    projection: Array<String>? = null,
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    sortOrder: String? = null,
    block: (Cursor) -> T?
): List<T> {
    val results = mutableListOf<T>()
    query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
        while (cursor.moveToNext()) {
            block(cursor)?.let(results::add)
        }
    }
    return results
}

/**
 * Queries [uri] and invokes [block] for every row. The cursor is always closed.
 */
inline fun ContentResolver.forEachRow(
    uri: Uri,
    projection: Array<String>? = null,
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    sortOrder: String? = null,
    block: (Cursor) -> Unit
) {
    query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
        while (cursor.moveToNext()) {
            block(cursor)
        }
    }
}
