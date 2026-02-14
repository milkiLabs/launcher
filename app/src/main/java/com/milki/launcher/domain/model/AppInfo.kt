/**
 * AppInfo.kt - Domain model representing a single installed application
 */

package com.milki.launcher.domain.model
import android.content.Intent

/**
 * AppInfo represents a single installed application.
 * @property name The human-readable display name (e.g., "YouTube")
 * @property packageName The unique identifier (e.g., "com.google.android.youtube")
 * @property launchIntent The Intent used to launch the app (null if not launchable)
 */
data class AppInfo(
    val name: String,
    val packageName: String,
    val launchIntent: Intent?
) {
    /**
     * Cached lowercase version of the app name.
     * 
     * We use 'by lazy' which means:
     * - The lowercase string is computed only ONCE (on first access)
     * - The result is cached for all future accesses
     * - This speeds up search operations significantly
     * 
     * Without caching, every search keystroke would call lowercase()
     * on all apps, which would be slow for 200+ apps.
     */
    val nameLower: String by lazy { name.lowercase() }
    
    /**
     * Cached lowercase version of the package name.
     * 
     * Same lazy caching as nameLower. 
     * Users can search by package name * (e.g., typing "youtube" finds "com.google.android.youtube").
     */
    val packageLower: String by lazy { packageName.lowercase() }
}

/**
 * Extension function to check if an AppInfo matches a search query.
 * @param query The search query
 */
fun AppInfo.matchesQuery(query: String): Boolean {
    val queryLower = query.lowercase()
    return nameLower.contains(queryLower) || packageLower.contains(queryLower)
}
