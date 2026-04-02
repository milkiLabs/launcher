package com.milki.launcher.domain.model.backup

data class LauncherBackupResult(
    val success: Boolean,
    val message: String
)

data class LauncherImportResult(
    val success: Boolean,
    val message: String,
    val importedTopLevelCount: Int,
    val skippedCount: Int,
    val skippedReasons: List<String>
)
