package com.milki.launcher.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.milki.launcher.domain.model.backup.LauncherImportResult
import com.milki.launcher.domain.model.backup.SkippedImportCategory
import com.milki.launcher.ui.theme.Spacing

/**
 * Shared dialogs used by the settings pages.
 *
 * Each page hosts its own dialogs; this file only owns dialog composition.
 */

@Composable
internal fun ResetSettingsDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Settings") },
        text = { Text("This will restore all settings to their default values. This action cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Reset", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun DeleteSourceDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete source") },
        text = { Text("Delete this source and all of its prefixes?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun ImportReportDialog(
    importReport: LauncherImportResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Report") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(importReport.message)
                Spacer(modifier = Modifier.height(Spacing.small))
                Text("Imported items: ${importReport.importedTopLevelCount}")
                Text("Skipped items: ${importReport.skippedCount}")

                if (importReport.skippedReasons.isNotEmpty()) {
                    ImportSkippedReasonGroups(importReport)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun ImportSkippedReasonGroups(importReport: LauncherImportResult) {
    Spacer(modifier = Modifier.height(Spacing.smallMedium))
    val groupedReasons = importReport.skippedReasons.groupBy { it.category }

    skippedImportCategoryDisplayOrder.forEach { category ->
        val reasonsForCategory = groupedReasons[category].orEmpty()
        if (reasonsForCategory.isEmpty()) return@forEach

        Text(
            text = category.toDisplayTitle(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        reasonsForCategory.forEach { reason ->
            Text("- ${reason.message}")
        }

        Spacer(modifier = Modifier.height(Spacing.small))
    }
}

private val skippedImportCategoryDisplayOrder = listOf(
    SkippedImportCategory.APP,
    SkippedImportCategory.FILE,
    SkippedImportCategory.WIDGET,
    SkippedImportCategory.SHORTCUT,
    SkippedImportCategory.FOLDER,
    SkippedImportCategory.OTHER
)

private fun SkippedImportCategory.toDisplayTitle(): String {
    return when (this) {
        SkippedImportCategory.APP -> "Apps"
        SkippedImportCategory.FILE -> "Files"
        SkippedImportCategory.WIDGET -> "Widgets"
        SkippedImportCategory.SHORTCUT -> "Shortcuts"
        SkippedImportCategory.FOLDER -> "Folders"
        SkippedImportCategory.OTHER -> "Other"
    }
}
