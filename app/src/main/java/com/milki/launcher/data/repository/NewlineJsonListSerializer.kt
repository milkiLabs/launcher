package com.milki.launcher.data.repository

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.Preferences.Key
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializes a list of items as newline-separated JSON in a DataStore preference key.
 * Corrupted rows are skipped so one bad line does not invalidate the full list.
 */
internal class NewlineJsonListSerializer<T>(
    private val key: Key<String>,
    private val json: Json,
    private val serializer: KSerializer<T>,
    private val default: () -> List<T> = { emptyList() }
) {

    fun readFrom(preferences: Preferences): List<T> {
        val encoded = preferences[key] ?: return default()
        if (encoded.isEmpty()) return default()

        return encoded
            .split("\n")
            .filter { it.isNotBlank() }
            .mapNotNull { row ->
                runCatching { json.decodeFromString(serializer, row) }.getOrNull()
            }
    }

    fun writeTo(items: List<T>, preferences: MutablePreferences) {
        preferences[key] = items.joinToString("\n") { json.encodeToString(serializer, it) }
    }
}
