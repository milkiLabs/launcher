package com.milki.launcher.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.milki.launcher.R
import com.milki.launcher.ui.components.settings.ActionSettingItem
import com.milki.launcher.ui.components.settings.GitHubIcon
import com.milki.launcher.ui.components.settings.SettingsCardSurface
import com.milki.launcher.ui.components.settings.SettingsCategory
import com.milki.launcher.ui.components.settings.XIcon
import com.milki.launcher.ui.theme.Spacing

@Composable
internal fun AboutSection(
    appVersion: String,
    onOpenLink: (String) -> Unit
) {
    SettingsCategory(title = stringResource(R.string.settings_group_about_title))

    SettingsCardSurface {
        Column(
            modifier = Modifier.padding(
                horizontal = Spacing.mediumLarge,
                vertical = Spacing.medium
            )
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.about_version, appVersion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Text(
        text = stringResource(R.string.about_app_tagline),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = Spacing.mediumLarge,
            vertical = Spacing.small
        )
    )

    ActionSettingItem(
        title = stringResource(R.string.about_github_title),
        subtitle = stringResource(R.string.about_github_subtitle),
        onClick = { onOpenLink("https://github.com/milkilabs/launcher") },
        icon = GitHubIcon
    )

    ActionSettingItem(
        title = stringResource(R.string.about_x_title),
        subtitle = stringResource(R.string.about_x_subtitle),
        onClick = { onOpenLink("https://x.com/milkilabs") },
        icon = XIcon
    )

    ActionSettingItem(
        title = stringResource(R.string.about_website_title),
        subtitle = stringResource(R.string.about_website_subtitle),
        onClick = { onOpenLink("https://milkilabs.github.io/launcher") },
        icon = Icons.Filled.Language
    )
}
