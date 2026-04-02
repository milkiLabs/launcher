package com.milki.launcher.ui.components.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.Spacing

@Composable
fun UnifiedSearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector = Icons.Default.Search,
    leadingIconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leadingIconContentDescription: String? = null,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Search,
    onImeAction: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    supportingContent: (@Composable (() -> Unit))? = null
) {
    val fieldModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = fieldModifier.fillMaxWidth(),
            placeholder = { Text(placeholderText) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction?.invoke() },
                onSearch = { onImeAction?.invoke() },
                onGo = { onImeAction?.invoke() }
            ),
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = leadingIconContentDescription,
                    tint = leadingIconTint
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onClear?.invoke() ?: onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.small)
                .height(Spacing.extraSmall)
                .clip(RoundedCornerShape(CornerRadius.extraSmall))
                .background(indicatorColor)
        )

        if (supportingContent != null) {
            supportingContent()
        } else {
            Spacer(modifier = Modifier.height(Spacing.smallMedium))
        }
    }
}