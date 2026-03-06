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
import kotlinx.coroutines.flow.map
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
        providerAccentColorById: MutableStateFlow<Map<String, String>>
    ) {
        scope.launch {
            settingsRepository.settings
                // Observe only search-runtime-relevant settings so unrelated toggles
                // (UI appearance, home gestures, etc.) do not trigger provider churn.
                .map(SearchRuntimeSettingsProjection::from)
                .distinctUntilChanged()
                .collect { projection ->
                    val enabledSources = projection.searchSources.filter { it.isEnabled }

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
                        put(
                            ProviderId.CONTACTS,
                            projection.prefixConfigurations[ProviderId.CONTACTS] ?: PrefixConfig.single("c")
                        )
                        put(
                            ProviderId.FILES,
                            projection.prefixConfigurations[ProviderId.FILES] ?: PrefixConfig.single("f")
                        )
                        if (projection.contactsSearchEnabled.not()) {
                            remove(ProviderId.CONTACTS)
                        }
                        if (projection.filesSearchEnabled.not()) {
                            remove(ProviderId.FILES)
                        }
                    }

                    val mergedConfigurations: ProviderPrefixConfiguration =
                        fixedProviderConfigurations + sourcePrefixConfigurations

                    providerRegistry.updatePrefixConfigurations(mergedConfigurations)
                    prefixConfigurations.value = mergedConfigurations
                    providerAccentColorById.value = projection.searchSources.associate { it.id to it.accentColorHex }
                }
        }
    }
}

/**
 * Narrow projection used by the search runtime.
 *
 * Keeping this as a dedicated data class gives us stable `equals` semantics for
 * `distinctUntilChanged` and documents exactly which settings can influence
 * search provider registration and prefix configuration.
 */
private data class SearchRuntimeSettingsProjection(
    val searchSources: List<SearchSource>,
    val contactsSearchEnabled: Boolean,
    val filesSearchEnabled: Boolean,
    val prefixConfigurations: ProviderPrefixConfiguration
) {
    companion object {
        fun from(settings: com.milki.launcher.domain.model.LauncherSettings): SearchRuntimeSettingsProjection {
            return SearchRuntimeSettingsProjection(
                searchSources = settings.searchSources,
                contactsSearchEnabled = settings.contactsSearchEnabled,
                filesSearchEnabled = settings.filesSearchEnabled,
                prefixConfigurations = settings.prefixConfigurations
            )
        }
    }
}
