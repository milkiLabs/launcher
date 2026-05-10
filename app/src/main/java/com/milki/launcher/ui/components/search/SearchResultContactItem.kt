package com.milki.launcher.ui.components.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import com.milki.launcher.domain.model.ContactSearchResult
import com.milki.launcher.ui.interaction.dragdrop.startExternalContactDrag
import com.milki.launcher.ui.interaction.grid.GridConfig
import com.milki.launcher.ui.interaction.grid.detectDragGesture
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * Renders a contact result row with direct-call trailing action and external drag support.
 */
@Composable
fun ContactSearchResultItem(
    result: ContactSearchResult,
    accentColor: Color?,
    onClick: () -> Unit,
    onDialClick: (() -> Unit)? = null,
    onExternalDragStarted: () -> Unit = {}
) {
    val hostView = LocalView.current
    val primaryPhone = result.contact.phoneNumbers.firstOrNull()
    val iconColor = accentColor ?: MaterialTheme.colorScheme.primary

    val trailingContent: (@Composable () -> Unit)? = if (result.contact.phoneNumbers.isNotEmpty() && onDialClick != null) {
        {
            Box(
                modifier = Modifier
                    .size(Spacing.extraLarge)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        onDialClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call directly",
                    tint = iconColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(IconSize.standard)
                )
            }
        }
    } else null

    Box(
        modifier = Modifier.detectDragGesture(
            key = "contact:${result.contact.id}:${result.contact.lookupKey}",
            dragThreshold = GridConfig.Default.dragThresholdPx,
            onTap = onClick,
            onLongPress = {},
            onLongPressRelease = {},
            onDragStart = {
                val dragStarted = startExternalContactDrag(
                    hostView = hostView,
                    contact = result.contact
                )

                if (dragStarted) {
                    hostView.post {
                        onExternalDragStarted()
                    }
                }
            },
            onDrag = { change, _ -> change.consume() },
            onDragEnd = {},
            onDragCancel = {}
        )
    ) {
        SearchResultListItem(
            headlineText = result.contact.displayName,
            supportingText = primaryPhone,
            leadingIcon = Icons.Default.Person,
            accentColor = accentColor,
            trailingContent = trailingContent,
            onClick = null
        )
    }
}
