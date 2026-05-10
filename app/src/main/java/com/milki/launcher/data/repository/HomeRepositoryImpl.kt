package com.milki.launcher.data.repository

import android.content.Context
import com.milki.launcher.data.repository.home.HomeGridOccupancyPolicy
import com.milki.launcher.data.repository.home.HomeSnapshotStore
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.HomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * DataStore-backed implementation of HomeRepository.
 *
 * ARCHITECTURE:
 * - HomeSnapshotStore: DataStore flow + transactional read/modify/write helper.
 * - HomeGridOccupancyPolicy: span-aware placement for utility slot lookup.
 */
class HomeRepositoryImpl(
    context: Context
) : HomeRepository {

    private val snapshotStore = HomeSnapshotStore(context)
    private val occupancyPolicy = HomeGridOccupancyPolicy()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var latestPinnedItems: List<HomeItem>? = null

    override val pinnedItems = snapshotStore.pinnedItems

    init {
        repositoryScope.launch {
            snapshotStore.pinnedItems.collectLatest { items ->
                latestPinnedItems = items
            }
        }
    }

    override suspend fun readPinnedItems(): List<HomeItem> {
        val cached = latestPinnedItems
        if (cached != null) return cached

        return snapshotStore.readSnapshot().also { items ->
            latestPinnedItems = items
        }
    }

    override suspend fun replacePinnedItems(items: List<HomeItem>) {
        latestPinnedItems = items
        snapshotStore.replaceAll(items)
    }

    override suspend fun isPinned(id: String): Boolean {
        return readPinnedItems().any { item -> item.id == id }
    }

    override suspend fun findAvailablePosition(columns: Int, maxRows: Int): GridPosition {
        val currentItems = readPinnedItems()
        return occupancyPolicy.findFirstAvailableSingleCell(
            items = currentItems,
            columns = columns,
            maxRows = maxRows
        )
    }

    override suspend fun clearAll() {
        latestPinnedItems = emptyList()
        snapshotStore.clearAll()
    }
}
