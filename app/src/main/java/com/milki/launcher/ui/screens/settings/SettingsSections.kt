package com.milki.launcher.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.milki.launcher.core.util.hexToColorOr
import com.milki.launcher.domain.model.LauncherInteractionCatalog
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.PrefixConfig
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.model.SourcePrefixOwner
import com.milki.launcher.domain.model.actionForTrigger
import com.milki.launcher.domain.model.targetForTrigger
import com.milki.launcher.ui.components.settings.ActionSettingItem
import com.milki.launcher.ui.components.settings.DropdownSettingItem
import com.milki.launcher.ui.components.settings.PrefixOwnerSettingItem
import com.milki.launcher.ui.components.settings.SettingsCardSurface
import com.milki.launcher.ui.components.settings.SettingsCategory
import com.milki.launcher.ui.components.settings.SwitchSettingItem
import com.milki.launcher.ui.theme.IconSize
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
internal fun SearchSourcesSection(
    settings: LauncherSettings,
    actions: SettingsSourceActions,
    onRequestAddSource: () -> Unit,
    onRequestEditSource: (SearchSource) -> Unit,
    onRequestDeleteSource: (String) -> Unit
) {
    SettingsCategory(title = "Search Sources")

    Text(
        text = "Manage search sources and their activation prefixes.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = Spacing.mediumLarge,
            vertical = Spacing.small
        )
    )

    if (settings.searchSources.isNotEmpty()) {
        val defaultSourceName = settings.searchSources
            .firstOrNull { it.id == settings.defaultSearchSourceId }
            ?.name
            ?: settings.searchSources.first().name

        DropdownSettingItem(
            title = "Default search engine",
            selectedValue = defaultSourceName,
            options = settings.searchSources.map { source -> source.name to source },
            onOptionSelected = { selectedSource ->
                actions.onSetDefaultSource(selectedSource.id)
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
        SourcePrefixSettingItem(
            source = source,
            actions = actions,
            onEdit = { onRequestEditSource(source) },
            onDelete = { onRequestDeleteSource(source.id) }
        )
    }
}

@Composable
private fun SourcePrefixSettingItem(
    source: SearchSource,
    actions: SettingsSourceActions,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sourceColor = hexToColorOr(source.accentColorHex, Color.Unspecified)
    val owner = SourcePrefixOwner(source)

    PrefixOwnerSettingItem(
        owner = owner,
        icon = Icons.Filled.Search,
        accentColor = sourceColor,
        onAddPrefix = { prefix, onResult ->
            actions.prefixes.onAddPrefix(source.id, prefix, onResult)
        },
        onRemovePrefix = { prefix ->
            actions.prefixes.onRemovePrefix(source.id, prefix)
        },
        onReset = { actions.prefixes.onResetPrefixes(source.id) },
        extraContent = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit source"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete source",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.small))

                SwitchSettingItem(
                    title = "Prefix search",
                    subtitle = "Enable searching by prefix (e.g. 'yt query')",
                    checked = source.isEnabled,
                    onCheckedChange = { actions.onSetSourceEnabled(source.id, it) }
                )

                Spacer(modifier = Modifier.height(Spacing.small))

                SwitchSettingItem(
                    title = "Suggested action",
                    subtitle = "Show this source as a quick action chip",
                    checked = source.showAsSuggestedAction,
                    onCheckedChange = { actions.onSetSourceSuggestedAction(source.id, it) }
                )

                Spacer(modifier = Modifier.height(Spacing.small))
            }
        }
    )
}

@Composable
internal fun LocalPrefixesSection(
    settings: LauncherSettings,
    actions: SettingsPrefixActions
) {
    SettingsCategory(title = "Local Prefixes")

    Text(
        text = "Customize local provider prefixes.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = Spacing.mediumLarge,
            vertical = Spacing.small
        )
    )

    LocalProviderPrefixItem(
        name = "Contacts",
        icon = Icons.Default.Person,
        color = MaterialTheme.colorScheme.secondary,
        providerId = ProviderId.CONTACTS,
        settings = settings,
        actions = actions
    )

    LocalProviderPrefixItem(
        name = "Files",
        icon = Icons.AutoMirrored.Filled.InsertDriveFile,
        color = MaterialTheme.colorScheme.primaryContainer,
        providerId = ProviderId.FILES,
        settings = settings,
        actions = actions
    )
}

@Composable
private fun LocalProviderPrefixItem(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    providerId: String,
    settings: LauncherSettings,
    actions: SettingsPrefixActions
) {
    val defaultPrefixes = PrefixConfig.defaults[providerId]?.prefixes.orEmpty()
    val currentPrefixes = settings.prefixConfigurations[providerId]?.prefixes ?: defaultPrefixes

    val owner = object : com.milki.launcher.domain.model.PrefixOwner {
        override val id: String = providerId
        override val name: String = name
        override val prefixes: List<String> = currentPrefixes
        override val defaultPrefixes: List<String> = defaultPrefixes
    }

    PrefixOwnerSettingItem(
        owner = owner,
        icon = icon,
        accentColor = color,
        onAddPrefix = { prefix, onResult ->
            actions.onAddPrefix(providerId, prefix, onResult)
        },
        onRemovePrefix = { prefix ->
            actions.onRemovePrefix(providerId, prefix)
        },
        onReset = { actions.onResetPrefixes(providerId) }
    )
}

@Composable
internal fun SupportSection(actions: SettingsSupportActions) {
    SettingsCategory(title = "Support Me")

    Text(
        text = "Support development and ongoing maintenance.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = Spacing.mediumLarge,
            vertical = Spacing.small
        )
    )

    ActionSettingItem(
        title = "Ko-fi",
        subtitle = "Donate on ko-fi.com/milkilabs",
        onClick = { actions.onOpenSupportLink(KO_FI_SUPPORT_URL) },
        icon = Icons.Filled.Favorite
    )

    ActionSettingItem(
        title = "Liberapay",
        subtitle = "Donate on liberapay.com/Muhammadatef",
        onClick = { actions.onOpenSupportLink(LIBERAPAY_SUPPORT_URL) },
        icon = Icons.Filled.Favorite
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

private const val KO_FI_SUPPORT_URL = "https://ko-fi.com/milkilabs"
private const val LIBERAPAY_SUPPORT_URL = "https://liberapay.com/Muhammadatef/donate"
