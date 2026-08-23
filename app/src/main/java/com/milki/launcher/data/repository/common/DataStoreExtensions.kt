package com.milki.launcher.data.repository.common

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

/**
 * Recover from DataStore IOExceptions by emitting empty preferences.
 *
 * This follows the official Android recommendation: when the DataStore file
 * is corrupted or unreadable, fall back to defaults instead of crashing.
 */
fun Flow<Preferences>.catchIoException(): Flow<Preferences> = catch { exception ->
    if (exception is IOException) {
        emit(emptyPreferences())
    } else {
        throw exception
    }
}

/**
 * Runs [block] inside a DataStore edit transaction and returns its result,
 * avoiding the fragile "capture into a local var" pattern.
 *
 * Note that [edit] (and therefore [block]) may run multiple times if the
 * write conflicts with another transaction; the returned value comes from
 * the last successful run.
 */
@Suppress("UNCHECKED_CAST")
suspend inline fun <T : Any> DataStore<Preferences>.mutate(
    crossinline block: (MutablePreferences) -> T
): T {
    var result: T? = null
    edit { preferences -> result = block(preferences) }
    return result as T
}
