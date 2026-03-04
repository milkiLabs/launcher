package com.milki.launcher.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.milki.launcher.domain.model.YouTubeSearchResult

/**
 * Renders a YouTube search result row.
 */
@Composable
fun YouTubeSearchResultItem(
    result: YouTubeSearchResult,
    accentColor: Color?,
    onClick: () -> Unit
) {
    SearchResultListItem(
        headlineText = result.title,
        supportingText = "YouTube",
        leadingIcon = Icons.Default.PlayArrow,
        accentColor = accentColor ?: MaterialTheme.colorScheme.primary,
        onClick = onClick
    )
}
