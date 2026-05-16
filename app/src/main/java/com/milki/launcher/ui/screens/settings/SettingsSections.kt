package com.milki.launcher.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.milki.launcher.domain.model.LauncherInteractionCatalog
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.model.actionForTrigger
import com.milki.launcher.domain.model.targetForTrigger
import com.milki.launcher.ui.components.settings.ActionSettingItem
import com.milki.launcher.ui.components.settings.DropdownSettingItem
import com.milki.launcher.ui.components.settings.PrefixSettingItem
import com.milki.launcher.ui.components.settings.SettingsCategory
import com.milki.launcher.ui.components.settings.SourceSettingItem
import com.milki.launcher.ui.theme.Spacing

/**
 * Section-level settings UI.
 *
 * These composables own only rendering and section-scoped events. Cross-section
 * modal state and navigation live in SettingsScreen.
 */
@Composable
internal fun HomeScreenSection(
    settings: LauncherSettings,
    actions: SettingsHomeScreenActions,
    onSelectOpenAppAction: (LauncherTrigger, LauncherTriggerAction) -> Unit
) {
    SettingsCategory(title = "Home Screen")

    LauncherInteractionCatalog.configurableTriggers.forEach { trigger ->
        val action = settings.actionForTrigger(trigger)
        val target = settings.targetForTrigger(trigger)
        DropdownSettingItem(
            title = trigger.displayName,
            subtitle = if (action.requiresTargetPicker) {
                target?.displayName ?: "Choose an app or shortcut"
            } else {
                null
            },
            selectedValue = action.displayName,
            options = LauncherInteractionCatalog.availableActions()
                .map { availableAction -> availableAction.displayName to availableAction },
            onOptionSelected = { selectedAction ->
                if (selectedAction.requiresTargetPicker) {
                    onSelectOpenAppAction(trigger, selectedAction)
                } else {
                    actions.onSetTriggerAction(trigger, selectedAction)
                }
            }
        )
    }
}

@Composable
internal fun CustomSourcesSection(
    settings: LauncherSettings,
    actions: SettingsCustomSourceActions,
    onRequestAddSource: () -> Unit,
    onRequestEditSource: (SearchSource) -> Unit,
    onRequestDeleteSource: (String) -> Unit
) {
    val availableDefaultSources = settings.searchSources

    SettingsCategory(title = "Search Sources")

    Text(
        text = "Manage custom URL-template search sources and their prefixes.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = Spacing.mediumLarge,
            vertical = Spacing.small
        )
    )

    if (availableDefaultSources.isNotEmpty()) {
        val defaultSourceName = availableDefaultSources
            .firstOrNull { it.id == settings.defaultSearchSourceId }
            ?.name
            ?: availableDefaultSources.first().name

        DropdownSettingItem(
            title = "Default search engine",
            selectedValue = defaultSourceName,
            options = availableDefaultSources.map { source -> source.name to source },
            onOptionSelected = { selectedSource ->
                actions.onSetDefaultSearchSource(selectedSource.id)
            }
        )
    }

    ActionSettingItem(
        title = "Add custom source",
        subtitle = "Define name, URL template, prefixes, and color",
        onClick = onRequestAddSource,
        icon = Icons.Default.Add
    )

    settings.searchSources.forEach { source ->
        SourceSettingItem(
            source = source,
            onToggleEnabled = { enabled -> actions.onSetSearchSourceEnabled(source.id, enabled) },
            onToggleSuggestedAction = { show -> actions.onSetSearchSourceSuggestedAction(source.id, show) },
            onAddPrefix = { prefix, onResult ->
                actions.onAddPrefixToSource(source.id, prefix, onResult)
            },
            onRemovePrefix = { prefix ->
                actions.onRemovePrefixFromSource(source.id, prefix)
            },
            onEdit = { onRequestEditSource(source) },
            onDelete = { onRequestDeleteSource(source.id) }
        )
    }
}

@Composable
internal fun LocalPrefixesSection(
    settings: LauncherSettings,
    actions: SettingsLocalPrefixActions
) {
    SettingsCategory(title = "Local Prefixes")

    Text(
        text = "Customize local provider prefixes and enable or disable each provider.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = Spacing.mediumLarge,
            vertical = Spacing.small
        )
    )

    PrefixSettingItem(
        providerName = "Contacts",
        providerIcon = Icons.Default.Person,
        providerColor = MaterialTheme.colorScheme.secondary,
        defaultPrefix = "c",
        isEnabled = settings.contactsSearchEnabled,
        onToggleEnabled = actions.onSetContactsSearchEnabled,
        currentPrefixes = settings.prefixConfigurations[ProviderId.CONTACTS]?.prefixes
            ?: listOf("c"),
        onAddPrefix = { prefix, onResult ->
            actions.onAddProviderPrefix(ProviderId.CONTACTS, prefix, onResult)
        },
        onRemovePrefix = { actions.onRemoveProviderPrefix(ProviderId.CONTACTS, it) },
        onReset = { actions.onResetProviderPrefixes(ProviderId.CONTACTS) }
    )

    PrefixSettingItem(
        providerName = "Files",
        providerIcon = Icons.AutoMirrored.Filled.InsertDriveFile,
        providerColor = MaterialTheme.colorScheme.primaryContainer,
        defaultPrefix = "f",
        isEnabled = settings.filesSearchEnabled,
        onToggleEnabled = actions.onSetFilesSearchEnabled,
        currentPrefixes = settings.prefixConfigurations[ProviderId.FILES]?.prefixes
            ?: listOf("f"),
        onAddPrefix = { prefix, onResult ->
            actions.onAddProviderPrefix(ProviderId.FILES, prefix, onResult)
        },
        onRemovePrefix = { actions.onRemoveProviderPrefix(ProviderId.FILES, it) },
        onReset = { actions.onResetProviderPrefixes(ProviderId.FILES) }
    )
}

@Composable
internal fun AdvancedSection(
    backupStatusMessage: String?,
    onRequestReset: () -> Unit,
    onRequestExport: () -> Unit,
    onRequestImport: () -> Unit
) {
    SettingsCategory(title = "Advanced")

    ActionSettingItem(
        title = "Export backup",
        subtitle = "Export settings and homescreen snapshot to a file",
        onClick = onRequestExport,
        icon = Icons.Default.FileUpload
    )

    ActionSettingItem(
        title = "Import backup (replace current)",
        subtitle = "Replace current settings and homescreen from a backup file",
        onClick = onRequestImport,
        icon = Icons.Default.FileDownload
    )

    ActionSettingItem(
        title = "Reset to defaults",
        subtitle = "Restore all settings to their default values",
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

private val LauncherTriggerAction.requiresTargetPicker: Boolean
    get() = this == LauncherTriggerAction.OPEN_APP ||
        this == LauncherTriggerAction.OPEN_ACTION_SHORTCUT
