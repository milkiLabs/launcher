package com.milki.launcher.ui.components.widget

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.ui.components.dragdrop.startExternalWidgetDrag
import com.milki.launcher.ui.components.grid.GridConfig
import com.milki.launcher.ui.components.grid.detectDragGesture
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

data class WidgetPickerEntry(
    val providerInfo: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val appIcon: Drawable?,
    val span: GridSpan
)

data class WidgetAppGroup(
    val packageName: String,
    val appLabel: String,
    val appIcon: Drawable?,
    val widgets: List<WidgetPickerEntry>
)

@Composable
fun WidgetPickerBottomSheet(
    onDismiss: () -> Unit,
    widgetHostManager: WidgetHostManager,
    onExternalDragStarted: () -> Unit = {}
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    val appGroups: List<WidgetAppGroup> = remember {
        widgetHostManager.getInstalledProviders()
            .map { info ->
                val (minCols, minRows) = widgetHostManager.calculateMinSpan(info)
                val widgetLabel = (info.loadLabel(packageManager) ?: info.provider.shortClassName).toString()
                val appLabel = try {
                    val appInfo = packageManager.getApplicationInfo(info.provider.packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    info.provider.packageName
                }
                WidgetPickerEntry(
                    providerInfo = info,
                    label = widgetLabel,
                    appLabel = appLabel,
                    appIcon = try {
                        packageManager.getApplicationIcon(info.provider.packageName)
                    } catch (_: Exception) {
                        null
                    },
                    span = GridSpan(columns = minCols, rows = minRows)
                )
            }
            .groupBy { it.providerInfo.provider.packageName }
            .map { (packageName, widgets) ->
                WidgetAppGroup(
                    packageName = packageName,
                    appLabel = widgets.first().appLabel,
                    appIcon = widgets.first().appIcon,
                    widgets = widgets.sortedBy { it.label.lowercase() }
                )
            }
            .sortedBy { it.appLabel.lowercase() }
    }

    val normalizedQuery = searchQuery.trim().lowercase()
    val isSearching = normalizedQuery.isNotEmpty()
    val filteredGroups = remember(appGroups, normalizedQuery) {
        appGroups.mapNotNull { group ->
            if (normalizedQuery.isBlank()) {
                group
            } else {
                val appMatches = group.appLabel.lowercase().contains(normalizedQuery)
                val matchingWidgets = if (appMatches) {
                    group.widgets
                } else {
                    group.widgets.filter { entry ->
                        entry.label.lowercase().contains(normalizedQuery)
                    }
                }

                if (matchingWidgets.isEmpty()) {
                    null
                } else {
                    group.copy(widgets = matchingWidgets)
                }
            }
        }
    }
    val totalWidgetCount = appGroups.sumOf { it.widgets.size }
    val visibleWidgetCount = filteredGroups.sumOf { it.widgets.size }

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
                onSearchQueryChange = { searchQuery = it },
                onClearSearch = { searchQuery = "" }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.mediumLarge,
                    end = Spacing.mediumLarge,
                    bottom = Spacing.extraLarge
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                if (filteredGroups.isEmpty()) {
                    item(key = "empty_state") {
                        EmptyWidgetSearchState(searchQuery = searchQuery)
                    }
                } else {
                    items(
                        items = filteredGroups,
                        key = { group -> group.packageName }
                    ) { group ->
                        val expanded = if (isSearching) {
                            true
                        } else {
                            expandedGroups[group.packageName] ?: false
                        }
                        AppGroupCard(
                            group = group,
                            expanded = expanded,
                            autoExpanded = isSearching,
                            onToggle = {
                                expandedGroups[group.packageName] =
                                    !(expandedGroups[group.packageName] ?: false)
                            },
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
    onClearSearch: () -> Unit
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
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
            ) {
                Text(
                    text = "Widgets",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Open an app, then long-press and drag a widget onto the home screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            StatPill(
                label = if (searchQuery.isBlank()) {
                    "$totalApps apps • $totalWidgets widgets"
                } else {
                    "$visibleApps apps • $visibleWidgets matches"
                }
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CornerRadius.extraLarge),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = Spacing.none
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Search apps or widgets",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = onClearSearch) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
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
private fun AppGroupCard(
    group: WidgetAppGroup,
    expanded: Boolean,
    autoExpanded: Boolean,
    onToggle: () -> Unit,
    onExternalDragStarted: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
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
            modifier = Modifier.animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(Spacing.mediumLarge),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                AppIcon(
                    drawable = group.appIcon,
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
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (expanded) {
                            "${group.widgets.size} widget${if (group.widgets.size == 1) "" else "s"}"
                        } else {
                            "Tap to reveal ${group.widgets.size} widget${if (group.widgets.size == 1) "" else "s"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatPill(
                    label = if (autoExpanded) "Results" else group.widgets.size.toString()
                )

                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse ${group.appLabel}" else "Expand ${group.appLabel}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                )
            }

            AnimatedVisibility(visible = expanded) {
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
private fun WidgetCard(
    entry: WidgetPickerEntry,
    onExternalDragStarted: () -> Unit = {}
) {
    val hostView = LocalView.current
    val shape = RoundedCornerShape(CornerRadius.large)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = Spacing.hairline,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                shape = shape
            )
            .detectDragGesture(
                key = "${entry.providerInfo.provider.packageName}/${entry.providerInfo.provider.className}",
                dragThreshold = GridConfig.Default.dragThresholdPx,
                onTap = {},
                onLongPress = {},
                onLongPressRelease = {},
                onDragStart = {
                    val dragStarted = startExternalWidgetDrag(
                        hostView = hostView,
                        providerInfo = entry.providerInfo,
                        span = entry.span,
                        dragShadowSize = IconSize.appGrid
                    )

                    if (dragStarted) {
                        hostView.post(onExternalDragStarted)
                    }
                },
                onDrag = { change, _ -> change.consume() },
                onDragEnd = {},
                onDragCancel = {}
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetPreview(
                entry = entry,
                modifier = Modifier.width(132.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.smallMedium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InfoPill(label = "${entry.span.columns} × ${entry.span.rows}")
                    InfoPill(label = "Drag to place")
                }

                Text(
                    text = "Long-press, then keep dragging to drop it on the grid.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSize.small)
                    )
                    Text(
                        text = entry.appLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetPreview(
    entry: WidgetPickerEntry,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val previewWidth = 132.dp
    val previewHeight = 92.dp
    val widthPx = with(density) { previewWidth.roundToPx() }
    val heightPx = with(density) { previewHeight.roundToPx() }

    val previewDrawable = remember(entry.providerInfo) {
        try {
            entry.providerInfo.loadPreviewImage(context, 0)
        } catch (_: Exception) {
            null
        }
    }
    val previewBitmap = remember(previewDrawable, widthPx, heightPx) {
        previewDrawable?.toBitmap(width = widthPx, height = heightPx)?.asImageBitmap()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CornerRadius.large))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
            )
            .height(previewHeight)
            .padding(Spacing.smallMedium)
    ) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap,
                contentDescription = entry.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppIcon(
                    drawable = entry.appIcon,
                    label = entry.label,
                    size = 40.dp
                )
                Spacer(modifier = Modifier.height(Spacing.smallMedium))
                Text(
                    text = "Preview unavailable",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AppIcon(
    drawable: Drawable?,
    label: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }

    if (drawable == null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(CornerRadius.medium))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Widgets,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.standard)
            )
        }
        return
    }

    val bitmap = remember(drawable, sizePx) {
        drawable.toBitmap(width = sizePx, height = sizePx).asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = label,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(CornerRadius.medium))
    )
}

@Composable
private fun InfoPill(label: String) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.extraLarge),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = Spacing.medium,
                vertical = Spacing.small
            )
        )
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
                text = "No widgets found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Try a different app or widget name for \"$searchQuery\".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
