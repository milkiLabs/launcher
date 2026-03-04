package com.milki.launcher.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.milki.launcher.domain.model.WebSearchResult

/**
 * Renders a web search result row.
 */
@Composable
fun WebSearchResultItem(
    result: WebSearchResult,
    accentColor: Color?,
    onClick: () -> Unit
) {
    SearchResultListItem(
        headlineText = result.title,
        supportingText = result.engine,
        leadingIcon = Icons.Default.Search,
        accentColor = accentColor ?: MaterialTheme.colorScheme.primary,
        onClick = onClick
    )
}
