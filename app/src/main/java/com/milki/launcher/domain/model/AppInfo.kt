package com.milki.launcher.domain.model

import androidx.compose.runtime.Immutable

/**
 * AppInfo represents a single installed application.
 * @property name The human-readable display name (e.g., "YouTube")
 * @property packageName The unique identifier (e.g., "com.google.android.youtube")
 * @property activityName The fully qualified activity class name for uniqueness
 *                        (e.g., "com.milki.launcher.SettingsActivity")
 *                        Multiple activities can share the same packageName.
 */
@Immutable
data class AppInfo(
    val name: String,
    val packageName: String,
    val activityName: String = packageName,
    val installedOrUpdatedAtMillis: Long = 0L
) {
    /** Precomputed lowercase fields used heavily by ranking and sorting paths. */
    val nameLower: String = name.lowercase()
    val packageLower: String = packageName.lowercase()
}
