package com.milki.launcher.domain.model

/**
 * SourcePrefixMutationResult.kt - Structured result contract for source prefix mutations
 *
 * WHY THIS CONTRACT EXISTS:
 * SettingsViewModel previously validated source-prefix uniqueness using a local
 * StateFlow snapshot and then wrote through a broad settings transform.
 * Under rapid edits (or any lag between snapshot and persistence), this could
 * produce race windows where user intent was silently normalized away.
 *
 * This sealed hierarchy allows repository methods to perform validation inside
 * the atomic DataStore edit transaction and report deterministic outcomes back
 * to callers.
 */
sealed interface SourcePrefixMutationResult {

    /**
     * Mutation was fully applied.
     */
    data object Success : SourcePrefixMutationResult

    /**
     * Prefix input was empty after normalization.
     */
    data object InvalidPrefixEmpty : SourcePrefixMutationResult

    /**
     * Prefix contains spaces and therefore cannot be used as a provider trigger.
     */
    data object InvalidPrefixContainsSpaces : SourcePrefixMutationResult

    /**
     * The target source was not found by the provided source ID.
     */
    data object SourceNotFound : SourcePrefixMutationResult

    /**
     * Prefix already exists on another source.
     *
     * @property ownerSourceId Source that currently owns the conflicting prefix.
     */
    data class DuplicatePrefixOnAnotherSource(
        val ownerSourceId: String
    ) : SourcePrefixMutationResult

    /**
     * Prefix was already present on the target source (no state change required).
     */
    data object PrefixAlreadyExistsOnTargetSource : SourcePrefixMutationResult

    /**
     * Prefix was not present on the target source during a remove operation.
     */
    data object PrefixNotFoundOnTargetSource : SourcePrefixMutationResult
}
