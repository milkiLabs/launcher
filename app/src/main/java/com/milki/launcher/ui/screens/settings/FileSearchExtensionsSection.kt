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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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
    SettingsCategory(title = "File Search Extensions")

    Text(
        text = "Choose which file types appear when searching with the file prefix.",
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
                text = "Custom Extensions",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Add file extensions not covered by categories above.",
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
                        AssistChip(
                            onClick = { actions.onRemoveCustomExtension(ext) },
                            label = { Text(".$ext") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove .$ext",
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
                    label = { Text("Extension") },
                    placeholder = { Text("e.g. sketch") },
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
                                    errorMessage = "Already added"
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
                                errorMessage = "Already added"
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
                        contentDescription = "Add extension",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
