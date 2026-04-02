package com.milki.launcher.data.repository.home

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.milki.launcher.domain.model.HomeItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Converts between DataStore Preferences payloads and HomeItem lists.
 *
 * Storage format is newline-separated JSON: one HomeItem per line.
 * Corrupted rows are skipped so one bad line does not invalidate the full model.
 */
internal class HomeItemSerializer(
    private val json: Json = homeStorageJson
) {

    fun readFrom(preferences: Preferences): List<HomeItem> {
        val encoded = preferences[HomePreferenceKeys.PINNED_ITEMS] ?: return emptyList()
        if (encoded.isEmpty()) return emptyList()

        return encoded
            .split("\n")
            .filter { it.isNotBlank() }
            .mapNotNull { row ->
                runCatching { json.decodeFromString<HomeItem>(row) }
                    .getOrNull()
            }
    }

    fun writeTo(items: List<HomeItem>, preferences: MutablePreferences) {
        preferences[HomePreferenceKeys.PINNED_ITEMS] = items
            .joinToString(separator = "\n") { item ->
                json.encodeToString(item)
            }
    }
}
