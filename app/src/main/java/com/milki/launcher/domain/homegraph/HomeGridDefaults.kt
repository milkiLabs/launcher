package com.milki.launcher.domain.homegraph

/**
 * Shared defaults for home-grid domain behavior.
 *
 * Keep this as the single source of truth for values used across
 * domain mutation logic, repository placement checks, and UI grid config.
 */
object HomeGridDefaults {
    const val COLUMNS = 5

    /**
     * Upper bound for row-major free-slot scans on the serialized (unbounded
     * rows) home layout. A scan that reaches this row places the item one row
     * below the grid instead of failing.
     */
    const val MAX_ROWS = 100
}
