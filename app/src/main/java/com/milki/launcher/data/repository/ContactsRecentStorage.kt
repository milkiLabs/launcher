package com.milki.launcher.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore delegate dedicated to recent-contact persistence.
 *
 * WHY THIS IS EXTRACTED:
 * The repository previously mixed three different concerns in one class:
 * 1) ContentResolver query code
 * 2) cursor-to-domain mapping code
 * 3) DataStore persistence for recently called numbers
 *
 * This class isolates concern #3 so the persistence behavior is fully local,
 * testable in isolation, and easier for new contributors to reason about.
 */
private val Context.recentContactsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_contacts"
)

/**
 * Handles read/write operations for recent contact phone numbers.
 *
 * STORAGE FORMAT:
 * - key: "recent_contacts"
 * - value: comma-separated phone numbers (most recent first)
 * - maximum stored entries: 8
 *
 * The storage format is intentionally simple so developers can inspect it
 * quickly while learning DataStore behavior in this project.
 */
internal class ContactsRecentStorage(
    private val context: Context
) {

    /**
     * Preference key used for the CSV payload.
     */
    private val recentContactsKey = stringPreferencesKey("recent_contacts")

    /**
     * Saves one recently used phone number.
     *
     * Behavior details:
     * - de-duplicates existing entry if present
     * - inserts at front (most recent first)
     * - trims list to 8 entries
     */
    suspend fun saveRecentContact(phoneNumber: String) {
        context.recentContactsDataStore.edit { preferences ->
            val current = preferences[recentContactsKey] ?: ""
            val recentPhones = current.split(",")
                .filter { it.isNotEmpty() }
                .toMutableList()

            recentPhones.remove(phoneNumber)
            recentPhones.add(0, phoneNumber)

            preferences[recentContactsKey] = recentPhones.take(8).joinToString(",")
        }
    }

    /**
     * Returns a hot Flow of recent phone numbers in recency order.
     */
    fun getRecentContacts(): Flow<List<String>> {
        return context.recentContactsDataStore.data.map { preferences ->
            preferences[recentContactsKey]
                ?.split(",")
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        }
    }
}
