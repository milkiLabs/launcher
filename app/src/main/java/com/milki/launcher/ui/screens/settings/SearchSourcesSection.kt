package com.milki.launcher.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.model.SourcePrefixOwner
import com.milki.launcher.ui.components.settings.ActionSettingItem
import com.milki.launcher.ui.components.settings.DropdownSettingItem
import com.milki.launcher.ui.components.settings.PrefixOwnerSettingItem
import com.milki.launcher.ui.components.settings.SettingsCategory
import com.milki.launcher.ui.components.settings.SwitchSettingItem
import com.milki.launcher.ui.theme.Spacing

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
