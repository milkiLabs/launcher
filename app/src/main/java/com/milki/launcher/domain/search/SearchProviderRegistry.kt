/**
 * SearchProviderRegistry.kt - Central registry for all search providers
 *
 * This file implements the Registry Pattern to manage all available search providers.
 * The registry is responsible for:
 * 1. Holding references to all registered providers
 * 2. Finding providers by prefix (supporting multiple prefixes per provider)
 * 3. Finding providers by provider ID
 * 4. Providing the list of all providers for UI display
 * 5. Supporting dynamic prefix configuration updates
 *
 *
 * HOW TO ADD A NEW PROVIDER:
 * 1. Create a class implementing SearchProvider interface
 * 2. Register it in the registry (via constructor)
 * 3. The provider will automatically be available for searches
 * 4. Optionally configure custom prefixes via updatePrefixConfigurations()
 *
 * MULTIPLE PREFIXES PER PROVIDER:
 * A single provider can have multiple prefixes. This is useful for:
 * - Multilingual support: "f" and "م" both trigger files search
 * - Shortcuts: "y" and "yt" both trigger YouTube search
 * - Custom preferences: User can change "s" to "g" for Google
 */

package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.PrefixConfig
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.repository.SearchProvider
import com.milki.launcher.domain.model.SearchProviderConfig

/**
 * Registry for all available search providers.
 *
 * This class is the single source of truth for which providers are available
 * and what prefixes activate them. It supports:
 *
 * 1. MULTIPLE PREFIXES PER PROVIDER:
 *    A provider can be activated by any of its configured prefixes.
 *    Example: FilesSearchProvider with prefixes ["f", "م", "find"]
 *
 * 2. DYNAMIC PREFIX UPDATES:
 *    Prefixes can be changed at runtime via updatePrefixConfigurations().
 *    This allows users to customize their prefixes in settings.
 *
 * 3. EFFICIENT LOOKUP:
 *    - By prefix: O(1) using a Map<String, providerId>
 *    - By provider ID: O(1) using a Map<String, Provider>
 *
 * USAGE:
 * ```kotlin
 * // Create registry with providers
 * val registry = SearchProviderRegistry(
 *     initialProviders = listOf(
 *         webSearchProvider,
 *         contactsSearchProvider,
 *         youtubeSearchProvider,
 *         filesSearchProvider
 *     )
 * )
 *
 * // Find provider by prefix (uses configured or default prefix)
 * val provider = registry.findByPrefix("s") // Returns web search provider
 *
 * // Find provider by ID
 * val filesProvider = registry.findByProviderId(ProviderId.FILES)
 *
 * // Get all providers for UI
 * val allProviders = registry.getAllProviders()
 *
 * // Update prefix configurations (e.g., from settings)
 * registry.updatePrefixConfigurations(mapOf(
 *     ProviderId.FILES to PrefixConfig(listOf("f", "م")),
 *     ProviderId.WEB to PrefixConfig(listOf("s", "ج"))
 * ))
 * ```
 *
 * ARCHITECTURE:
 * ┌─────────────────────────────────────────────────────────────┐
 * │                  SearchProviderRegistry                      │
 * │                                                              │
 * │  providersById: Map<providerId, SearchProvider>             │
 * │  ┌─────────────────────────────────────────────────────┐    │
 * │  │ "web" → WebSearchProvider                            │    │
 * │  │ "contacts" → ContactsSearchProvider                  │    │
 * │  │ "youtube" → YouTubeSearchProvider                    │    │
 * │  │ "files" → FilesSearchProvider                        │    │
 * │  └─────────────────────────────────────────────────────┘    │
 * │                                                              │
 * │  prefixToProviderId: Map<prefix, providerId>                │
 * │  ┌─────────────────────────────────────────────────────┐    │
 * │  │ "s" → "web", "ج" → "web"                             │    │
 * │  │ "c" → "contacts"                                     │    │
 * │  │ "y" → "youtube", "yt" → "youtube"                    │    │
 * │  │ "f" → "files", "م" → "files"                         │    │
 * │  └─────────────────────────────────────────────────────┘    │
 * └─────────────────────────────────────────────────────────────┘
 */
class SearchProviderRegistry(
    /**
     * Initial list of providers to register.
     * Can be empty and providers added via register() method.
     */
    initialProviders: List<SearchProvider> = emptyList()
) {
    /**
     * Internal map of provider ID to provider for O(1) lookup.
     * This is the primary storage for all providers.
     * The key is the providerId from the provider's config.
     */
    private val providersById: MutableMap<String, SearchProvider> = mutableMapOf()

    /**
     * Internal map of prefix to provider ID for O(1) lookup.
     * This enables quick resolution of a user-typed prefix to the correct provider.
     * Multiple prefixes can map to the same provider ID.
     */
    private val prefixToProviderId: MutableMap<String, String> = mutableMapOf()

    /**
     * Current prefix configurations.
     * Key: provider ID (e.g., "web", "files")
     * Value: PrefixConfig with list of prefixes
     */
    private var prefixConfigurations: ProviderPrefixConfiguration = emptyMap()

    init {
        // Register all initial providers
        initialProviders.forEach { register(it) }

        // Initialize prefix mappings with default prefixes
        rebuildPrefixMappings()
    }

    /**
     * Register a new search provider.
     *
     * If a provider with the same providerId already exists, it will be replaced.
     * This allows for dynamic provider replacement (e.g., for testing).
     *
     * IMPORTANT: After registering, call rebuildPrefixMappings() to update
     * the prefix-to-provider mappings based on current configuration.
     *
     * @param provider The provider to register
     */
    fun register(provider: SearchProvider) {
        providersById[provider.config.providerId] = provider
    }

    /**
     * Update the prefix configurations for all providers.
     *
     * This method should be called when the user changes their prefix settings.
     * It rebuilds the prefix-to-provider mapping to reflect the new configuration.
     *
     * CONFIGURATION MERGING:
     * - If a provider has a custom configuration, use those prefixes
     * - If a provider has no custom configuration, use its default prefix
     *
     * @param configurations Map of provider ID to PrefixConfig
     */
    fun updatePrefixConfigurations(configurations: ProviderPrefixConfiguration) {
        this.prefixConfigurations = configurations
        rebuildPrefixMappings()
    }

    /**
     * Rebuild the prefix-to-provider ID mappings.
     *
     * This internal method is called after:
     * 1. Initial provider registration
     * 2. Prefix configuration updates
     *
     * ALGORITHM:
     * 1. Clear existing prefix mappings
     * 2. For each registered provider:
     *    a. Check if custom prefixes exist in prefixConfigurations
     *    b. If yes, use custom prefixes
     *    c. If no, use default prefix from provider's config
     * 3. Add each prefix to the prefix-to-providerId map
     */
    private fun rebuildPrefixMappings() {
        prefixToProviderId.clear()

        for ((providerId, provider) in providersById) {
            // Get prefixes for this provider: custom or default
            val prefixes = getPrefixesForProvider(providerId, provider)

            // Map each prefix to this provider
            for (prefix in prefixes) {
                prefixToProviderId[prefix] = providerId
            }
        }
    }

    /**
     * Get the list of prefixes for a provider.
     *
     * If the provider has a custom configuration, use those prefixes.
     * Otherwise, fall back to the default prefix from the provider's config.
     *
     * @param providerId The ID of the provider
     * @param provider The provider instance (for default prefix)
     * @return List of prefixes for this provider
     */
    private fun getPrefixesForProvider(providerId: String, provider: SearchProvider): List<String> {
        // Check if there's a custom configuration for this provider
        val customConfig = prefixConfigurations[providerId]

        return if (customConfig != null && customConfig.prefixes.isNotEmpty()) {
            // Use custom prefixes
            customConfig.prefixes
        } else {
            // Fall back to default prefix
            listOf(provider.config.prefix)
        }
    }

    /**
     * Find a provider by any of its configured prefixes.
     *
     * This method checks all prefixes for all providers.
     * It supports both single-character and multi-character prefixes.
     *
     * @param prefix The prefix to search for (e.g., "s", "f", "yt", "م")
     * @return The matching provider, or null if not found
     */
    fun findByPrefix(prefix: String): SearchProvider? {
        val providerId = prefixToProviderId[prefix] ?: return null
        return providersById[providerId]
    }

    /**
     * Find a provider by its unique provider ID.
     *
     * @param providerId The provider ID to search for (e.g., ProviderId.WEB)
     * @return The matching provider, or null if not found
     */
    fun findByProviderId(providerId: String): SearchProvider? {
        return providersById[providerId]
    }

    /**
     * Get all prefixes that are currently configured.
     *
     * This includes both default and custom prefixes.
     * Useful for displaying all available prefixes in the UI.
     *
     * @return Set of all configured prefixes
     */
    fun getAllPrefixes(): Set<String> {
        return prefixToProviderId.keys
    }

    /**
     * Get the configured prefixes for a specific provider.
     *
     * @param providerId The provider ID to get prefixes for
     * @return List of prefixes for this provider, or empty list if provider not found
     */
    fun getPrefixesForProvider(providerId: String): List<String> {
        val provider = providersById[providerId] ?: return emptyList()
        return getPrefixesForProvider(providerId, provider)
    }

    /**
     * Get all registered providers.
     *
     * @return List of all registered providers
     */
    fun getAllProviders(): List<SearchProvider> {
        return providersById.values.toList()
    }

    /**
     * Get all provider configurations.
     *
     * Useful for displaying available providers in the UI without
     * exposing the full provider implementations.
     *
     * @return List of provider configurations
     */
    fun getAllConfigs(): List<SearchProviderConfig> {
        return providersById.values.map { it.config }
    }

    /**
     * Check if a provider exists for the given prefix.
     *
     * @param prefix The prefix to check
     * @return True if a provider is registered for this prefix
     */
    fun hasProviderForPrefix(prefix: String): Boolean {
        return prefixToProviderId.containsKey(prefix)
    }

    /**
     * Check if a provider exists with the given provider ID.
     *
     * @param providerId The provider ID to check
     * @return True if a provider is registered with this ID
     */
    fun hasProvider(providerId: String): Boolean {
        return providersById.containsKey(providerId)
    }

    /**
     * Get the number of registered providers.
     */
    val size: Int
        get() = providersById.size

    /**
     * Get the total number of configured prefixes across all providers.
     */
    val totalPrefixCount: Int
        get() = prefixToProviderId.size
}
