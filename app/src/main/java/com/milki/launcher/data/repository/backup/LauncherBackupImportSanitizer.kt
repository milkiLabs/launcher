package com.milki.launcher.data.repository.backup

import android.content.ComponentName
import android.content.Context
import com.milki.launcher.core.file.ContentUriFailurePolicy
import com.milki.launcher.core.file.PinnedFileAvailability
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.backup.SkippedImportCategory
import com.milki.launcher.domain.model.backup.SkippedImportReason
import com.milki.launcher.domain.repository.WidgetBindPermissionRequester

internal class LauncherBackupImportSanitizer(
    private val appContext: Context,
    private val widgetHostManager: WidgetHostManager
) {
    suspend fun sanitizeTopLevelItems(
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

    fun sanitizeActionShortcuts(
        items: List<HomeItem.ActionShortcut>,
        context: ImportContext
    ): List<HomeItem.ActionShortcut> {
        val seenIds = mutableSetOf<String>()
        return items.mapNotNull { item ->
            val sanitized = sanitizeActionShortcut(item, context) ?: return@mapNotNull null
            if (!seenIds.add(sanitized.id)) {
                context.skip(
                    category = SkippedImportCategory.OTHER,
                    message = "Skipped duplicate shortcut id: ${sanitized.id}"
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
            is HomeItem.ActionShortcut -> sanitizeActionShortcut(item, context)
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

    private fun sanitizeActionShortcut(
        item: HomeItem.ActionShortcut,
        context: ImportContext
    ): HomeItem.ActionShortcut? {
        val packageName = item.packageName ?: return item
        if (packageName !in context.validPackages) {
            context.skip(
                category = SkippedImportCategory.SHORTCUT,
                message = "Missing action shortcut package $packageName"
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

}

internal data class ImportContext(
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
