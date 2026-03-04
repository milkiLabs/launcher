package com.milki.launcher.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.milki.launcher.domain.model.UrlSearchResult
import com.milki.launcher.ui.theme.IconSize

/**
 * Renders a direct URL result with optional resolved handler-app affordance.
 */
@Composable
fun UrlSearchResultItem(
    result: UrlSearchResult,
    onOpenInApp: () -> Unit,
    @Suppress("UNUSED_PARAMETER")
    onOpenInBrowser: (() -> Unit)? = null
) {
    val leadingIcon: ImageVector = if (result.handlerApp != null) {
        Icons.Default.Language
    } else {
        Icons.Default.Info
    }

    val trailingContent: (@Composable () -> Unit)? = if (result.handlerApp != null) {
        {
            Icon(
                imageVector = Icons.Default.ArrowOutward,
                contentDescription = "Opens in ${result.handlerApp.label}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(IconSize.small)
            )
        }
    } else {
        null
    }

    SearchResultListItem(
        headlineText = result.title,
        supportingText = result.displayUrl,
        leadingIcon = leadingIcon,
        trailingContent = trailingContent,
        onClick = onOpenInApp
    )
}
