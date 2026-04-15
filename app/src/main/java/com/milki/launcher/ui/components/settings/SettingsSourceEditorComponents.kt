package com.milki.launcher.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * Card that displays one user-configured source with quick actions.
 */
@Composable
fun SourceSettingItem(
    source: SearchSource,
    onToggleEnabled: (Boolean) -> Unit,
    onAddPrefix: (String, (String) -> Unit) -> Unit,
    onRemovePrefix: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sourceColor = parseHexColorOrFallback(source.accentColorHex)
    var showAddPrefixDialog by remember { mutableStateOf(false) }

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
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = sourceColor,
                    modifier = Modifier.size(IconSize.standard)
                )

                Spacer(modifier = Modifier.size(Spacing.smallMedium))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${source.prefixes.joinToString(", ")} • ${source.accentColorHex}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit source"
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete source",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.small))

            SwitchSettingItem(
                title = "Enabled",
                subtitle = "Use this source for prefix search",
                checked = source.isEnabled,
                onCheckedChange = onToggleEnabled
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            Text(
                text = "Prefixes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(Spacing.extraSmall))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    items(source.prefixes) { prefix ->
                        PrefixChip(
                            text = prefix,
                            onRemove = { onRemovePrefix(prefix) },
                            color = sourceColor
                        )
                    }
                }

                IconButton(onClick = { showAddPrefixDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add prefix",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (showAddPrefixDialog) {
                AddPrefixDialog(
                    existingPrefixes = source.prefixes,
                    onDismiss = { showAddPrefixDialog = false },
                    onAdd = { newPrefix, onResult ->
                        onAddPrefix(newPrefix) { validationMessage ->
                            onResult(validationMessage)
                            if (validationMessage.isEmpty()) {
                                showAddPrefixDialog = false
                            }
                        }
                    },
                    description = "Enter a new prefix for this source. It can be one or more characters.",
                    duplicatePrefixMessage = "Prefix already exists in this source"
                )
            }
        }
    }
}

/**
 * Dialog to create or edit one source.
 */
@Composable
fun SourceEditorDialog(
    initialSource: SearchSource?,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String,
        onValidationResult: (String) -> Unit
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialSource?.name.orEmpty()) }
    var urlTemplate by remember {
        mutableStateOf(initialSource?.urlTemplate ?: "https://example.com/search?q={query}")
    }
    var prefixesText by remember {
        mutableStateOf(initialSource?.prefixes?.joinToString(", ").orEmpty())
    }
    var colorHex by remember { mutableStateOf(initialSource?.accentColorHex ?: "#4285F4") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialSource == null) "Add Source" else "Edit Source")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.smallMedium)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorText = null
                    },
                    label = { Text("Source name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = urlTemplate,
                    onValueChange = {
                        urlTemplate = it
                        errorText = null
                    },
                    label = { Text("URL template") },
                    supportingText = { Text("Must include {query}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = prefixesText,
                    onValueChange = {
                        prefixesText = it
                        errorText = null
                    },
                    label = { Text("Prefixes") },
                    supportingText = { Text("Comma-separated, e.g. yt, y") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = colorHex,
                    onValueChange = {
                        colorHex = it
                        errorText = null
                    },
                    label = { Text("Color (hex)") },
                    supportingText = { Text("Example: #FF0000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorText != null) {
                    Text(
                        text = errorText.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalizedName = name.trim()
                    val normalizedTemplate = urlTemplate.trim()
                    val normalizedPrefixes = prefixesText
                        .split(",")
                        .map(SearchSource.Companion::normalizePrefix)
                        .filter { it.isNotBlank() }
                        .distinct()

                    when {
                        normalizedName.isEmpty() -> errorText = "Name cannot be empty"
                        !SearchSource.isValidUrlTemplate(normalizedTemplate) -> errorText = "URL template must start with http/https and include {query}"
                        normalizedPrefixes.isEmpty() -> errorText = "At least one prefix is required"
                        normalizedPrefixes.any { it.contains(" ") } -> errorText = "Prefixes cannot contain spaces"
                        else -> {
                            onConfirm(
                                normalizedName,
                                normalizedTemplate,
                                normalizedPrefixes,
                                SearchSource.normalizeHexColor(colorHex)
                            ) { validationMessage ->
                                errorText = validationMessage.ifBlank { null }
                            }
                        }
                    }
                }
            ) {
                Text(if (initialSource == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Parses #RRGGBB into color, with primary fallback.
 */
private fun parseHexColorOrFallback(hex: String): Color {
    val normalized = SearchSource.normalizeHexColor(hex)
    val red = normalized.substring(1, 3).toInt(16)
    val green = normalized.substring(3, 5).toInt(16)
    val blue = normalized.substring(5, 7).toInt(16)
    return Color(red = red, green = green, blue = blue)
}
