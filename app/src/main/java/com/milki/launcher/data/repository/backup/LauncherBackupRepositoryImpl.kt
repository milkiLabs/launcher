package com.milki.launcher.data.repository.backup

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import com.milki.launcher.core.file.ContentUriFailurePolicy
import com.milki.launcher.core.file.PinnedFileAvailability
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.backup.LauncherBackupFile
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
import kotlinx.serialization.json.Json

class LauncherBackupRepositoryImpl(
    private val appContext: Context,
    private val settingsRepository: SettingsReader,
    private val homeRepository: HomeRepository,
    private val appRepository: AppRepository,
    private val widgetHostManager: WidgetHostManager,
    private val actionShortcutRepository: ActionShortcutRepository
) : LauncherBackupRepository {

    private val importSanitizer = LauncherBackupImportSanitizer(appContext, widgetHostManager)

    private val backupJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    override suspend fun exportToUri(uri: Uri): LauncherBackupResult {
        return runCatching {
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

            appContext.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
                writer.write(payload)
            } ?: error("Could not open output stream")

            LauncherBackupResult(
                success = true,
                message = "Backup exported successfully"
            )
        }.getOrElse { throwable ->
            LauncherBackupResult(
                success = false,
                message = throwable.message ?: "Failed to export backup"
            )
        }
    }

    override suspend fun importFromUri(
        uri: Uri,
        requestWidgetBindPermission: WidgetBindPermissionRequester
    ): LauncherImportResult {
        return runCatching {
            val filePayload = appContext.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Could not open input stream")

            val backupFile = backupJson.decodeFromString<LauncherBackupFile>(filePayload)
            val snapshot = backupFile.snapshot

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
            val validPackages = installedApps.mapTo(mutableSetOf()) { it.packageName }
            val validComponents = installedApps.mapTo(mutableSetOf()) {
                ComponentName(it.packageName, it.activityName).flattenToString()
            }

            val skippedReasons = mutableListOf<SkippedImportReason>()
            val importContext = ImportContext(
                validPackages = validPackages,
                validPinnedAppComponents = validComponents,
                skippedReasons = skippedReasons,
                requestWidgetBindPermission = requestWidgetBindPermission
            )

            val sanitizedHomeItems = importSanitizer.sanitizeTopLevelItems(
                items = snapshot.homeItems,
                context = importContext
            )

            val existingHomeItems = homeRepository.readPinnedItems()
            collectWidgetIds(existingHomeItems).forEach(widgetHostManager::deallocateWidgetId)

            val sanitizedActionShortcuts = importSanitizer.sanitizeActionShortcuts(
                items = snapshot.actionShortcuts,
                context = importContext
            )
            settingsRepository.updateSettings { snapshot.settings }
            homeRepository.replacePinnedItems(sanitizedHomeItems)
            actionShortcutRepository.replaceAllShortcuts(sanitizedActionShortcuts)

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
            LauncherImportResult(
                success = false,
                message = throwable.message ?: "Failed to import backup",
                importedTopLevelCount = 0,
                skippedCount = 0,
                skippedReasons = emptyList()
            )
        }
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
