package com.milki.launcher.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    SettingsCategory(title = "About")

    SettingsCardSurface {
        Column(
            modifier = Modifier.padding(
                horizontal = Spacing.mediumLarge,
                vertical = Spacing.medium
            )
        ) {
            Text(
                text = "Milki Launcher",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Version $appVersion",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Text(
        text = "A fast, keyboard-first productivity launcher for Android.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = Spacing.mediumLarge,
            vertical = Spacing.small
        )
    )

    ActionSettingItem(
        title = "GitHub",
        subtitle = "github.com/milkilabs/launcher",
        onClick = { onOpenLink("https://github.com/milkilabs/launcher") },
        icon = GitHubIcon
    )

    ActionSettingItem(
        title = "X",
        subtitle = "x.com/milkilabs",
        onClick = { onOpenLink("https://x.com/milkilabs") },
        icon = XIcon
    )

    ActionSettingItem(
        title = "Website",
        subtitle = "milkilabs.github.io/launcher",
        onClick = { onOpenLink("https://milkilabs.github.io/launcher") },
        icon = Icons.Filled.Language
    )
}
