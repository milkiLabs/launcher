/**
 * SettingsViewModel.kt - ViewModel for the settings screen
 *
 * Manages the settings UI state and handles user interactions.
 * Follows the same UDF pattern as SearchViewModel.
 *
 * RESPONSIBILITIES:
 * - Observe settings from SettingsRepository
 * - Apply user-requested setting changes
 * - NOT responsible for rendering UI (that's the Composables)
 */

package com.milki.launcher.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.domain.model.*
import com.milki.launcher.domain.model.backup.LauncherImportResult
import com.milki.launcher.domain.repository.LauncherBackupRepository
import com.milki.launcher.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the settings screen.
 *
 * @param settingsRepository Repository for reading/writing settings
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val launcherBackupRepository: LauncherBackupRepository
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
     * Starts with default settings until DataStore emits.
     */
    val settings: StateFlow<LauncherSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LauncherSettings()
        )

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage: StateFlow<String?> = _backupStatusMessage

    private val _lastImportReport = MutableStateFlow<LauncherImportResult?>(null)
    val lastImportReport: StateFlow<LauncherImportResult?> = _lastImportReport

    // ========================================================================
    // SEARCH BEHAVIOR
    // ========================================================================

    // ========================================================================
    // APPEARANCE
    // ========================================================================

    fun setSearchResultLayout(layout: SearchResultLayout) {
        viewModelScope.launch {
            settingsRepository.setSearchResultLayout(layout)
        }
    }

    fun setShowHomescreenHint(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowHomescreenHint(value)
        }
    }

    fun setShowAppIcons(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowAppIcons(value)
        }
    }

    // ========================================================================
    // HOME SCREEN
    // ========================================================================

    fun setTriggerAction(
        trigger: LauncherTrigger,
        action: LauncherTriggerAction
    ) {
        viewModelScope.launch {
            settingsRepository.setTriggerAction(trigger, action)
        }
    }

    // ========================================================================
    // SEARCH PROVIDERS
    // ========================================================================

    fun setContactsSearchEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setContactsSearchEnabled(value)
        }
    }

    fun setFilesSearchEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFilesSearchEnabled(value)
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
            val normalizedPrefixes = prefixes
                .map(SearchSource.Companion::normalizePrefix)
                .filter { it.isNotBlank() && !it.contains(" ") }
                .distinct()

            val newSource = SearchSource.create(
                name = name.trim(),
                urlTemplate = urlTemplate.trim(),
                prefixes = normalizedPrefixes,
                accentColorHex = accentColorHex
            )

            val mutationResult = settingsRepository.addSearchSource(newSource)
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
            val normalizedPrefixes = prefixes
                .map(SearchSource.Companion::normalizePrefix)
                .filter { it.isNotBlank() && !it.contains(" ") }
                .distinct()

            val mutationResult = settingsRepository.updateSearchSource(
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
            settingsRepository.deleteSearchSource(sourceId)
        }
    }

    /**
     * Toggles source enabled/disabled state.
     */
    fun setSearchSourceEnabled(sourceId: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSearchSourceEnabled(sourceId, enabled)
        }
    }

    /**
     * Adds a prefix to a source.
     *
     * INPUT VALIDATION STRATEGY:
     * 1) Keep immediate client-side checks for empty/spacing so dialog UX stays snappy.
     * 2) Delegate uniqueness and source-existence validation to repository transaction.
     *
     * This split gives quick feedback without relying on potentially stale snapshots
     * for correctness-critical uniqueness rules.
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
            val mutationResult = settingsRepository.addPrefixToSource(
                sourceId = sourceId,
                prefix = normalizedPrefix
            )
            onValidationResult(mutationResult.toPrefixUserMessage())
        }
    }

    /**
     * Removes one prefix from a source.
     *
     * The operation is executed in repository-level transaction to keep behavior
     * deterministic when source data changes concurrently.
     */
    fun removePrefixFromSource(sourceId: String, prefix: String) {
        val normalizedPrefix = SearchSource.normalizePrefix(prefix)

        viewModelScope.launch {
            settingsRepository.removePrefixFromSource(
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
     *
     * This replaces all existing prefixes for the provider with the new list.
     * The first prefix in the list is considered the "primary" prefix for display.
     *
    * @param providerId The provider ID (contacts/files)
     * @param prefixes List of prefixes to set for this provider
     */
    fun setProviderPrefixes(providerId: String, prefixes: List<String>) {
        viewModelScope.launch {
            settingsRepository.setProviderPrefixes(providerId, prefixes)
        }
    }

    /**
     * Add a prefix to a provider's existing prefixes.
     *
     * If the provider has no existing configuration, this creates one with
     * the default prefix plus the new prefix.
     *
     * @param providerId The provider ID
     * @param prefix The prefix to add
     */
    fun addProviderPrefix(providerId: String, prefix: String, onValidationResult: (String) -> Unit) {
        viewModelScope.launch {
            val mutationResult = settingsRepository.addProviderPrefix(
                providerId = providerId,
                prefix = prefix,
                defaultPrefix = getDefaultPrefix(providerId)
            )
            onValidationResult(mutationResult.toPrefixUserMessage())
        }
    }

    /**
     * Remove a prefix from a provider's prefixes.
     *
     * If this is the last prefix, the provider will fall back to its default prefix.
     *
     * @param providerId The provider ID
     * @param prefix The prefix to remove
     */
    fun removeProviderPrefix(providerId: String, prefix: String) {
        viewModelScope.launch {
            settingsRepository.removeProviderPrefix(providerId, prefix)
        }
    }

    /**
     * Reset a provider's prefixes to the default.
     *
     * This removes any custom configuration for the provider.
     *
     * @param providerId The provider ID to reset
     */
    fun resetProviderPrefixes(providerId: String) {
        viewModelScope.launch {
            settingsRepository.resetProviderPrefixes(providerId)
        }
    }

    /**
     * Reset all prefix configurations to defaults.
     *
     * This removes all custom prefix configurations.
     */
    fun resetAllPrefixConfigurations() {
        viewModelScope.launch {
            settingsRepository.resetAllPrefixConfigurations()
        }
    }

    /**
     * Get the default prefix for a provider.
     *
     * This is used when there's no custom configuration for the provider.
     *
     * @param providerId The provider ID
     * @return The default prefix for this provider
     */
    private fun getDefaultPrefix(providerId: String): String {
        return when (providerId) {
            ProviderId.CONTACTS -> "c"
            ProviderId.FILES -> "f"
            else -> ""
        }
    }

    // ========================================================================
    // HIDDEN APPS
    // ========================================================================

    fun toggleHiddenApp(packageName: String) {
        viewModelScope.launch {
            settingsRepository.toggleHiddenApp(packageName)
        }
    }

    // ========================================================================
    // RESET
    // ========================================================================

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsRepository.updateSettings { LauncherSettings() }
        }
    }

    fun exportBackup(targetUri: Uri) {
        viewModelScope.launch {
            val result = launcherBackupRepository.exportToUri(targetUri)
            _backupStatusMessage.value = result.message
        }
    }

    fun importBackup(sourceUri: Uri) {
        viewModelScope.launch {
            val result = launcherBackupRepository.importFromUri(sourceUri)
            _lastImportReport.value = result
            _backupStatusMessage.value = result.toUiMessage()
        }
    }

    fun clearBackupStatusMessage() {
        _backupStatusMessage.value = null
    }

    fun clearLastImportReport() {
        _lastImportReport.value = null
    }

    private fun LauncherImportResult.toUiMessage(): String {
        if (!success) return message
        if (skippedCount == 0) return message

        val preview = skippedReasons
            .take(3)
            .joinToString(separator = "\n") { reason -> reason.message }

        return "$message\n$preview"
    }

}
