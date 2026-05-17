package com.milki.launcher.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.ActionShortcutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.actionShortcutDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "action_shortcuts"
)

private object ActionShortcutPreferenceKeys {
    val SHORTCUTS = stringPreferencesKey("shortcuts_ordered")
}

class ActionShortcutRepositoryImpl(
    context: Context
) : ActionShortcutRepository {

    private val dataStore = context.actionShortcutDataStore
    private val serializer = NewlineJsonListSerializer(
        key = ActionShortcutPreferenceKeys.SHORTCUTS,
        json = HomeItem.json,
        serializer = HomeItem.ActionShortcut.serializer(),
        default = { listOf(HomeItem.ActionShortcut.DefaultDocsShortcut) }
    )

    override val shortcuts: Flow<List<HomeItem.ActionShortcut>> = dataStore.data
        .catchIoException()
        .map(serializer::readFrom)

    override suspend fun readShortcuts(): List<HomeItem.ActionShortcut> {
        return dataStore.data.map(serializer::readFrom).first()
    }

    override suspend fun saveShortcut(shortcut: HomeItem.ActionShortcut): Boolean {
        var success = true
        dataStore.edit { preferences ->
            val existing = serializer.readFrom(preferences).toMutableList()
            
            val isDuplicate = existing.any { 
                it.id != shortcut.id && 
                it.destinationUri == shortcut.destinationUri && 
                it.packageName == shortcut.packageName 
            }
            if (isDuplicate) {
                success = false
                return@edit
            }
            
            val index = existing.indexOfFirst { it.id == shortcut.id }
            if (index >= 0) {
                existing[index] = shortcut
            } else {
                existing.add(shortcut)
            }
            serializer.writeTo(existing, preferences)
        }
        return success
    }

    override suspend fun replaceAllShortcuts(shortcuts: List<HomeItem.ActionShortcut>) {
        dataStore.edit { preferences ->
            serializer.writeTo(shortcuts, preferences)
        }
    }

    override suspend fun deleteShortcut(shortcutId: String) {
        dataStore.edit { preferences ->
            val existing = serializer.readFrom(preferences)
            serializer.writeTo(
                items = existing.filterNot { it.id == shortcutId },
                preferences = preferences
            )
        }
    }
}
