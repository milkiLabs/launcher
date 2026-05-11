package com.milki.launcher.ui.screens.launcher

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milki.launcher.core.url.UrlDestinationValidationResult
import com.milki.launcher.core.url.UrlValidator
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.common.AppIcon
import com.milki.launcher.ui.components.launcher.PinnedItemView
import com.milki.launcher.ui.components.search.UnifiedSearchInputField
import com.milki.launcher.ui.interaction.dragdrop.startExternalActionShortcutDrag
import com.milki.launcher.ui.interaction.grid.GridConfig
import com.milki.launcher.ui.interaction.grid.detectDragGesture
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionShortcutManagerSheet(
    shortcuts: List<HomeItem.ActionShortcut>,
    installedApps: List<AppInfo>,
    onSaveShortcut: (HomeItem.ActionShortcut, (Boolean) -> Unit) -> Unit,
    onDeleteShortcut: (HomeItem.ActionShortcut) -> Unit,
    onExternalDragStarted: () -> Unit,
    headerDragHandleModifier: Modifier = Modifier
) {
    var editingShortcut by remember { mutableStateOf<HomeItem.ActionShortcut?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    BackHandler(enabled = isCreating || editingShortcut != null) {
        isCreating = false
        editingShortcut = null
    }

    val shortcutForEditor = editingShortcut
    if (isCreating || shortcutForEditor != null) {
        ActionShortcutEditor(
            installedApps = installedApps,
            existingShortcut = shortcutForEditor,
            onBack = {
                isCreating = false
                editingShortcut = null
            },
            onSave = { shortcut, onResult ->
                onSaveShortcut(shortcut) { success ->
                    if (success) {
                        isCreating = false
                        editingShortcut = null
                    }
                    onResult(success)
                }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = headerDragHandleModifier,
                title = {
                    Text(
                        text = "Shortcuts",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(onClick = { isCreating = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add shortcut"
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
                Button(onClick = { isCreating = true }) {
                    Text("Add shortcut")
                }
            }
            return@Scaffold
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
                    onClick = { editingShortcut = shortcut },
                    onDelete = { onDeleteShortcut(shortcut) },
                    onExternalDragStarted = onExternalDragStarted
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
                .size(88.dp)
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
                            dragShadowSize = IconSize.appGrid
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
                size = IconSize.appGrid
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
                modifier = Modifier.size(16.dp)
            )
            Text("Delete")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionShortcutEditor(
    installedApps: List<AppInfo>,
    existingShortcut: HomeItem.ActionShortcut?,
    onBack: () -> Unit,
    onSave: (HomeItem.ActionShortcut, (Boolean) -> Unit) -> Unit
) {
    var label by remember(existingShortcut?.id) {
        mutableStateOf(existingShortcut?.label.orEmpty())
    }
    var destination by remember(existingShortcut?.id) {
        mutableStateOf(existingShortcut?.destinationUri.orEmpty())
    }
    var selectedApp by remember(existingShortcut?.id, installedApps) {
        mutableStateOf(
            existingShortcut?.packageName?.let { packageName ->
                installedApps.firstOrNull { it.packageName == packageName }
                    ?: AppInfo(
                        name = existingShortcut.packageLabel ?: packageName,
                        packageName = packageName,
                        activityName = ""
                    )
            }
        )
    }
    var choosingApp by remember { mutableStateOf(false) }
    val validationResult = remember(destination) { UrlValidator.validateUrlOrUri(destination) }
    var showDuplicateError by remember { mutableStateOf(false) }
    val validationMessage = remember(destination, validationResult) {
        validateActionShortcutDestination(destination, validationResult)
    }

    androidx.compose.runtime.LaunchedEffect(destination, selectedApp?.packageName) {
        showDuplicateError = false
    }

    if (choosingApp) {
        ActionShortcutAppPicker(
            installedApps = installedApps,
            selectedPackageName = selectedApp?.packageName,
            onBack = { choosingApp = false },
            onAppSelected = { app ->
                selectedApp = app
                choosingApp = false
            },
            onClearApp = {
                selectedApp = null
                choosingApp = false
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (existingShortcut == null) "Add shortcut" else "Edit shortcut",
                        fontWeight = FontWeight.SemiBold
                    )
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
                onChooseApp = { choosingApp = true },
                onClearApp = { selectedApp = null }
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Choose app",
                        fontWeight = FontWeight.SemiBold
                    )
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
                    ActionShortcutAppRow(
                        app = app,
                        selected = app.packageName == selectedPackageName,
                        onClick = { onAppSelected(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionShortcutAppRow(
    app: AppInfo,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = app.packageName,
                size = IconSize.appList
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.medium)
            ) {
                Text(
                    text = app.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
