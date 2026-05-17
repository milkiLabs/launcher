package com.milki.launcher.data.repository

import androidx.datastore.preferences.core.Preferences
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
fun <T : Preferences> Flow<T>.catchIoException(): Flow<T> = catch { exception ->
    if (exception is IOException) {
        emit(emptyPreferences() as T)
    } else {
        throw exception
    }
}
