package com.milki.launcher.presentation.search

import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.repository.SettingsRepository
import com.milki.launcher.domain.search.SearchProviderRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Bridges settings updates into search-facing state and registry updates.
 *
 * This class isolates prefix settings observation concerns so SearchViewModel can
 * stay focused on lifecycle + public API behavior.
 */
internal class SearchViewModelSettingsAdapter(
    private val settingsRepository: SettingsRepository,
    private val providerRegistry: SearchProviderRegistry
) {

    /**
     * Starts collecting prefix configuration changes and updates:
     * 1) SearchProviderRegistry prefix mapping
     * 2) in-memory StateFlow used by the pipeline trigger
     */
    fun bind(
        scope: CoroutineScope,
        prefixConfigurations: MutableStateFlow<ProviderPrefixConfiguration>
    ) {
        scope.launch {
            settingsRepository.settings
                .map { it.prefixConfigurations }
                .distinctUntilChanged()
                .collect { updatedConfigurations ->
                    providerRegistry.updatePrefixConfigurations(updatedConfigurations)
                    prefixConfigurations.value = updatedConfigurations
                }
        }
    }
}
