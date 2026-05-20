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
import com.milki.launcher.domain.repository.PrefixConfigurationRepository
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

/**
 * ViewModel for the settings screen.
 */
class SettingsViewModel(
    private val settingsReader: SettingsReader,
    private val searchSourceRepository: SearchSourceRepository,
    private val prefixConfigRepository: PrefixConfigurationRepository,
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

    /**
     * Current settings state, observed from the repository.
     */
    val settings: StateFlow<LauncherSettings> = settingsReader.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LauncherSettings()
        )

    val installedApps: StateFlow<List<AppInfo>> = appRepository.observeInstalledApps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val actionShortcuts: StateFlow<List<HomeItem.ActionShortcut>> = actionShortcutRepository.shortcuts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage: StateFlow<String?> = _backupStatusMessage

    private val _lastImportReport = MutableStateFlow<LauncherImportResult?>(null)
    val lastImportReport: StateFlow<LauncherImportResult?> = _lastImportReport

    // ========================================================================
    // HOME SCREEN
    // ========================================================================

    fun setTriggerAction(
        trigger: LauncherTrigger,
        action: LauncherTriggerAction
    ) {
        viewModelScope.launch {
            homeTriggerRepository.setTriggerAction(trigger, action)
        }
    }

    fun setTriggerOpenAppTarget(
        trigger: LauncherTrigger,
        target: LauncherTriggerTarget
    ) {
        viewModelScope.launch {
            homeTriggerRepository.setTriggerOpenAppTarget(trigger, target)
        }
    }

    // ========================================================================
    // SEARCH PROVIDERS
    // ========================================================================

    fun setContactsSearchEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsReader.updateSettings { it.copy(contactsSearchEnabled = value) }
        }
    }

    fun setFilesSearchEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsReader.updateSettings { it.copy(filesSearchEnabled = value) }
        }
    }

    // ========================================================================
    // DYNAMIC SEARCH SOURCES
    // ========================================================================

    /**
     * Adds a new custom source.
     */
    fun addSearchSource(
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String,
        onValidationResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            val normalizedPrefixes = SearchSource.normalizePrefixes(prefixes)

            val newSource = SearchSource.create(
                name = name.trim(),
                urlTemplate = urlTemplate.trim(),
                prefixes = normalizedPrefixes,
                accentColorHex = accentColorHex
            )

            val mutationResult = searchSourceRepository.addSearchSource(newSource)
            onValidationResult(mutationResult.toPrefixUserMessage())
        }
    }

    /**
     * Updates an existing source.
     */
    fun updateSearchSource(
        sourceId: String,
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String,
        onValidationResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            val normalizedPrefixes = SearchSource.normalizePrefixes(prefixes)

            val mutationResult = searchSourceRepository.updateSearchSource(
                sourceId = sourceId,
                name = name.trim(),
                urlTemplate = urlTemplate.trim(),
                prefixes = normalizedPrefixes,
                accentColorHex = SearchSource.normalizeHexColor(accentColorHex)
            )
            onValidationResult(mutationResult.toPrefixUserMessage())
        }
    }

    /**
     * Deletes one source by ID.
     */
    fun deleteSearchSource(sourceId: String) {
        viewModelScope.launch {
            searchSourceRepository.deleteSearchSource(sourceId)
        }
    }

    /**
     * Toggles source enabled/disabled state.
     */
    fun setSearchSourceEnabled(sourceId: String, enabled: Boolean) {
        viewModelScope.launch {
            searchSourceRepository.setSearchSourceEnabled(sourceId, enabled)
        }
    }

    /**
     * Toggles whether the source is shown as a suggested action chip.
     */
    fun setSearchSourceSuggestedAction(sourceId: String, showAsSuggestedAction: Boolean) {
        viewModelScope.launch {
            searchSourceRepository.setSearchSourceSuggestedAction(sourceId, showAsSuggestedAction)
        }
    }

    /**
     * Sets the preferred default search engine source.
     */
    fun setDefaultSearchSource(sourceId: String?) {
        viewModelScope.launch {
            searchSourceRepository.setDefaultSearchSourceId(sourceId)
        }
    }

    /**
     * Adds a prefix to a source.
     */
    fun addPrefixToSource(sourceId: String, prefix: String, onValidationResult: (String) -> Unit) {
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
            val mutationResult = searchSourceRepository.addPrefixToSource(
                sourceId = sourceId,
                prefix = normalizedPrefix
            )
            onValidationResult(mutationResult.toPrefixUserMessage())
        }
    }

    /**
     * Removes one prefix from a source.
     */
    fun removePrefixFromSource(sourceId: String, prefix: String) {
        val normalizedPrefix = SearchSource.normalizePrefix(prefix)

        viewModelScope.launch {
            searchSourceRepository.removePrefixFromSource(
                sourceId = sourceId,
                prefix = normalizedPrefix
            )
        }
    }

    /**
     * Translates repository mutation outcomes into existing dialog contract.
     */
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
    // PREFIX CONFIGURATION
    // ========================================================================

    /**
     * Set the prefixes for a specific provider.
     */
    fun setProviderPrefixes(
        providerId: String,
        prefixes: List<String>,
        onValidationResult: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val mutationResult = prefixConfigRepository.setProviderPrefixes(providerId, prefixes)
            onValidationResult(mutationResult.toPrefixUserMessage())
        }
    }

    /**
     * Add a prefix to a provider's existing prefixes.
     */
    fun addProviderPrefix(providerId: String, prefix: String, onValidationResult: (String) -> Unit) {
        viewModelScope.launch {
            val mutationResult = prefixConfigRepository.addProviderPrefix(
                providerId = providerId,
                prefix = prefix,
                defaultPrefix = getDefaultPrefix(providerId)
            )
            onValidationResult(mutationResult.toPrefixUserMessage())
        }
    }

    /**
     * Remove a prefix from a provider's prefixes.
     */
    fun removeProviderPrefix(providerId: String, prefix: String) {
        viewModelScope.launch {
            prefixConfigRepository.removeProviderPrefix(providerId, prefix)
        }
    }

    /**
     * Reset a provider's prefixes to the default.
     */
    fun resetProviderPrefixes(providerId: String) {
        viewModelScope.launch {
            prefixConfigRepository.resetProviderPrefixes(providerId)
        }
    }

    /**
     * Reset all prefix configurations to defaults.
     */
    fun resetAllPrefixConfigurations() {
        viewModelScope.launch {
            prefixConfigRepository.resetAllPrefixConfigurations()
        }
    }

    /**
     * Get the default prefix for a provider.
     */
    private fun getDefaultPrefix(providerId: String): String {
        return PrefixConfig.defaults[providerId]?.primaryPrefix.orEmpty()
    }

    // ========================================================================
    // RESET
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

}
