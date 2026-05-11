/**
 * SettingsScreen.kt - Main settings screen for the launcher
 *
 * Displays launcher settings organized into focused section composables.
 *
 * REFACTOR NOTE:
 * This file was decomposed into section-level composables and grouped action
 * contracts (`SettingsActions`) so each section receives only the callbacks it
 * actually needs. This improves readability and keeps change impact localized.
 */

package com.milki.launcher.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milki.launcher.core.url.UrlValidator
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.LauncherInteractionCatalog
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.LauncherTriggerTarget
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.model.UrlHandlerApp
import com.milki.launcher.domain.model.actionForTrigger
import com.milki.launcher.domain.model.backup.SkippedImportCategory
import com.milki.launcher.domain.model.backup.LauncherImportResult
import com.milki.launcher.domain.model.targetForTrigger
import com.milki.launcher.ui.components.common.AppIcon
import com.milki.launcher.ui.components.common.ShortcutIcon
import com.milki.launcher.ui.components.common.getAppQuickActions
import com.milki.launcher.ui.components.launcher.PinnedItemView
import com.milki.launcher.ui.components.settings.ActionSettingItem
import com.milki.launcher.ui.components.settings.DropdownSettingItem
import com.milki.launcher.ui.components.settings.PrefixSettingItem
import com.milki.launcher.ui.components.settings.SettingsCategory
import com.milki.launcher.ui.components.settings.SourceEditorDialog
import com.milki.launcher.ui.components.settings.SourceSettingItem
import com.milki.launcher.ui.components.search.UnifiedSearchInputField
import com.milki.launcher.ui.interaction.dragdrop.startExternalActionShortcutDrag
import com.milki.launcher.ui.interaction.grid.GridConfig
import com.milki.launcher.ui.interaction.grid.detectDragGesture
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * SettingsScreen - launcher settings page with grouped action contract.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    installedApps: List<AppInfo>,
    actionShortcuts: List<HomeItem.ActionShortcut>,
    showSetDefaultLauncherOption: Boolean,
    backupStatusMessage: String?,
    importReport: LauncherImportResult?,
    onDismissImportReport: () -> Unit,
    actions: SettingsActions
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Dialog state is kept at screen scope because multiple sections trigger it.
    var showResetDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<SearchSource?>(null) }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var sourceIdPendingDelete by remember { mutableStateOf<String?>(null) }
    var appPickerTrigger by remember { mutableStateOf<Pair<LauncherTrigger, LauncherTriggerAction>?>(null) }
    var showActionShortcutCreator by remember { mutableStateOf(false) }

    appPickerTrigger?.let { (trigger, action) ->
        if (action == LauncherTriggerAction.OPEN_ACTION_SHORTCUT) {
            TriggerActionShortcutPickerScreen(
                trigger = trigger,
                actionShortcuts = actionShortcuts,
                currentTarget = settings.targetForTrigger(trigger),
                onBack = { appPickerTrigger = null },
                onTargetSelected = { target ->
                    actions.homeScreen.onSetTriggerOpenAppTarget(trigger, target)
                    appPickerTrigger = null
                }
            )
        } else {
            TriggerAppPickerScreen(
                trigger = trigger,
                installedApps = installedApps,
                currentTarget = settings.targetForTrigger(trigger),
                onBack = { appPickerTrigger = null },
                onTargetSelected = { target ->
                    actions.homeScreen.onSetTriggerOpenAppTarget(trigger, target)
                    appPickerTrigger = null
                }
            )
        }
        return
    }

    if (showActionShortcutCreator) {
        ActionShortcutCreatorScreen(
            installedApps = installedApps,
            onBack = { showActionShortcutCreator = false },
            onResolveUrlHandler = actions.onResolveUrlHandler,
            onSaveShortcut = actions.onSaveActionShortcut,
            onExternalDragStarted = actions.onShortcutExternalDragStarted
        )
        return
    }

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
                    onClick = actions.onOpenDefaultLauncherSettings,
                    icon = Icons.Default.Home
                )
            }

            HomeScreenSection(
                settings = settings,
                actions = actions.homeScreen,
                onSelectOpenAppAction = { trigger, action -> appPickerTrigger = trigger to action },
                onCreateActionShortcut = { showActionShortcutCreator = true }
            )

            CustomSourcesSection(
                settings = settings,
                actions = actions.customSources,
                onRequestAddSource = { showAddSourceDialog = true },
                onRequestEditSource = { editingSource = it },
                onRequestDeleteSource = { sourceIdPendingDelete = it }
            )

            LocalPrefixesSection(
                settings = settings,
                actions = actions.localPrefixes
            )

            AdvancedSection(
                backupStatusMessage = backupStatusMessage,
                onRequestReset = { showResetDialog = true },
                onRequestExport = actions.advanced.onExportBackup,
                onRequestImport = actions.advanced.onImportBackup
            )

            Spacer(modifier = Modifier.height(Spacing.extraLarge))
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings") },
            text = { Text("This will restore all settings to their default values. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.advanced.onResetToDefaults()
                        showResetDialog = false
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddSourceDialog) {
        SourceEditorDialog(
            initialSource = null,
            onDismiss = { showAddSourceDialog = false },
            onConfirm = { name, urlTemplate, prefixes, accentColorHex, onValidationResult ->
                actions.customSources.onAddSearchSource(
                    name,
                    urlTemplate,
                    prefixes,
                    accentColorHex,
                    { validationMessage ->
                        onValidationResult(validationMessage)
                        if (validationMessage.isBlank()) {
                            showAddSourceDialog = false
                        }
                    }
                )
            }
        )
    }

    if (editingSource != null) {
        SourceEditorDialog(
            initialSource = editingSource,
            onDismiss = { editingSource = null },
            onConfirm = { name, urlTemplate, prefixes, accentColorHex, onValidationResult ->
                val sourceId = editingSource?.id ?: return@SourceEditorDialog
                actions.customSources.onUpdateSearchSource(
                    sourceId,
                    name,
                    urlTemplate,
                    prefixes,
                    accentColorHex,
                    { validationMessage ->
                        onValidationResult(validationMessage)
                        if (validationMessage.isBlank()) {
                            editingSource = null
                        }
                    }
                )
            }
        )
    }

    if (sourceIdPendingDelete != null) {
        AlertDialog(
            onDismissRequest = { sourceIdPendingDelete = null },
            title = { Text("Delete source") },
            text = { Text("Delete this source and all of its prefixes?") },
            confirmButton = {
                TextButton(onClick = {
                    val sourceId = sourceIdPendingDelete ?: return@TextButton
                    actions.customSources.onDeleteSearchSource(sourceId)
                    sourceIdPendingDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { sourceIdPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (importReport != null) {
        AlertDialog(
            onDismissRequest = onDismissImportReport,
            title = { Text("Import Report") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(importReport.message)
                    Spacer(modifier = Modifier.height(Spacing.small))
                    Text("Imported items: ${importReport.importedTopLevelCount}")
                    Text("Skipped items: ${importReport.skippedCount}")

                    if (importReport.skippedReasons.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.smallMedium))
                        val groupedReasons = importReport.skippedReasons.groupBy { it.category }
                        val orderedCategories = listOf(
                            SkippedImportCategory.APP,
                            SkippedImportCategory.FILE,
                            SkippedImportCategory.WIDGET,
                            SkippedImportCategory.SHORTCUT,
                            SkippedImportCategory.FOLDER,
                            SkippedImportCategory.OTHER
                        )

                        orderedCategories.forEach { category ->
                            val reasonsForCategory = groupedReasons[category].orEmpty()
                            if (reasonsForCategory.isEmpty()) return@forEach

                            Text(
                                text = category.toDisplayTitle(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            reasonsForCategory.forEach { reason ->
                                Text("- ${reason.message}")
                            }

                            Spacer(modifier = Modifier.height(Spacing.small))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissImportReport) {
                    Text("OK")
                }
            }
        )
    }
}

private fun SkippedImportCategory.toDisplayTitle(): String {
    return when (this) {
        SkippedImportCategory.APP -> "Apps"
        SkippedImportCategory.FILE -> "Files"
        SkippedImportCategory.WIDGET -> "Widgets"
        SkippedImportCategory.SHORTCUT -> "Shortcuts"
        SkippedImportCategory.FOLDER -> "Folders"
        SkippedImportCategory.OTHER -> "Other"
    }
}
/**
 * Section: Home Screen.
 */
@Composable
private fun HomeScreenSection(
    settings: LauncherSettings,
    actions: SettingsHomeScreenActions,
    onSelectOpenAppAction: (LauncherTrigger, LauncherTriggerAction) -> Unit,
    onCreateActionShortcut: () -> Unit
) {
    SettingsCategory(title = "Home Screen")

    ActionSettingItem(
        title = "Create action shortcut",
        subtitle = "Build a draggable shortcut for a URL, deep link, chat, profile, or app destination",
        onClick = onCreateActionShortcut,
        icon = Icons.Default.Link
    )

    LauncherInteractionCatalog.configurableTriggers.forEach { trigger ->
        val action = settings.actionForTrigger(trigger)
        val target = settings.targetForTrigger(trigger)
        DropdownSettingItem(
            title = trigger.displayName,
            subtitle = if (action == LauncherTriggerAction.OPEN_APP || action == LauncherTriggerAction.OPEN_ACTION_SHORTCUT) {
                target?.displayName ?: "Choose an app or shortcut"
            } else {
                null
            },
            selectedValue = action.displayName,
            options = LauncherInteractionCatalog.availableActions()
                .map { action -> action.displayName to action },
            onOptionSelected = { selectedAction ->
                if (selectedAction == LauncherTriggerAction.OPEN_APP || selectedAction == LauncherTriggerAction.OPEN_ACTION_SHORTCUT) {
                    onSelectOpenAppAction(trigger, selectedAction)
                } else {
                    actions.onSetTriggerAction(trigger, selectedAction)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionShortcutCreatorScreen(
    installedApps: List<AppInfo>,
    onBack: () -> Unit,
    onResolveUrlHandler: (String, (UrlHandlerApp?) -> Unit) -> Unit,
    onSaveShortcut: (HomeItem.ActionShortcut) -> Unit,
    onExternalDragStarted: () -> Unit
) {
    BackHandler(onBack = onBack)

    var label by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var createdShortcut by remember { mutableStateOf<HomeItem.ActionShortcut?>(null) }
    var choosingApp by remember { mutableStateOf(false) }
    var resolvedHandler by remember { mutableStateOf<UrlHandlerApp?>(null) }
    val validationResult = remember(destination) { UrlValidator.validateUrlOrUri(destination) }
    val validationMessage = remember(destination, validationResult) {
        validateActionShortcutDestination(destination, validationResult)
    }
    val targetLabel = selectedApp?.name ?: resolvedHandler?.label
    val targetPackage = selectedApp?.packageName ?: resolvedHandler?.packageName

    LaunchedEffect(validationResult?.uri, selectedApp?.packageName) {
        val uri = validationResult?.uri
        if (uri == null || selectedApp != null) {
            resolvedHandler = null
            return@LaunchedEffect
        }
        onResolveUrlHandler(uri) { handler ->
            resolvedHandler = handler
        }
    }

    if (choosingApp) {
        ActionShortcutAppPickerScreen(
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
                        text = "Create shortcut",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.mediumLarge),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Spacer(modifier = Modifier.height(Spacing.small))

            TextField(
                value = label,
                onValueChange = {
                    label = it
                    createdShortcut = null
                },
                label = { Text("Shortcut name") },
                placeholder = { Text("WhatsApp chat, Facebook profile, website") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            TextField(
                value = destination,
                onValueChange = {
                    destination = it
                    createdShortcut = null
                },
                label = { Text("Destination URI") },
                placeholder = { Text("https://example.com or whatsapp://send?phone=...") },
                isError = validationMessage != null,
                supportingText = {
                    Text(validationMessage ?: "Any Android deep link or web URL can be used.")
                },
                modifier = Modifier.fillMaxWidth()
            )

            ActionShortcutPresetRow(
                onPresetSelected = { preset ->
                    label = preset.label
                    destination = preset.destination
                    createdShortcut = null
                }
            )

            ActionShortcutAppSelector(
                selectedApp = selectedApp,
                onChooseApp = { choosingApp = true },
                onClearApp = {
                    selectedApp = null
                    createdShortcut = null
                }
            )

            Button(
                onClick = {
                    val app = selectedApp
                    val shortcut = HomeItem.ActionShortcut.create(
                        label = label,
                        destinationUri = validationResult?.uri.orEmpty(),
                        packageName = app?.packageName ?: resolvedHandler?.packageName,
                        packageLabel = app?.name ?: resolvedHandler?.label
                    )
                    createdShortcut = shortcut
                    onSaveShortcut(shortcut)
                },
                enabled = validationMessage == null && validationResult != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save shortcut")
            }

            ActionShortcutResolvedTargetText(
                targetLabel = targetLabel,
                targetPackage = targetPackage
            )

            createdShortcut?.let { shortcut ->
                ActionShortcutDragPreview(
                    shortcut = shortcut,
                    onExternalDragStarted = onExternalDragStarted
                )
            }

            Spacer(modifier = Modifier.height(Spacing.extraLarge))
        }
    }
}

@Composable
private fun ActionShortcutPresetRow(
    onPresetSelected: (ActionShortcutPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(
            text = "Templates",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            ActionShortcutPreset.Defaults.forEach { preset ->
                OutlinedButton(
                    onClick = { onPresetSelected(preset) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(preset.label)
                }
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
private fun ActionShortcutResolvedTargetText(
    targetLabel: String?,
    targetPackage: String?
) {
    if (targetLabel == null || targetPackage == null) return

    Text(
        text = "Icon and launch target: $targetLabel",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ActionShortcutDragPreview(
    shortcut: HomeItem.ActionShortcut,
    onExternalDragStarted: () -> Unit
) {
    val hostView = LocalView.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .detectDragGesture(
                        key = shortcut.id,
                        dragThreshold = GridConfig.Default.dragThresholdPx,
                        onTap = {},
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
                PinnedItemView(item = shortcut, compactLayout = false)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.medium)
            ) {
                Text(
                    text = "Drag the icon to the home screen",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = shortcut.destinationUri,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionShortcutAppPickerScreen(
    installedApps: List<AppInfo>,
    selectedPackageName: String?,
    onBack: () -> Unit,
    onAppSelected: (AppInfo) -> Unit,
    onClearApp: () -> Unit
) {
    BackHandler(onBack = onBack)

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose app", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
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
                onQueryChange = { query = it },
                placeholderText = "Search apps",
                onClear = { query = "" },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.medium)
            )

            ActionShortcutAnyAppRow(
                selected = selectedPackageName == null,
                onClick = onClearApp
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
            ) {
                items(
                    items = filteredApps,
                    key = { app -> "${app.packageName}/${app.activityName}" }
                ) { app ->
                    TriggerTargetRow(
                        title = app.name,
                        subtitle = app.packageName,
                        selected = app.packageName == selectedPackageName,
                        leadingContent = {
                            AppIcon(
                                packageName = app.packageName,
                                size = IconSize.appList
                            )
                        },
                        onClick = { onAppSelected(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionShortcutAnyAppRow(
    selected: Boolean,
    onClick: () -> Unit
) {
    TriggerTargetRow(
        title = "Any matching app",
        subtitle = "Let Android choose the right app for this destination",
        selected = selected,
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.appList)
            )
        },
        onClick = onClick
    )
}

private data class ActionShortcutPreset(
    val label: String,
    val destination: String
) {
    companion object {
        val Defaults = listOf(
            ActionShortcutPreset("Web URL", "https://example.com"),
            ActionShortcutPreset("WhatsApp", "whatsapp://send?phone=15551234567"),
            ActionShortcutPreset("Facebook", "fb://profile/100000000000000")
        )
    }
}

private fun validateActionShortcutDestination(
    value: String,
    validationResult: com.milki.launcher.core.url.UrlDestinationValidationResult?
): String? {
    if (value.isBlank()) return null
    return if (validationResult == null) {
        "Destination must be a web URL or Android URI with a scheme."
    } else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerTargetPickerScreen(
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
private fun TriggerActionShortcutPickerScreen(
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

    TriggerTargetPickerScreen(
        title = "Choose shortcut",
        triggerDisplayName = trigger.displayName,
        query = query,
        onQueryChange = { query = it },
        placeholderText = "Search shortcuts",
        onBack = onBack,
        isEmpty = filteredShortcuts.isEmpty(),
        emptyState = { TriggerAppPickerEmptyState() }
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
                        com.milki.launcher.ui.components.launcher.ActionShortcutIcon(
                            shortcut = shortcut,
                            size = IconSize.appList
                        )
                    },
                    onClick = {
                        onTargetSelected(
                            LauncherTriggerTarget.ActionShortcut(
                                id = shortcut.id,
                                label = shortcut.label,
                                destinationUri = shortcut.destinationUri,
                                packageName = shortcut.packageName,
                                packageLabel = shortcut.packageLabel
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun TriggerAppPickerScreen(
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

    TriggerTargetPickerScreen(
        title = "Choose app",
        triggerDisplayName = trigger.displayName,
        query = query,
        onQueryChange = { query = it },
        placeholderText = "Search apps and shortcuts",
        onBack = onBack,
        isEmpty = filteredApps.isEmpty(),
        emptyState = { TriggerAppPickerEmptyState() }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
        ) {
            items(
                items = filteredApps,
                key = { app -> "${app.packageName}/${app.activityName}" }
            ) { app ->
                TriggerAppPickerAppGroup(
                    app = app,
                    query = query,
                    currentTarget = currentTarget,
                    onTargetSelected = onTargetSelected
                )
            }
        }
    }
}

@Composable
private fun TriggerAppPickerAppGroup(
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

        visibleShortcuts.forEach { shortcut ->
            TriggerTargetRow(
                title = shortcut.shortLabel.ifBlank { shortcut.longLabel },
                subtitle = "Shortcut in ${app.name}",
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
    }
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
private fun TriggerAppPickerEmptyState() {
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

private fun HomeItem.AppShortcut.toTriggerTarget(): LauncherTriggerTarget.AppShortcut {
    return LauncherTriggerTarget.AppShortcut(
        packageName = packageName,
        shortcutId = shortcutId,
        shortLabel = shortLabel,
        longLabel = longLabel
    )
}

/**
 * Section: Dynamic/custom source CRUD and source-prefix edits.
 */
@Composable
private fun CustomSourcesSection(
    settings: LauncherSettings,
    actions: SettingsCustomSourceActions,
    onRequestAddSource: () -> Unit,
    onRequestEditSource: (SearchSource) -> Unit,
    onRequestDeleteSource: (String) -> Unit
) {
    val availableDefaultSources = settings.searchSources

    SettingsCategory(title = "Search Sources")

    Text(
        text = "Manage custom URL-template search sources and their prefixes.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = Spacing.mediumLarge,
            vertical = Spacing.small
        )
    )

    // Default search engine selector
    if (availableDefaultSources.isNotEmpty()) {
        val defaultSourceName = availableDefaultSources
            .firstOrNull { it.id == settings.defaultSearchSourceId }
            ?.name
            ?: availableDefaultSources.first().name

        DropdownSettingItem(
            title = "Default search engine",
            selectedValue = defaultSourceName,
            options = availableDefaultSources.map { source -> source.name to source },
            onOptionSelected = { selectedSource ->
                actions.onSetDefaultSearchSource(selectedSource.id)
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
        SourceSettingItem(
            source = source,
            onToggleEnabled = { enabled -> actions.onSetSearchSourceEnabled(source.id, enabled) },
            onToggleSuggestedAction = { show -> actions.onSetSearchSourceSuggestedAction(source.id, show) },
            onAddPrefix = { prefix, onResult ->
                actions.onAddPrefixToSource(source.id, prefix, onResult)
            },
            onRemovePrefix = { prefix ->
                actions.onRemovePrefixFromSource(source.id, prefix)
            },
            onEdit = { onRequestEditSource(source) },
            onDelete = { onRequestDeleteSource(source.id) }
        )
    }
}

/**
 * Section: Local provider prefix customization.
 */
@Composable
private fun LocalPrefixesSection(
    settings: LauncherSettings,
    actions: SettingsLocalPrefixActions
) {
    SettingsCategory(title = "Local Prefixes")

    Text(
        text = "Customize local provider prefixes and enable or disable each provider.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = Spacing.mediumLarge,
            vertical = Spacing.small
        )
    )

    PrefixSettingItem(
        providerName = "Contacts",
        providerIcon = Icons.Default.Person,
        providerColor = MaterialTheme.colorScheme.secondary,
        defaultPrefix = "c",
        isEnabled = settings.contactsSearchEnabled,
        onToggleEnabled = actions.onSetContactsSearchEnabled,
        currentPrefixes = settings.prefixConfigurations[ProviderId.CONTACTS]?.prefixes
            ?: listOf("c"),
        onAddPrefix = { prefix, onResult ->
            actions.onAddProviderPrefix(ProviderId.CONTACTS, prefix, onResult)
        },
        onRemovePrefix = { actions.onRemoveProviderPrefix(ProviderId.CONTACTS, it) },
        onReset = { actions.onResetProviderPrefixes(ProviderId.CONTACTS) }
    )

    PrefixSettingItem(
        providerName = "Files",
        providerIcon = Icons.AutoMirrored.Filled.InsertDriveFile,
        providerColor = MaterialTheme.colorScheme.primaryContainer,
        defaultPrefix = "f",
        isEnabled = settings.filesSearchEnabled,
        onToggleEnabled = actions.onSetFilesSearchEnabled,
        currentPrefixes = settings.prefixConfigurations[ProviderId.FILES]?.prefixes
            ?: listOf("f"),
        onAddPrefix = { prefix, onResult ->
            actions.onAddProviderPrefix(ProviderId.FILES, prefix, onResult)
        },
        onRemovePrefix = { actions.onRemoveProviderPrefix(ProviderId.FILES, it) },
        onReset = { actions.onResetProviderPrefixes(ProviderId.FILES) }
    )
}

/**
 * Section: Advanced actions.
 */
@Composable
private fun AdvancedSection(
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
