package com.milki.launcher.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.milki.launcher.core.util.parseCsv
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore delegate dedicated to recent-file persistence.
 *
 * This class isolates DataStore persistence for recently opened files
 * so the persistence behavior is fully local, testable in isolation,
 * and similar to ContactsRecentStorage.
 */
private val Context.recentFilesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_files"
)

/**
 * Handles read/write operations for recent file IDs.
 *
 * STORAGE FORMAT:
 * - key: "recent_files"
 * - value: comma-separated file IDs (most recent first)
 * - maximum stored entries: 8
 */
internal class FilesRecentStorage(
    private val context: Context
) {

    /**
     * Preference key used for the CSV payload.
     */
    private val recentFilesKey = stringPreferencesKey("recent_files")

    /**
     * Saves one recently used file ID.
     *
     * Behavior details:
     * - de-duplicates existing entry if present
     * - inserts at front (most recent first)
     * - trims list to 8 entries
     */
    suspend fun saveRecentFile(fileId: Long) {
        val fileIdStr = fileId.toString()
        context.recentFilesDataStore.edit { preferences ->
            val current = preferences[recentFilesKey] ?: ""
            val recentFiles = parseCsv(current)
                .toMutableList()

            recentFiles.remove(fileIdStr)
            recentFiles.add(0, fileIdStr)

            preferences[recentFilesKey] = recentFiles.take(8).joinToString(",")
        }
    }

    /**
     * Returns a hot Flow of recent file IDs in recency order.
     */
    fun getRecentFileIds(): Flow<List<Long>> {
        return context.recentFilesDataStore.data.map { preferences ->
            preferences[recentFilesKey]
                ?.let { parseCsv(it) }
                ?.mapNotNull { it.toLongOrNull() }
                ?: emptyList()
        }
    }
}
