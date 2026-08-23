/**
 * LauncherCellSemantics.kt - Shared accessibility semantics for launcher cells
 *
 * Grid cells drive behavior through raw pointer-input gestures
 * (detectDragGesture / detectAppExternalDragGesture), which expose no
 * accessibility actions. Without this modifier TalkBack announces an
 * unlabeled rectangle: icons intentionally pass contentDescription = null
 * and there is no clickable() in the gesture path.
 *
 * Every interactive cell shell funnels through here - AppCellLayout
 * (drawer grid/list, search results), PinnedItem, and the home-grid item
 * boxes in InternalGridDragLayer - so one fix covers all surfaces:
 *
 * - Role.Button with [label] as the accessible name
 * - Click action mapped from [onTap] (TalkBack double-tap)
 * - Long-click action plus a "More options" custom action mapped from
 *   [onLongPress] (opens the same context menu as a touch long-press)
 *
 * Descendant semantics are cleared deliberately: cells are leaf interactive
 * elements whose visible label text would otherwise be announced twice
 * alongside [label]. Context menus and widget popups render in their own
 * Popup windows, so clearing does not affect their accessibility.
 */

package com.milki.launcher.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import com.milki.launcher.R

@Composable
fun Modifier.launcherCellSemantics(
    label: String,
    onTap: (() -> Unit)?,
    onLongPress: (() -> Unit)? = null
): Modifier {
    val optionsLabel = stringResource(R.string.a11y_cell_more_options)

    return clearAndSetSemantics {
        role = Role.Button
        contentDescription = label

        onTap?.let { handler ->
            onClick { handler(); true }
        }

        if (onLongPress != null) {
            val handler = onLongPress
            onLongClick { handler(); true }
            customActions = listOf(
                CustomAccessibilityAction(optionsLabel) { handler(); true }
            )
        }
    }
}
