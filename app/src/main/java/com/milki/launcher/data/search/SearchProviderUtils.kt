/**
 * SearchProviderUtils.kt - Shared utilities for search providers
 *
 * ARCHITECTURAL NOTE:
 * This file previously contained functions to create fake "hint" and "empty"
 * results with id=-1. This was an anti-pattern that mixed display logic with
 * search logic.
 *
 * REFACTORING COMPLETED:
 * - Removed fake hint/empty result creation
 * - Search providers now return empty lists for empty states
 * - UI layer properly handles empty states using EmptyState composable
 * - This keeps concerns properly separated
 *
 * FUTURE USE:
 * This file can be used for shared search utilities that are truly
 * about search logic, not display logic. For example:
 * - Query normalization functions
 * - Common search scoring algorithms
 * - Search result ranking utilities
 */

package com.milki.launcher.data.search

/**
 * Shared utility object for search provider helper functions.
 *
 * Currently empty after refactoring to remove fake hint/empty results.
 * Can be extended with actual search logic utilities in the future.
 */
object SearchProviderUtils {
    // This object is currently empty but kept for future search utilities
    // that are truly about search logic, not display logic
}
