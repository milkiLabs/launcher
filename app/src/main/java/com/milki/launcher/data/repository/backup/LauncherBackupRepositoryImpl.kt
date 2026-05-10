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
import com.milki.launcher.domain.repository.HomeRepository
import com.milki.launcher.domain.repository.LauncherBackupRepository
import com.milki.launcher.domain.repository.SettingsRepository
import com.milki.launcher.domain.repository.WidgetBindPermissionRequester
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LauncherBackupRepositoryImpl(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val homeRepository: HomeRepository,
    private val appRepository: AppRepository,
    private val widgetHostManager: WidgetHostManager
) : LauncherBackupRepository {

    private val backupJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    override suspend fun exportToUri(uri: Uri): LauncherBackupResult {
        return runCatching {
            val settings = settingsRepository.settings.first()
            val homeItems = homeRepository.readPinnedItems()

            val snapshot = LauncherBackupSnapshot(
                schemaVersion = LauncherBackupSnapshot.CURRENT_SCHEMA_VERSION,
                createdAtEpochMillis = System.currentTimeMillis(),
                appVersionName = resolveAppVersionName(),
                settings = settings,
                homeItems = homeItems
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

            val sanitizedHomeItems = sanitizeTopLevelItems(
                items = snapshot.homeItems,
                context = importContext
            )

            val existingHomeItems = homeRepository.readPinnedItems()
            collectWidgetIds(existingHomeItems).forEach(widgetHostManager::deallocateWidgetId)

            // Replace behavior: imported settings/home overwrite all current state.
            settingsRepository.updateSettings { snapshot.settings }
            homeRepository.replacePinnedItems(sanitizedHomeItems)

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

    private suspend fun sanitizeTopLevelItems(
        items: List<HomeItem>,
        context: ImportContext
    ): List<HomeItem> {
        val seenIds = mutableSetOf<String>()
        return items.mapNotNull { item ->
            val sanitized = sanitizeItem(
                item = item,
                context = context
            ) ?: return@mapNotNull null
            if (!seenIds.add(sanitized.id)) {
                context.skip(
                    category = SkippedImportCategory.OTHER,
                    message = "Skipped duplicate item id: ${sanitized.id}"
                )
                null
            } else {
                sanitized
            }
        }
    }

    private suspend fun sanitizeItem(
        item: HomeItem,
        context: ImportContext
    ): HomeItem? {
        return when (item) {
            is HomeItem.PinnedApp -> sanitizePinnedApp(item, context)
            is HomeItem.PinnedFile -> sanitizePinnedFile(item, context)
            is HomeItem.AppShortcut -> sanitizeAppShortcut(item, context)
            is HomeItem.WidgetItem -> sanitizeWidget(
                item = item,
                context = context
            )

            is HomeItem.PinnedContact -> item
            is HomeItem.FolderItem -> sanitizeFolder(
                folder = item,
                context = context
            )
        }
    }

    private fun sanitizePinnedApp(
        item: HomeItem.PinnedApp,
        context: ImportContext
    ): HomeItem.PinnedApp? {
        val componentName = ComponentName(item.packageName, item.activityName).flattenToString()
        if (componentName !in context.validPinnedAppComponents) {
            context.skip(
                category = SkippedImportCategory.APP,
                message = "Missing app component for ${item.label} (${item.packageName})"
            )
            return null
        }
        return item
    }

    private fun sanitizeAppShortcut(
        item: HomeItem.AppShortcut,
        context: ImportContext
    ): HomeItem.AppShortcut? {
        if (item.packageName !in context.validPackages) {
            context.skip(
                category = SkippedImportCategory.SHORTCUT,
                message = "Missing shortcut package ${item.packageName}"
            )
            return null
        }
        return item
    }

    private fun sanitizePinnedFile(
        item: HomeItem.PinnedFile,
        context: ImportContext
    ): HomeItem.PinnedFile? {
        if (!PinnedFileAvailability.isAvailable(
                contentResolver = appContext.contentResolver,
                uriString = item.uri,
                contentUriFailurePolicy = ContentUriFailurePolicy.TREAT_AS_UNAVAILABLE
            )
        ) {
            context.skip(
                category = SkippedImportCategory.FILE,
                message = "Missing or inaccessible file ${item.name}"
            )
            return null
        }
        return item
    }

    private suspend fun sanitizeWidget(
        item: HomeItem.WidgetItem,
        context: ImportContext
    ): HomeItem.WidgetItem? {
        if (item.providerPackage !in context.validPackages) {
            context.skip(
                category = SkippedImportCategory.WIDGET,
                message = "Missing widget provider package ${item.providerPackage}"
            )
            return null
        }

        val providerComponent = runCatching {
            ComponentName(item.providerPackage, item.providerClass)
        }.getOrNull()
            ?: return skipWidget(
                context = context,
                message = "Invalid widget provider component ${item.providerPackage}/${item.providerClass}"
            )

        val providerInfo = widgetHostManager.findInstalledProvider(providerComponent)
            ?: return skipWidget(
                context = context,
                message = "Widget provider not installed ${item.providerPackage}/${item.providerClass}"
            )

        val appWidgetId = widgetHostManager.allocateWidgetId()
        val isBound = bindWidgetForImport(
            appWidgetId = appWidgetId,
            providerInfo = providerInfo,
            context = context
        )
        if (!isBound) {
            widgetHostManager.deallocateWidgetId(appWidgetId)
            return skipWidget(
                context = context,
                message = "Widget permission not granted for ${item.label}"
            )
        }

        val needsConfiguration = widgetHostManager.getProviderInfo(appWidgetId)?.configure != null
        if (needsConfiguration) {
            widgetHostManager.deallocateWidgetId(appWidgetId)
            context.skip(
                category = SkippedImportCategory.WIDGET,
                message = "Widget ${item.label} requires configuration; skipped in batch import"
            )
            return null
        }

        return HomeItem.WidgetItem.create(
            appWidgetId = appWidgetId,
            providerPackage = item.providerPackage,
            providerClass = item.providerClass,
            label = item.label,
            position = item.position,
            span = item.span
        )
    }

    private suspend fun sanitizeFolder(
        folder: HomeItem.FolderItem,
        context: ImportContext
    ): HomeItem.FolderItem? {
        val sanitizedChildren = folder.children.mapNotNull { child ->
            sanitizeItem(
                item = child,
                context = context
            )
        }

        if (sanitizedChildren.isEmpty()) {
            context.skip(
                category = SkippedImportCategory.FOLDER,
                message = "Folder ${folder.name} became empty after import filtering"
            )
            return null
        }

        return folder.copy(children = sanitizedChildren)
    }

    private suspend fun bindWidgetForImport(
        appWidgetId: Int,
        providerInfo: android.appwidget.AppWidgetProviderInfo,
        context: ImportContext
    ): Boolean {
        if (widgetHostManager.bindWidget(appWidgetId, providerInfo)) {
            return true
        }

        return context.requestWidgetBindPermission(
            widgetHostManager.createBindPermissionIntent(
                appWidgetId = appWidgetId,
                providerInfo = providerInfo
            )
        )
    }

    private fun skipWidget(
        context: ImportContext,
        message: String
    ): HomeItem.WidgetItem? {
        context.skip(
            category = SkippedImportCategory.WIDGET,
            message = message
        )
        return null
    }

    private fun collectWidgetIds(items: List<HomeItem>): List<Int> {
        val ids = mutableListOf<Int>()

        fun visit(item: HomeItem) {
            when (item) {
                is HomeItem.WidgetItem -> ids += item.appWidgetId
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

    private data class ImportContext(
        val validPackages: Set<String>,
        val validPinnedAppComponents: Set<String>,
        val skippedReasons: MutableList<SkippedImportReason>,
        val requestWidgetBindPermission: WidgetBindPermissionRequester
    ) {
        fun skip(category: SkippedImportCategory, message: String) {
            skippedReasons += SkippedImportReason(
                category = category,
                message = message
            )
        }
    }
}
