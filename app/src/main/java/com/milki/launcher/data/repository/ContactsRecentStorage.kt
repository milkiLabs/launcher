package com.milki.launcher.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private val Context.recentContactsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_contacts"
)

internal class ContactsRecentStorage(context: Context) : RecentListStorage<String>(
    dataStore = context.recentContactsDataStore,
    key = stringPreferencesKey("recent_contacts"),
    maxSize = 8,
) {
    override fun encode(item: String): String = item
    override fun decode(raw: String): String? = raw
}
