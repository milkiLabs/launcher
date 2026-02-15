/**
 * SearchProviderRegistry.kt - Central registry for all search providers
 *
 * This file implements the Registry Pattern to manage all available search providers.
 * The registry is responsible for:
 * 1. Holding references to all registered providers
 * 2. Finding providers by prefix
 * 3. Providing the list of all providers for UI display
 *
 * WHY A REGISTRY?
 * - Single Responsibility: One place to manage all providers
 * - Open/Closed: Add new providers by registering them, not modifying code
 * - Easy testing: Can create registries with different provider sets
 *
 * HOW TO ADD A NEW PROVIDER:
 * 1. Create a class implementing SearchProvider interface
 * 2. Register it in the registry (either via constructor or register() method)
 * 3. The provider will automatically be available for searches
 */

package com.milki.launcher.domain.search

import com.milki.launcher.domain.repository.SearchProvider
import com.milki.launcher.domain.model.SearchProviderConfig

/**
 * Registry for all available search providers.
 *
 * This class is the single source of truth for which providers are available.
 * It's typically created once at app startup and provided via AppContainer.
 *
 * USAGE:
 * ```kotlin
 * // Create registry with providers
 * val registry = SearchProviderRegistry(listOf(
 *     webSearchProvider,
 *     contactsSearchProvider,
 *     youtubeSearchProvider
 * ))
 *
 * // Find provider by prefix
 * val provider = registry.findByPrefix("s") // Returns web search provider
 *
 * // Get all providers for UI
 * val allProviders = registry.getAllProviders()
 * ```
 */
class SearchProviderRegistry(
    /**
     * Initial list of providers to register.
     * Can be empty and providers added via register() method.
     */
    initialProviders: List<SearchProvider> = emptyList()
) {
    /**
     * Internal map of prefix to provider for O(1) lookup.
     * Using a map allows quick prefix-based provider resolution.
     */
    private val providersByPrefix: MutableMap<String, SearchProvider> = mutableMapOf()

    init {
        // Register all initial providers
        initialProviders.forEach { register(it) }
    }

    /**
     * Register a new search provider.
     *
     * If a provider with the same prefix already exists, it will be replaced.
     * This allows for dynamic provider replacement (e.g., for testing).
     *
     * @param provider The provider to register
     */
    fun register(provider: SearchProvider) {
        providersByPrefix[provider.config.prefix] = provider
    }

    /**
     * Find a provider by its prefix.
     *
     * @param prefix The prefix to search for (e.g., "s", "c", "y")
     * @return The matching provider, or null if not found
     */
    fun findByPrefix(prefix: String): SearchProvider? {
        return providersByPrefix[prefix]
    }

    /**
     * Get all registered providers.
     *
     * @return List of all registered providers
     */
    fun getAllProviders(): List<SearchProvider> {
        return providersByPrefix.values.toList()
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
        return providersByPrefix.values.map { it.config }
    }

    /**
     * Check if a provider exists for the given prefix.
     *
     * @param prefix The prefix to check
     * @return True if a provider is registered for this prefix
     */
    fun hasProvider(prefix: String): Boolean {
        return providersByPrefix.containsKey(prefix)
    }

    /**
     * Get the number of registered providers.
     */
    val size: Int
        get() = providersByPrefix.size
}
