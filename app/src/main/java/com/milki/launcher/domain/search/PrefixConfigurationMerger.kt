/**
 * PrefixConfigurationMerger.kt - Pure logic for merging search prefix configurations
 *
 * Combines user-customized prefix configurations with built-in provider defaults
 * and dynamic (user-defined) source prefixes into the single configuration map
 * consumed by [com.milki.launcher.domain.search.SearchProviderRegistry].
 *
 * MERGING RULES:
 * 1. Fixed providers (contacts/files) only appear when their feature is enabled.
 * 2. A user-customized config wins over the built-in default ("c" / "f").
 * 3. Dynamic source prefixes are appended last and win on any key collision.
 */

package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.PrefixConfig
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.ProviderPrefixConfiguration

object PrefixConfigurationMerger {

    /**
     * Merges fixed-provider prefixes (with default fallbacks) and dynamic
     * source prefixes into one effective configuration.
     *
     * @param prefixConfigurations User-customized prefix configurations.
     * @param contactsSearchEnabled Whether the contacts provider is enabled.
     * @param filesSearchEnabled Whether the files provider is enabled.
     * @param sourcePrefixConfigurations Prefix configs derived from enabled
     *        dynamic sources (keyed by source id).
     * @return The effective prefix configuration map.
     */
    fun merge(
        prefixConfigurations: ProviderPrefixConfiguration,
        contactsSearchEnabled: Boolean,
        filesSearchEnabled: Boolean,
        sourcePrefixConfigurations: ProviderPrefixConfiguration
    ): ProviderPrefixConfiguration {
        val fixedProviderConfigurations = buildMap {
            if (contactsSearchEnabled) {
                put(
                    ProviderId.CONTACTS,
                    prefixConfigurations[ProviderId.CONTACTS]
                        ?: PrefixConfig.defaults.getValue(ProviderId.CONTACTS)
                )
            }
            if (filesSearchEnabled) {
                put(
                    ProviderId.FILES,
                    prefixConfigurations[ProviderId.FILES]
                        ?: PrefixConfig.defaults.getValue(ProviderId.FILES)
                )
            }
        }

        return fixedProviderConfigurations + sourcePrefixConfigurations
    }
}
