/**
 * SearchProviderConfig.kt - Configuration for search providers without implementation
 *
 * This file defines the CONFIGURATION for search providers. The actual search
 * implementation is handled by SearchProvider implementations in the data layer.
 *
 * ARCHITECTURE OVERVIEW:
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    DOMAIN LAYER                             │
 * │  SearchProviderConfig (data class) - semantic config only    │
 * │  SearchProvider (interface) - defines search contract       │
 * └─────────────────────────────────────────────────────────────┘
 *                              │
 *                              ▼
 * ┌─────────────────────────────────────────────────────────────┐
 * │                     DATA LAYER                              │
 * │  WebSearchProvider, ContactsSearchProvider, etc.            │
 * │  (implement SearchProvider interface)                       │
 * └─────────────────────────────────────────────────────────────┘
 */

package com.milki.launcher.domain.model

/**
 * Configuration for a search provider's semantic metadata.
 *
 * This is a pure data class that contains semantic provider information
 * used by query parsing and provider identification. It does NOT contain
 * search implementation logic.
 *
 * The search logic is implemented in SearchProvider implementations
 * (see domain/repository/SearchProvider.kt interface).
 *
 * @property providerId Unique identifier for this provider.
 *                       Used to match with prefix configurations.
 *                       Must match one of the constants in ProviderId.
 * @property prefix The default prefix that activates this provider (e.g., "s", "c", "y").
 *                   This is the fallback prefix if no custom configuration exists.
 *                   NOTE: This is now the DEFAULT prefix - users can configure different ones.
 * @property name Human-readable name for display (e.g., "Web Search", "Contacts")
 * @property description Short description shown in hints
 * IMPORTANT ARCHITECTURE NOTE:
 * This model intentionally contains ONLY semantic provider metadata.
 * It must not contain UI framework types (such as Compose Color/ImageVector)
 * because this class lives in the domain layer.
 *
 * Visual representation (icons/colors) is mapped in the presentation layer
 * using providerId. This keeps domain logic reusable and test-friendly.
 *
 * Example:
 * ```kotlin
 * val webConfig = SearchProviderConfig(
 *     providerId = ProviderId.WEB,
 *     prefix = "s",
 *     name = "Web Search",
 *     description = "Search the web"
 * )
 * ```
 */
data class SearchProviderConfig(
    val providerId: String,
    val prefix: String,
    val name: String,
    val description: String
)
