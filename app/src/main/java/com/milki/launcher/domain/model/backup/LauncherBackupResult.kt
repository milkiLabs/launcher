package com.milki.launcher.domain.model.backup

data class LauncherBackupResult(
    val success: Boolean,
    val message: String
)

/**
 * Lightweight metadata about a backup file, read before a full import so the
 * UI can request permissions (e.g. file and contacts access for pinned items)
 * up front.
 */
data class LauncherBackupInspection(
    val pinnedFileCount: Int,
    val pinnedContactCount: Int
)

data class LauncherImportResult(
    val success: Boolean,
    val message: String,
    val importedTopLevelCount: Int,
    val skippedCount: Int,
    val skippedReasons: List<SkippedImportReason>
)

data class SkippedImportReason(
    val category: SkippedImportCategory,
    val message: String
)

enum class SkippedImportCategory {
    APP,
    FILE,
    WIDGET,
    SHORTCUT,
    FOLDER,
    CONTACT,
    OTHER
}
