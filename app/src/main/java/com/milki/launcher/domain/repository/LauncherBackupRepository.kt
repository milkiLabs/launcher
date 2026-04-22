package com.milki.launcher.domain.repository

import android.content.Intent
import android.net.Uri
import com.milki.launcher.domain.model.backup.LauncherBackupResult
import com.milki.launcher.domain.model.backup.LauncherImportResult

interface LauncherBackupRepository {
    suspend fun exportToUri(uri: Uri): LauncherBackupResult
    suspend fun importFromUri(
        uri: Uri,
        onWidgetBindPermissionRequested: suspend (appWidgetId: Int, intent: Intent) -> Boolean
    ): LauncherImportResult
}
