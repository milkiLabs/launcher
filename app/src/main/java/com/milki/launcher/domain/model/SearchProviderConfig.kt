/**
 * SearchProviderConfig.kt - Configuration for search providers without implementation
 *
 * This file defines the CONFIGURATION for search providers. The actual search
 * implementation is handled by SearchProvider implementations in the data layer.
 *
 * ARCHITECTURE OVERVIEW:
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    DOMAIN LAYER                             │
 * │  SearchProviderConfig (data class) - just display config    │
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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Configuration for a search provider's visual appearance.
 *
 * This is a pure data class that only contains the properties needed
 * to display the provider in the UI. It does NOT contain any search logic.
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
 * @property color Accent color for visual indicators
 * @property icon Icon representing this search type
 *
 * Example:
 * ```kotlin
 * val webConfig = SearchProviderConfig(
 *     providerId = ProviderId.WEB,
 *     prefix = "s",
 *     name = "Web Search",
 *     description = "Search the web",
 *     color = Color(0xFF4285F4),
 *     icon = Icons.Default.Search
 * )
 * ```
 */
data class SearchProviderConfig(
    val providerId: String,
    val prefix: String,
    val name: String,
    val description: String,
    val color: Color,
    val icon: ImageVector
)
