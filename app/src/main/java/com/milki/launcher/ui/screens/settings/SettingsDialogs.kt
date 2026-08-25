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
import androidx.compose.ui.res.stringResource
import com.milki.launcher.R
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
        title = { Text(stringResource(R.string.reset_settings_title)) },
        text = { Text(stringResource(R.string.reset_settings_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.action_reset),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
        title = { Text(stringResource(R.string.delete_source_title)) },
        text = { Text(stringResource(R.string.delete_source_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
internal fun ImportFileAccessDialog(
    pinnedFileCount: Int,
    pinnedContactCount: Int,
    onGrant: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_access_title)) },
        text = {
            Column {
                Text(stringResource(R.string.import_access_message))
                Spacer(modifier = Modifier.height(Spacing.small))
                if (pinnedFileCount > 0) {
                    Text(
                        stringResource(R.string.import_access_files_bullet, pinnedFileCount),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (pinnedContactCount > 0) {
                    Text(
                        stringResource(R.string.import_access_contacts_bullet, pinnedContactCount),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onGrant) {
                Text(stringResource(R.string.import_access_grant))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.import_access_dismiss))
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
        title = { Text(stringResource(R.string.import_report_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(importReport.message)
                Spacer(modifier = Modifier.height(Spacing.small))
                Text(stringResource(R.string.import_report_imported_count, importReport.importedTopLevelCount))
                Text(stringResource(R.string.import_report_skipped_count, importReport.skippedCount))

                if (importReport.skippedReasons.isNotEmpty()) {
                    ImportSkippedReasonGroups(importReport)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
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
    SkippedImportCategory.CONTACT,
    SkippedImportCategory.OTHER
)

@Composable
private fun SkippedImportCategory.toDisplayTitle(): String {
    return when (this) {
        SkippedImportCategory.APP -> stringResource(R.string.skipped_category_apps)
        SkippedImportCategory.FILE -> stringResource(R.string.skipped_category_files)
        SkippedImportCategory.WIDGET -> stringResource(R.string.skipped_category_widgets)
        SkippedImportCategory.SHORTCUT -> stringResource(R.string.skipped_category_shortcuts)
        SkippedImportCategory.FOLDER -> stringResource(R.string.skipped_category_folders)
        SkippedImportCategory.CONTACT -> stringResource(R.string.skipped_category_contacts)
        SkippedImportCategory.OTHER -> stringResource(R.string.skipped_category_other)
    }
}
