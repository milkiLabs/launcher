package com.milki.launcher.ui.components.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.milki.launcher.domain.model.PhoneNumberSearchResult
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * Renders a typed phone number result with explicit call and save-contact actions.
 */
@Composable
fun PhoneNumberSearchResultItem(
    result: PhoneNumberSearchResult,
    accentColor: Color?,
    onCallClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    val iconColor = accentColor ?: MaterialTheme.colorScheme.primary

    SearchResultListItem(
        headlineText = result.phoneNumber,
        supportingText = "Call or save phone number",
        leadingIcon = Icons.Default.Phone,
        accentColor = accentColor,
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhoneNumberActionIcon(
                    iconColor = iconColor,
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call number",
                    onClick = onCallClick
                )
                PhoneNumberActionIcon(
                    iconColor = iconColor,
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Save number",
                    onClick = onSaveClick
                )
            }
        },
        onClick = onCallClick
    )
}

@Composable
private fun PhoneNumberActionIcon(
    iconColor: Color,
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(Spacing.extraLarge)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = iconColor.copy(alpha = 0.8f),
            modifier = Modifier.size(IconSize.standard)
        )
    }
}
