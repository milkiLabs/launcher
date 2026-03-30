package com.milki.launcher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.milki.launcher.domain.model.SearchProviderConfig
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * Empty-state UI for search results.
 */
@Composable
fun EmptyState(
    searchQuery: String,
    activeProvider: SearchProviderConfig?,
    prefixHint: String,
    providerAccentColorById: Map<String, String>,
    modifier: Modifier = Modifier
) {
    val providerVisual = rememberSearchProviderVisual(
        providerId = activeProvider?.providerId,
        customAccentHex = activeProvider?.providerId?.let(providerAccentColorById::get)
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(
                horizontal = Spacing.extraLarge,
                vertical = Spacing.large
            )
        ) {
            val icon = providerVisual?.icon ?: Icons.Default.Search
            val tint = providerVisual?.accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(IconSize.appLarge),
                tint = tint.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(Spacing.mediumLarge))

            val message = when {
                searchQuery.isBlank() && activeProvider != null -> {
                    "No recent ${activeProvider.name.lowercase()} results\nType to search ${activeProvider.name.lowercase()}"
                }
                searchQuery.isBlank() -> {
                    "No recent apps\nType to search"
                }
                activeProvider != null -> {
                    "No ${activeProvider.name.lowercase()} results found"
                }
                else -> {
                    "No apps found"
                }
            }

            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            if (searchQuery.isBlank()) {
                Spacer(modifier = Modifier.height(Spacing.large))
                Text(
                    text = prefixHint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
