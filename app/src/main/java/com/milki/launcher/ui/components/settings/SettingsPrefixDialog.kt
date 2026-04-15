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
import com.milki.launcher.ui.theme.Spacing

/**
 * Dialog for adding a new provider prefix.
 */
@Composable
fun AddPrefixDialog(
    existingPrefixes: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String, (String) -> Unit) -> Unit
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
                        else -> {
                            onAdd(trimmed) { validationMessage ->
                                error = validationMessage.ifBlank { null }
                            }
                        }
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
