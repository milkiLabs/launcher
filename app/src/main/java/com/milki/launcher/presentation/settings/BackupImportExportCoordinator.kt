package com.milki.launcher.presentation.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.milki.launcher.app.activity.SuspendActivityResultLauncher
import com.milki.launcher.core.permission.PermissionChecker
import com.milki.launcher.core.permission.PermissionSettingsNavigator
import com.milki.launcher.domain.repository.WidgetBindRequest
import com.milki.launcher.domain.widget.WidgetHostPort

/**
 * Owns the backup import/export activity-result plumbing, mirroring
 * [com.milki.launcher.presentation.launcher.WidgetPlacementCoordinator]:
 *
 * - SAF launchers for export (CreateDocument) and import (OpenDocument)
 * - Persistable URI permission handling for imports
 * - File-access request used before importing backups with pinned files
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
    private lateinit var legacyFilesPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var contactsPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var manageStorageLauncher: ActivityResultLauncher<Intent>

    private val pendingAccessRequests = ArrayDeque<() -> Unit>()
    private var accessRequestsFinished: (() -> Unit)? = null

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

        legacyFilesPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            onAccessRequestStepCompleted()
        }

        contactsPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            onAccessRequestStepCompleted()
        }

        manageStorageLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            onAccessRequestStepCompleted()
        }
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

    /**
     * Asks the user for the access required to fully restore a backup:
     * contacts (runtime dialog) and file access ("All files access" settings
     * screen on Android R+, runtime dialog below).
     *
     * Requests run sequentially; [onFinished] is always invoked exactly once,
     * even when nothing needs to be requested or a request cannot be launched.
     */
    fun requestMissingImportAccess(
        needsFileAccess: Boolean,
        needsContactsAccess: Boolean,
        onFinished: () -> Unit
    ) {
        pendingAccessRequests.clear()
        accessRequestsFinished = onFinished

        if (needsContactsAccess &&
            !PermissionChecker.hasContactsPermission(activity)
        ) {
            pendingAccessRequests += {
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }

        if (needsFileAccess && !PermissionChecker.hasFilesPermission(activity)) {
            pendingAccessRequests += { launchFileAccessStep() }
        }

        startNextAccessRequest()
    }

    private fun launchFileAccessStep() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val settingsIntent = PermissionSettingsNavigator.manageStorageIntent(activity)
            if (settingsIntent != null) {
                manageStorageLauncher.launch(settingsIntent)
            } else {
                onAccessRequestStepCompleted()
            }
        } else {
            legacyFilesPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun onAccessRequestStepCompleted() {
        startNextAccessRequest()
    }

    private fun startNextAccessRequest() {
        val next = pendingAccessRequests.removeFirstOrNull()
        if (next == null) {
            val finished = accessRequestsFinished
            accessRequestsFinished = null
            finished?.invoke()
        } else {
            next()
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
