/**
 * PrefixOwnerRepository.kt - Unified repository for all prefix operations
 *
 * WHY THIS EXISTS:
 * Replaces the previous split between SearchSourceRepository prefix methods
 * and PrefixConfigurationRepository. All prefix owners (providers and sources)
 * are managed through one consistent API.
 */

package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.PrefixMutationResult

interface PrefixOwnerRepository {

    /**
     * Add a prefix to any owner (provider or source).
     *
     * Returns Success if added, or an error result if validation fails.
     */
    suspend fun addPrefix(ownerId: String, prefix: String): PrefixMutationResult

    /**
     * Remove a prefix from any owner.
     *
     * If this would leave the owner with no prefixes, the owner's prefixes
     * are reset to their defaults instead.
     */
    suspend fun removePrefix(ownerId: String, prefix: String): PrefixMutationResult

    /**
     * Reset an owner's prefixes back to their defaults.
     */
    suspend fun resetPrefixes(ownerId: String)

    /**
     * Reset all prefix customizations across all owners.
     */
    suspend fun resetAllPrefixes()
}
