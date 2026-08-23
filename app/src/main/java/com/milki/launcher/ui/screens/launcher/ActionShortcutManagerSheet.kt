package com.milki.launcher.ui.screens.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import com.milki.launcher.core.url.UrlDestinationValidationResult
import com.milki.launcher.core.url.UrlValidator
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.common.AppIcon
import com.milki.launcher.ui.components.common.LauncherScreenScaffold
import com.milki.launcher.ui.components.common.SelectableAppRow
import com.milki.launcher.ui.components.search.UnifiedSearchInputField
import com.milki.launcher.ui.interaction.dragdrop.startExternalActionShortcutDrag
import com.milki.launcher.ui.interaction.grid.GridConfig
import com.milki.launcher.ui.interaction.grid.detectDragGesture
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing
import kotlinx.serialization.Serializable

/** Touch target for one action-shortcut tile in the library grid. */
private val ShortcutTileSize: Dp = 88.dp

@Serializable
private sealed interface ActionShortcutManagerRoute : NavKey {
    @Serializable
    data object Library : ActionShortcutManagerRoute

    @Serializable
    data class Editor(val shortcutId: String?) : ActionShortcutManagerRoute

    @Serializable
    data object AppPicker : ActionShortcutManagerRoute
}

@Composable
internal fun ActionShortcutManagerSheet(
    shortcuts: List<HomeItem.ActionShortcut>,
    installedApps: List<AppInfo>,
    onSaveShortcut: (HomeItem.ActionShortcut, (Boolean) -> Unit) -> Unit,
    onDeleteShortcut: (HomeItem.ActionShortcut) -> Unit,
    onDismissRequest: () -> Unit,
    onExternalDragStarted: () -> Unit,
    headerDragHandleModifier: Modifier = Modifier
) {
    val backStack = remember {
        mutableStateListOf<ActionShortcutManagerRoute>(
            ActionShortcutManagerRoute.Library
        )
    }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }

    fun appFromShortcut(shortcut: HomeItem.ActionShortcut?): AppInfo? {
        return shortcut?.packageName?.let { packageName ->
            installedApps.firstOrNull { it.packageName == packageName }
                ?: AppInfo(
                    name = shortcut.packageLabel ?: packageName,
                    packageName = packageName,
                    activityName = ""
                )
        }
    }

    fun popToLibrary() {
        while (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
        selectedApp = null
    }

    fun navigateToEditor(shortcut: HomeItem.ActionShortcut?) {
        selectedApp = appFromShortcut(shortcut)
        backStack.add(ActionShortcutManagerRoute.Editor(shortcut?.id))
    }

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            } else {
                onDismissRequest()
            }
        },
        entryProvider = { route ->
            when (route) {
                ActionShortcutManagerRoute.Library -> NavEntry(route) {
                    ActionShortcutLibrary(
                        shortcuts = shortcuts,
                        onCreateShortcut = {
                            navigateToEditor(null)
                        },
                        onEditShortcut = { shortcut ->
                            navigateToEditor(shortcut)
                        },
                        onDeleteShortcut = onDeleteShortcut,
                        onExternalDragStarted = onExternalDragStarted,
                        headerDragHandleModifier = headerDragHandleModifier
                    )
                }

                is ActionShortcutManagerRoute.Editor -> NavEntry(route) {
                    val existingShortcut = route.shortcutId?.let { shortcutId ->
                        shortcuts.firstOrNull { it.id == shortcutId }
                    }

                    if (route.shortcutId != null && existingShortcut == null) {
                        LaunchedEffect(route.shortcutId) {
                            popToLibrary()
                        }
                        return@NavEntry
                    }

                    ActionShortcutEditor(
                        existingShortcut = existingShortcut,
                        selectedApp = selectedApp,
                        onSelectedAppChange = { selectedApp = it },
                        onChooseApp = {
                            backStack.add(ActionShortcutManagerRoute.AppPicker)
                        },
                        onBack = { popToLibrary() },
                        onSave = { shortcut, onResult ->
                            onSaveShortcut(shortcut) { success ->
                                if (success) {
                                    popToLibrary()
                                }
                                onResult(success)
                            }
                        }
                    )
                }

                ActionShortcutManagerRoute.AppPicker -> NavEntry(route) {
                    ActionShortcutAppPicker(
                        installedApps = installedApps,
                        selectedPackageName = selectedApp?.packageName,
                        onBack = { backStack.removeLastOrNull() },
                        onAppSelected = { app ->
                            selectedApp = app
                            backStack.removeLastOrNull()
                        },
                        onClearApp = {
                            selectedApp = null
                            backStack.removeLastOrNull()
                        }
                    )
                }

            }
        }
    )
}

@Composable
private fun ActionShortcutLibrary(
    shortcuts: List<HomeItem.ActionShortcut>,
    onCreateShortcut: () -> Unit,
    onEditShortcut: (HomeItem.ActionShortcut) -> Unit,
    onDeleteShortcut: (HomeItem.ActionShortcut) -> Unit,
    onExternalDragStarted: () -> Unit,
    headerDragHandleModifier: Modifier
) {
    LauncherScreenScaffold(
        title = "Shortcuts",
        topAppBarModifier = headerDragHandleModifier,
        actions = {
            IconButton(onClick = onCreateShortcut) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add shortcut"
                )
            }
        }
    ) { paddingValues ->
        if (shortcuts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(Spacing.mediumLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No shortcuts yet",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(Spacing.small))
                Button(onClick = onCreateShortcut) {
                    Text("Add shortcut")
                }
                Spacer(modifier = Modifier.height(Spacing.medium))
                Text(
                    text = "Long press and drag a shortcut to add it to your home screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            return@LauncherScreenScaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Spacing.mediumLarge),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            items(
                items = shortcuts,
                key = { it.id }
            ) { shortcut ->
                ActionShortcutGridItem(
                    shortcut = shortcut,
                    onClick = { onEditShortcut(shortcut) },
                    onDelete = { onDeleteShortcut(shortcut) },
                    onExternalDragStarted = onExternalDragStarted
                )
            }
            item {
                Spacer(modifier = Modifier.height(Spacing.medium))
                Text(
                    text = "Long press and drag a shortcut to add it to your home screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.mediumLarge),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ActionShortcutGridItem(
    shortcut: HomeItem.ActionShortcut,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onExternalDragStarted: () -> Unit
) {
    val hostView = LocalView.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(ShortcutTileSize)
                .detectDragGesture(
                    key = shortcut.id,
                    dragThreshold = GridConfig.Default.dragThresholdPx,
                    onTap = onClick,
                    onLongPress = {},
                    onLongPressRelease = {},
                    onDragStart = {
                        val started = startExternalActionShortcutDrag(
                            hostView = hostView,
                            shortcut = shortcut,
                            dragShadowSize = IconSize.appHomeCompact
                        )
                        if (started) {
                            hostView.post(onExternalDragStarted)
                        }
                    },
                    onDrag = { change, _ -> change.consume() },
                    onDragEnd = {},
                    onDragCancel = {}
                ),
            contentAlignment = Alignment.Center
        ) {
            com.milki.launcher.ui.components.launcher.ActionShortcutIcon(
                shortcut = shortcut,
                size = IconSize.appHomeCompact
            )
        }

        Text(
            text = shortcut.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        TextButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(IconSize.extraSmall)
            )
            Text("Delete")
        }
    }
}

@Composable
private fun ActionShortcutEditor(
    existingShortcut: HomeItem.ActionShortcut?,
    selectedApp: AppInfo?,
    onSelectedAppChange: (AppInfo?) -> Unit,
    onChooseApp: () -> Unit,
    onBack: () -> Unit,
    onSave: (HomeItem.ActionShortcut, (Boolean) -> Unit) -> Unit
) {
    var label by rememberSaveable(existingShortcut?.id) {
        mutableStateOf(existingShortcut?.label.orEmpty())
    }
    var destination by rememberSaveable(existingShortcut?.id) {
        mutableStateOf(existingShortcut?.destinationUri.orEmpty())
    }
    val validationResult = remember(destination) { UrlValidator.validateUrlOrUri(destination) }
    var showDuplicateError by rememberSaveable(existingShortcut?.id) {
        mutableStateOf(false)
    }
    val validationMessage = remember(destination, validationResult) {
        validateActionShortcutDestination(destination, validationResult)
    }

    LaunchedEffect(destination, selectedApp?.packageName) {
        showDuplicateError = false
    }

    LauncherScreenScaffold(
        title = if (existingShortcut == null) "Add shortcut" else "Edit shortcut",
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Spacing.mediumLarge),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Spacer(modifier = Modifier.height(Spacing.small))
            TextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Shortcut name") },
                placeholder = { Text("WhatsApp chat, Facebook profile, website") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text("Destination URI") },
                placeholder = { Text("https://example.com or whatsapp://send?phone=...") },
                isError = validationMessage != null || showDuplicateError,
                supportingText = {
                    if (showDuplicateError) {
                        Text("A shortcut with this destination and app already exists.")
                    } else {
                        Text(validationMessage ?: "Any Android deep link or web URL can be used.")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            ActionShortcutAppSelector(
                selectedApp = selectedApp,
                onChooseApp = onChooseApp,
                onClearApp = { onSelectedAppChange(null) }
            )
            Button(
                onClick = {
                    val app = selectedApp
                    val shortcut = existingShortcut?.copy(
                        label = label.trim().ifBlank { "Shortcut" },
                        destinationUri = validationResult?.uri.orEmpty(),
                        packageName = app?.packageName,
                        packageLabel = app?.name
                    ) ?: HomeItem.ActionShortcut.create(
                        label = label,
                        destinationUri = validationResult?.uri.orEmpty(),
                        packageName = app?.packageName,
                        packageLabel = app?.name
                    )
                    onSave(shortcut) { success ->
                        if (!success) {
                            showDuplicateError = true
                        }
                    }
                },
                enabled = validationMessage == null && validationResult != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save shortcut")
            }
        }
    }
}

@Composable
private fun ActionShortcutAppSelector(
    selectedApp: AppInfo?,
    onChooseApp: () -> Unit,
    onClearApp: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedApp != null) {
                AppIcon(
                    packageName = selectedApp.packageName,
                    size = IconSize.appList
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.appList)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.medium)
            ) {
                Text(
                    text = selectedApp?.name ?: "Open with any matching app",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = selectedApp?.packageName
                        ?: "Choose an app only when the destination should be forced there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TextButton(onClick = onChooseApp) {
                Text("Choose")
            }
            if (selectedApp != null) {
                TextButton(onClick = onClearApp) {
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun ActionShortcutAppPicker(
    installedApps: List<AppInfo>,
    selectedPackageName: String?,
    onBack: () -> Unit,
    onAppSelected: (AppInfo) -> Unit,
    onClearApp: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase()
    val visibleApps = remember(installedApps, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter { app ->
                app.name.lowercase().contains(normalizedQuery) ||
                    app.packageName.lowercase().contains(normalizedQuery)
            }
        }
    }

    LauncherScreenScaffold(
        title = "Choose app",
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Spacing.mediumLarge),
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            UnifiedSearchInputField(
                query = query,
                onQueryChange = { query = it },
                placeholderText = "Search apps",
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = onClearApp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open with any matching app")
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
            ) {
                items(
                    items = visibleApps,
                    key = { "${it.packageName}/${it.activityName}" }
                ) { app ->
                    SelectableAppRow(
                        app = app,
                        selected = app.packageName == selectedPackageName,
                        onClick = { onAppSelected(app) }
                    )
                }
            }
        }
    }
}

private fun validateActionShortcutDestination(
    destination: String,
    validationResult: UrlDestinationValidationResult?
): String? {
    if (destination.isBlank()) return "Destination is required."
    if (validationResult == null) return "Enter a valid web URL or Android URI."
    return null
}
