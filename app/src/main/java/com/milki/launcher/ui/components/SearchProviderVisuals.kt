/**
 * SearchProviderVisuals.kt - Presentation-layer visual mapping for search providers
 *
 * WHY THIS FILE EXISTS:
 * The domain layer intentionally contains semantic provider metadata only
 * (providerId, name, description, prefix). Visual representation belongs to
 * presentation, so this file maps providerId -> icon/color for Compose UI.
 *
 * ARCHITECTURE BENEFIT:
 * - Domain stays UI-framework agnostic
 * - UI can change icon/color choices without touching domain/data layers
 * - Tests for query parsing/provider behavior do not require Compose types
 *
 * IMPORTANT DESIGN RULE:
 * Colors in this mapping use Material theme roles (theme tokens), not hardcoded
 * hex values. This ensures visuals adapt naturally across light/dark and dynamic
 * color themes.
 */

package com.milki.launcher.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.milki.launcher.domain.model.ProviderId

/**
 * UI-only visual representation for a search provider.
 *
 * @property icon The icon shown in search UI for the provider
 * @property accentColor The themed accent color used for bars/icons/text highlights
 */
data class SearchProviderVisual(
    val icon: ImageVector,
    val accentColor: Color
)

/**
 * Resolve the visual representation for a provider ID.
 *
 * This function is composable because it depends on MaterialTheme color roles.
 * The returned value is memoized to avoid re-allocating visual objects on
 * every recomposition when provider/theme values are unchanged.
 *
 * @param providerId Semantic provider identifier from SearchProviderConfig
 * @return Provider visual mapping, or null when no provider mode is active
 */
@Composable
fun rememberSearchProviderVisual(providerId: String?): SearchProviderVisual? {
    val colorScheme = MaterialTheme.colorScheme

    return remember(providerId, colorScheme) {
        when (providerId) {
            ProviderId.WEB -> SearchProviderVisual(
                icon = Icons.Filled.Search,
                accentColor = colorScheme.primary
            )
            ProviderId.CONTACTS -> SearchProviderVisual(
                icon = Icons.Filled.Person,
                accentColor = colorScheme.secondary
            )
            ProviderId.YOUTUBE -> SearchProviderVisual(
                icon = Icons.Filled.PlayArrow,
                accentColor = colorScheme.tertiary
            )
            ProviderId.FILES -> SearchProviderVisual(
                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                accentColor = colorScheme.primaryContainer
            )
            else -> null
        }
    }
}
