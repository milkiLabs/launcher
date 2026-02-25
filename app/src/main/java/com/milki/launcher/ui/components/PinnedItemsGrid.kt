/**
 * PinnedItemsGrid.kt - Grid layout for pinned home screen items
 *
 * This component displays pinned items in a responsive grid layout on the home screen.
 * It adapts to different screen sizes and provides access to all pinned items.
 *
 * LAYOUT:
 * - 4 columns on most phones
 * - Items are displayed in rows with consistent spacing
 * - Empty state is shown when no items are pinned
 *
 * INTERACTION:
 * - Tap: Open/launch the item
 * - Long press: Show dropdown menu with actions (handled by PinnedItem composable)
 *
 * The dropdown menu is shown directly by the PinnedItem composable using
 * ItemActionMenu. Actions flow through LocalSearchActionHandler for consistency
 * with search result actions.
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.theme.Spacing

/**
 * PinnedItemsGrid displays pinned items in a responsive grid.
 *
 * GRID CONFIGURATION:
 * - 4 columns (fixed for consistency with most launchers)
 * - Adaptive spacing between items
 * - Vertical scrolling when items exceed screen height
 *
 * INTERACTION:
 * Each PinnedItem handles its own long-press interaction, showing a
 * dropdown menu with actions. The menu actions are emitted through
 * LocalSearchActionHandler for unified action handling.
 *
 * @param items List of pinned items to display
 * @param onItemClick Called when user taps an item
 * @param modifier Optional modifier for external customization
 */
@Composable
fun PinnedItemsGrid(
    items: List<HomeItem>,
    onItemClick: (HomeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        EmptyHomeGrid(modifier = modifier)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.smallMedium),
            verticalArrangement = Arrangement.spacedBy(Spacing.smallMedium)
        ) {
            items(
                items = items,
                key = { it.id }
            ) { item ->
                /**
                 * PinnedItem handles both tap and long-press interactions.
                 * Long-press shows the ItemActionMenu dropdown with available actions.
                 */
                PinnedItem(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = { /* Handled internally by PinnedItem */ }
                )
            }
        }
    }
}

/**
 * Empty state displayed when no items are pinned.
 *
 * Shows a hint text encouraging the user to search and pin items.
 */
@Composable
private fun EmptyHomeGrid(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Tap to search",
            color = Color.White.copy(alpha = 0.3f),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
