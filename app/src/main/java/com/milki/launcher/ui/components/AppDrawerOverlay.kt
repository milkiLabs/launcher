/**
 * AppDrawerOverlay.kt - Full-screen app drawer shown above homescreen
 *
 * FEATURE SUMMARY:
 * - Shows all installed apps.
 * - Supports sort dropdown (A→Z, Z→A, last update date).
 * - Supports long-press icon menu via AppGridItem (same pattern as search/home).
 * - Supports external drag start from each icon (same platform DnD bridge), and
 *   notifies host to close drawer immediately when drag starts.
 * - Supports swipe-down-to-close, but ONLY when grid is currently scrolled to top.
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import com.milki.launcher.domain.model.AppSearchResult
import com.milki.launcher.presentation.drawer.AppDrawerSortMode
import com.milki.launcher.presentation.drawer.AppDrawerUiState
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.ui.theme.Spacing
import kotlin.math.abs

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

        /**
         * A lightweight, derived check to know whether the grid is exactly at top.
         * We require BOTH index==0 and offset==0 for strict "top reached" semantics.
         */
        val isAtTop by remember {
            derivedStateOf {
                gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
            }
        }

        /**
         * Swipe threshold in pixels for dismiss gesture.
         * We derive from design-system spacing token to avoid hardcoded dp values.
         */
        val closeSwipeThresholdPx = with(LocalDensity.current) {
            Spacing.extraLarge.toPx()
        }

        Surface(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(isAtTop, closeSwipeThresholdPx) {
                    // Swipe-down close is only armed when the grid is at top.
                    if (!isAtTop) return@pointerInput

                    awaitPointerEventScope {
                        while (true) {
                            /**
                             * Read down/move events in the INITIAL pass so this detector can
                             * observe gesture deltas even when child composables (like the grid)
                             * also participate in pointer handling.
                             */
                            val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: continue
                            val activePointerId = down.id
                            var totalDragY = 0f
                            var totalDragX = 0f
                            var dismissTriggered = false

                            while (!dismissTriggered) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == activePointerId } ?: break

                                if (change.changedToUpIgnoreConsumed()) {
                                    break
                                }

                                /**
                                 * Use positionChangeIgnoreConsumed so we still receive useful
                                 * movement values even if descendants consumed parts of the drag.
                                 * This is what enables "swipe down anywhere" while at top.
                                 */
                                val delta = change.positionChangeIgnoreConsumed()
                                totalDragY += delta.y
                                totalDragX += delta.x

                                val isPredominantlyVertical = abs(totalDragY) > abs(totalDragX) * 1.2f
                                if (isAtTop && isPredominantlyVertical && totalDragY > closeSwipeThresholdPx) {
                                    dismissTriggered = true
                                    onDismiss()
                                }
                            }
                        }
                    }
                },
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.mediumLarge, vertical = Spacing.mediumLarge)
            ) {
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

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(AppDrawerSortMode.ALPHABETICAL_ASC.displayName) },
                                onClick = {
                                    onSortModeSelected(AppDrawerSortMode.ALPHABETICAL_ASC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(AppDrawerSortMode.ALPHABETICAL_DESC.displayName) },
                                onClick = {
                                    onSortModeSelected(AppDrawerSortMode.ALPHABETICAL_DESC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(AppDrawerSortMode.LAST_UPDATED_DESC.displayName) },
                                onClick = {
                                    onSortModeSelected(AppDrawerSortMode.LAST_UPDATED_DESC)
                                    showSortMenu = false
                                }
                            )
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
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = Spacing.medium),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        verticalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        items(
                            items = uiState.sortedApps,
                            key = { "${it.packageName}/${it.activityName}" }
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
