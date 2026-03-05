/**
 * LauncherScreen.kt - Main home screen of the launcher with multi-mode search
 *
 * This is the main UI of the launcher. It displays a transparent background
 * that shows the user's system wallpaper with a pinned items grid.
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpOffset
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.presentation.home.HomeUiState
import com.milki.launcher.presentation.search.SearchUiState
import androidx.compose.runtime.mutableStateOf
import com.milki.launcher.ui.components.AppSearchDialog
import com.milki.launcher.ui.components.DraggablePinnedItemsGrid
import com.milki.launcher.ui.components.FolderPopupDialog
import com.milki.launcher.ui.theme.Spacing
import com.milki.launcher.util.openFile
import com.milki.launcher.util.launchPinnedApp
import com.milki.launcher.util.launchAppShortcut

/**
 * LauncherScreen - The main home screen of the launcher.
 *
 * Displays a transparent background showing the user's system wallpaper,
 * with a pinned items grid. The search dialog opens when the user presses the home button.
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
 * @param onItemDroppedToHome Callback for external drag payload drops (app/file/contact)
 * @param onOpenSettings Called when user opens the homescreen long-press menu and selects Settings
 */
@Composable
fun LauncherScreen(
    searchUiState: SearchUiState,
    homeUiState: HomeUiState,
    onQueryChange: (String) -> Unit,
    onDismissSearch: () -> Unit,
    onPinnedItemClick: (HomeItem) -> Unit,
    onPinnedItemLongPress: (HomeItem) -> Unit,
    onPinnedItemMove: (itemId: String, newPosition: GridPosition) -> Unit,
    onItemDroppedToHome: (HomeItem, GridPosition) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    isHomescreenMenuOpen: Boolean = false,
    onHomescreenMenuOpenChange: (Boolean) -> Unit = {},
    // ---- Folder lifecycle callbacks ----
    /** Called when a non-folder icon is dropped onto another non-folder icon. */
    onCreateFolder: (item1: HomeItem, item2: HomeItem, atPosition: GridPosition) -> Unit = { _, _, _ -> },
    /** Called when a non-folder icon is dropped onto an existing folder icon. */
    onAddItemToFolder: (folderId: String, item: HomeItem) -> Unit = { _, _ -> },
    /** Called when a folder icon is dropped onto another folder icon. */
    onMergeFolders: (sourceFolderId: String, targetFolderId: String) -> Unit = { _, _ -> },
    /** Called when the user taps the scrim or back-navigates from the folder popup. */
    onFolderClose: () -> Unit = {},
    /** Called when the user edits the folder title inside the popup and confirms. */
    onFolderRename: (folderId: String, newName: String) -> Unit = { _, _ -> },
    /** Called when the user taps an icon inside the folder popup to launch it. */
    onFolderItemClick: (HomeItem) -> Unit = {},
    /** Called from the context menu inside the folder popup to remove the item. */
    onFolderItemRemove: (folderId: String, itemId: String) -> Unit = { _, _ -> },
    /** Called after the user reorders items via drag inside the folder popup. */
    onFolderItemReorder: (folderId: String, newChildren: List<HomeItem>) -> Unit = { _, _ -> },
    /**
     * Called when the user finishes dragging an item OUT of the folder popup
     * and releases it over an empty home grid cell.
     */
    onExtractItemFromFolder: (folderId: String, itemId: String, targetPosition: GridPosition) -> Unit = { _, _, _ -> },
    /**
     * Called when the user drags an item from one folder popup and drops it
     * onto a DIFFERENT folder icon on the home grid.
     */
    onMoveFolderItemToFolder: (sourceFolderId: String, itemId: String, targetFolderId: String) -> Unit = { _, _, _ -> },
    /**
     * Called when the user drags a folder child icon and drops it onto a NON-FOLDER
     * home grid icon.  The two icons should be merged into a brand new folder.
     *
     * This mirrors the existing drag-two-grid-icons-together behaviour; the only
     * difference is that the dragged item starts inside a folder rather than the
     * flat home grid.
     *
     * Parameters:
     *   sourceFolderId – the folder the child came from.
     *   childItem      – the item that was dragged (the folder child).
     *   occupantItem   – the existing grid icon it was dropped onto.
     *   atPosition     – the grid cell where the new folder should appear.
     */
    onFolderChildDroppedOnItem: (sourceFolderId: String, childItem: HomeItem, occupantItem: HomeItem, atPosition: GridPosition) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var homescreenMenuAnchorPx by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(searchUiState.isSearchVisible) {
        if (searchUiState.isSearchVisible) {
            onHomescreenMenuOpenChange(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (isHomescreenMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onHomescreenMenuOpenChange(false)
                    }
            )
        }

        DraggablePinnedItemsGrid(
            items = homeUiState.pinnedItems,
            onItemClick = onPinnedItemClick,
            onItemLongPress = onPinnedItemLongPress,
            onItemMove = onPinnedItemMove,
            onEmptyAreaLongPress = { touchOffset ->
                homescreenMenuAnchorPx = touchOffset
                onHomescreenMenuOpenChange(true)
            },
            onItemDroppedToHome = { item, position ->
                onItemDroppedToHome(item, position)
                onDismissSearch()
            },
            // ---- Folder operation callbacks -----
            // Routed from DraggablePinnedItemsGrid's occupancy-check logic in onDragEnd.
            onCreateFolder = onCreateFolder,
            onAddItemToFolder = onAddItemToFolder,
            onMergeFolders = onMergeFolders,
            onFolderItemExtracted = onExtractItemFromFolder,
            onMoveFolderItemToFolder = onMoveFolderItemToFolder,
            onFolderChildDroppedOnItem = onFolderChildDroppedOnItem,
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.mediumLarge)
                .align(Alignment.Center)
        )

        DropdownMenu(
            expanded = isHomescreenMenuOpen,
            onDismissRequest = { onHomescreenMenuOpenChange(false) },
            offset = with(density) {
                DpOffset(
                    x = homescreenMenuAnchorPx.x.toDp(),
                    y = homescreenMenuAnchorPx.y.toDp()
                )
            }
        ) {
            DropdownMenuItem(
                text = { Text("Settings") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null
                    )
                },
                onClick = {
                    onHomescreenMenuOpenChange(false)
                    onOpenSettings()
                }
            )
        }

        // ─── Folder popup dialog ──────────────────────────────────────────────────
        // Displayed as a full-screen overlay (with its own scrim) on top of the
        // home grid when the user taps a folder icon.
        //
        // The dialog renders INSIDE the root Box so it sits above all home-grid
        // content (items, drop highlights, homescreen menu dropdown).
        //
        // It is keyed on [folder.id] so Compose recreates the composable fresh
        // when a different folder is opened (avoids stale local state from the
        // previously open folder leaking into the new one).
        homeUiState.openFolderItem?.let { folder ->
            key(folder.id) {
                FolderPopupDialog(
                    folder = folder,
                    onClose = onFolderClose,
                    onRenameFolder = { newName ->
                        onFolderRename(folder.id, newName)
                    },
                    onItemClick = onFolderItemClick,
                    onReorderFolderItems = { newChildren ->
                        onFolderItemReorder(folder.id, newChildren)
                    },
                    onRemoveItemFromFolder = { itemId ->
                        onFolderItemRemove(folder.id, itemId)
                    }
                )
            }
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
        is HomeItem.PinnedContact -> openPinnedContact(item, context)
        is HomeItem.AppShortcut -> openAppShortcut(item, context)
        // FolderItem taps are intercepted in MainActivity's onPinnedItemClick
        // BEFORE reaching this function (checked as HomeItem.FolderItem and
        // routed to homeViewModel.openFolder). This branch is here solely to
        // make the when expression exhaustive and avoid a compile error.
        is HomeItem.FolderItem -> { /* handled upstream; no-op here */ }
    }
}

/**
 * Launches a pinned app.
 */
private fun openPinnedApp(item: HomeItem.PinnedApp, context: Context) {
    if (!launchPinnedApp(context, item)) {
        Toast.makeText(context, "App not found: ${item.label}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens a pinned file with an appropriate app.
 */
private fun openPinnedFile(item: HomeItem.PinnedFile, context: Context) {
    val uri = Uri.parse(item.uri)
    openFile(context, uri, item.mimeType, item.name)
}

/**
 * Opens a pinned contact using the dialer with the contact's primary number.
 */
private fun openPinnedContact(item: HomeItem.PinnedContact, context: Context) {
    val phoneNumber = item.primaryPhone
    if (phoneNumber.isNullOrBlank()) {
        Toast.makeText(context, "No phone number for ${item.displayName}", Toast.LENGTH_SHORT).show()
        return
    }

    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:$phoneNumber")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(dialIntent)
    } catch (_: Exception) {
        Toast.makeText(context, "No phone app found", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens an app shortcut.
 *
 * This uses LauncherApps to launch the shortcut.
 * TODO: Implement using LauncherApps.pinShortcut() API
 */
private fun openAppShortcut(item: HomeItem.AppShortcut, context: Context) {
    if (!launchAppShortcut(context, item)) {
        Toast.makeText(context, "App not found: ${item.shortLabel}", Toast.LENGTH_SHORT).show()
    }
}
