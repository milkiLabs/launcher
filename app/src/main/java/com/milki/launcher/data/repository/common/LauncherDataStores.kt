package com.milki.launcher.data.repository.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Registry of every Preferences DataStore instance in the app.
 *
 * All instances are declared here so the storage topology can be audited in
 * one place. Each `name` maps directly to a file in the app's "datastore"
 * directory and must not be renamed — there is no migration machinery.
 */
internal val Context.homeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "home_items"
)

internal val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "launcher_prefs"
)

internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "launcher_settings"
)

internal val Context.contactsRecentDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_contacts"
)

internal val Context.filesRecentDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_files"
)

internal val Context.actionShortcutDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "action_shortcuts"
)
