/**
 * SettingsRepository.kt - Repository interface for launcher settings
 *
 * Defines the contract for reading and writing launcher settings.
 * The implementation (SettingsRepositoryImpl) uses DataStore for persistence.
 *
 * ARCHITECTURE:
 * Domain layer defines the interface → Data layer implements it
 * This allows swapping the storage mechanism without changing the domain.
 */

package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.HomeTapAction
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.model.SearchResultLayout
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.model.SourcePrefixMutationResult
import com.milki.launcher.domain.model.SwipeUpAction
import kotlinx.coroutines.flow.Flow

/**
 * Repository for reading and writing launcher settings.
 *
 * All operations are suspend/Flow-based for non-blocking I/O.
 */
interface SettingsRepository {

    /**
     * Observe the current settings as a Flow.
     * Emits whenever any setting changes.
     */
    val settings: Flow<LauncherSettings>

    /**
     * Update settings with a transformation function.
     *
     * @param transform Function that receives current settings and returns updated settings
     */
    suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings)

    // ========================================================================
    // TARGETED SINGLE-KEY SETTINGS UPDATES
    // ========================================================================

    /**
     * Update max number of search results.
     */
    suspend fun setMaxSearchResults(value: Int)

    /**
     * Update keyboard auto-focus behavior.
     */
    suspend fun setAutoFocusKeyboard(value: Boolean)

    /**
     * Update whether recent apps are shown.
     */
    suspend fun setShowRecentApps(value: Boolean)

    /**
     * Update whether search closes after launch.
     */
    suspend fun setCloseSearchOnLaunch(value: Boolean)

    /**
     * Update search result layout mode.
     */
    suspend fun setSearchResultLayout(layout: SearchResultLayout)

    /**
     * Update homescreen hint visibility.
     */
    suspend fun setShowHomescreenHint(value: Boolean)

    /**
     * Update app icon visibility in results.
     */
    suspend fun setShowAppIcons(value: Boolean)

    /**
     * Update homescreen tap action.
     */
    suspend fun setHomeTapAction(action: HomeTapAction)

    /**
     * Update homescreen swipe-up action.
     */
    suspend fun setSwipeUpAction(action: SwipeUpAction)

    /**
     * Update contacts provider enabled state.
     */
    suspend fun setContactsSearchEnabled(value: Boolean)

    /**
     * Update files provider enabled state.
     */
    suspend fun setFilesSearchEnabled(value: Boolean)

    /**
     * Append a custom search source using a targeted search-sources key write.
     *
     * The implementation normalizes/validates the resulting list before
     * persistence, matching existing repository behavior.
     */
    suspend fun addSearchSource(source: SearchSource)

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
    )

    /**
     * Delete one custom search source by ID.
     */
    suspend fun deleteSearchSource(sourceId: String)

    /**
     * Set enabled/disabled flag for one custom source by ID.
     */
    suspend fun setSearchSourceEnabled(sourceId: String, enabled: Boolean)

    /**
     * Replace all prefixes for one provider with a new list.
     *
     * HOT-PATH OPTIMIZATION NOTE:
     * This helper exists so prefix edits can update only the prefix-related
     * DataStore key instead of remapping and rewriting the full LauncherSettings
     * snapshot on each edit interaction.
     *
     * @param providerId Stable provider key (see ProviderId constants)
     * @param prefixes New prefixes for this provider; empty means remove custom override
     */
    suspend fun setProviderPrefixes(providerId: String, prefixes: List<String>)

    /**
     * Add a prefix for a provider with duplicate protection.
     *
     * If the provider has no custom configuration, repository starts from the
     * provided default prefix so behavior matches existing business logic.
     *
     * @param providerId Stable provider key (see ProviderId constants)
     * @param prefix Prefix to add
     * @param defaultPrefix Default provider prefix when no custom prefixes exist
     */
    suspend fun addProviderPrefix(providerId: String, prefix: String, defaultPrefix: String)

    /**
     * Remove a single prefix from a provider configuration.
     *
     * If this removes the last prefix, the custom provider override is deleted,
     * allowing fallback to the provider default prefix.
     */
    suspend fun removeProviderPrefix(providerId: String, prefix: String)

    /**
     * Remove custom prefix configuration for one provider.
     */
    suspend fun resetProviderPrefixes(providerId: String)

    /**
     * Remove all custom prefix configurations at once.
     */
    suspend fun resetAllPrefixConfigurations()

    /**
     * Toggle one app package name inside the hidden apps set.
     *
     * HOT-PATH OPTIMIZATION NOTE:
     * This helper updates only the hidden-apps key to avoid full settings
     * read/write cycles for a simple set mutation.
     */
    suspend fun toggleHiddenApp(packageName: String)

    /**
     * Atomically replace the full prefix configuration map.
     *
     * This method is exposed for advanced callers that already have a complete,
     * validated map and want one targeted prefix-key write.
     */
    suspend fun setAllPrefixConfigurations(configurations: ProviderPrefixConfiguration)

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
    suspend fun addPrefixToSource(sourceId: String, prefix: String): SourcePrefixMutationResult

    /**
     * Remove one prefix from a custom source with atomic repository-level lookup.
     *
     * @param sourceId Stable source ID
     * @param prefix Raw user input prefix to remove
     * @return Structured mutation result for deterministic UI handling/debugging
     */
    suspend fun removePrefixFromSource(sourceId: String, prefix: String): SourcePrefixMutationResult
}
