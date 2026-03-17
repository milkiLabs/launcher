package com.milki.launcher.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.HomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.homeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "home_items"
)

class HomeRepositoryImpl(
    private val context: Context
) : HomeRepository {

    private object Keys {
        val PINNED_ITEMS = stringPreferencesKey("pinned_items_ordered")
    }

    private val json: Json = HomeItem.json

    override val pinnedItems: Flow<List<HomeItem>> = context.homeDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            deserializeItems(preferences)
        }

    override suspend fun replacePinnedItems(items: List<HomeItem>) {
        context.homeDataStore.edit { preferences ->
            serializeItems(items, preferences)
        }
    }

    override suspend fun isPinned(id: String): Boolean {
        val items = context.homeDataStore.data
            .map { preferences -> deserializeItems(preferences) }
            .first()

        return items.any { it.id == id }
    }

    override suspend fun findAvailablePosition(columns: Int, maxRows: Int): GridPosition {
        val currentItems = context.homeDataStore.data
            .map { preferences -> deserializeItems(preferences) }
            .first()

        val occupiedPositions = buildOccupiedCellsMap(currentItems).keys
        for (row in 0 until maxRows) {
            for (column in 0 until columns) {
                val position = GridPosition(row, column)
                if (position !in occupiedPositions) {
                    return position
                }
            }
        }

        return GridPosition(maxRows, 0)
    }

    override suspend fun clearAll() {
        context.homeDataStore.edit { preferences ->
            preferences.remove(Keys.PINNED_ITEMS)
        }
    }

    private fun deserializeItems(preferences: Preferences): List<HomeItem> {
        val itemsString = preferences[Keys.PINNED_ITEMS] ?: return emptyList()
        if (itemsString.isEmpty()) return emptyList()

        return itemsString
            .split("\n")
            .filter { it.isNotBlank() }
            .mapNotNull { jsonLine ->
                try {
                    json.decodeFromString<HomeItem>(jsonLine)
                } catch (_: Exception) {
                    null
                }
            }
    }

    private fun serializeItems(items: List<HomeItem>, preferences: MutablePreferences) {
        val itemsString = items.joinToString("\n") { item ->
            json.encodeToString(item)
        }
        preferences[Keys.PINNED_ITEMS] = itemsString
    }

    private fun buildOccupiedCellsMap(
        items: List<HomeItem>,
        excludeItemId: String? = null
    ): Map<GridPosition, String> {
        val occupiedCells = mutableMapOf<GridPosition, String>()
        for (item in items) {
            if (item.id == excludeItemId) continue
            val span = (item as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
            for (pos in span.occupiedPositions(item.position)) {
                occupiedCells[pos] = item.id
            }
        }
        return occupiedCells
    }
}
