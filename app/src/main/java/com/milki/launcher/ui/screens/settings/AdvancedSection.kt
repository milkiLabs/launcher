package com.milki.launcher.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.milki.launcher.R
import com.milki.launcher.ui.components.settings.ActionSettingItem
import com.milki.launcher.ui.components.settings.SettingsCategory
import com.milki.launcher.ui.theme.Spacing

@Composable
internal fun AdvancedSection(
    backupStatusMessage: String?,
    onRequestReset: () -> Unit,
    onRequestExport: () -> Unit,
    onRequestImport: () -> Unit,
    onShareCrashLogs: () -> Unit
) {
    SettingsCategory(title = stringResource(R.string.settings_group_advanced_title))

    ActionSettingItem(
        title = stringResource(R.string.advanced_export_title),
        subtitle = stringResource(R.string.advanced_export_subtitle),
        onClick = onRequestExport,
        icon = Icons.Default.FileUpload
    )

    ActionSettingItem(
        title = stringResource(R.string.advanced_import_title),
        subtitle = stringResource(R.string.advanced_import_subtitle),
        onClick = onRequestImport,
        icon = Icons.Default.FileDownload
    )

    HorizontalDivider(
        modifier = Modifier.padding(
            horizontal = Spacing.mediumLarge,
            vertical = Spacing.smallMedium
        ),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        thickness = 1.dp
    )

    ActionSettingItem(
        title = stringResource(R.string.advanced_share_logs_title),
        subtitle = stringResource(R.string.advanced_share_logs_subtitle),
        onClick = onShareCrashLogs,
        icon = Icons.Default.BugReport
    )

    HorizontalDivider(
        modifier = Modifier.padding(
            horizontal = Spacing.mediumLarge,
            vertical = Spacing.smallMedium
        ),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        thickness = 1.dp
    )

    ActionSettingItem(
        title = stringResource(R.string.advanced_reset_title),
        subtitle = stringResource(R.string.advanced_reset_subtitle),
        onClick = onRequestReset,
        textColor = MaterialTheme.colorScheme.error
    )

    if (!backupStatusMessage.isNullOrBlank()) {
        Text(
            text = backupStatusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = Spacing.mediumLarge,
                vertical = Spacing.small
            )
        )
    }
}
