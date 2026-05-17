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
fun Flow<Preferences>.catchIoException(): Flow<Preferences> = catch { exception ->
    if (exception is IOException) {
        emit(emptyPreferences())
    } else {
        throw exception
    }
}
