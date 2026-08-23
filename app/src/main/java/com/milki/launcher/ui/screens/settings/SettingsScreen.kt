/**
 * SettingsIndexScreen.kt - Root settings page listing the setting groups.
 *
 * Each group opens its own page (see SettingsPages.kt) through the
 * Navigation 3 graph hosted in SettingsNav.kt.
 */

package com.milki.launcher.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.milki.launcher.R
import com.milki.launcher.ui.components.common.LauncherScreenScaffold
import com.milki.launcher.ui.components.settings.ActionSettingItem
import com.milki.launcher.ui.theme.Spacing

/**
 * Root page that lists the settings groups and quick actions.
 */
@Composable
internal fun SettingsIndexScreen(
    showSetDefaultLauncherOption: Boolean,
    onOpenDefaultLauncherSettings: () -> Unit,
    onOpenHomeScreen: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenAbout: () -> Unit
) {
    LauncherScreenScaffold(title = stringResource(R.string.settings_title)) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            if (showSetDefaultLauncherOption) {
                ActionSettingItem(
                    title = stringResource(R.string.settings_default_launcher_title),
                    subtitle = stringResource(R.string.settings_default_launcher_subtitle),
                    onClick = onOpenDefaultLauncherSettings,
                    icon = Icons.Default.Home
                )
            }

            SettingsGroupItem(
                title = stringResource(R.string.settings_group_home_title),
                subtitle = stringResource(R.string.settings_group_home_subtitle),
                icon = Icons.Default.Home,
                onClick = onOpenHomeScreen
            )

            SettingsGroupItem(
                title = stringResource(R.string.settings_group_search_title),
                subtitle = stringResource(R.string.settings_group_search_subtitle),
                icon = Icons.Default.Search,
                onClick = onOpenSearch
            )

            SettingsGroupItem(
                title = stringResource(R.string.settings_group_advanced_title),
                subtitle = stringResource(R.string.settings_group_advanced_subtitle),
                icon = Icons.Default.Settings,
                onClick = onOpenAdvanced
            )

            SettingsGroupItem(
                title = stringResource(R.string.settings_group_about_title),
                subtitle = stringResource(R.string.settings_group_about_subtitle),
                icon = Icons.Default.Info,
                onClick = onOpenAbout
            )

            Spacer(modifier = Modifier.height(Spacing.extraLarge))
        }
    }
}

/**
 * Clickable card representing a settings group that opens its own page.
 */
@Composable
private fun SettingsGroupItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ActionSettingItem(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        icon = icon,
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
