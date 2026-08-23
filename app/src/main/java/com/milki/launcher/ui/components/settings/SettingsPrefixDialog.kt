package com.milki.launcher.ui.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.milki.launcher.ui.theme.Spacing

/**
 * Dialog for adding a new provider prefix.
 */
@Composable
fun AddPrefixDialog(
    existingPrefixes: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String, (String) -> Unit) -> Unit,
    title: String = stringResource(R.string.prefix_dialog_title),
    description: String = stringResource(R.string.prefix_dialog_description),
    duplicatePrefixMessage: String = stringResource(R.string.prefix_dialog_duplicate_error)
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = description,
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
                    label = { Text(stringResource(R.string.prefix_label)) },
                    placeholder = { Text(stringResource(R.string.prefix_placeholder)) },
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
                        trimmed.isEmpty() -> error = context.getString(R.string.prefix_error_empty)
                        trimmed.contains(" ") -> error = context.getString(R.string.prefix_error_spaces)
                        trimmed in existingPrefixes -> error = duplicatePrefixMessage
                        else -> {
                            onAdd(trimmed) { validationMessage ->
                                error = validationMessage.ifBlank { null }
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
