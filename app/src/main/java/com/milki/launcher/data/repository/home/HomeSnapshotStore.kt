package com.milki.launcher.data.repository.home

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.milki.launcher.data.repository.common.catchIoException
import com.milki.launcher.domain.model.HomeItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Transaction helper around home DataStore snapshots.
 *
 * It centralizes read/modify/write boilerplate so higher-level repository code
 * only expresses mutation rules.
 */
internal class HomeSnapshotStore(
    context: Context,
    private val serializer: HomeItemSerializer = HomeItemSerializer()
) {

    private val dataStore = context.homeDataStore

    val pinnedItems: Flow<List<HomeItem>> = dataStore.data
        .catchIoException()
        .map(serializer::readFrom)

    suspend fun replaceAll(items: List<HomeItem>) {
        dataStore.edit { preferences ->
            serializer.writeTo(items, preferences)
        }
    }

    suspend fun readSnapshot(): List<HomeItem> {
        return pinnedItems.first()
    }

    suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.remove(HomePreferenceKeys.PINNED_ITEMS)
        }
    }
}
