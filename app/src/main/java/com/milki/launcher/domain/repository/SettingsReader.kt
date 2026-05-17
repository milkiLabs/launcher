package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.LauncherSettings
import kotlinx.coroutines.flow.Flow

/**
 * Read-only access to launcher settings.
 *
 * This is the narrowest interface for components that only need to observe
 * settings changes (e.g., search pipeline, UI rendering).
 */
interface SettingsReader {
    /**
     * Observe the current settings as a Flow.
     * Emits whenever any setting changes.
     */
    val settings: Flow<LauncherSettings>

    /**
     * Atomically replace all settings with a transformed snapshot.
     *
     * Used for bulk operations like backup import or reset to defaults.
     */
    suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings)
}
