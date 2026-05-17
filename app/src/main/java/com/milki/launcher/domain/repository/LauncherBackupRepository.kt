package com.milki.launcher.domain.repository

import android.net.Uri
import com.milki.launcher.domain.model.backup.LauncherBackupResult
import com.milki.launcher.domain.model.backup.LauncherImportResult

data class WidgetBindRequest(
    val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String
)

typealias WidgetBindPermissionRequester = suspend (WidgetBindRequest) -> Boolean

interface LauncherBackupRepository {
    suspend fun exportToUri(uri: Uri): LauncherBackupResult
    suspend fun importFromUri(
        uri: Uri,
        requestWidgetBindPermission: WidgetBindPermissionRequester
    ): LauncherImportResult
}
