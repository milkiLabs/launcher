package com.milki.launcher.presentation.search

import com.milki.launcher.data.search.ConfigurableUrlSearchProvider
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.model.PrefixConfig
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.repository.SettingsRepository
import com.milki.launcher.domain.search.SearchProviderRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
        prefixConfigurations: MutableStateFlow<ProviderPrefixConfiguration>,
        searchSources: MutableStateFlow<List<SearchSource>>,
        providerAccentColorById: MutableStateFlow<Map<String, String>>,
        defaultPlainQueryUrlTemplate: MutableStateFlow<String>
    ) {
        scope.launch {
            settingsRepository.settings
                .distinctUntilChanged()
                .collect { settings ->
                    val enabledSources = settings.searchSources.filter { it.isEnabled }

                    val dynamicProviderIds = providerRegistry
                        .getAllConfigs()
                        .map { it.providerId }
                        .filter { it.startsWith("source_") }
                        .toSet()

                    val nextDynamicProviderIds = enabledSources.map { it.id }.toSet()

                    dynamicProviderIds
                        .filter { it !in nextDynamicProviderIds }
                        .forEach(providerRegistry::unregister)

                    enabledSources.forEach { source ->
                        providerRegistry.register(ConfigurableUrlSearchProvider(source))
                    }

                    val sourcePrefixConfigurations = enabledSources.associate { source ->
                        source.id to PrefixConfig(source.prefixes)
                    }

                    val fixedProviderConfigurations = buildMap {
                        put(ProviderId.CONTACTS, settings.prefixConfigurations[ProviderId.CONTACTS] ?: PrefixConfig.single("c"))
                        put(ProviderId.FILES, settings.prefixConfigurations[ProviderId.FILES] ?: PrefixConfig.single("f"))
                        if (settings.contactsSearchEnabled.not()) {
                            remove(ProviderId.CONTACTS)
                        }
                        if (settings.filesSearchEnabled.not()) {
                            remove(ProviderId.FILES)
                        }
                    }

                    val mergedConfigurations: ProviderPrefixConfiguration =
                        fixedProviderConfigurations + sourcePrefixConfigurations

                    providerRegistry.updatePrefixConfigurations(mergedConfigurations)
                    prefixConfigurations.value = mergedConfigurations
                    searchSources.value = settings.searchSources
                    providerAccentColorById.value = settings.searchSources.associate { it.id to it.accentColorHex }

                    val defaultSource = settings.searchSources.firstOrNull {
                        it.isEnabled && it.isDefaultForPlainQueryAction
                    } ?: settings.searchSources.firstOrNull { it.isEnabled }

                    defaultPlainQueryUrlTemplate.value =
                        defaultSource?.urlTemplate ?: "https://www.google.com/search?q={query}"
                }
        }
    }
}
