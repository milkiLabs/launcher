/**
 * SettingsScreen.kt - Main settings screen for the launcher
 *
 * Displays launcher settings organized into focused section composables.
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.model.backup.LauncherImportResult
import com.milki.launcher.domain.model.targetForTrigger
import com.milki.launcher.ui.components.settings.ActionSettingItem
import com.milki.launcher.ui.theme.Spacing

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

    var showResetDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<SearchSource?>(null) }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var sourceIdPendingDelete by remember { mutableStateOf<String?>(null) }
    var appPickerTrigger by remember { mutableStateOf<Pair<LauncherTrigger, LauncherTriggerAction>?>(null) }

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
                onSelectOpenAppAction = { trigger, action -> appPickerTrigger = trigger to action }
            )

            SearchSourcesSection(
                settings = settings,
                actions = actions.sources,
                onRequestAddSource = { showAddSourceDialog = true },
                onRequestEditSource = { editingSource = it },
                onRequestDeleteSource = { sourceIdPendingDelete = it }
            )

            LocalPrefixesSection(
                settings = settings,
                actions = actions.sources.prefixes
            )

            SupportSection(actions = actions.support)

            AdvancedSection(
                backupStatusMessage = backupStatusMessage,
                onRequestReset = { showResetDialog = true },
                onRequestExport = actions.advanced.onExportBackup,
                onRequestImport = actions.advanced.onImportBackup
            )

            Spacer(modifier = Modifier.height(Spacing.extraLarge))
        }
    }

    SettingsDialogHost(
        showResetDialog = showResetDialog,
        onDismissResetDialog = { showResetDialog = false },
        onConfirmReset = {
            actions.advanced.onResetToDefaults()
            showResetDialog = false
        },
        showAddSourceDialog = showAddSourceDialog,
        onDismissAddSourceDialog = { showAddSourceDialog = false },
        onAddSearchSource = { name, urlTemplate, prefixes, accentColorHex, onValidationResult ->
            actions.sources.onAddSource(
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
        },
        editingSource = editingSource,
        onDismissEditSourceDialog = { editingSource = null },
        onUpdateSearchSource = { sourceId, name, urlTemplate, prefixes, accentColorHex, onValidationResult ->
            actions.sources.onUpdateSource(
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
        },
        sourceIdPendingDelete = sourceIdPendingDelete,
        onDismissDeleteSourceDialog = { sourceIdPendingDelete = null },
        onConfirmDeleteSource = { sourceId ->
            actions.sources.onDeleteSource(sourceId)
            sourceIdPendingDelete = null
        },
        importReport = importReport,
        onDismissImportReport = onDismissImportReport
    )
}
