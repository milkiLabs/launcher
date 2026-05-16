package com.milki.launcher.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerTarget
import com.milki.launcher.ui.components.common.AppIcon
import com.milki.launcher.ui.components.common.ShortcutIcon
import com.milki.launcher.ui.components.common.getAppQuickActions
import com.milki.launcher.ui.components.launcher.ActionShortcutIcon
import com.milki.launcher.ui.components.search.UnifiedSearchInputField
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * Full-screen target picker used when a homescreen gesture launches an app,
 * dynamic shortcut, or user-created action shortcut.
 */
@Composable
internal fun TriggerActionShortcutPickerScreen(
    trigger: LauncherTrigger,
    actionShortcuts: List<HomeItem.ActionShortcut>,
    currentTarget: LauncherTriggerTarget?,
    onBack: () -> Unit,
    onTargetSelected: (LauncherTriggerTarget) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredShortcuts = remember(actionShortcuts, query) {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            actionShortcuts
        } else {
            actionShortcuts.filter { shortcut ->
                shortcut.label.lowercase().contains(normalizedQuery)
            }
        }
    }

    TriggerTargetPickerScaffold(
        title = "Choose shortcut",
        triggerDisplayName = trigger.displayName,
        query = query,
        onQueryChange = { query = it },
        placeholderText = "Search shortcuts",
        onBack = onBack,
        isEmpty = filteredShortcuts.isEmpty(),
        emptyState = { TriggerTargetPickerEmptyState() }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
        ) {
            items(
                items = filteredShortcuts,
                key = { it.id }
            ) { shortcut ->
                TriggerTargetRow(
                    title = shortcut.label,
                    subtitle = shortcut.destinationUri,
                    selected = currentTarget is LauncherTriggerTarget.ActionShortcut &&
                        currentTarget.id == shortcut.id,
                    leadingContent = {
                        ActionShortcutIcon(
                            shortcut = shortcut,
                            size = IconSize.appList
                        )
                    },
                    onClick = {
                        onTargetSelected(shortcut.toTriggerTarget())
                    }
                )
            }
        }
    }
}

@Composable
internal fun TriggerAppPickerScreen(
    trigger: LauncherTrigger,
    installedApps: List<AppInfo>,
    currentTarget: LauncherTriggerTarget?,
    onBack: () -> Unit,
    onTargetSelected: (LauncherTriggerTarget) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredApps = remember(installedApps, query) {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter { app ->
                app.nameLower.contains(normalizedQuery) ||
                    app.packageLower.contains(normalizedQuery)
            }
        }
    }

    TriggerTargetPickerScaffold(
        title = "Choose app",
        triggerDisplayName = trigger.displayName,
        query = query,
        onQueryChange = { query = it },
        placeholderText = "Search apps and shortcuts",
        onBack = onBack,
        isEmpty = filteredApps.isEmpty(),
        emptyState = { TriggerTargetPickerEmptyState() }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
        ) {
            items(
                items = filteredApps,
                key = { app -> "${app.packageName}/${app.activityName}" }
            ) { app ->
                TriggerAppTargetGroup(
                    app = app,
                    query = query,
                    currentTarget = currentTarget,
                    onTargetSelected = onTargetSelected
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerTargetPickerScaffold(
    title: String,
    triggerDisplayName: String,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholderText: String,
    onBack: () -> Unit,
    isEmpty: Boolean,
    emptyState: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = triggerDisplayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
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
                .padding(horizontal = Spacing.mediumLarge)
        ) {
            UnifiedSearchInputField(
                query = query,
                onQueryChange = onQueryChange,
                placeholderText = placeholderText,
                onClear = { onQueryChange("") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.medium)
            )

            if (isEmpty) {
                emptyState()
            } else {
                content()
            }
        }
    }
}

@Composable
private fun TriggerAppTargetGroup(
    app: AppInfo,
    query: String,
    currentTarget: LauncherTriggerTarget?,
    onTargetSelected: (LauncherTriggerTarget) -> Unit
) {
    val quickShortcuts = getAppQuickActions(
        packageName = app.packageName,
        maxCount = 8
    )
    val normalizedQuery = query.trim().lowercase()
    val visibleShortcuts = remember(quickShortcuts, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            quickShortcuts
        } else {
            quickShortcuts.filter { shortcut ->
                shortcut.shortLabel.lowercase().contains(normalizedQuery) ||
                    shortcut.longLabel.lowercase().contains(normalizedQuery)
            }
        }
    }

    Column {
        TriggerAppTargetRow(
            app = app,
            currentTarget = currentTarget,
            onTargetSelected = onTargetSelected
        )

        visibleShortcuts.forEach { shortcut ->
            TriggerAppShortcutTargetRow(
                appName = app.name,
                shortcut = shortcut,
                currentTarget = currentTarget,
                onTargetSelected = onTargetSelected
            )
        }
    }
}

@Composable
private fun TriggerAppTargetRow(
    app: AppInfo,
    currentTarget: LauncherTriggerTarget?,
    onTargetSelected: (LauncherTriggerTarget) -> Unit
) {
    TriggerTargetRow(
        title = app.name,
        subtitle = app.packageName,
        selected = currentTarget is LauncherTriggerTarget.App &&
            currentTarget.packageName == app.packageName &&
            currentTarget.activityName == app.activityName,
        leadingContent = {
            AppIcon(
                packageName = app.packageName,
                size = IconSize.appList
            )
        },
        onClick = {
            onTargetSelected(
                LauncherTriggerTarget.App(
                    packageName = app.packageName,
                    activityName = app.activityName,
                    displayName = app.name
                )
            )
        }
    )
}

@Composable
private fun TriggerAppShortcutTargetRow(
    appName: String,
    shortcut: HomeItem.AppShortcut,
    currentTarget: LauncherTriggerTarget?,
    onTargetSelected: (LauncherTriggerTarget) -> Unit
) {
    TriggerTargetRow(
        title = shortcut.shortLabel.ifBlank { shortcut.longLabel },
        subtitle = "Shortcut in $appName",
        selected = currentTarget is LauncherTriggerTarget.AppShortcut &&
            currentTarget.packageName == shortcut.packageName &&
            currentTarget.shortcutId == shortcut.shortcutId,
        leadingContent = {
            ShortcutIcon(
                shortcut = shortcut,
                size = IconSize.appList,
                showBrowserBadge = true
            )
        },
        onClick = {
            onTargetSelected(shortcut.toTriggerTarget())
        },
        modifier = Modifier.padding(start = Spacing.mediumLarge)
    )
}

@Composable
private fun TriggerTargetRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    leadingContent: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.medium,
                vertical = Spacing.smallMedium
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingContent()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.medium)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (selected) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = Spacing.smallMedium)
                )
            }
        }
    }
}

@Composable
private fun TriggerTargetPickerEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Apps,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "No apps found",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.smallMedium)
        )
        Text(
            text = "Try a different search.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun HomeItem.ActionShortcut.toTriggerTarget(): LauncherTriggerTarget.ActionShortcut {
    return LauncherTriggerTarget.ActionShortcut(
        id = id,
        label = label,
        destinationUri = destinationUri,
        packageName = packageName,
        packageLabel = packageLabel
    )
}

private fun HomeItem.AppShortcut.toTriggerTarget(): LauncherTriggerTarget.AppShortcut {
    return LauncherTriggerTarget.AppShortcut(
        packageName = packageName,
        shortcutId = shortcutId,
        shortLabel = shortLabel,
        longLabel = longLabel
    )
}
