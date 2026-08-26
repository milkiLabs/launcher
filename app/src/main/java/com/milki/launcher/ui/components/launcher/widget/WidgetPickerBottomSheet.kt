package com.milki.launcher.ui.components.launcher.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.milki.launcher.R
import com.milki.launcher.data.widget.WidgetAppGroup
import com.milki.launcher.data.widget.WidgetPickerCatalogStore
import com.milki.launcher.domain.search.QueryRanker
import com.milki.launcher.domain.search.QueryTextMatcher
import com.milki.launcher.ui.components.search.UnifiedSearchInputField
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

@Composable
fun WidgetPickerBottomSheet(
    catalogStore: WidgetPickerCatalogStore,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    headerDragHandleModifier: Modifier = Modifier,
    onExternalDragStarted: () -> Unit = {}
) {
    val catalogUiState = rememberWidgetPickerCatalogUiState(catalogStore)
    val appGroups = catalogUiState.appGroups

    val normalizedQuery = QueryTextMatcher.normalize(searchQuery)
    val filteredGroups = remember(appGroups, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            appGroups
        } else {
            val filtered = appGroups.mapNotNull { group ->
                val appMatches = QueryTextMatcher.containsNormalized(
                    text = group.appLabel,
                    normalizedQuery = normalizedQuery
                )
                val matchingWidgets = if (appMatches) {
                    group.widgets
                } else {
                    group.widgets.filter { entry ->
                        QueryTextMatcher.containsNormalized(
                            text = entry.label,
                            normalizedQuery = normalizedQuery
                        )
                    }
                }

                if (matchingWidgets.isEmpty()) {
                    null
                } else {
                    group.copy(widgets = matchingWidgets)
                }
            }
            QueryRanker.rank(
                items = filtered,
                query = normalizedQuery,
                nameSelector = { it.appLabel },
                secondaryTextSelector = { group ->
                    group.widgets.firstOrNull {
                        it.label.lowercase().contains(normalizedQuery)
                    }?.label ?: group.widgets.first().label
                },
                identitySelector = { it.packageName },
            )
        }
    }
    val totalWidgetCount = appGroups.sumOf { it.widgets.size }
    val visibleWidgetCount = filteredGroups.sumOf { it.widgets.size }

    val listState = rememberLazyListState()
    LaunchedEffect(normalizedQuery) {
        if (filteredGroups.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.surfaceContainer,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            WidgetPickerHeader(
                totalApps = appGroups.size,
                totalWidgets = totalWidgetCount,
                visibleApps = filteredGroups.size,
                visibleWidgets = visibleWidgetCount,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onClearSearch = { onSearchQueryChange("") },
                headerDragHandleModifier = headerDragHandleModifier
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.mediumLarge,
                    end = Spacing.mediumLarge,
                    bottom = Spacing.extraLarge
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                if (catalogUiState.isLoading) {
                    item(key = "loading_state") {
                        LoadingWidgetCatalogState()
                    }
                } else if (filteredGroups.isEmpty()) {
                    item(key = "empty_state") {
                        EmptyWidgetSearchState(searchQuery = searchQuery)
                    }
                } else {
                    items(
                        items = filteredGroups,
                        key = { group -> group.packageName }
                    ) { group ->
                        AppGroupCard(
                            group = group,
                            onExternalDragStarted = onExternalDragStarted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPickerHeader(
    totalApps: Int,
    totalWidgets: Int,
    visibleApps: Int,
    visibleWidgets: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    headerDragHandleModifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.mediumLarge,
                end = Spacing.mediumLarge,
                top = Spacing.mediumLarge,
                bottom = Spacing.medium
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        Row(
            modifier = headerDragHandleModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            WidgetHeaderIcon()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
            ) {
                Text(
                    text = stringResource(R.string.widget_picker_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = stringResource(R.string.widget_picker_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            StatPill(
                label = if (searchQuery.isBlank()) {
                    stringResource(R.string.widget_picker_stats_idle, totalApps, totalWidgets)
                } else {
                    stringResource(R.string.widget_picker_stats_filtered, visibleApps, visibleWidgets)
                }
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = Spacing.none
        ) {
            UnifiedSearchInputField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholderText = stringResource(R.string.widget_picker_search_placeholder),
                modifier = Modifier.fillMaxWidth(),
                onClear = onClearSearch,
                indicatorColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StatPill(label: String) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.extraLarge),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = Spacing.medium,
                vertical = Spacing.smallMedium
            )
        )
    }
}

@Composable
private fun WidgetHeaderIcon() {
    Surface(
        shape = RoundedCornerShape(CornerRadius.large),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ) {
        Icon(
            imageVector = Icons.Default.Widgets,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(Spacing.medium)
                .size(IconSize.standard)
        )
    }
}

@Composable
private fun LoadingWidgetCatalogState() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.smallMedium),
        shape = RoundedCornerShape(CornerRadius.extraLarge),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Spacing.large,
                    vertical = Spacing.extraLarge
                ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetHeaderIcon()

            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
            ) {
                Text(
                    text = stringResource(R.string.widget_picker_loading_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.widget_picker_loading_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AppGroupCard(
    group: WidgetAppGroup,
    onExternalDragStarted: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 120, easing = LinearOutSlowInEasing),
        label = "widget_group_rotation"
    )
    val shape = RoundedCornerShape(CornerRadius.extraLarge)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = Spacing.hairline,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = shape
            ),
        shape = shape,
        color = if (expanded) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        tonalElevation = Spacing.none
    ) {
        Column(
            modifier = Modifier.animateContentSize(
                animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { expanded = !expanded })
                    .padding(Spacing.mediumLarge),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                WidgetAppIcon(
                    drawable = group.appIcon?.newDrawable(),
                    label = group.appLabel,
                    size = 48.dp
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
                ) {
                    Text(
                        text = group.appLabel,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (expanded) {
                            pluralStringResource(R.plurals.widget_count, group.widgets.size, group.widgets.size)
                        } else {
                            pluralStringResource(
                                R.plurals.tap_to_reveal_widgets,
                                group.widgets.size,
                                group.widgets.size
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatPill(
                    label = group.widgets.size.toString()
                )

                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) {
                        stringResource(R.string.widget_group_collapse, group.appLabel)
                    } else {
                        stringResource(R.string.widget_group_expand, group.appLabel)
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = tween(durationMillis = 120)),
                exit = fadeOut(animationSpec = tween(durationMillis = 90))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Spacing.mediumLarge,
                            end = Spacing.mediumLarge,
                            bottom = Spacing.mediumLarge
                        ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.smallMedium)
                ) {
                    group.widgets.forEach { entry ->
                        WidgetCard(
                            entry = entry,
                            onExternalDragStarted = onExternalDragStarted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyWidgetSearchState(searchQuery: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.smallMedium),
        shape = RoundedCornerShape(CornerRadius.extraLarge),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Spacing.large,
                    vertical = Spacing.extraLarge
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.smallMedium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.large)
            )
            Text(
                text = stringResource(R.string.widget_picker_empty_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.widget_picker_empty_hint, searchQuery),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
