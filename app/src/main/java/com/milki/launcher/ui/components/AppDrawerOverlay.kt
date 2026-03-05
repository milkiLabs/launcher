/**
 * AppDrawerOverlay.kt - Full-screen app drawer shown above homescreen
 *
 * FEATURE SUMMARY:
 * - Shows all installed apps in an adaptive grid (adjusts column count for
 *   different screen widths such as phones, foldables, and tablets).
 * - Supports sort dropdown (A→Z, Z→A, last update date) with a visual checkmark
 *   indicating the currently active sort mode for clear UX feedback.
 * - Supports long-press icon menu via AppGridItem (same pattern as search/home).
 * - Supports external drag start from each icon (same platform DnD bridge), and
 *   notifies host to close drawer immediately when drag starts.
 * - Supports swipe-down-to-close, but ONLY when grid is currently scrolled to top.
 *
 * SYSTEM BAR HANDLING:
 * The drawer is hosted inside a ModalBottomSheet with contentWindowInsets zeroed
 * out, so this composable is responsible for its own status-bar and navigation-bar
 * padding. We apply statusBarsPadding() and navigationBarsPadding() to the
 * content column so header text and grid items never render behind system UI.
 *
 * DESIGN NOTE:
 * This composable intentionally lives in the same launcher composition tree rather
 * than a separate Dialog window. This keeps drag-host behavior aligned with the
 * home surface and avoids additional window-layer complexity.
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.milki.launcher.domain.model.AppSearchResult
import com.milki.launcher.presentation.drawer.AppDrawerSortMode
import com.milki.launcher.presentation.drawer.AppDrawerUiState
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * AppDrawerOverlay - Full-screen drawer surface.
 *
 * @param uiState Drawer state from AppDrawerViewModel.
 * @param onDismiss Called when drawer should close.
 * @param onSortModeSelected Called when user selects a sort mode from dropdown.
 * @param modifier Optional modifier.
 */
@Composable
fun AppDrawerOverlay(
    uiState: AppDrawerUiState,
    onDismiss: () -> Unit,
    onSortModeSelected: (AppDrawerSortMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val actionHandler = LocalSearchActionHandler.current

    /**
     * Controls visibility of the sort dropdown menu in the header row.
     */
    var showSortMenu by remember { mutableStateOf(false) }

    /**
     * Strict no-lag top reset strategy:
     *
     * We key the entire drawer surface by sort mode. When sort mode changes,
     * Compose recreates this subtree, which creates a fresh LazyGridState with
     * index 0 / offset 0 immediately.
     *
     * This avoids coroutine scheduling and post-sort scroll work, producing an
     * instant "starts from top" result even on large app lists.
     */
    key(uiState.sortMode) {
        val gridState = rememberLazyGridState()

        Surface(
            modifier = modifier
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    // Apply system bar insets so content never renders behind the
                    // status bar or navigation bar. The hosting ModalBottomSheet
                    // has contentWindowInsets zeroed out, so inset handling is
                    // this composable's responsibility.
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.mediumLarge, vertical = Spacing.mediumLarge)
            ) {
                // ── Header row: title + sort button ──────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All apps",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Box {
                        TextButton(onClick = { showSortMenu = true }) {
                            Text(text = "Sort")
                        }

                        // ── Sort mode dropdown ──────────────────────────────
                        // Iterates over all enum values so adding a new sort
                        // mode only requires adding an enum entry — no UI
                        // boilerplate needed here.
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            AppDrawerSortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.displayName) },
                                    onClick = {
                                        onSortModeSelected(mode)
                                        showSortMenu = false
                                    },
                                    // Show a checkmark next to the currently active
                                    // sort mode so the user always knows which ordering
                                    // is applied. The icon is placed as a trailing
                                    // element following Material Design patterns.
                                    trailingIcon = if (mode == uiState.sortMode) {
                                        {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Active sort mode",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    }
                }

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    // ── App grid ─────────────────────────────────────────────
                    // Uses Adaptive columns so the grid automatically picks the
                    // right column count for the available width:
                    //   - Phone portrait  (~360dp) → 4 columns
                    //   - Phone landscape (~720dp) → 8 columns
                    //   - Tablet portrait (~600dp) → 7 columns
                    //
                    // The minimum cell width is derived from the standard app-grid
                    // icon size (IconSize.appGrid = 56dp) plus comfortable label
                    // room (Spacing.large = 24dp), totalling 80dp per column.
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = IconSize.appGrid + Spacing.large),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = Spacing.medium),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        verticalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        items(
                            items = uiState.sortedApps,
                            key = { "${it.packageName}/${it.activityName}" },
                            contentType = { "drawer_app_item" }
                        ) { appInfo ->
                            AppGridItem(
                                appInfo = appInfo,
                                onClick = {
                                    // Reuse the same action pipeline as search results so app
                                    // launching behavior stays centralized in ActionExecutor.
                                    actionHandler(SearchResultAction.Tap(AppSearchResult(appInfo)))
                                    onDismiss()
                                },
                                onExternalDragStarted = {
                                    // Required UX: when drawer drag starts, close drawer first,
                                    // then user can drop icon on homescreen target.
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
