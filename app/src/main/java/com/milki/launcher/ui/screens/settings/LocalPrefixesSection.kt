package com.milki.launcher.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.milki.launcher.R
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.PrefixConfig
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.ProviderPrefixOwner
import com.milki.launcher.ui.components.search.SearchProviderVisual
import com.milki.launcher.ui.components.search.rememberSearchProviderVisual
import com.milki.launcher.ui.components.settings.PrefixOwnerSettingItem
import com.milki.launcher.ui.components.settings.SettingsCategory
import com.milki.launcher.ui.theme.Spacing

@Composable
internal fun LocalPrefixesSection(
    settings: LauncherSettings,
    actions: SettingsPrefixActions
) {
    SettingsCategory(title = stringResource(R.string.local_prefixes_section_title))

    Text(
        text = stringResource(R.string.local_prefixes_section_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = Spacing.mediumLarge,
            vertical = Spacing.small
        )
    )

    LocalProviderPrefixItem(
        name = stringResource(R.string.provider_contacts_name),
        providerId = ProviderId.CONTACTS,
        settings = settings,
        actions = actions
    )

    LocalProviderPrefixItem(
        name = stringResource(R.string.provider_files_name),
        providerId = ProviderId.FILES,
        settings = settings,
        actions = actions
    )
}

@Composable
private fun LocalProviderPrefixItem(
    name: String,
    providerId: String,
    settings: LauncherSettings,
    actions: SettingsPrefixActions
) {
    val defaultPrefixes = PrefixConfig.defaults[providerId]?.prefixes.orEmpty()
    val currentPrefixes = settings.prefixConfigurations[providerId]?.prefixes ?: defaultPrefixes
    val visual = rememberSearchProviderVisual(providerId)
        ?: SearchProviderVisual(
            icon = Icons.Filled.Search,
            accentColor = MaterialTheme.colorScheme.primary
        )

    val owner = ProviderPrefixOwner(
        id = providerId,
        name = name,
        prefixes = currentPrefixes,
        defaultPrefixes = defaultPrefixes
    )

    PrefixOwnerSettingItem(
        owner = owner,
        icon = visual.icon,
        accentColor = visual.accentColor,
        onAddPrefix = { prefix, onResult ->
            actions.onAddPrefix(providerId, prefix, onResult)
        },
        onRemovePrefix = { prefix ->
            actions.onRemovePrefix(providerId, prefix)
        },
        onReset = { actions.onResetPrefixes(providerId) }
    )
}
