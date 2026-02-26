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
    onResetToDefaults: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showResetDialog by remember { mutableStateOf(false) }

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

            DropdownSettingItem(
                title = "Default search engine",
                subtitle = "Search engine used with the \"s\" prefix",
                selectedValue = settings.defaultSearchEngine.displayName,
                options = SearchEngine.entries.map { it.displayName to it },
                onOptionSelected = onSetDefaultSearchEngine
            )

            SwitchSettingItem(
                title = "Web search",
                subtitle = "Search the web with prefix \"s\"",
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
                subtitle = "Search YouTube with prefix \"y\"",
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
            // PREFIX CONFIGURATION
            // ================================================================
            SettingsCategory(title = "Prefix Configuration")

            // Helper text for prefix configuration
            Text(
                text = "Customize prefixes for each search provider. You can add multiple prefixes per provider (useful for multilingual input).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = Spacing.mediumLarge,
                    vertical = Spacing.small
                )
            )

            // Web Search prefix configuration
            PrefixSettingItem(
                providerName = "Web Search",
                providerIcon = Icons.Default.Search,
                providerColor = androidx.compose.ui.graphics.Color(0xFF4285F4),
                defaultPrefix = "s",
                currentPrefixes = settings.prefixConfigurations[ProviderId.WEB]?.prefixes
                    ?: listOf("s"),
                onAddPrefix = { onAddProviderPrefix(ProviderId.WEB, it) },
                onRemovePrefix = { onRemoveProviderPrefix(ProviderId.WEB, it) },
                onReset = { onResetProviderPrefixes(ProviderId.WEB) }
            )

            // Contacts prefix configuration
            PrefixSettingItem(
                providerName = "Contacts",
                providerIcon = Icons.Default.Person,
                providerColor = androidx.compose.ui.graphics.Color(0xFF34A853),
                defaultPrefix = "c",
                currentPrefixes = settings.prefixConfigurations[ProviderId.CONTACTS]?.prefixes
                    ?: listOf("c"),
                onAddPrefix = { onAddProviderPrefix(ProviderId.CONTACTS, it) },
                onRemovePrefix = { onRemoveProviderPrefix(ProviderId.CONTACTS, it) },
                onReset = { onResetProviderPrefixes(ProviderId.CONTACTS) }
            )

            // YouTube prefix configuration
            PrefixSettingItem(
                providerName = "YouTube",
                providerIcon = Icons.Default.PlayArrow,
                providerColor = androidx.compose.ui.graphics.Color(0xFFFF0000),
                defaultPrefix = "y",
                currentPrefixes = settings.prefixConfigurations[ProviderId.YOUTUBE]?.prefixes
                    ?: listOf("y"),
                onAddPrefix = { onAddProviderPrefix(ProviderId.YOUTUBE, it) },
                onRemovePrefix = { onRemoveProviderPrefix(ProviderId.YOUTUBE, it) },
                onReset = { onResetProviderPrefixes(ProviderId.YOUTUBE) }
            )

            // Files prefix configuration
            PrefixSettingItem(
                providerName = "Files",
                providerIcon = Icons.AutoMirrored.Filled.InsertDriveFile,
                providerColor = androidx.compose.ui.graphics.Color(0xFFFF9800),
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
}
