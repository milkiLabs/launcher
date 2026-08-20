package com.milki.launcher.data.repository.common

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
 * Instances are configured with [encoder]/[decoder] lambdas for their element type plus
 * the DataStore, preference key, and maximum size. Subclasses that need extra behavior
 * (e.g. [RecentAppsStore]) may extend this class and override [encode]/[decode] instead
 * of passing the lambdas.
 */
open class RecentListStorage<T>(
    protected val dataStore: DataStore<Preferences>,
    protected val key: Preferences.Key<String>,
    protected val maxSize: Int,
    protected val encoder: (T) -> String = { item -> "${item}" },
    protected val decoder: (String) -> T? = { null },
) {
    protected open fun encode(item: T): String = encoder(item)
    protected open fun decode(raw: String): T? = decoder(raw)

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
