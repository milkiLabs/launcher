package com.milki.launcher.data.repository.settings

import com.milki.launcher.domain.model.PrefixConfig
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.model.SearchSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal fun parsePrefixConfigurations(json: String?): ProviderPrefixConfiguration {
    if (json.isNullOrBlank()) {
        return emptyMap()
    }

    return runCatching {
        val serializedConfiguration: SerializedPrefixConfiguration =
            settingsStorageJson.decodeFromString(json)

        serializedConfiguration
            .filterValues { it.isNotEmpty() }
            .mapValues { (_, prefixes) -> PrefixConfig(prefixes) }
    }.getOrElse {
        emptyMap()
    }
}

internal fun serializePrefixConfigurations(config: ProviderPrefixConfiguration): String {
    if (config.isEmpty()) {
        return "{}"
    }

    val serializedConfiguration: SerializedPrefixConfiguration =
        config
            .mapValues { (_, prefixConfig) -> prefixConfig.prefixes }
            .filterValues { it.isNotEmpty() }

    if (serializedConfiguration.isEmpty()) {
        return "{}"
    }

    return settingsStorageJson.encodeToString(serializedConfiguration)
}

internal fun parseSearchSources(
    json: String?,
    isInitialized: Boolean
): List<SearchSource> {
    if (!isInitialized) {
        if (json.isNullOrBlank()) {
            return SearchSource.defaultSources()
        }

        return runCatching {
            val decoded: SerializedSearchSources =
                settingsStorageJson.decodeFromString(json)
            normalizeAndValidateSearchSources(decoded)
        }.getOrElse {
            SearchSource.defaultSources()
        }
    }

    if (json.isNullOrBlank()) {
        return emptyList()
    }

    return runCatching {
        val decoded: SerializedSearchSources =
            settingsStorageJson.decodeFromString(json)
        normalizeAndValidateSearchSources(decoded)
    }.getOrElse {
        emptyList()
    }
}

internal fun serializeSearchSources(sources: List<SearchSource>): String {
    val normalized = normalizeAndValidateSearchSources(sources)
    return settingsStorageJson.encodeToString(normalized)
}

internal fun normalizeAndValidateSearchSources(
    rawSources: List<SearchSource>
): List<SearchSource> {
    val normalized = rawSources.mapIndexed { index, source ->
        val normalizedPrefixes = source.prefixes
            .map(SearchSource.Companion::normalizePrefix)
            .filter { it.isNotBlank() && !it.contains(" ") }
            .distinct()

        val safeName = source.name.trim().ifBlank { "Source ${index + 1}" }
        val safeTemplate = if (SearchSource.isValidUrlTemplate(source.urlTemplate)) {
            source.urlTemplate.trim()
        } else {
            "https://www.google.com/search?q={query}"
        }

        source.copy(
            name = safeName,
            urlTemplate = safeTemplate,
            prefixes = normalizedPrefixes,
            accentColorHex = SearchSource.normalizeHexColor(source.accentColorHex)
        )
    }

    val seenPrefixes = mutableSetOf<String>()
    return normalized.map { source ->
        val filteredPrefixes = source.prefixes.filter { prefix ->
            if (prefix in seenPrefixes) {
                false
            } else {
                seenPrefixes.add(prefix)
                true
            }
        }
        source.copy(prefixes = filteredPrefixes)
    }
}
