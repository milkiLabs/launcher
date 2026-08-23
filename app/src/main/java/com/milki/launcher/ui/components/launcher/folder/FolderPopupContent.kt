package com.milki.launcher.ui.components.launcher.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.milki.launcher.R
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.common.ItemContextMenuRegistry
import com.milki.launcher.ui.components.launcher.ItemActionMenu
import com.milki.launcher.ui.components.common.buildHomeItemMenuActions
import com.milki.launcher.ui.components.launcher.MenuAction
import com.milki.launcher.ui.interaction.grid.detectDragGesture
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.Spacing
import com.milki.launcher.ui.util.windowRect

@Composable
internal fun FolderNameHeader(
    name: String,
    isEditing: Boolean,
    focusRequester: FocusRequester,
    itemCount: Int,
    onNameChange: (String) -> Unit,
    onEditingChanged: (Boolean) -> Unit,
    onEditRequested: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onEditRequested
                    )
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = onNameChange,
                    textStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .widthIn(max = FOLDER_NAME_MAX_WIDTH)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            onEditingChanged(focusState.isFocused)
                        },
                    readOnly = !isEditing
                )
            }

            Spacer(modifier = Modifier.width(Spacing.extraSmall))
            Text(
                text = pluralStringResource(R.plurals.folder_item_count, itemCount, itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        if (isEditing) {
            Spacer(modifier = Modifier.height(Spacing.extraSmall))
            Box(
                modifier = Modifier
                    .widthIn(max = FOLDER_NAME_UNDERLINE_MAX_WIDTH)
                    .fillMaxWidth(0.45f)
                    .height(Spacing.hairline)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
            )
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
internal fun FolderGridPage(
    page: Int,
    layout: FolderGridLayout,
    localChildren: List<HomeItem>,
    cellWidth: Dp,
    cellHeight: Dp,
    cellSpacing: Dp,
    draggedItemId: String?,
    hoveredSlot: FolderDropSlot?,
    menuRegistry: ItemContextMenuRegistry,
    onGridBoundsMeasured: (Rect) -> Unit,
    onTap: (HomeItem) -> Unit,
    onLongPress: (String) -> Unit,
    onRemoveFromFolder: (String) -> Unit,
    onExternalDragStarted: () -> Unit,
    onDragStart: (item: HomeItem, itemWindowTopLeft: Offset) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val pageStart = page * layout.pageSize
    val pageItems = localChildren.drop(pageStart).take(layout.pageSize)
    val slots = List(layout.pageSize) { slotIndex ->
        pageItems.getOrNull(slotIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                onGridBoundsMeasured(coords.windowRect())
            }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(cellSpacing)
        ) {
            repeat(layout.rows) { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(cellSpacing)
                ) {
                    repeat(layout.columns) { column ->
                        val slotIndex = (row * layout.columns) + column
                        val item = slots.getOrNull(slotIndex)

                        Box(
                            modifier = Modifier
                                .size(width = cellWidth, height = cellHeight)
                                .then(
                                    if (hoveredSlot == FolderDropSlot(page, slotIndex)) {
                                        Modifier
                                            .clip(RoundedCornerShape(CornerRadius.medium))
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            )
                                            .border(
                                                width = Spacing.hairline,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                                shape = RoundedCornerShape(CornerRadius.medium)
                                            )
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            if (item != null) {
                                FolderPopupItem(
                                    item = item,
                                    isDragged = item.id == draggedItemId,
                                    menuRegistry = menuRegistry,
                                    onTap = { onTap(item) },
                                    onLongPress = { onLongPress(item.id) },
                                    onRemoveFromFolder = { onRemoveFromFolder(item.id) },
                                    onExternalDragStarted = onExternalDragStarted,
                                    onDragStart = { onDragStart(item, it) },
                                    onDragDelta = onDragDelta,
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel,
                                    modifier = Modifier.fillMaxSize()
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
internal fun FolderPagerIndicator(
    pageCount: Int,
    currentPage: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.smallMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { page ->
            val isSelected = page == currentPage
            Box(
                modifier = Modifier
                    .size(
                        width = if (isSelected) FOLDER_INDICATOR_ACTIVE_DOT_WIDTH else FOLDER_INDICATOR_HEIGHT,
                        height = FOLDER_INDICATOR_HEIGHT
                    )
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun FolderPopupItem(
    item: HomeItem,
    isDragged: Boolean,
    menuRegistry: ItemContextMenuRegistry,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onRemoveFromFolder: () -> Unit,
    onExternalDragStarted: () -> Unit,
    onDragStart: (itemWindowTopLeft: Offset) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var windowTopLeft by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                windowTopLeft = coords.localToWindow(Offset.Zero)
            }
            .alpha(if (isDragged) FOLDER_DRAG_GHOST_ALPHA else 1f)
            .detectDragGesture(
                key = item.id,
                dragThreshold = FOLDER_DRAG_THRESHOLD_PX,
                onTap = { onTap() },
                onLongPress = {
                    menuRegistry.show(item.id)
                    onLongPress()
                },
                onLongPressRelease = {
                    menuRegistry.endLongPressGesture()
                },
                onDragStart = {
                    menuRegistry.onInternalDragStarted()
                    onDragStart(windowTopLeft)
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount)
                },
                onDragEnd = onDragEnd,
                onDragCancel = {
                    menuRegistry.cancelGesture()
                    onDragCancel()
                }
            )
    ) {
        com.milki.launcher.ui.components.launcher.PinnedItemView(item = item)

        ItemActionMenu(
            actions = buildHomeItemMenuActions(
                item = item,
                extraActions = listOf(
                    MenuAction(
                        label = stringResource(R.string.folder_action_remove_item),
                        icon = Icons.Filled.Delete,
                        onClick = onRemoveFromFolder,
                        isDestructive = true
                    )
                ),
                includeUnpin = false
            ),
            expanded = menuRegistry.shownForItemId == item.id,
            onDismiss = menuRegistry::dismiss,
            focusable = menuRegistry.isMenuFocusable,
            onExternalDragStarted = onExternalDragStarted,
        )
    }
}

private const val FOLDER_DRAG_THRESHOLD_PX = 20f
private const val FOLDER_DRAG_GHOST_ALPHA = 0.18f

/** Max width of the folder name text field before it wraps into the item count. */
private val FOLDER_NAME_MAX_WIDTH: Dp = 220.dp

/** Max width of the underline shown under the folder name while editing. */
private val FOLDER_NAME_UNDERLINE_MAX_WIDTH: Dp = 140.dp

/** Width of the selected page-indicator dot (elongated pill). */
private val FOLDER_INDICATOR_ACTIVE_DOT_WIDTH: Dp = 18.dp
