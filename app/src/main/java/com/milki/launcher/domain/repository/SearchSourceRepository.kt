package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.PrefixMutationResult
import com.milki.launcher.domain.model.SearchSource

/**
 * Manages dynamic search sources (web engines, custom sources).
 *
 * Handles CRUD operations, enable/disable toggling, suggested action flags,
 * and default source selection.
 *
 * Prefix management is handled separately by PrefixOwnerRepository.
 */
interface SearchSourceRepository {

    suspend fun addSearchSource(source: SearchSource): PrefixMutationResult

    suspend fun updateSearchSource(
        sourceId: String,
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String
    ): PrefixMutationResult

    suspend fun deleteSearchSource(sourceId: String)

    suspend fun setSearchSourceEnabled(sourceId: String, enabled: Boolean)

    suspend fun setSearchSourceSuggestedAction(sourceId: String, showAsSuggestedAction: Boolean)

    suspend fun setDefaultSearchSourceId(sourceId: String?)
}
