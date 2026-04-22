/**
 * AppDrawerOverlay.kt - Full-screen app drawer shown above homescreen
 *
 * PERFORMANCE DESIGN:
 * The drawer grid is the most scroll-intensive surface in the launcher.
 * Every composable created per grid cell directly impacts frame times.
 * This file uses a lightweight DrawerGridCell instead of the shared
 * AppGridItem to keep per-cell overhead minimal:
 *
 * - No per-item context menu state or ItemActionMenu composable
 * - No per-item quick-actions loading
 * - Context menu is composed only for the one long-pressed item
 * - Icons are batch-preloaded when items arrive, not loaded during scroll
 *
 * FEATURE SUMMARY:
 * - Shows all installed apps in an adaptive grid
 * - Long-press shows context menu with app shortcuts and info
 * - Long-press + drag starts app drag-to-homescreen
 * - Supports swipe-down-to-close when scrolled to top
 *
 * SYSTEM BAR HANDLING:
 * The drawer is hosted inside a LauncherSheet with its own inset handling.
 * We apply statusBarsPadding() and navigationBarsPadding() so content
 * never renders behind system UI.
 */

package com.milki.launcher.ui.components.launcher

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.milki.launcher.data.icon.AppIconMemoryCache
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.AppSearchResult
import com.milki.launcher.presentation.drawer.AppDrawerUiState
import com.milki.launcher.presentation.drawer.DrawerAdapterItem
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.ui.components.common.AppGridItem
import com.milki.launcher.ui.components.common.AppIcon
import com.milki.launcher.ui.components.common.buildAppItemMenuActions
import com.milki.launcher.ui.components.common.detectAppExternalDragGesture
import com.milki.launcher.ui.components.common.rememberItemContextMenuState
import com.milki.launcher.ui.components.common.rememberAppQuickActions
import com.milki.launcher.ui.components.search.UnifiedSearchInputField
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PORTRAIT_RECENT_ROW_CAPACITY = 4
private const val LANDSCAPE_RECENT_ROW_CAPACITY = 6
private const val BENCHMARK_SCROLL_DOWN_FRACTION = 0.75f
private const val DRAWER_GRID_PORTRAIT_COLUMNS = 4
private const val RECENT_ROW_ITEM_KEY = "drawer_recently_changed_row"
private const val RECENT_ROW_CONTENT_TYPE = "drawer_recently_changed_row"
private const val SECTION_HEADER_CONTENT_TYPE = "drawer_section_header"
private const val APP_ITEM_CONTENT_TYPE = "drawer_app_item"

internal fun drawerGridItemKey(index: Int, item: DrawerAdapterItem): String {
    return when (item) {
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
    val recentRowCapacity = if (isPortrait) {
        PORTRAIT_RECENT_ROW_CAPACITY
    } else {
        LANDSCAPE_RECENT_ROW_CAPACITY
    }
    val topRecentApps = uiState.recentlyChangedApps.take(recentRowCapacity)
    val shouldShowTopRecentRow = uiState.query.isBlank() && topRecentApps.isNotEmpty()

    // ── Shared context menu state ───────────────────────────────────
    // Instead of composing ItemActionMenu inside every grid cell,
    // we lift menu state here and only compose the menu for the one
    // long-pressed item. This eliminates ~10 objects per cell.
    var menuTargetApp by remember { mutableStateOf<AppInfo?>(null) }
    val menuState = rememberItemContextMenuState()

    HandleDrawerIconPreload(adapterItems = uiState.adapterItems)
    HandleDrawerQueryScroll(
        query = uiState.query,
        hasItems = uiState.adapterItems.isNotEmpty(),
        gridState = gridState
    )
    HandleBenchmarkScrollSequence(
        benchmarkScrollSequenceToken = uiState.benchmarkScrollSequenceToken,
        itemCount = uiState.adapterItems.size,
        gridState = gridState
    )

    Surface(
        modifier = modifier
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
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

            when {
                uiState.isLoading -> {
                    DrawerLoadingState()
                }

                uiState.adapterItems.isEmpty() -> {
                    DrawerEmptyState(query = uiState.query)
                }

                else -> {
                    DrawerGrid(
                        adapterItems = uiState.adapterItems,
                        isPortrait = isPortrait,
                        shouldShowTopRecentRow = shouldShowTopRecentRow,
                        topRecentApps = topRecentApps,
                        recentRowCapacity = recentRowCapacity,
                        gridState = gridState,
                        menuTargetApp = menuTargetApp,
                        menuState = menuState,
                        onMenuTargetChange = { menuTargetApp = it },
                        onAppClick = { appInfo ->
                            actionHandler(SearchResultAction.Tap(AppSearchResult(appInfo)))
                            onDismiss()
                        },
                        onExternalDragStarted = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun HandleDrawerIconPreload(adapterItems: List<DrawerAdapterItem>) {
    val context = LocalContext.current

    LaunchedEffect(adapterItems) {
        val appEntries = adapterItems.filterIsInstance<DrawerAdapterItem.AppEntry>()
        if (appEntries.isEmpty()) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            AppIconMemoryCache.preloadMissing(
                appEntries.map { it.app.packageName },
                context.packageManager
            )
        }
    }
}

@Composable
private fun HandleDrawerQueryScroll(
    query: String,
    hasItems: Boolean,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState
) {
    LaunchedEffect(query) {
        if (hasItems) {
            gridState.scrollToItem(0)
        }
    }
}

@Composable
private fun HandleBenchmarkScrollSequence(
    benchmarkScrollSequenceToken: Long,
    itemCount: Int,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState
) {
    LaunchedEffect(benchmarkScrollSequenceToken) {
        if (benchmarkScrollSequenceToken == 0L) return@LaunchedEffect

        val lastIndex = itemCount - 1
        if (lastIndex <= 0) return@LaunchedEffect

        val downIndex = ((lastIndex * BENCHMARK_SCROLL_DOWN_FRACTION).toInt()).coerceIn(1, lastIndex)
        gridState.animateScrollToItem(downIndex)
        gridState.animateScrollToItem(lastIndex)
        gridState.animateScrollToItem(0)
    }
}

@Composable
private fun DrawerLoadingState() {
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
}

@Composable
private fun DrawerEmptyState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = Spacing.medium)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (query.isBlank()) "No apps installed" else "No apps found",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DrawerGrid(
    adapterItems: List<DrawerAdapterItem>,
    isPortrait: Boolean,
    shouldShowTopRecentRow: Boolean,
    topRecentApps: List<AppInfo>,
    recentRowCapacity: Int,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    menuTargetApp: AppInfo?,
    menuState: com.milki.launcher.ui.components.common.ItemContextMenuState,
    onMenuTargetChange: (AppInfo?) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onExternalDragStarted: () -> Unit
) {
    LazyVerticalGrid(
        columns = drawerGridColumns(isPortrait = isPortrait),
        modifier = Modifier
            .fillMaxSize()
            .padding(top = Spacing.medium),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
    ) {
        addRecentAppsRow(
            shouldShowTopRecentRow = shouldShowTopRecentRow,
            topRecentApps = topRecentApps,
            recentRowCapacity = recentRowCapacity,
            onAppClick = onAppClick,
            onExternalDragStarted = onExternalDragStarted
        )

        addDrawerItems(
            adapterItems = adapterItems,
            menuTargetApp = menuTargetApp,
            menuState = menuState,
            onMenuTargetChange = onMenuTargetChange,
            onAppClick = onAppClick,
            onExternalDragStarted = onExternalDragStarted
        )
    }
}

private fun drawerGridColumns(isPortrait: Boolean): GridCells {
    return if (isPortrait) {
        GridCells.Fixed(DRAWER_GRID_PORTRAIT_COLUMNS)
    } else {
        GridCells.Adaptive(minSize = IconSize.appGrid + Spacing.large)
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.addRecentAppsRow(
    shouldShowTopRecentRow: Boolean,
    topRecentApps: List<AppInfo>,
    recentRowCapacity: Int,
    onAppClick: (AppInfo) -> Unit,
    onExternalDragStarted: () -> Unit
) {
    if (!shouldShowTopRecentRow) return

    items(
        count = 1,
        key = { RECENT_ROW_ITEM_KEY },
        span = { GridItemSpan(maxLineSpan) },
        contentType = { RECENT_ROW_CONTENT_TYPE }
    ) {
        RecentlyChangedAppsRow(
            apps = topRecentApps,
            rowCapacity = recentRowCapacity,
            onAppClick = onAppClick,
            onExternalDragStarted = onExternalDragStarted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.small)
        )
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.addDrawerItems(
    adapterItems: List<DrawerAdapterItem>,
    menuTargetApp: AppInfo?,
    menuState: com.milki.launcher.ui.components.common.ItemContextMenuState,
    onMenuTargetChange: (AppInfo?) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onExternalDragStarted: () -> Unit
) {
    items(
        count = adapterItems.size,
        key = { index ->
            val item = adapterItems[index]
            drawerGridItemKey(index = index, item = item)
        },
        span = { index ->
            val item = adapterItems[index]
            when (item) {
                is DrawerAdapterItem.SectionHeader -> GridItemSpan(maxLineSpan)
                is DrawerAdapterItem.AppEntry -> GridItemSpan(1)
            }
        },
        contentType = { index ->
            val item = adapterItems[index]
            when (item) {
                is DrawerAdapterItem.SectionHeader -> SECTION_HEADER_CONTENT_TYPE
                is DrawerAdapterItem.AppEntry -> APP_ITEM_CONTENT_TYPE
            }
        }
    ) { index ->
        DrawerGridItem(
            item = adapterItems[index],
            menuTargetApp = menuTargetApp,
            menuState = menuState,
            onMenuTargetChange = onMenuTargetChange,
            onAppClick = onAppClick,
            onExternalDragStarted = onExternalDragStarted
        )
    }
}

@Composable
private fun DrawerGridItem(
    item: DrawerAdapterItem,
    menuTargetApp: AppInfo?,
    menuState: com.milki.launcher.ui.components.common.ItemContextMenuState,
    onMenuTargetChange: (AppInfo?) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onExternalDragStarted: () -> Unit
) {
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
            val isMenuTarget = menuTargetApp == appInfo

            DrawerGridCell(
                appInfo = appInfo,
                onClick = { onAppClick(appInfo) },
                onLongPress = {
                    onMenuTargetChange(appInfo)
                    menuState.onLongPress()
                },
                onLongPressRelease = menuState::onLongPressRelease,
                onDragStarted = {
                    onMenuTargetChange(null)
                    menuState.onDragStart()
                },
                onDragCancelled = menuState::onDragCancel,
                showMenu = isMenuTarget && menuState.showMenu,
                menuFocusable = menuState.isMenuFocusable,
                onMenuDismiss = {
                    onMenuTargetChange(null)
                    menuState.dismiss()
                },
                onExternalDragStarted = onExternalDragStarted
            )
        }
    }
}

// ── Lightweight drawer grid cell ────────────────────────────────────────
//
// PERFORMANCE:
// This cell is intentionally stripped down compared to AppGridItem.
// It avoids all per-item overhead that kills scroll performance:
// - No ItemContextMenuState allocation
// - No rememberAppQuickActions (no LaunchedEffect per cell)
// - No Surface wrapper
// - No IconLabelLayout data class allocation
// - Context menu only composed for the ONE item that is long-pressed
//
// On a typical drawer with 50+ apps, this saves ~500 objects and ~150
// coroutines from being created during initial composition alone.

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerGridCell(
    appInfo: AppInfo,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onLongPressRelease: () -> Unit,
    onDragStarted: () -> Unit,
    onDragCancelled: () -> Unit,
    showMenu: Boolean,
    menuFocusable: Boolean,
    onMenuDismiss: () -> Unit,
    onExternalDragStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .detectAppExternalDragGesture(
                    appInfo = appInfo,
                    dragShadowSize = IconSize.appGrid,
                    onTap = onClick,
                    onLongPress = onLongPress,
                    onLongPressRelease = onLongPressRelease,
                    onDragStart = onDragStarted,
                    onDragCancel = onDragCancelled,
                    onExternalDragStarted = onExternalDragStarted
                )
                .padding(vertical = Spacing.extraSmall),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIcon(
                packageName = appInfo.packageName,
                size = IconSize.appGrid
            )

            Text(
                text = appInfo.name,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.smallMedium)
            )
        }

        // Context menu only composed for the one long-pressed item.
        // This is the key optimization: instead of creating ItemActionMenu
        // infrastructure for every cell, we only pay the cost when the
        // user actually long-presses.
        if (showMenu) {
            val quickActions = rememberAppQuickActions(
                packageName = appInfo.packageName,
                shouldLoad = true
            )
            val menuActions = remember(appInfo, quickActions) {
                buildAppItemMenuActions(appInfo, quickActions)
            }
            ItemActionMenu(
                expanded = true,
                onDismiss = onMenuDismiss,
                focusable = menuFocusable,
                onExternalDragStarted = {
                    onMenuDismiss()
                    onExternalDragStarted()
                },
                actions = menuActions
            )
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
