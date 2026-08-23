package com.milki.launcher.data.repository.common

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Generic LRU list backed by a DataStore preference.
 *
 * Entries are persisted newline-separated; [encoder]/[decoder] are required so a
 * missing codec is a compile error rather than silently yielding an empty list.
 * [encode]d values must not contain the separators (newline or legacy comma);
 * violations fail fast on write instead of corrupting the stored list silently.
 *
 * Values written by the previous comma-separated format are still readable.
 */
open class RecentListStorage<T>(
    protected val dataStore: DataStore<Preferences>,
    protected val key: Preferences.Key<String>,
    protected val maxSize: Int,
    protected val encoder: (T) -> String,
    protected val decoder: (String) -> T?,
) {
    protected open fun encode(item: T): String = encoder(item)
    protected open fun decode(raw: String): T? = decoder(raw)

    suspend fun saveRecent(item: T) {
        val encoded = encode(item)
        check(SEPARATORS.none { it in encoded }) {
            "RecentListStorage item encodes to \"$encoded\", which contains a list separator"
        }

        dataStore.edit { preferences ->
            val items = readEntries(preferences[key] ?: "").toMutableList()
            items.remove(encoded)
            items.add(0, encoded)
            preferences[key] = items.take(maxSize).joinToString("\n")
        }
    }

    fun observeRecent(): Flow<List<T>> =
        dataStore.data.map { preferences -> readItems(preferences[key] ?: "") }

    protected fun readItems(raw: String): List<T> =
        readEntries(raw).mapNotNull(::decode)

    protected fun writeItems(items: List<T>): String =
        items.joinToString("\n") { encode(it) }

    private fun readEntries(raw: String): List<String> {
        if (raw.isEmpty()) return emptyList()

        return if ('\n' !in raw && ',' in raw) {
            raw.split(",").filter { it.isNotEmpty() }
        } else {
            raw.split("\n").filter { it.isNotEmpty() }
        }
    }

    private companion object {
        val SEPARATORS = charArrayOf('\n', ',')
    }
}
