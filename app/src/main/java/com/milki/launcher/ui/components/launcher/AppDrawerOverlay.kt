/**
 * AppDrawerOverlay.kt - Full-screen app drawer shown above homescreen
 *
 * FEATURE SUMMARY:
 * - Shows all installed apps in an adaptive grid (adjusts column count for
 *   different screen widths such as phones, foldables, and tablets).
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

package com.milki.launcher.ui.components.launcher

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.AppSearchResult
import com.milki.launcher.presentation.drawer.AppDrawerUiState
import com.milki.launcher.presentation.drawer.DrawerAdapterItem
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.ui.components.common.AppGridItem
import com.milki.launcher.ui.components.search.UnifiedSearchInputField
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

internal fun drawerGridItemKey(index: Int, item: DrawerAdapterItem): String {
    return when (item) {
        // Search ranking can interleave the same section key more than once (e.g. C, M, C).
        // Include index to keep Lazy grid keys unique and avoid duplicate-key crashes.
        is DrawerAdapterItem.SectionHeader -> "header:${item.title}:$index"
        is DrawerAdapterItem.AppEntry -> "app:${item.app.packageName}/${item.app.activityName}"
    }
}

/**
 * AppDrawerOverlay - Full-screen drawer surface.
 *
 * @param uiState Drawer state from AppDrawerViewModel.
 * @param onDismiss Called when drawer should close.
 * @param modifier Optional modifier.
 */
@Composable
fun AppDrawerOverlay(
    uiState: AppDrawerUiState,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    headerDragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val actionHandler = LocalSearchActionHandler.current
    val gridState = rememberLazyGridState()
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val recentRowCapacity = if (isPortrait) 4 else 6
    val topRecentApps = uiState.recentlyChangedApps.take(recentRowCapacity)
    val shouldShowTopRecentRow = uiState.query.isBlank() && topRecentApps.isNotEmpty()

    LaunchedEffect(uiState.query) {
        if (uiState.adapterItems.isNotEmpty()) {
            gridState.scrollToItem(0)
        }
    }

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
            Box(
                modifier = headerDragHandleModifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.extraSmall)
            ) {
                Text(
                    text = "All apps",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            UnifiedSearchInputField(
                query = uiState.query,
                onQueryChange = onQueryChange,
                placeholderText = "Search apps",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.small),
                onClear = { onQueryChange("") }
            )

            if (shouldShowTopRecentRow) {
                RecentlyChangedAppsRow(
                    apps = topRecentApps,
                    rowCapacity = recentRowCapacity,
                    onAppClick = { app ->
                        actionHandler(SearchResultAction.Tap(AppSearchResult(app)))
                        onDismiss()
                    },
                    onExternalDragStarted = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.medium)
                )
            }

            if (uiState.isLoading) {
                // Keep loading state scrollable so downward drag can also
                // propagate to LauncherSheet while results are still loading.
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.adapterItems.isEmpty()) {
                // Keep this state scrollable so downward drag can propagate to
                // LauncherSheet and dismiss the drawer even with no grid items.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = Spacing.medium)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (uiState.query.isBlank()) "No apps installed" else "No apps found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    columns = if (isPortrait) {
                        GridCells.Fixed(4)
                    } else {
                        GridCells.Adaptive(minSize = IconSize.appGrid + Spacing.large)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = Spacing.medium),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
                    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
                ) {
                    items(
                        count = uiState.adapterItems.size,
                        key = { index ->
                            val item = uiState.adapterItems[index]
                            drawerGridItemKey(index = index, item = item)
                        },
                        span = { index ->
                            val item = uiState.adapterItems[index]
                            when (item) {
                                is DrawerAdapterItem.SectionHeader -> GridItemSpan(maxLineSpan)
                                is DrawerAdapterItem.AppEntry -> GridItemSpan(1)
                            }
                        },
                        contentType = { index ->
                            val item = uiState.adapterItems[index]
                            when (item) {
                                is DrawerAdapterItem.SectionHeader -> "drawer_section_header"
                                is DrawerAdapterItem.AppEntry -> "drawer_app_item"
                            }
                        }
                    ) { index ->
                        val item = uiState.adapterItems[index]
                        when (item) {
                            is DrawerAdapterItem.SectionHeader -> {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = Spacing.small, bottom = Spacing.extraSmall)
                                )
                            }

                            is DrawerAdapterItem.AppEntry -> {
                                val appInfo = item.app
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
}

@Composable
private fun RecentlyChangedAppsRow(
    apps: List<AppInfo>,
    rowCapacity: Int,
    onAppClick: (AppInfo) -> Unit,
    onExternalDragStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
    ) {
        Text(
            text = "Recently updated",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            apps.forEach { app ->
                Box(modifier = Modifier.weight(1f)) {
                    AppGridItem(
                        appInfo = app,
                        onClick = { onAppClick(app) },
                        onExternalDragStarted = onExternalDragStarted
                    )
                }
            }

            repeat((rowCapacity - apps.size).coerceAtLeast(0)) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
