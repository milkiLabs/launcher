/**
 * LauncherScreen.kt - Main home screen of the launcher with multi-mode search
 *
 * This is the main UI of the launcher. It displays a simple black background
 * that users can tap to open the search dialog.
 *
 * ARCHITECTURE:
 * This is a "dumb" UI component following the Unidirectional Data Flow pattern:
 * - State flows down from ViewModel via SearchUiState
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
import com.milki.launcher.presentation.search.SearchUiState
import com.milki.launcher.ui.components.AppSearchDialog

/**
 * LauncherScreen - The main home screen of the launcher.
 *
 * Displays a simple black background with a "Tap to search" hint.
 * When tapped, opens the AppSearchDialog with multi-mode search capabilities.
 *
 * ACTION HANDLING:
 * Search result clicks are handled via LocalSearchActionHandler, which is
 * provided by MainActivity. This eliminates the need for callback props.
 *
 * @param uiState Current search state from ViewModel
 * @param onShowSearch Called when user taps the home screen background
 * @param onQueryChange Called when user types in search field
 * @param onDismissSearch Called when search dialog should close
 */
@Composable
fun LauncherScreen(
    uiState: SearchUiState,
    onShowSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onDismissSearch: () -> Unit
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

    if (uiState.isSearchVisible) {
        AppSearchDialog(
            uiState = uiState,
            onQueryChange = onQueryChange,
            onDismiss = onDismissSearch
        )
    }
}
