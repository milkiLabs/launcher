/**
 * PrefixConfig.kt - Configuration model for customizable search provider prefixes
 *
 * This file defines the data models for storing and managing prefix configurations
 * for search providers. The prefix configuration system allows users to:
 *
 * 1. CHANGE DEFAULT PREFIXES:
 *    Instead of "f" for files search, use "sd" or any other character(s)
 *
 * 2. ADD MULTIPLE PREFIXES PER PROVIDER:
 *    This is especially useful for multilingual users who want to use prefixes
 *    in their native language without switching keyboards.
 *    Example: Both "f" and "م" (Arabic letter) can trigger files search
 *
 * 3. PREFIXES CAN BE ONE OR MORE CHARACTERS:
 *    - Single character: "s", "c", "y", "f"
 *    - Multiple characters: "web", "find", "م"
 *
 * ARCHITECTURE:
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    PrefixConfiguration                       │
 * │  Map<ProviderId, PrefixConfig>                              │
 * │                                                              │
 * │  Example:                                                    │
 * │  {                                                          │
 * │    "web" -> PrefixConfig(["s", "ج"]),      // Arabic 'ج'    │
 * │    "files" -> PrefixConfig(["f", "م"]),    // Arabic 'م'    │
 * │    "contacts" -> PrefixConfig(["c"]),                       │
 * │    "youtube" -> PrefixConfig(["y", "yt"])                   │
 * │  }                                                          │
 * └─────────────────────────────────────────────────────────────┘
 *
 * DATA FLOW:
 * 1. User changes prefix in Settings UI
 * 2. SettingsViewModel updates LauncherSettings.prefixConfigurations
 * 3. SettingsRepositoryImpl persists to DataStore
 * 4. SearchProviderRegistry reads current configuration
 * 5. QueryParser uses configured prefixes to match queries
 */

package com.milki.launcher.domain.model

/**
 * Represents the prefix configuration for a single search provider.
 *
 * A provider can have multiple prefixes, allowing users to trigger
 * the same search using different characters or words.
 *
 * @property prefixes List of prefixes that trigger this provider.
 *                     Each prefix can be 1 or more characters.
 *                     The first prefix in the list is considered the "primary"
 *                     prefix and is used for display purposes.
 *
 * EXAMPLES:
 * ```kotlin
 * // Default configuration - single prefix
 * PrefixConfig(listOf("s"))
 *
 * // Multilingual configuration - English and Arabic
 * PrefixConfig(listOf("s", "ج"))  // 'ج' is Arabic letter equivalent to 'j'
 *
 * // Multi-character prefixes
 * PrefixConfig(listOf("y", "yt", "tube"))
 * ```
 */
data class PrefixConfig(
    val prefixes: List<String>
) {
    /**
     * Returns the primary prefix for display purposes.
     * This is the first prefix in the list.
     */
    val primaryPrefix: String
        get() = prefixes.firstOrNull() ?: ""

    /**
     * Check if this configuration contains the given prefix.
     * Used when parsing user queries.
     *
     * @param prefix The prefix to check
     * @return True if this provider is configured with this prefix
     */
    fun hasPrefix(prefix: String): Boolean = prefixes.contains(prefix)

    /**
     * Check if any of the configured prefixes match the start of the input.
     * This is used when the user types but hasn't added a space yet.
     *
     * @param input The user's input text
     * @return True if the input starts with any configured prefix (without space)
     */
    fun matchesPartial(input: String): Boolean {
        return prefixes.any { prefix ->
            input.startsWith(prefix) && input.length >= prefix.length
        }
    }

    companion object {
        /**
         * Create a PrefixConfig with a single prefix.
         * Convenience factory method for common use case.
         *
         * @param prefix The single prefix to use
         * @return PrefixConfig with just this prefix
         */
        fun single(prefix: String): PrefixConfig = PrefixConfig(listOf(prefix))

        /**
         * Default prefix configurations for all built-in providers.
         * These are used when the user hasn't customized any prefixes.
         *
         * The keys here MUST match the providerId in SearchProviderConfig.
         */
        val defaults: Map<String, PrefixConfig> = mapOf(
            ProviderId.WEB to PrefixConfig(listOf("s")),
            ProviderId.CONTACTS to PrefixConfig(listOf("c")),
            ProviderId.YOUTUBE to PrefixConfig(listOf("y")),
            ProviderId.FILES to PrefixConfig(listOf("f"))
        )
    }
}

/**
 * Constants for provider IDs.
 *
 * These IDs are used as keys in the prefix configuration map.
 * They must remain stable across app versions - do not change these values
 * as they are stored in user preferences.
 *
 * WHY USE IDs INSTEAD OF PREFIXES?
 * - Prefixes can be changed by users
 * - IDs remain constant and stable
 * - IDs are used for storage keys and lookups
 * - This allows the same provider to have multiple prefixes
 */
object ProviderId {
    /**
     * ID for the web search provider (Google, DuckDuckGo, etc.)
     */
    const val WEB = "web"

    /**
     * ID for the contacts search provider
     */
    const val CONTACTS = "contacts"

    /**
     * ID for the YouTube search provider
     */
    const val YOUTUBE = "youtube"

    /**
     * ID for the files search provider
     */
    const val FILES = "files"

    /**
     * List of all valid provider IDs.
     * Used for validation and iteration.
     */
    val all: List<String> = listOf(WEB, CONTACTS, YOUTUBE, FILES)
}
