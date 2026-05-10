package com.milki.launcher.domain.model.backup

import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.LauncherSettings
import kotlinx.serialization.Serializable

/**
 * Portable launcher backup payload.
 *
 * Storage internals are intentionally not exposed in this schema so imports
 * remain stable if the app changes DataStore implementation later.
 */
@Serializable
data class LauncherBackupSnapshot(
    val schemaVersion: Int,
    val createdAtEpochMillis: Long,
    val appVersionName: String,
    val settings: LauncherSettings,
    val homeItems: List<HomeItem>
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

@Serializable
data class LauncherBackupFile(
    val snapshot: LauncherBackupSnapshot
)
