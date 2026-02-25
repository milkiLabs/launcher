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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.milki.launcher.ui.theme.CornerRadius
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
 * @param title Setting name
 * @param subtitle Optional description
 * @param selectedValue The currently selected option's display name
 * @param options List of (displayName, onSelect) pairs
 * @param icon Optional leading icon
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.mediumLarge, vertical = Spacing.extraSmall),
        shape = RoundedCornerShape(CornerRadius.medium),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .clickable { expanded = !expanded }
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

            // Expandable option list
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .padding(bottom = Spacing.smallMedium)
                ) {
                    options.forEach { (displayName, value) ->
                        val isSelected = displayName == selectedValue
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOptionSelected(value)
                                    expanded = false
                                }
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    }
                                )
                                .padding(
                                    horizontal = Spacing.large + Spacing.mediumLarge,
                                    vertical = Spacing.medium
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
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

