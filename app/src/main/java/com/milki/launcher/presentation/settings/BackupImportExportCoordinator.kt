package com.milki.launcher.presentation.settings

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.milki.launcher.app.activity.SuspendActivityResultLauncher
import com.milki.launcher.domain.repository.WidgetBindRequest
import com.milki.launcher.domain.widget.WidgetHostPort

/**
 * Owns the backup import/export activity-result plumbing, mirroring
 * [com.milki.launcher.presentation.launcher.WidgetPlacementCoordinator]:
 *
 * - SAF launchers for export (CreateDocument) and import (OpenDocument)
 * - Persistable URI permission handling for imports
 * - The suspend bridge over the system widget-bind permission dialog used by
 *   [SettingsViewModel.importBackup] to re-bind widgets restored from backup.
 */
class BackupImportExportCoordinator(
    private val activity: ComponentActivity,
    private val settingsViewModel: SettingsViewModel,
    private val widgetHost: WidgetHostPort
) {

    companion object {
        private const val TAG = "BackupImportExport"
        private const val BACKUP_MIME_TYPE = "application/json"
    }

    private lateinit var exportBackupLauncher: ActivityResultLauncher<String>
    private lateinit var importBackupLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var widgetBindLauncher: SuspendActivityResultLauncher

    fun initialize() {
        exportBackupLauncher = activity.registerForActivityResult(
            ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)
        ) { uri ->
            if (uri != null) {
                settingsViewModel.exportBackup(uri.toString())
            }
        }

        importBackupLauncher = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                handleImportUri(uri)
            }
        }

        widgetBindLauncher = SuspendActivityResultLauncher(
            activity.activityResultRegistry,
            "backup-widget-bind-permission"
        )
    }

    fun launchExport() {
        runCatching {
            val suggestedName = "launcher-backup-${System.currentTimeMillis()}.json"
            exportBackupLauncher.launch(suggestedName)
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to launch backup export flow", throwable)
        }
    }

    fun launchImport() {
        runCatching {
            importBackupLauncher.launch(arrayOf(BACKUP_MIME_TYPE, "*/*"))
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to launch backup import flow", throwable)
        }
    }

    private fun handleImportUri(uri: Uri) {
        runCatching {
            activity.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        settingsViewModel.importBackup(uri.toString()) { bindRequest ->
            awaitWidgetBindPermission(bindRequest)
        }
    }

    private suspend fun awaitWidgetBindPermission(request: WidgetBindRequest): Boolean {
        val bindIntent = widgetHost.createBindPermissionIntent(
            appWidgetId = request.appWidgetId,
            providerPackage = request.providerPackage,
            providerClass = request.providerClass
        )
        return widgetBindLauncher.launchForResult(bindIntent)
    }
}
