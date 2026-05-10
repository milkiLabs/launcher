package com.milki.launcher.data.repository.home

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.milki.launcher.domain.model.HomeItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

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
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map(serializer::readFrom)

    suspend fun replaceAll(items: List<HomeItem>) {
        dataStore.edit { preferences ->
            serializer.writeTo(items, preferences)
        }
    }

    suspend fun readSnapshot(): List<HomeItem> {
        return dataStore.data
            .map(serializer::readFrom)
            .first()
    }

    suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.remove(HomePreferenceKeys.PINNED_ITEMS)
        }
    }

    suspend fun <T> edit(transform: (MutableList<HomeItem>) -> EditDecision<T>): T {
        var result: EditDecision<T>? = null

        dataStore.edit { preferences ->
            val mutableItems = serializer.readFrom(preferences).toMutableList()
            val decision = transform(mutableItems)

            if (decision.shouldPersist) {
                serializer.writeTo(mutableItems, preferences)
            }

            result = decision
        }

        return checkNotNull(result).value
    }

    data class EditDecision<out T>(
        val shouldPersist: Boolean,
        val value: T
    ) {
        companion object {
            fun <T> persist(value: T): EditDecision<T> = EditDecision(
                shouldPersist = true,
                value = value
            )

            fun <T> noChange(value: T): EditDecision<T> = EditDecision(
                shouldPersist = false,
                value = value
            )
        }
    }
}
