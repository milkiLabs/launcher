package com.milki.launcher.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.milki.launcher.core.util.parseCsv
import com.milki.launcher.core.util.toCsv
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Generic LRU list backed by a DataStore CSV preference.
 *
 * Subclasses provide encoding/decoding for their element type and
 * configure the DataStore, preference key, and maximum size.
 */
internal abstract class RecentListStorage<T>(
    protected val dataStore: DataStore<Preferences>,
    protected val key: Preferences.Key<String>,
    protected val maxSize: Int,
) {
    protected abstract fun encode(item: T): String
    protected abstract fun decode(raw: String): T?

    suspend fun saveRecent(item: T) {
        val encoded = encode(item)
        dataStore.edit { preferences ->
            val items = parseCsv(preferences[key] ?: "").toMutableList()
            items.remove(encoded)
            items.add(0, encoded)
            preferences[key] = items.take(maxSize).toCsv()
        }
    }

    fun observeRecent(): Flow<List<T>> =
        dataStore.data.map { preferences ->
            parseCsv(preferences[key] ?: "").mapNotNull { decode(it) }
        }
}
