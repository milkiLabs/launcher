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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.milki.launcher.R
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
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initialSource == null) R.string.source_editor_add_title
                    else R.string.source_editor_edit_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.smallMedium)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorText = null
                    },
                    label = { Text(stringResource(R.string.source_editor_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = urlTemplate,
                    onValueChange = {
                        urlTemplate = it
                        errorText = null
                    },
                    label = { Text(stringResource(R.string.source_editor_url_label)) },
                    supportingText = { Text(stringResource(R.string.source_editor_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = prefixesText,
                    onValueChange = {
                        prefixesText = it
                        errorText = null
                    },
                    label = { Text(stringResource(R.string.source_editor_prefixes_label)) },
                    supportingText = { Text(stringResource(R.string.source_editor_prefixes_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = colorHex,
                    onValueChange = {
                        colorHex = it
                        errorText = null
                    },
                    label = { Text(stringResource(R.string.source_editor_color_label)) },
                    supportingText = { Text(stringResource(R.string.source_editor_color_hint)) },
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
                        normalizedName.isEmpty() ->
                            errorText = context.getString(R.string.source_editor_error_empty_name)
                        !SearchSource.isValidUrlTemplate(normalizedTemplate) ->
                            errorText = context.getString(R.string.source_editor_error_invalid_template)
                        normalizedPrefixes.isEmpty() ->
                            errorText = context.getString(R.string.source_editor_error_no_prefixes)
                        normalizedPrefixes.any { it.contains(" ") } ->
                            errorText = context.getString(R.string.source_editor_error_prefix_spaces)
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
                Text(
                    stringResource(
                        if (initialSource == null) R.string.action_add
                        else R.string.action_save
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
