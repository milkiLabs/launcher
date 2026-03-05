/**
 * SettingsScreen.kt - Main settings screen for the launcher
 *
 * Displays all configurable launcher settings organized into sections:
 * 1. Search Behavior - Result limits, keyboard behavior, recent apps
 * 2. Appearance - Layout, hints, icons
 * 3. Home Screen - Tap/gesture behavior
 * 4. Search Providers - Enable/disable providers, default search engine
 * 5. Advanced - Reset to defaults, app info
 *
 * ARCHITECTURE:
 * This is a stateless composable. All state comes from SettingsViewModel
 * and all changes are propagated via ViewModel methods.
 */

package com.milki.launcher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import com.milki.launcher.domain.model.*
import com.milki.launcher.ui.components.settings.*
import com.milki.launcher.ui.theme.Spacing

/**
 * SettingsScreen - The main settings page for the launcher.
 *
 * @param settings Current launcher settings
 * @param onNavigateBack Called when user taps the back button
 * @param onSetMaxSearchResults Set max search results
 * @param onSetAutoFocusKeyboard Toggle auto-focus keyboard
 * @param onSetShowRecentApps Toggle recent apps display
 * @param onSetMaxRecentApps Set max recent apps count
 * @param onSetCloseSearchOnLaunch Toggle close-on-launch behavior
 * @param onSetSearchResultLayout Set result layout type
 * @param onSetShowHomescreenHint Toggle homescreen hint text
 * @param onSetShowAppIcons Toggle app icons in results
 * @param onSetHomeTapAction Set home tap action
 * @param onSetSwipeUpAction Set swipe up action
 * @param onSetHomeButtonClearsQuery Toggle home-button-clears-query behavior
 * @param onSetDefaultSearchEngine Set default search engine
 * @param onSetWebSearchEnabled Toggle web search provider
 * @param onSetContactsSearchEnabled Toggle contacts search provider
 * @param onSetYoutubeSearchEnabled Toggle YouTube search provider
 * @param onSetFilesSearchEnabled Toggle files search provider
 * @param onAddProviderPrefix Add a prefix to a provider
 * @param onRemoveProviderPrefix Remove a prefix from a provider
 * @param onResetProviderPrefixes Reset a provider's prefixes to default
 * @param onResetToDefaults Reset all settings to defaults
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    onNavigateBack: () -> Unit,
    onSetMaxSearchResults: (Int) -> Unit,
    onSetAutoFocusKeyboard: (Boolean) -> Unit,
    onSetShowRecentApps: (Boolean) -> Unit,
    onSetMaxRecentApps: (Int) -> Unit,
    onSetCloseSearchOnLaunch: (Boolean) -> Unit,
    onSetSearchResultLayout: (SearchResultLayout) -> Unit,
    onSetShowHomescreenHint: (Boolean) -> Unit,
    onSetShowAppIcons: (Boolean) -> Unit,
    onSetHomeTapAction: (HomeTapAction) -> Unit,
    onSetSwipeUpAction: (SwipeUpAction) -> Unit,
    onSetHomeButtonClearsQuery: (Boolean) -> Unit,
    onSetDefaultSearchEngine: (SearchEngine) -> Unit,
    onSetWebSearchEnabled: (Boolean) -> Unit,
    onSetContactsSearchEnabled: (Boolean) -> Unit,
    onSetYoutubeSearchEnabled: (Boolean) -> Unit,
    onSetFilesSearchEnabled: (Boolean) -> Unit,
    onAddProviderPrefix: (String, String) -> Unit,
    onRemoveProviderPrefix: (String, String) -> Unit,
    onResetProviderPrefixes: (String) -> Unit,
    onAddSearchSource: (
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String,
        includeInPlainQuerySuggestions: Boolean
    ) -> Unit,
    onUpdateSearchSource: (
        sourceId: String,
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String,
        includeInPlainQuerySuggestions: Boolean
    ) -> Unit,
    onDeleteSearchSource: (String) -> Unit,
    onSetSearchSourceEnabled: (String, Boolean) -> Unit,
    onSetDefaultPlainQuerySource: (String) -> Unit,
    onSetIncludeInPlainQuerySuggestions: (String, Boolean) -> Unit,
    onAddPrefixToSource: (String, String, (String) -> Unit) -> Unit,
    onRemovePrefixFromSource: (String, String) -> Unit,
    onResetToDefaults: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
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
            // ================================================================
            // SEARCH BEHAVIOR
            // ================================================================
            SettingsCategory(title = "Search Behavior")

            SwitchSettingItem(
                title = "Auto-focus keyboard",
                subtitle = "Automatically show keyboard when search opens",
                checked = settings.autoFocusKeyboard,
                onCheckedChange = onSetAutoFocusKeyboard
            )

            SwitchSettingItem(
                title = "Show recent apps",
                subtitle = "Display recently used apps when search is empty",
                checked = settings.showRecentApps,
                onCheckedChange = onSetShowRecentApps
            )

            SliderSettingItem(
                title = "Max recent apps",
                subtitle = "Number of recent apps to show",
                value = settings.maxRecentApps,
                onValueChange = onSetMaxRecentApps,
                valueRange = 1..10,
                steps = 8
            )

            SliderSettingItem(
                title = "Max search results",
                subtitle = "Maximum results shown per search",
                value = settings.maxSearchResults,
                onValueChange = onSetMaxSearchResults,
                valueRange = 3..20,
                steps = 16
            )

            SwitchSettingItem(
                title = "Close search on launch",
                subtitle = "Close the search dialog after launching an app",
                checked = settings.closeSearchOnLaunch,
                onCheckedChange = onSetCloseSearchOnLaunch
            )

            // ================================================================
            // APPEARANCE
            // ================================================================
            SettingsCategory(title = "Appearance")

            DropdownSettingItem(
                title = "Search result layout",
                subtitle = "How search results are displayed",
                selectedValue = settings.searchResultLayout.displayName,
                options = SearchResultLayout.entries.map { it.displayName to it },
                onOptionSelected = onSetSearchResultLayout
            )

            SwitchSettingItem(
                title = "Show homescreen hint",
                subtitle = "Display \"Tap to search\" text on homescreen",
                checked = settings.showHomescreenHint,
                onCheckedChange = onSetShowHomescreenHint
            )

            SwitchSettingItem(
                title = "Show app icons",
                subtitle = "Display app icons in search results",
                checked = settings.showAppIcons,
                onCheckedChange = onSetShowAppIcons
            )

            // ================================================================
            // HOME SCREEN
            // ================================================================
            SettingsCategory(title = "Home Screen")

            DropdownSettingItem(
                title = "Homescreen tap action",
                subtitle = "What happens when you tap the homescreen",
                selectedValue = settings.homeTapAction.displayName,
                options = HomeTapAction.entries.map { it.displayName to it },
                onOptionSelected = onSetHomeTapAction
            )

            DropdownSettingItem(
                title = "Swipe up action",
                subtitle = "What happens when you swipe up on the homescreen",
                selectedValue = settings.swipeUpAction.displayName,
                options = SwipeUpAction.entries.map { it.displayName to it },
                onOptionSelected = onSetSwipeUpAction
            )

            SwitchSettingItem(
                title = "Home button clears query",
                subtitle = "Pressing home clears query before closing search",
                checked = settings.homeButtonClearsQuery,
                onCheckedChange = onSetHomeButtonClearsQuery
            )

            // ================================================================
            // SEARCH PROVIDERS
            // ================================================================
            SettingsCategory(title = "Search Providers")

            SwitchSettingItem(
                title = "Web search",
                subtitle = "Enable default web sources (Google + DuckDuckGo)",
                checked = settings.webSearchEnabled,
                onCheckedChange = onSetWebSearchEnabled
            )

            SwitchSettingItem(
                title = "Contacts search",
                subtitle = "Search contacts with prefix \"c\"",
                checked = settings.contactsSearchEnabled,
                onCheckedChange = onSetContactsSearchEnabled
            )

            SwitchSettingItem(
                title = "YouTube search",
                subtitle = "Enable default YouTube source",
                checked = settings.youtubeSearchEnabled,
                onCheckedChange = onSetYoutubeSearchEnabled
            )

            SwitchSettingItem(
                title = "Files search",
                subtitle = "Search files with prefix \"f\"",
                checked = settings.filesSearchEnabled,
                onCheckedChange = onSetFilesSearchEnabled
            )

            // ================================================================
            // CUSTOM SOURCES (NEW)
            // ================================================================
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
                onClick = { showAddSourceDialog = true },
                icon = Icons.Default.Add
            )

            settings.searchSources.forEach { source ->
                SourceSettingItem(
                    source = source,
                    isDefault = source.isEnabled && source.isDefaultForPlainQueryAction,
                    onToggleEnabled = { enabled -> onSetSearchSourceEnabled(source.id, enabled) },
                    onToggleIncludeInSuggestions = { include ->
                        onSetIncludeInPlainQuerySuggestions(source.id, include)
                    },
                    onSetAsDefault = { onSetDefaultPlainQuerySource(source.id) },
                    onAddPrefix = { prefix, onResult ->
                        onAddPrefixToSource(source.id, prefix, onResult)
                    },
                    onRemovePrefix = { prefix ->
                        onRemovePrefixFromSource(source.id, prefix)
                    },
                    onEdit = { editingSource = source },
                    onDelete = { sourceIdPendingDelete = source.id }
                )
            }

            // ================================================================
            // PREFIX CONFIGURATION
            // ================================================================
            SettingsCategory(title = "Local Prefixes")

            // Helper text for prefix configuration
            Text(
                text = "Customize prefixes for local providers. External source prefixes are configured in the Custom Sources section.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = Spacing.mediumLarge,
                    vertical = Spacing.small
                )
            )

            // Contacts prefix configuration
            PrefixSettingItem(
                providerName = "Contacts",
                providerIcon = Icons.Default.Person,
                providerColor = MaterialTheme.colorScheme.secondary,
                defaultPrefix = "c",
                currentPrefixes = settings.prefixConfigurations[ProviderId.CONTACTS]?.prefixes
                    ?: listOf("c"),
                onAddPrefix = { onAddProviderPrefix(ProviderId.CONTACTS, it) },
                onRemovePrefix = { onRemoveProviderPrefix(ProviderId.CONTACTS, it) },
                onReset = { onResetProviderPrefixes(ProviderId.CONTACTS) }
            )


            // Files prefix configuration
            PrefixSettingItem(
                providerName = "Files",
                providerIcon = Icons.AutoMirrored.Filled.InsertDriveFile,
                providerColor = MaterialTheme.colorScheme.primaryContainer,
                defaultPrefix = "f",
                currentPrefixes = settings.prefixConfigurations[ProviderId.FILES]?.prefixes
                    ?: listOf("f"),
                onAddPrefix = { onAddProviderPrefix(ProviderId.FILES, it) },
                onRemovePrefix = { onRemoveProviderPrefix(ProviderId.FILES, it) },
                onReset = { onResetProviderPrefixes(ProviderId.FILES) }
            )

            // ================================================================
            // ADVANCED
            // ================================================================
            SettingsCategory(title = "Advanced")

            ActionSettingItem(
                title = "Reset to defaults",
                subtitle = "Restore all settings to their default values",
                onClick = { showResetDialog = true },
                textColor = MaterialTheme.colorScheme.error
            )

            // Bottom padding for navigation gesture area
            Spacer(modifier = Modifier.height(Spacing.extraLarge))
        }
    }

    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings") },
            text = { Text("This will restore all settings to their default values. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetToDefaults()
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
            onConfirm = { name, urlTemplate, prefixes, accentColorHex, includeInSuggestions ->
                onAddSearchSource(name, urlTemplate, prefixes, accentColorHex, includeInSuggestions)
                showAddSourceDialog = false
            }
        )
    }

    if (editingSource != null) {
        SourceEditorDialog(
            initialSource = editingSource,
            onDismiss = { editingSource = null },
            onConfirm = { name, urlTemplate, prefixes, accentColorHex, includeInSuggestions ->
                val sourceId = editingSource?.id ?: return@SourceEditorDialog
                onUpdateSearchSource(
                    sourceId,
                    name,
                    urlTemplate,
                    prefixes,
                    accentColorHex,
                    includeInSuggestions
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
                    onDeleteSearchSource(sourceId)
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
}
