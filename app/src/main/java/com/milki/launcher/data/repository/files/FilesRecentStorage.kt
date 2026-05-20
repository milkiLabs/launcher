package com.milki.launcher.data.repository.files

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.milki.launcher.data.repository.common.RecentListStorage

private val Context.recentFilesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_files"
)

internal class FilesRecentStorage(context: Context) : RecentListStorage<Long>(
    dataStore = context.recentFilesDataStore,
    key = stringPreferencesKey("recent_files"),
    maxSize = 8,
) {
    override fun encode(item: Long): String = item.toString()
    override fun decode(raw: String): Long? = raw.toLongOrNull()
}
