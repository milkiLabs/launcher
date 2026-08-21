package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.backup.LauncherBackupResult
import com.milki.launcher.domain.model.backup.LauncherImportResult

data class WidgetBindRequest(
    val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String
)

typealias WidgetBindPermissionRequester = suspend (WidgetBindRequest) -> Boolean

interface LauncherBackupRepository {
    suspend fun exportToUri(uri: String): LauncherBackupResult
    suspend fun importFromUri(
        uri: String,
        requestWidgetBindPermission: WidgetBindPermissionRequester
    ): LauncherImportResult
}
