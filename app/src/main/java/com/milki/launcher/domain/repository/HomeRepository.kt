/**
 * HomeRepository.kt - Repository interface for home screen pinned items
 *
 * This repository manages the items pinned to the launcher's home screen.
 * It follows the same repository pattern as the focused settings interfaces and AppRepository.
 *
 * RESPONSIBILITIES:
 * - Store and retrieve pinned items
 * - Provide a Flow for observing changes
 * - Handle add/remove operations
 *
 * IMPLEMENTATION:
 * HomeRepositoryImpl uses DataStore for persistence, similar to SettingsRepositoryImpl.
 * Each item is serialized to a string and stored in a StringSet.
 */

package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository for persisted home layout state.
 *
 * Mutation rules now live in HomeModelWriter. This repository only exposes
 * read access and atomic whole-model commits.
 */
interface HomeRepository {

    /** Stream of persisted top-level home items. */
    val pinnedItems: Flow<List<HomeItem>>

    /** Returns the latest persisted home snapshot. */
    suspend fun readPinnedItems(): List<HomeItem>

    /** Atomically replaces the entire persisted home layout. */
    suspend fun replacePinnedItems(items: List<HomeItem>)

    /** Utility lookup retained for non-layout callers. */
    suspend fun isPinned(id: String): Boolean

    /** Utility placement helper retained for bootstrap/reset flows. */
    suspend fun findAvailablePosition(columns: Int, maxRows: Int = 100): GridPosition

    /** Clears all persisted home items. */
    suspend fun clearAll()
}
