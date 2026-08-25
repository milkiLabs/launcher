/**
 * SettingsPages.kt - Individual settings group pages.
 *
 * Each page owns a Scaffold with a back arrow and renders the relevant
 * section composables. Cross-page modal coordination (import report) lives
 * in SettingsNav.kt; page-scoped dialogs are hosted here.
 */

package com.milki.launcher.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.milki.launcher.BuildConfig
import com.milki.launcher.R
import com.milki.launcher.core.intent.openUrlDestination
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.ui.components.common.LauncherScreenScaffold
import com.milki.launcher.ui.components.settings.SourceEditorDialog
import com.milki.launcher.ui.theme.Spacing

@Composable
internal fun HomeScreenSettingsScreen(
    settings: LauncherSettings,
    actions: SettingsHomeScreenActions,
    onSelectOpenAppAction: (LauncherTrigger, LauncherTriggerAction) -> Unit,
    onBack: () -> Unit
) {
    LauncherScreenScaffold(
        title = stringResource(R.string.settings_group_home_title),
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            HomeScreenSection(
                settings = settings,
                actions = actions,
                onSelectOpenAppAction = onSelectOpenAppAction
            )

            Spacer(modifier = Modifier.height(Spacing.extraLarge))
        }
    }
}

@Composable
internal fun SearchSettingsScreen(
    settings: LauncherSettings,
    actions: SettingsActions,
    onBack: () -> Unit
) {
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<SearchSource?>(null) }
    var sourceIdPendingDelete by remember { mutableStateOf<String?>(null) }

    LauncherScreenScaffold(
        title = stringResource(R.string.settings_group_search_title),
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            SearchLayoutSection(
                settings = settings,
                onSetSearchLayout = actions.onSetSearchLayout
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

            FileSearchExtensionsSection(
                extensionConfig = settings.fileSearchExtensionConfig,
                actions = actions.fileSearch
            )

            Spacer(modifier = Modifier.height(Spacing.extraLarge))
        }
    }

    if (showAddSourceDialog) {
        SourceEditorDialog(
            initialSource = null,
            onDismiss = { showAddSourceDialog = false },
            onConfirm = { name, urlTemplate, prefixes, accentColorHex, onValidationResult ->
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
            }
        )
    }

    editingSource?.let { source ->
        SourceEditorDialog(
            initialSource = source,
            onDismiss = { editingSource = null },
            onConfirm = { name, urlTemplate, prefixes, accentColorHex, onValidationResult ->
                actions.sources.onUpdateSource(
                    source.id,
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

    sourceIdPendingDelete?.let { sourceId ->
        DeleteSourceDialog(
            onDismiss = { sourceIdPendingDelete = null },
            onConfirm = {
                actions.sources.onDeleteSource(sourceId)
                sourceIdPendingDelete = null
            }
        )
    }
}

@Composable
internal fun AdvancedSettingsScreen(
    backupStatusMessage: String?,
    onRequestReset: () -> Unit,
    onRequestExport: () -> Unit,
    onRequestImport: () -> Unit,
    onShareCrashLogs: () -> Unit,
    onBack: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }

    LauncherScreenScaffold(
        title = stringResource(R.string.settings_group_advanced_title),
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            AdvancedSection(
                backupStatusMessage = backupStatusMessage,
                onRequestReset = { showResetDialog = true },
                onRequestExport = onRequestExport,
                onRequestImport = onRequestImport,
                onShareCrashLogs = onShareCrashLogs
            )

            Spacer(modifier = Modifier.height(Spacing.extraLarge))
        }
    }

    if (showResetDialog) {
        ResetSettingsDialog(
            onDismiss = { showResetDialog = false },
            onConfirm = {
                onRequestReset()
                showResetDialog = false
            }
        )
    }
}

@Composable
internal fun AboutSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    LauncherScreenScaffold(
        title = stringResource(R.string.settings_group_about_title),
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            AboutSection(
                appVersion = BuildConfig.VERSION_NAME,
                onOpenLink = { url -> openUrlDestination(context, url) }
            )

            Spacer(modifier = Modifier.height(Spacing.extraLarge))
        }
    }
}
