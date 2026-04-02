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

package com.milki.launcher.ui.components.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Person
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
fun rememberSearchProviderVisual(
    providerId: String?,
    customAccentHex: String? = null
): SearchProviderVisual? {
    val colorScheme = MaterialTheme.colorScheme

    return remember(providerId, customAccentHex, colorScheme) {
        val customAccentColor = parseHexColorOrNull(customAccentHex)

        when (providerId) {
            ProviderId.CONTACTS -> SearchProviderVisual(
                icon = Icons.Filled.Person,
                accentColor = customAccentColor ?: colorScheme.secondary
            )
            ProviderId.FILES -> SearchProviderVisual(
                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                accentColor = customAccentColor ?: colorScheme.primaryContainer
            )
            null -> null
            else -> SearchProviderVisual(
                icon = Icons.Filled.Search,
                accentColor = customAccentColor ?: colorScheme.primary
            )
        }
    }
}

/**
 * Parses #RRGGBB into Compose Color.
 * Returns null when input is invalid.
 */
private fun parseHexColorOrNull(hex: String?): Color? {
    val normalized = hex?.trim()?.uppercase() ?: return null
    val withHash = if (normalized.startsWith("#")) normalized else "#$normalized"
    if (!Regex("^#[0-9A-F]{6}$").matches(withHash)) {
        return null
    }

    val red = withHash.substring(1, 3).toInt(16)
    val green = withHash.substring(3, 5).toInt(16)
    val blue = withHash.substring(5, 7).toInt(16)
    return Color(red = red, green = green, blue = blue)
}
