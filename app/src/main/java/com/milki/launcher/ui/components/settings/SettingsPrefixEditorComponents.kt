package com.milki.launcher.ui.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * Prefix configuration card for a single provider.
 */
@Composable
fun PrefixSettingItem(
    providerName: String,
    providerIcon: ImageVector,
    providerColor: Color,
    defaultPrefix: String,
    isEnabled: Boolean? = null,
    onToggleEnabled: ((Boolean) -> Unit)? = null,
    currentPrefixes: List<String>,
    onAddPrefix: (String, (String) -> Unit) -> Unit,
    onRemovePrefix: (String) -> Unit,
    onReset: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.mediumLarge, vertical = Spacing.extraSmall),
        shape = RoundedCornerShape(CornerRadius.medium),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Spacing.none
    ) {
        Column(modifier = Modifier.padding(Spacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = providerIcon,
                    contentDescription = null,
                    tint = providerColor,
                    modifier = Modifier.size(IconSize.standard)
                )
                Spacer(modifier = Modifier.width(Spacing.smallMedium))
                Text(
                    text = providerName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isEnabled != null && onToggleEnabled != null) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onToggleEnabled
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                }
                if (currentPrefixes != listOf(defaultPrefix) && currentPrefixes.isNotEmpty()) {
                    TextButton(
                        onClick = onReset,
                        contentPadding = PaddingValues(horizontal = Spacing.smallMedium)
                    ) {
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.smallMedium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    items(currentPrefixes) { prefix ->
                        PrefixChip(
                            text = prefix,
                            onRemove = { onRemovePrefix(prefix) },
                            color = providerColor
                        )
                    }
                }

                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.size(IconSize.appList)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add prefix",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddPrefixDialog(
            existingPrefixes = currentPrefixes,
            onDismiss = { showAddDialog = false },
            onAdd = { prefix, onResult ->
                onAddPrefix(prefix) { validationMessage ->
                    onResult(validationMessage)
                    if (validationMessage.isBlank()) {
                        showAddDialog = false
                    }
                }
            }
        )
    }
}

/**
 * Prefix chip with inline remove affordance.
 */
@Composable
fun PrefixChip(
    text: String,
    onRemove: () -> Unit,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.small),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(
            width = Spacing.hairline,
            color = color.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(
                start = Spacing.smallMedium,
                end = Spacing.extraSmall,
                top = Spacing.small,
                bottom = Spacing.small
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = color
            )
            Spacer(modifier = Modifier.width(Spacing.extraSmall))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove",
                tint = color,
                modifier = Modifier
                    .size(IconSize.small)
                    .clip(RoundedCornerShape(CornerRadius.extraSmall))
                    .clickable(onClick = onRemove)
            )
        }
    }
}
