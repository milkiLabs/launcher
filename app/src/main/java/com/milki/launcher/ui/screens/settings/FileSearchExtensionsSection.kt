package com.milki.launcher.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.milki.launcher.R
import com.milki.launcher.domain.model.FileSearchCategory
import com.milki.launcher.domain.model.FileSearchExtensionConfig
import com.milki.launcher.ui.components.settings.SettingsCardSurface
import com.milki.launcher.ui.components.settings.SettingsCategory
import com.milki.launcher.ui.components.settings.SwitchSettingItem
import com.milki.launcher.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FileSearchExtensionsSection(
    extensionConfig: FileSearchExtensionConfig,
    actions: SettingsFileSearchActions
) {
    SettingsCategory(title = stringResource(R.string.file_extensions_section_title))

    Text(
        text = stringResource(R.string.file_extensions_section_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = Spacing.mediumLarge,
            vertical = Spacing.small
        )
    )

    FileSearchCategory.entries.forEach { category ->
        val extensions = FileSearchExtensionConfig.categoryExtensions[category].orEmpty()
        val exampleExtensions = extensions.take(4).joinToString(", ") { ".$it" }
        val suffix = if (extensions.size > 4) " …" else ""

        SwitchSettingItem(
            title = category.displayName,
            subtitle = exampleExtensions + suffix,
            checked = category in extensionConfig.enabledCategories,
            onCheckedChange = { enabled -> actions.onToggleCategory(category, enabled) }
        )
    }

    Spacer(modifier = Modifier.height(Spacing.small))

    // Custom extensions
    SettingsCardSurface {
        Column(
            modifier = Modifier.padding(
                horizontal = Spacing.mediumLarge,
                vertical = Spacing.medium
            )
        ) {
            Text(
                text = stringResource(R.string.file_extensions_custom_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.file_extensions_custom_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            if (extensionConfig.customExtensions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
                ) {
                    extensionConfig.customExtensions.sorted().forEach { ext ->
                        val removeLabel = stringResource(R.string.file_extension_remove_a11y, ext)
                        AssistChip(
                            onClick = { actions.onRemoveCustomExtension(ext) },
                            label = { Text(".$ext") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = removeLabel,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.small))
            }

            var newExtension by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            val context = LocalContext.current

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = newExtension,
                    onValueChange = { value ->
                        newExtension = value.lowercase().filter { it.isLetterOrDigit() }
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.file_extension_label)) },
                    placeholder = { Text(stringResource(R.string.file_extension_placeholder)) },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { msg -> { Text(msg) } },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val trimmed = newExtension.trim()
                            if (trimmed.isNotEmpty()) {
                                if (trimmed in extensionConfig.customExtensions) {
                                    errorMessage = context.getString(R.string.file_extension_error_duplicate)
                                } else {
                                    actions.onAddCustomExtension(trimmed)
                                    newExtension = ""
                                    errorMessage = null
                                }
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        val trimmed = newExtension.trim()
                        if (trimmed.isNotEmpty()) {
                            if (trimmed in extensionConfig.customExtensions) {
                                errorMessage = context.getString(R.string.file_extension_error_duplicate)
                            } else {
                                actions.onAddCustomExtension(trimmed)
                                newExtension = ""
                                errorMessage = null
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.file_extension_add_a11y),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
