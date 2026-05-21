package com.milki.launcher.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.ui.theme.Spacing

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
