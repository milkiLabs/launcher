package com.milki.launcher.domain.model

/**
 * Structured result contract for prefix mutation operations.
 *
 * Used by both custom-source and local-provider prefix edits so callers can
 * handle validation outcomes deterministically.
 */
sealed interface PrefixMutationResult {

    /**
     * Mutation was fully applied.
     */
    data object Success : PrefixMutationResult

    /**
     * Prefix input was empty after normalization.
     */
    data object InvalidPrefixEmpty : PrefixMutationResult

    /**
     * Prefix contains spaces and therefore cannot be used as a trigger.
     */
    data object InvalidPrefixContainsSpaces : PrefixMutationResult

    /**
     * Target owner was not found.
     */
    data object TargetNotFound : PrefixMutationResult

    /**
     * Prefix already exists on another owner.
     *
     * @property ownerId The conflicting owner ID (provider or source).
     */
    data class DuplicatePrefixOnAnotherOwner(
        val ownerId: String
    ) : PrefixMutationResult

    /**
     * Prefix already exists on the target owner (no state change required).
     */
    data object PrefixAlreadyExistsOnTarget : PrefixMutationResult

    /**
     * Prefix was not present on the target owner during a remove operation.
     */
    data object PrefixNotFoundOnTarget : PrefixMutationResult
}
