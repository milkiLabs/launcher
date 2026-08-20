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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import com.milki.launcher.ui.components.settings.ActionSettingItem
import com.milki.launcher.ui.theme.Spacing

/**
 * Root page that lists the settings groups and quick actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsIndexScreen(
    showSetDefaultLauncherOption: Boolean,
    onOpenDefaultLauncherSettings: () -> Unit,
    onOpenHomeScreen: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            if (showSetDefaultLauncherOption) {
                ActionSettingItem(
                    title = "Set as default launcher",
                    subtitle = "Open Android Home app settings to set Milki Launcher as default",
                    onClick = onOpenDefaultLauncherSettings,
                    icon = Icons.Default.Home
                )
            }

            SettingsGroupItem(
                title = "Home Screen",
                subtitle = "Home button and gesture actions",
                icon = Icons.Default.Home,
                onClick = onOpenHomeScreen
            )

            SettingsGroupItem(
                title = "Search",
                subtitle = "Layout, sources, prefixes, and file types",
                icon = Icons.Default.Search,
                onClick = onOpenSearch
            )

            SettingsGroupItem(
                title = "Advanced",
                subtitle = "Backup, import, and reset",
                icon = Icons.Default.Settings,
                onClick = onOpenAdvanced
            )

            SettingsGroupItem(
                title = "About",
                subtitle = "Version and links",
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
