package com.milki.launcher.ui.components.search

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import com.milki.launcher.domain.model.FileDocumentSearchResult
import com.milki.launcher.domain.model.formattedSize
import com.milki.launcher.domain.model.isEpub
import com.milki.launcher.domain.model.isExcelSpreadsheet
import com.milki.launcher.domain.model.isPdf
import com.milki.launcher.domain.model.isPowerPoint
import com.milki.launcher.domain.model.isTextFile
import com.milki.launcher.domain.model.isWordDocument
import com.milki.launcher.ui.components.common.buildFileItemMenuActions
import com.milki.launcher.ui.components.common.rememberItemContextMenuState
import com.milki.launcher.ui.components.launcher.ItemActionMenu
import com.milki.launcher.ui.interaction.dragdrop.startExternalFileDrag
import com.milki.launcher.ui.interaction.grid.GridConfig
import com.milki.launcher.ui.interaction.grid.detectDragGesture

/**
 * Renders a file/document result row with file-type icon, pin action menu, and drag support.
 */
@Composable
fun FileDocumentSearchResultItem(
    result: FileDocumentSearchResult,
    accentColor: Color?,
    onClick: () -> Unit,
    onExternalDragStarted: () -> Unit = {}
) {
    val file = result.file
    val hostView = LocalView.current
    val menuState = rememberItemContextMenuState()
    val menuActions = remember(file) { buildFileItemMenuActions(file) }

    val fileIcon = when {
        file.isPdf() -> Icons.Outlined.PictureAsPdf
        file.isWordDocument() -> Icons.AutoMirrored.Outlined.Article
        file.isExcelSpreadsheet() -> Icons.Outlined.TableChart
        file.isPowerPoint() -> Icons.Outlined.Slideshow
        file.isEpub() -> Icons.AutoMirrored.Outlined.MenuBook
        file.isTextFile() -> Icons.AutoMirrored.Outlined.TextSnippet
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    val supportingText = buildString {
        if (file.folderPath.isNotEmpty()) {
            append(file.folderPath)
        }
        if (file.size > 0) {
            if (isNotEmpty()) append(" • ")
            append(file.formattedSize)
        }
    }.takeIf { it.isNotEmpty() }

    Box(
        modifier = Modifier.detectDragGesture(
            key = "file:${file.id}:${file.uri}",
            dragThreshold = GridConfig.Default.dragThresholdPx,
            onTap = onClick,
            onLongPress = { menuState.onLongPress() },
            onLongPressRelease = menuState::onLongPressRelease,
            onDragStart = {
                menuState.onDragStart()

                val dragStarted = startExternalFileDrag(
                    hostView = hostView,
                    fileDocument = file
                )

                if (dragStarted) {
                    hostView.post {
                        onExternalDragStarted()
                    }
                }
            },
            onDrag = { change, _ -> change.consume() },
            onDragEnd = {},
            onDragCancel = menuState::onDragCancel
        )
    ) {
        SearchResultListItem(
            headlineText = file.name,
            supportingText = supportingText,
            leadingIcon = fileIcon,
            accentColor = accentColor,
            onClick = null
        )

        ItemActionMenu(
            expanded = menuState.showMenu,
            onDismiss = menuState::dismiss,
            focusable = menuState.isMenuFocusable,
            actions = menuActions
        )
    }
}
