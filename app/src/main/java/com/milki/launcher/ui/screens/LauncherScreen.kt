/**
 * LauncherScreen.kt - Main home screen of the launcher with multi-mode search
 *
 * This is the main UI of the launcher. It displays a transparent background
 * that shows the user's system wallpaper, with pinned items grid and a search hint.
 *
 * ARCHITECTURE:
 * This is a "dumb" UI component following the Unidirectional Data Flow pattern:
 * - State flows down from ViewModel via SearchUiState and HomeUiState
 * - Events flow up via callbacks
 * - No business logic in this file
 *
 * ACTION HANDLING:
 * Search result actions are handled via LocalSearchActionHandler (CompositionLocal),
 * not via callbacks. This eliminates prop drilling and simplifies the component hierarchy.
 *
 * The search supports multiple modes via prefix shortcuts:
 * - No prefix: Search installed apps
 * - "s ": Web search
 * - "c ": Contacts search (requires permission)
 * - "y ": YouTube search
 *
 * WALLPAPER:
 * The background is transparent, allowing the system wallpaper to show through.
 * This is achieved by setting windowShowWallpaper=true and windowBackground=transparent
 * in the theme (see themes.xml). This approach supports both static and live wallpapers.
 *
 * PINNED ITEMS:
 * The home screen displays a grid of pinned items (apps, files, shortcuts) using
 * DraggablePinnedItemsGrid, which allows users to:
 * - Tap: Opens/launches the item
 * - Long press (no movement): Shows dropdown menu with actions
 * - Long press + drag: Moves the item to a new position
 *
 * All actions from the dropdown menu are handled via LocalSearchActionHandler.
 */

package com.milki.launcher.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.presentation.home.HomeUiState
import com.milki.launcher.presentation.search.SearchUiState
import com.milki.launcher.ui.components.AppSearchDialog
import com.milki.launcher.ui.components.DraggablePinnedItemsGrid
import com.milki.launcher.ui.theme.Spacing

/**
 * LauncherScreen - The main home screen of the launcher.
 *
 * Displays a transparent background showing the user's system wallpaper,
 * with pinned items grid and a hint to press home to search when empty.
 * The search dialog opens when the user presses the home button.
 *
 * ACTION HANDLING:
 * Search result clicks are handled via LocalSearchActionHandler, which is
 * provided by MainActivity. This eliminates the need for callback props.
 *
 * PINNED ITEM ACTIONS:
 * - Long-press (no movement): Shows dropdown menu with actions (Unpin, App info for apps)
 * - Long-press + drag: Moves item to a new grid position
 *
 * WALLPAPER:
 * The background uses Color.Transparent to let the system wallpaper show through.
 * The wallpaper visibility is configured in themes.xml:
 * - android:windowShowWallpaper="true" - Enables wallpaper display
 * - android:windowBackground="@android:color/transparent" - Makes window transparent
 *
 * @param searchUiState Current search state from SearchViewModel
 * @param homeUiState Current home screen state from HomeViewModel
 * @param onQueryChange Called when user types in search field
 * @param onDismissSearch Called when search dialog should close
 * @param onPinnedItemClick Called when a pinned item is clicked
 * @param onPinnedItemLongPress Called when a pinned item is long-pressed (for menu)
 * @param onPinnedItemMove Called when a pinned item is dragged to a new position
 */
@Composable
fun LauncherScreen(
    searchUiState: SearchUiState,
    homeUiState: HomeUiState,
    onQueryChange: (String) -> Unit,
    onDismissSearch: () -> Unit,
    onPinnedItemClick: (HomeItem) -> Unit,
    onPinnedItemLongPress: (HomeItem) -> Unit,
    onPinnedItemMove: (itemId: String, newPosition: GridPosition) -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (homeUiState.pinnedItems.isEmpty()) {
            Text(
                text = "Press home to search",
                color = Color.White.copy(alpha = 0.3f),
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            /**
             * DraggablePinnedItemsGrid displays pinned items at their grid positions.
             * Users can drag items to rearrange them on the home screen.
             *
             * INTERACTION MODEL:
             * - Tap: Opens/launches the item
             * - Long press (no drag): Shows action menu
             * - Long press + drag: Moves item to new position
             */
            DraggablePinnedItemsGrid(
                items = homeUiState.pinnedItems,
                columns = 4,
                onItemClick = onPinnedItemClick,
                onItemLongPress = onPinnedItemLongPress,
                onItemMove = onPinnedItemMove,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.mediumLarge)
                    .align(Alignment.Center)
            )
        }
    }

    if (searchUiState.isSearchVisible) {
        AppSearchDialog(
            uiState = searchUiState,
            onQueryChange = onQueryChange,
            onDismiss = onDismissSearch
        )
    }
}

/**
 * Handles opening a pinned item.
 *
 * Dispatches to the appropriate handler based on item type.
 *
 * @param item The pinned item to open
 * @param context Android context for starting activities
 */
fun openPinnedItem(item: HomeItem, context: Context) {
    when (item) {
        is HomeItem.PinnedApp -> openPinnedApp(item, context)
        is HomeItem.PinnedFile -> openPinnedFile(item, context)
        is HomeItem.AppShortcut -> openAppShortcut(item, context)
    }
}

/**
 * Launches a pinned app.
 */
private fun openPinnedApp(item: HomeItem.PinnedApp, context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(item.packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "App not found: ${item.label}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens a pinned file with an appropriate app.
 */
private fun openPinnedFile(item: HomeItem.PinnedFile, context: Context) {
    val uri = Uri.parse(item.uri)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, item.mimeType.ifBlank { "application/octet-stream" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val chooserIntent = Intent.createChooser(intent, "Open ${item.name}").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open ${item.name}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens an app shortcut.
 *
 * This uses LauncherApps to launch the shortcut.
 * TODO: Implement using LauncherApps.pinShortcut() API
 */
private fun openAppShortcut(item: HomeItem.AppShortcut, context: Context) {
    // For now, just open the parent app
    val intent = context.packageManager.getLaunchIntentForPackage(item.packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "App not found: ${item.shortLabel}", Toast.LENGTH_SHORT).show()
    }
}
