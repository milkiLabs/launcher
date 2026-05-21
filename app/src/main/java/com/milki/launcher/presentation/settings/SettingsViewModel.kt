/**
 * SettingsViewModel.kt - ViewModel for the settings screen
 *
 * RESPONSIBILITIES:
 * - Observe settings from SettingsReader
 * - Apply user-requested setting changes via focused repositories
 * - NOT responsible for rendering UI (that's the Composables)
 */

package com.milki.launcher.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.presentation.common.ViewModelSharingStarted
import com.milki.launcher.domain.model.backup.LauncherImportResult
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.LauncherTriggerTarget
import com.milki.launcher.domain.model.PrefixConfig
import com.milki.launcher.domain.model.PrefixMutationResult
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.model.UrlHandlerApp
import com.milki.launcher.domain.repository.HomeTriggerRepository
import com.milki.launcher.domain.repository.LauncherBackupRepository
import com.milki.launcher.domain.repository.ActionShortcutRepository
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.PrefixOwnerRepository
import com.milki.launcher.domain.repository.SearchSourceRepository
import com.milki.launcher.domain.repository.SettingsReader
import com.milki.launcher.domain.repository.WidgetBindPermissionRequester
import com.milki.launcher.domain.search.UrlHandlerResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val settingsReader: SettingsReader,
    private val searchSourceRepository: SearchSourceRepository,
    private val prefixOwnerRepository: PrefixOwnerRepository,
    private val homeTriggerRepository: HomeTriggerRepository,
    appRepository: AppRepository,
    private val actionShortcutRepository: ActionShortcutRepository,
    private val launcherBackupRepository: LauncherBackupRepository,
    private val urlHandlerResolver: UrlHandlerResolver
) : ViewModel() {

    companion object {
        const val PREFIX_ADD_SUCCESS = ""
        const val PREFIX_ERROR_EMPTY = "Prefix cannot be empty"
        const val PREFIX_ERROR_SPACES = "Prefix cannot contain spaces"
        const val PREFIX_ERROR_DUPLICATE = "Prefix is already used by another source or provider"
        const val PREFIX_ERROR_SOURCE_NOT_FOUND = "Source no longer exists"
    }

    val settings: StateFlow<LauncherSettings> = settingsReader.settings
        .stateIn(
            scope = viewModelScope,
            started = ViewModelSharingStarted,
            initialValue = LauncherSettings()
        )

    val installedApps: StateFlow<List<AppInfo>> = appRepository.observeInstalledApps()
        .stateIn(
            scope = viewModelScope,
            started = ViewModelSharingStarted,
            initialValue = emptyList()
        )

    val actionShortcuts: StateFlow<List<HomeItem.ActionShortcut>> = actionShortcutRepository.shortcuts
        .stateIn(
            scope = viewModelScope,
            started = ViewModelSharingStarted,
            initialValue = emptyList()
        )

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage: StateFlow<String?> = _backupStatusMessage

    private val _lastImportReport = MutableStateFlow<LauncherImportResult?>(null)
    val lastImportReport: StateFlow<LauncherImportResult?> = _lastImportReport

    // ========================================================================
    // HOME SCREEN
    // ========================================================================

    fun setTriggerAction(trigger: LauncherTrigger, action: LauncherTriggerAction) {
        viewModelScope.launch {
            homeTriggerRepository.setTriggerAction(trigger, action)
        }
    }

    fun setTriggerOpenAppTarget(trigger: LauncherTrigger, target: LauncherTriggerTarget) {
        viewModelScope.launch {
            homeTriggerRepository.setTriggerOpenAppTarget(trigger, target)
        }
    }

    // ========================================================================
    // SEARCH SOURCES
    // ========================================================================

    fun addSearchSource(
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String,
        onValidationResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            val newSource = SearchSource.create(
                name = name.trim(),
                urlTemplate = urlTemplate.trim(),
                prefixes = SearchSource.normalizePrefixes(prefixes),
                accentColorHex = accentColorHex
            )
            val result = searchSourceRepository.addSearchSource(newSource)
            onValidationResult(result.toPrefixUserMessage())
        }
    }

    fun updateSearchSource(
        sourceId: String,
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String,
        onValidationResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = searchSourceRepository.updateSearchSource(
                sourceId = sourceId,
                name = name.trim(),
                urlTemplate = urlTemplate.trim(),
                prefixes = SearchSource.normalizePrefixes(prefixes),
                accentColorHex = SearchSource.normalizeHexColor(accentColorHex)
            )
            onValidationResult(result.toPrefixUserMessage())
        }
    }

    fun deleteSearchSource(sourceId: String) {
        viewModelScope.launch {
            searchSourceRepository.deleteSearchSource(sourceId)
        }
    }

    fun setSearchSourceEnabled(sourceId: String, enabled: Boolean) {
        viewModelScope.launch {
            searchSourceRepository.setSearchSourceEnabled(sourceId, enabled)
        }
    }

    fun setSearchSourceSuggestedAction(sourceId: String, showAsSuggestedAction: Boolean) {
        viewModelScope.launch {
            searchSourceRepository.setSearchSourceSuggestedAction(sourceId, showAsSuggestedAction)
        }
    }

    fun setDefaultSearchSource(sourceId: String?) {
        viewModelScope.launch {
            searchSourceRepository.setDefaultSearchSourceId(sourceId)
        }
    }

    // ========================================================================
    // UNIFIED PREFIX OPERATIONS
    // ========================================================================

    fun addPrefix(ownerId: String, prefix: String, onValidationResult: (String) -> Unit) {
        val normalizedPrefix = SearchSource.normalizePrefix(prefix)

        when {
            normalizedPrefix.isEmpty() -> {
                onValidationResult(PREFIX_ERROR_EMPTY)
                return
            }
            normalizedPrefix.contains(" ") -> {
                onValidationResult(PREFIX_ERROR_SPACES)
                return
            }
        }

        viewModelScope.launch {
            val result = prefixOwnerRepository.addPrefix(ownerId, normalizedPrefix)
            onValidationResult(result.toPrefixUserMessage())
        }
    }

    fun removePrefix(ownerId: String, prefix: String) {
        viewModelScope.launch {
            prefixOwnerRepository.removePrefix(ownerId, SearchSource.normalizePrefix(prefix))
        }
    }

    fun resetPrefixes(ownerId: String) {
        viewModelScope.launch {
            prefixOwnerRepository.resetPrefixes(ownerId)
        }
    }

    fun resetAllPrefixes() {
        viewModelScope.launch {
            prefixOwnerRepository.resetAllPrefixes()
        }
    }

    // ========================================================================
    // RESULT MAPPING
    // ========================================================================

    private fun PrefixMutationResult.toPrefixUserMessage(): String {
        return when (this) {
            PrefixMutationResult.Success -> PREFIX_ADD_SUCCESS
            PrefixMutationResult.PrefixAlreadyExistsOnTarget -> PREFIX_ADD_SUCCESS
            is PrefixMutationResult.DuplicatePrefixOnAnotherOwner -> PREFIX_ERROR_DUPLICATE
            PrefixMutationResult.InvalidPrefixEmpty -> PREFIX_ERROR_EMPTY
            PrefixMutationResult.InvalidPrefixContainsSpaces -> PREFIX_ERROR_SPACES
            PrefixMutationResult.TargetNotFound -> PREFIX_ERROR_SOURCE_NOT_FOUND
            PrefixMutationResult.PrefixNotFoundOnTarget -> PREFIX_ADD_SUCCESS
        }
    }

    // ========================================================================
    // RESET / BACKUP
    // ========================================================================

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsReader.updateSettings { LauncherSettings() }
        }
    }

    fun exportBackup(targetUri: Uri) {
        viewModelScope.launch {
            val result = launcherBackupRepository.exportToUri(targetUri)
            _backupStatusMessage.value = result.message
        }
    }

    fun importBackup(
        sourceUri: Uri,
        requestWidgetBindPermission: WidgetBindPermissionRequester
    ) {
        viewModelScope.launch {
            val result = launcherBackupRepository.importFromUri(
                uri = sourceUri,
                requestWidgetBindPermission = requestWidgetBindPermission
            )
            _lastImportReport.value = result
            _backupStatusMessage.value = result.message
        }
    }

    fun clearBackupStatusMessage() {
        _backupStatusMessage.value = null
    }

    fun clearLastImportReport() {
        _lastImportReport.value = null
    }

    fun getDefaultPrefix(providerId: String): String {
        return PrefixConfig.defaults[providerId]?.primaryPrefix.orEmpty()
    }
}
