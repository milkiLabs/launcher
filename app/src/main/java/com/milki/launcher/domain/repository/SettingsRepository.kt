/**
 * SettingsRepository.kt - Repository interface for launcher settings
 *
 * Defines the contract for reading and writing launcher settings.
 * The implementation (SettingsRepositoryImpl) uses DataStore for persistence.
 *
 * ARCHITECTURE:
 * Domain layer defines the interface → Data layer implements it
 * This allows swapping the storage mechanism without changing the domain.
 */

package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.LauncherSettings
import kotlinx.coroutines.flow.Flow

/**
 * Repository for reading and writing launcher settings.
 *
 * All operations are suspend/Flow-based for non-blocking I/O.
 */
interface SettingsRepository {

    /**
     * Observe the current settings as a Flow.
     * Emits whenever any setting changes.
     */
    val settings: Flow<LauncherSettings>

    /**
     * Update settings with a transformation function.
     *
     * @param transform Function that receives current settings and returns updated settings
     */
    suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings)
}
