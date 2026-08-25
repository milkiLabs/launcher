package com.milki.launcher.data.repository.backup

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import com.milki.launcher.core.file.ContentUriFailurePolicy
import com.milki.launcher.core.file.PinnedFileAvailability
import com.milki.launcher.core.util.lenientJson
import com.milki.launcher.domain.widget.WidgetHostPort
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.backup.LauncherBackupFile
import com.milki.launcher.domain.model.backup.LauncherBackupInspection
import com.milki.launcher.domain.model.backup.LauncherBackupResult
import com.milki.launcher.domain.model.backup.LauncherBackupSnapshot
import com.milki.launcher.domain.model.backup.LauncherImportResult
import com.milki.launcher.domain.model.backup.SkippedImportCategory
import com.milki.launcher.domain.model.backup.SkippedImportReason
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.ActionShortcutRepository
import com.milki.launcher.domain.repository.HomeRepository
import com.milki.launcher.domain.repository.LauncherBackupRepository
import com.milki.launcher.domain.repository.SettingsReader
import com.milki.launcher.domain.repository.WidgetBindPermissionRequester
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class LauncherBackupRepositoryImpl(
    private val appContext: Context,
    private val settingsRepository: SettingsReader,
    private val homeRepository: HomeRepository,
    private val appRepository: AppRepository,
    private val widgetHost: WidgetHostPort,
    private val actionShortcutRepository: ActionShortcutRepository
) : LauncherBackupRepository {

    private val importSanitizer = LauncherBackupImportSanitizer(appContext, widgetHost)

    private val backupJson = lenientJson { prettyPrint = true }

    override suspend fun exportToUri(uri: String): LauncherBackupResult {
        return runCatching {
            val targetUri = Uri.parse(uri)
            val settings = settingsRepository.settings.first()
            val homeItems = homeRepository.readPinnedItems()
            val actionShortcuts = actionShortcutRepository.readShortcuts()

            val snapshot = LauncherBackupSnapshot(
                schemaVersion = LauncherBackupSnapshot.CURRENT_SCHEMA_VERSION,
                createdAtEpochMillis = System.currentTimeMillis(),
                appVersionName = resolveAppVersionName(),
                settings = settings,
                homeItems = homeItems,
                actionShortcuts = actionShortcuts
            )

            val payload = backupJson.encodeToString(
                LauncherBackupFile(snapshot = snapshot)
            )

            appContext.contentResolver.openOutputStream(targetUri, "wt")?.bufferedWriter()?.use { writer ->
                writer.write(payload)
            } ?: error("Could not open output stream")

            LauncherBackupResult(
                success = true,
                message = "Backup exported successfully"
            )
        }.getOrElse { throwable ->
            Log.e(TAG, "Failed to export backup to $uri", throwable)
            LauncherBackupResult(
                success = false,
                message = "Failed to export backup"
            )
        }
    }

    override suspend fun inspectBackup(uri: String): LauncherBackupInspection {
        return runCatching {
            val snapshot = readBackupSnapshot(Uri.parse(uri))
            LauncherBackupInspection(
                pinnedFileCount = countPinnedItemsOfType(snapshot.homeItems) { it is HomeItem.PinnedFile },
                pinnedContactCount = countPinnedItemsOfType(snapshot.homeItems) { it is HomeItem.PinnedContact }
            )
        }.getOrElse { throwable ->
            Log.e(TAG, "Failed to inspect backup from $uri", throwable)
            LauncherBackupInspection(pinnedFileCount = 0, pinnedContactCount = 0)
        }
    }

    override suspend fun importFromUri(
        uri: String,
        requestWidgetBindPermission: WidgetBindPermissionRequester
    ): LauncherImportResult {
        return runCatching {
            val snapshot = readBackupSnapshot(Uri.parse(uri))
            if (snapshot.schemaVersion > LauncherBackupSnapshot.CURRENT_SCHEMA_VERSION) {
                return LauncherImportResult(
                    success = false,
                    message = "Backup schema ${snapshot.schemaVersion} is not supported",
                    importedTopLevelCount = 0,
                    skippedCount = 0,
                    skippedReasons = emptyList()
                )
            }

            val installedApps = appRepository.getInstalledApps()
            val validComponents = installedApps.mapTo(mutableSetOf()) {
                ComponentName(it.packageName, it.activityName).flattenToString()
            }

            val skippedReasons = mutableListOf<SkippedImportReason>()
            val importContext = ImportContext(
                isPackageInstalled = ::isPackageInstalled,
                validPinnedAppComponents = validComponents,
                skippedReasons = skippedReasons,
                requestWidgetBindPermission = requestWidgetBindPermission
            )

            val sanitizedHomeItems = importSanitizer.sanitizeTopLevelItems(
                items = snapshot.homeItems,
                context = importContext
            )

            val sanitizedActionShortcuts = importSanitizer.sanitizeActionShortcuts(
                items = snapshot.actionShortcuts,
                context = importContext
            )

            val existingWidgetIds = collectWidgetIds(homeRepository.readPinnedItems())
            try {
                settingsRepository.updateSettings { snapshot.settings }
                homeRepository.replacePinnedItems(sanitizedHomeItems)
                actionShortcutRepository.replaceAllShortcuts(sanitizedActionShortcuts)
            } catch (throwable: Throwable) {
                collectWidgetIds(sanitizedHomeItems).forEach(widgetHost::deallocateWidgetId)
                throw throwable
            }
            existingWidgetIds.forEach(widgetHost::deallocateWidgetId)

            LauncherImportResult(
                success = true,
                message = buildSummaryMessage(
                    importedCount = sanitizedHomeItems.size,
                    skippedCount = skippedReasons.size
                ),
                importedTopLevelCount = sanitizedHomeItems.size,
                skippedCount = skippedReasons.size,
                skippedReasons = skippedReasons.toList()
            )
        }.getOrElse { throwable ->
            Log.e(TAG, "Failed to import backup from $uri", throwable)
            LauncherImportResult(
                success = false,
                message = "Failed to import backup",
                importedTopLevelCount = 0,
                skippedCount = 0,
                skippedReasons = emptyList()
            )
        }
    }

    private fun readBackupSnapshot(sourceUri: Uri): LauncherBackupSnapshot {
        val payload = appContext.contentResolver.openInputStream(sourceUri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Could not open input stream")

        return backupJson.decodeFromString<LauncherBackupFile>(payload).snapshot
    }

    private fun countPinnedItemsOfType(
        items: List<HomeItem>,
        isMatch: (HomeItem) -> Boolean
    ): Int {
        return items.sumOf { item ->
            when {
                item is HomeItem.FolderItem ->
                    countPinnedItemsOfType(item.children, isMatch)

                isMatch(item) -> 1
                else -> 0
            }
        }
    }

    private companion object {
        const val TAG = "LauncherBackupRepo"
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            appContext.packageManager.getPackageInfo(packageName, 0)
        }.isSuccess
    }

    private fun collectWidgetIds(items: List<HomeItem>): List<Int> {
        val ids = mutableListOf<Int>()

        fun visit(item: HomeItem) {
            when (item) {
                is HomeItem.WidgetItem -> ids.add(item.appWidgetId)
                is HomeItem.FolderItem -> item.children.forEach(::visit)
                else -> Unit
            }
        }

        items.forEach(::visit)
        return ids
    }

    private fun buildSummaryMessage(importedCount: Int, skippedCount: Int): String {
        return if (skippedCount == 0) {
            "Import complete: replaced with $importedCount items"
        } else {
            "Import complete: replaced with $importedCount items, skipped $skippedCount unavailable items"
        }
    }

    private fun resolveAppVersionName(): String {
        val packageName = appContext.packageName
        return runCatching {
            val packageInfo = appContext.packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "unknown"
        }.getOrDefault("unknown")
    }

}
