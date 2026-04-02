package com.milki.launcher.data.repository

import android.content.Context
import com.milki.launcher.data.repository.home.HomeGridOccupancyPolicy
import com.milki.launcher.data.repository.home.HomeSnapshotStore
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.repository.HomeRepository

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

    override val pinnedItems = snapshotStore.pinnedItems

    override suspend fun replacePinnedItems(items: List<HomeItem>) {
        snapshotStore.replaceAll(items)
    }

    override suspend fun isPinned(id: String): Boolean {
        return snapshotStore.readSnapshot().any { item -> item.id == id }
    }

    override suspend fun findAvailablePosition(columns: Int, maxRows: Int): GridPosition {
        val currentItems = snapshotStore.readSnapshot()
        return occupancyPolicy.findFirstAvailableSingleCell(
            items = currentItems,
            columns = columns,
            maxRows = maxRows
        )
    }

    override suspend fun clearAll() {
        snapshotStore.clearAll()
    }
}
