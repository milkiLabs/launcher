package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.PrefixMutationResult
import com.milki.launcher.domain.model.ProviderPrefixConfiguration

/**
 * Manages prefix configurations for fixed search providers (contacts, files).
 *
 * Handles adding, removing, replacing, and resetting prefixes per provider,
 * as well as bulk reset operations.
 */
interface PrefixConfigurationRepository {

    /**
     * Replace all prefixes for one provider with a new list.
     *
     * @param providerId Stable provider key (see ProviderId constants)
     * @param prefixes New prefixes for this provider; empty means remove custom override
     */
    suspend fun setProviderPrefixes(
        providerId: String,
        prefixes: List<String>
    ): PrefixMutationResult

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
    suspend fun addProviderPrefix(
        providerId: String,
        prefix: String,
        defaultPrefix: String
    ): PrefixMutationResult

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
     * Atomically replace the full prefix configuration map.
     *
     * This method is exposed for advanced callers that already have a complete,
     * validated map and want one targeted prefix-key write.
     */
    suspend fun setAllPrefixConfigurations(configurations: ProviderPrefixConfiguration)
}
