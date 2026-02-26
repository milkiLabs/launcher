/**
 * SettingsComponents.kt - Reusable UI components for the settings screen
 *
 * These composables provide consistent, polished settings UI elements:
 * - SettingsCategory: Section header for grouping settings
 * - SwitchSettingItem: Toggle switch for boolean settings
 * - DropdownSettingItem: Dropdown selector for enum settings
 * - SliderSettingItem: Slider for numeric range settings
 *
 * All components use the existing design system (Spacing, CornerRadius, etc.)
 * and Material Design 3 theming.
 */

package com.milki.launcher.ui.components.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Section header for a group of settings.
 *
 * @param title Section title (e.g., "Search Behavior", "Appearance")
 * @param icon Optional icon for the section
 */
@Composable
fun SettingsCategory(
    title: String,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.mediumLarge,
                end = Spacing.mediumLarge,
                top = Spacing.large,
                bottom = Spacing.smallMedium
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = Spacing.smallMedium)
            )
        }
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
    }
}

/**
 * Toggle switch setting item.
 *
 * @param title Setting name
 * @param subtitle Optional description
 * @param checked Current toggle state
 * @param onCheckedChange Called when toggle is changed
 * @param icon Optional leading icon
 * @param enabled Whether the setting is interactive
 */
@Composable
fun SwitchSettingItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.mediumLarge, vertical = Spacing.extraSmall),
        shape = RoundedCornerShape(CornerRadius.medium),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(
                    horizontal = Spacing.mediumLarge,
                    vertical = Spacing.medium
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = Spacing.smallMedium)
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        }
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

/**
 * Dropdown selector for choosing from a list of options.
 *
 * IMPLEMENTATION DETAILS:
 * This component uses Material3's DropdownMenu which provides:
 * - Automatic outside-tap dismissal (tapping anywhere outside the menu closes it)
 * - Proper positioning relative to the anchor element
 * - Built-in elevation and theming
 * - Keyboard navigation support
 *
 * PREVIOUS IMPLEMENTATION ISSUE:
 * The old implementation used an inline Column that expanded within the Surface.
 * This caused a bug where tapping outside the dropdown didn't close it because
 * there was no mechanism to detect "outside" taps. DropdownMenu solves this by
 * using a Popup internally that captures outside touches.
 *
 * @param title Setting name
 * @param subtitle Optional description
 * @param selectedValue The currently selected option's display name
 * @param options List of (displayName, value) pairs where displayName is shown to user
 *                and value is the actual enum/value to be passed to onOptionSelected
 * @param onOptionSelected Callback invoked when user selects an option
 * @param icon Optional leading icon displayed before the title
 */
@Composable
fun <T> DropdownSettingItem(
    title: String,
    subtitle: String? = null,
    selectedValue: String,
    options: List<Pair<String, T>>,
    onOptionSelected: (T) -> Unit,
    icon: ImageVector? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.mediumLarge, vertical = Spacing.extraSmall)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CornerRadius.medium),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .padding(
                        horizontal = Spacing.mediumLarge,
                        vertical = Spacing.medium
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(IconSize.standard)
                            .padding(end = Spacing.smallMedium)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = selectedValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (displayName, value) ->
                val isSelected = displayName == selectedValue
                DropdownMenuItem(
                    text = {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        onOptionSelected(value)
                        expanded = false
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(IconSize.small)
                            )
                        }
                    } else null
                )
            }
        }
    }
}

/**
 * Slider for numeric settings within a range.
 *
 * @param title Setting name
 * @param subtitle Optional description
 * @param value Current value
 * @param onValueChange Called when value changes
 * @param valueRange Min..Max range for the slider
 * @param steps Number of discrete steps (0 for continuous)
 * @param valueLabel Function to format the displayed value
 */
@Composable
fun SliderSettingItem(
    title: String,
    subtitle: String? = null,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    steps: Int = 0,
    valueLabel: (Int) -> String = { it.toString() }
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.mediumLarge, vertical = Spacing.extraSmall),
        shape = RoundedCornerShape(CornerRadius.medium),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Spacing.mediumLarge,
                vertical = Spacing.medium
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(CornerRadius.small),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 0.dp
                ) {
                    Text(
                        text = valueLabel(value),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            horizontal = Spacing.smallMedium,
                            vertical = Spacing.small
                        )
                    )
                }
            }

            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.roundToInt()) },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                steps = steps,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.small)
            )
        }
    }
}

/**
 * A clickable action item (for navigation or triggering actions).
 *
 * @param title Action name
 * @param subtitle Optional description
 * @param onClick Called when tapped
 * @param icon Optional leading icon
 * @param trailingContent Optional trailing content
 */
@Composable
fun ActionSettingItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.mediumLarge, vertical = Spacing.extraSmall),
        shape = RoundedCornerShape(CornerRadius.medium),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(
                    horizontal = Spacing.mediumLarge,
                    vertical = Spacing.medium
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = Spacing.smallMedium)
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

// ============================================================================
// PREFIX CONFIGURATION COMPONENTS
// ============================================================================

/**
 * A setting item for configuring provider prefixes.
 *
 * This component displays:
 * - The provider name and icon
 * - The current prefixes as chips
 * - An add button to add new prefixes
 * - A reset button to restore defaults
 *
 * MULTI-PREFIX SUPPORT:
 * The user can add multiple prefixes for each provider.
 * Each prefix is displayed as a chip that can be tapped to remove.
 * This is useful for multilingual users who want both English
 * and native language prefixes (e.g., "f" and "م" for files).
 *
 * @param providerName The display name of the provider (e.g., "Files")
 * @param providerIcon The icon for the provider
 * @param providerColor The accent color for the provider
 * @param defaultPrefix The default prefix for this provider
 * @param currentPrefixes The currently configured prefixes
 * @param onAddPrefix Called when user wants to add a new prefix
 * @param onRemovePrefix Called when user wants to remove a prefix
 * @param onReset Called when user wants to reset to default
 */
@Composable
fun PrefixSettingItem(
    providerName: String,
    providerIcon: ImageVector,
    providerColor: androidx.compose.ui.graphics.Color,
    defaultPrefix: String,
    currentPrefixes: List<String>,
    onAddPrefix: (String) -> Unit,
    onRemovePrefix: (String) -> Unit,
    onReset: () -> Unit
) {
    // State for the add prefix dialog
    var showAddDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.mediumLarge, vertical = Spacing.extraSmall),
        shape = RoundedCornerShape(CornerRadius.medium),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(Spacing.medium)
        ) {
            // Header row with provider name and icon
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
                // Reset button - only show if there are custom prefixes
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

            // Prefix chips row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Display current prefixes as chips
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

                // Add prefix button
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

    // Add prefix dialog
    if (showAddDialog) {
        AddPrefixDialog(
            existingPrefixes = currentPrefixes,
            onDismiss = { showAddDialog = false },
            onAdd = { prefix ->
                onAddPrefix(prefix)
                showAddDialog = false
            }
        )
    }
}

/**
 * A chip displaying a prefix with a remove button.
 *
 * @param text The prefix text to display
 * @param onRemove Called when the remove button is tapped
 * @param color The accent color for the chip
 */
@Composable
fun PrefixChip(
    text: String,
    onRemove: () -> Unit,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.small),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
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

/**
 * Dialog for adding a new prefix.
 *
 * @param existingPrefixes List of prefixes that already exist (for validation)
 * @param onDismiss Called when the dialog is dismissed
 * @param onAdd Called when the user confirms adding a new prefix
 */
@Composable
fun AddPrefixDialog(
    existingPrefixes: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Prefix") },
        text = {
            Column {
                Text(
                    text = "Enter a new prefix for this provider. It can be one or more characters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.medium))
                OutlinedTextField(
                    value = text,
                    onValueChange = { newText ->
                        text = newText
                        error = null
                    },
                    label = { Text("Prefix") },
                    placeholder = { Text("e.g., f, م, find") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = text.trim()
                    when {
                        trimmed.isEmpty() -> error = "Prefix cannot be empty"
                        trimmed.contains(" ") -> error = "Prefix cannot contain spaces"
                        trimmed in existingPrefixes -> error = "This prefix already exists"
                        else -> onAdd(trimmed)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

