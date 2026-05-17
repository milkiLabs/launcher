package com.milki.launcher.domain.repository

/**
 * Manages the set of hidden app packages.
 *
 * Hidden apps are excluded from search results and app drawer listings.
 */
interface HiddenAppsRepository {

    /**
     * Toggle one app package name inside the hidden apps set.
     *
     * If the package is already hidden, it is unhidden. Otherwise it is hidden.
     */
    suspend fun toggleHiddenApp(packageName: String)
}
