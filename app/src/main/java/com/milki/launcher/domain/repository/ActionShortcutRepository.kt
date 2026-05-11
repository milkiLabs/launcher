package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.HomeItem
import kotlinx.coroutines.flow.Flow

interface ActionShortcutRepository {
    val shortcuts: Flow<List<HomeItem.ActionShortcut>>

    suspend fun readShortcuts(): List<HomeItem.ActionShortcut>

    suspend fun saveShortcut(shortcut: HomeItem.ActionShortcut)

    suspend fun replaceAllShortcuts(shortcuts: List<HomeItem.ActionShortcut>)

    suspend fun deleteShortcut(shortcutId: String)
}
