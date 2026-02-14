/**
 * LauncherScreen.kt - Main home screen of the launcher with multi-mode search
 *
 * This is the main UI of the launcher. It displays a simple black background
 * that users can tap to open the search dialog. The search supports multiple
 * modes via prefix shortcuts:
 * - No prefix: Search installed apps
 * - "s ": Web search
 * - "c ": Contacts search (requires permission)
 * - "y ": YouTube search
 *
 * The contacts search feature includes permission handling - if permission is not
 * granted, shows a button to request permission instead of search results.
 */

package com.milki.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.Contact
import com.milki.launcher.ui.components.AppSearchDialog

/**
 * LauncherScreen - The main home screen of the launcher.
 *
 * Displays a simple black background with a "Tap to search" hint.
 * When tapped, opens the AppSearchDialog with multi-mode search capabilities.
 *
 * @param showSearch Whether the search dialog is currently visible
 * @param searchQuery Current text in the search field
 * @param onShowSearch Called when user taps the home screen background
 * @param onHideSearch Called when search dialog should close
 * @param onSearchQueryChange Called when user types in search field
 * @param installedApps List of all installed apps (from ViewModel)
 * @param recentApps List of recently launched apps (from ViewModel)
 * @param onLaunchApp Called when user selects an app from the list
 * @param onSearchWeb Called when user selects a web search result
 * @param onSearchYouTube Called when user selects a YouTube search result
 * @param hasContactsPermission Whether READ_CONTACTS permission is granted
 * @param onRequestContactsPermission Called when user requests contacts permission
 * @param searchContacts Function to search contacts (requires permission)
 * @param onCallContact Called when user clicks a contact to make a phone call
 */
@Composable
fun LauncherScreen(
    showSearch: Boolean,
    searchQuery: String,
    onShowSearch: () -> Unit,
    onHideSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    installedApps: List<AppInfo>,
    recentApps: List<AppInfo>,
    onLaunchApp: (AppInfo) -> Unit,
    onSearchWeb: (query: String, engine: String) -> Unit = { _, _ -> },
    onSearchYouTube: (query: String) -> Unit = {},
    hasContactsPermission: Boolean = false,
    onRequestContactsPermission: () -> Unit = {},
    searchContacts: suspend (query: String) -> List<Contact> = { emptyList() },
    onCallContact: (contact: Contact) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onShowSearch() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Tap to search",
            color = Color.White.copy(alpha = 0.3f),
            style = MaterialTheme.typography.bodyLarge
        )
    }

    // Only show the search dialog when showSearch is true.
    if (showSearch) {
        AppSearchDialog(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            installedApps = installedApps,
            recentApps = recentApps,
            onDismiss = onHideSearch,
            onLaunchApp = onLaunchApp,
            onSearchWeb = onSearchWeb,
            onSearchYouTube = onSearchYouTube,
            hasContactsPermission = hasContactsPermission,
            onRequestContactsPermission = onRequestContactsPermission,
            searchContacts = searchContacts,
            onCallContact = onCallContact
        )
    }
}
