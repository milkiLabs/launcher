package com.milki.launcher.domain.repository

import android.net.Uri
import com.milki.launcher.domain.model.backup.LauncherBackupResult
import com.milki.launcher.domain.model.backup.LauncherImportResult

interface LauncherBackupRepository {
    suspend fun exportToUri(uri: Uri): LauncherBackupResult
    suspend fun importFromUri(uri: Uri): LauncherImportResult
}
