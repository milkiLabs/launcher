package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.PrefixMutationResult
import com.milki.launcher.domain.model.SearchSource

/**
 * Manages dynamic search sources (web engines, custom sources).
 *
 * Handles CRUD operations, enable/disable toggling, suggested action flags,
 * and default source selection.
 */
interface SearchSourceRepository {

    /**
     * Append a custom search source using a targeted search-sources key write.
     *
     * The implementation normalizes/validates the resulting list before
     * persistence, matching existing repository behavior.
     */
    suspend fun addSearchSource(source: SearchSource): PrefixMutationResult

    /**
     * Update one existing custom search source by ID.
     *
     * If source is not found, the operation is a no-op.
     */
    suspend fun updateSearchSource(
        sourceId: String,
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String
    ): PrefixMutationResult

    /**
     * Delete one custom search source by ID.
     */
    suspend fun deleteSearchSource(sourceId: String)

    /**
     * Set enabled/disabled flag for one custom source by ID.
     */
    suspend fun setSearchSourceEnabled(sourceId: String, enabled: Boolean)

    /**
     * Set whether this source appears as a suggested action chip by ID.
     */
    suspend fun setSearchSourceSuggestedAction(sourceId: String, showAsSuggestedAction: Boolean)

    /**
     * Set the ID of the user's preferred default search engine.
     *
     * Passing null clears the preference and defaults to first enabled source.
     */
    suspend fun setDefaultSearchSourceId(sourceId: String?)

    /**
     * Add one prefix to a custom source with atomic repository-level validation.
     *
     * Validation and write happen in the same DataStore transaction so callers do
     * not need to rely on potentially stale UI snapshots for uniqueness checks.
     *
     * @param sourceId Stable source ID (for example: source_google)
     * @param prefix Raw user input prefix
     * @return Structured mutation result for deterministic UI handling
     */
    suspend fun addPrefixToSource(sourceId: String, prefix: String): PrefixMutationResult

    /**
     * Remove one prefix from a custom source with atomic repository-level lookup.
     *
     * @param sourceId Stable source ID
     * @param prefix Raw user input prefix to remove
     * @return Structured mutation result for deterministic UI handling/debugging
     */
    suspend fun removePrefixFromSource(sourceId: String, prefix: String): PrefixMutationResult
}
