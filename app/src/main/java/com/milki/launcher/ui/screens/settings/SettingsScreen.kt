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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import com.milki.launcher.domain.model.HomeTapAction
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.model.SwipeUpAction
import com.milki.launcher.domain.model.backup.SkippedImportCategory
import com.milki.launcher.domain.model.backup.LauncherImportResult
import com.milki.launcher.ui.components.settings.ActionSettingItem
import com.milki.launcher.ui.components.settings.DropdownSettingItem
import com.milki.launcher.ui.components.settings.PrefixSettingItem
import com.milki.launcher.ui.components.settings.SettingsCategory
import com.milki.launcher.ui.components.settings.SliderSettingItem
import com.milki.launcher.ui.components.settings.SourceEditorDialog
import com.milki.launcher.ui.components.settings.SourceSettingItem
import com.milki.launcher.ui.components.settings.SwitchSettingItem
import com.milki.launcher.ui.theme.Spacing

/**
 * SettingsScreen - launcher settings page with grouped action contract.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    onNavigateBack: () -> Unit,
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
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
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
            SearchBehaviorSection(
                settings = settings,
                actions = actions.searchBehavior
            )

            HomeScreenSection(
                settings = settings,
                actions = actions.homeScreen
            )

            LocalProvidersSection(
                settings = settings,
                actions = actions.localProviders
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
            onConfirm = { name, urlTemplate, prefixes, accentColorHex ->
                actions.customSources.onAddSearchSource(name, urlTemplate, prefixes, accentColorHex)
                showAddSourceDialog = false
            }
        )
    }

    if (editingSource != null) {
        SourceEditorDialog(
            initialSource = editingSource,
            onDismiss = { editingSource = null },
            onConfirm = { name, urlTemplate, prefixes, accentColorHex ->
                val sourceId = editingSource?.id ?: return@SourceEditorDialog
                actions.customSources.onUpdateSearchSource(
                    sourceId,
                    name,
                    urlTemplate,
                    prefixes,
                    accentColorHex
                )
                editingSource = null
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
 * Section: Search Behavior.
 */
@Composable
private fun SearchBehaviorSection(
    settings: LauncherSettings,
    actions: SettingsSearchBehaviorActions
) {
    SettingsCategory(title = "Search Behavior")

    SliderSettingItem(
        title = "Max recent apps",
        subtitle = "Number of recent apps to show",
        value = settings.maxRecentApps,
        onValueChange = actions.onSetMaxRecentApps,
        valueRange = 1..10,
        steps = 8
    )
}

/**
 * Section: Home Screen.
 */
@Composable
private fun HomeScreenSection(
    settings: LauncherSettings,
    actions: SettingsHomeScreenActions
) {
    SettingsCategory(title = "Home Screen")

    DropdownSettingItem(
        title = "Homescreen tap action",
        subtitle = "What happens when you tap the homescreen",
        selectedValue = settings.homeTapAction.displayName,
        options = HomeTapAction.entries.map { it.displayName to it },
        onOptionSelected = actions.onSetHomeTapAction
    )

    DropdownSettingItem(
        title = "Swipe up action",
        subtitle = "What happens when you swipe up on the homescreen",
        selectedValue = settings.swipeUpAction.displayName,
        options = SwipeUpAction.entries.map { it.displayName to it },
        onOptionSelected = actions.onSetSwipeUpAction
    )

    SwitchSettingItem(
        title = "Home button clears query",
        subtitle = "Pressing home clears query before closing search",
        checked = settings.homeButtonClearsQuery,
        onCheckedChange = actions.onSetHomeButtonClearsQuery
    )
}

/**
 * Section: Local providers enabled/disabled toggles.
 */
@Composable
private fun LocalProvidersSection(
    settings: LauncherSettings,
    actions: SettingsLocalProviderActions
) {
    SettingsCategory(title = "Search Providers")

    SwitchSettingItem(
        title = "Contacts search",
        subtitle = "Search contacts with prefix \"c\"",
        checked = settings.contactsSearchEnabled,
        onCheckedChange = actions.onSetContactsSearchEnabled
    )

    SwitchSettingItem(
        title = "Files search",
        subtitle = "Search files with prefix \"f\"",
        checked = settings.filesSearchEnabled,
        onCheckedChange = actions.onSetFilesSearchEnabled
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
    SettingsCategory(title = "Custom Sources")

    Text(
        text = "Create your own search sources (YouTube, Instagram, Twitter/X, engines, or any URL template). You can configure prefixes, fallback behavior via URL handlers, and custom colors.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = Spacing.mediumLarge,
            vertical = Spacing.small
        )
    )

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
        text = "Customize prefixes for local providers. External source prefixes are configured in the Custom Sources section.",
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
        currentPrefixes = settings.prefixConfigurations[ProviderId.CONTACTS]?.prefixes
            ?: listOf("c"),
        onAddPrefix = { actions.onAddProviderPrefix(ProviderId.CONTACTS, it) },
        onRemovePrefix = { actions.onRemoveProviderPrefix(ProviderId.CONTACTS, it) },
        onReset = { actions.onResetProviderPrefixes(ProviderId.CONTACTS) }
    )

    PrefixSettingItem(
        providerName = "Files",
        providerIcon = Icons.AutoMirrored.Filled.InsertDriveFile,
        providerColor = MaterialTheme.colorScheme.primaryContainer,
        defaultPrefix = "f",
        currentPrefixes = settings.prefixConfigurations[ProviderId.FILES]?.prefixes
            ?: listOf("f"),
        onAddPrefix = { actions.onAddProviderPrefix(ProviderId.FILES, it) },
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
