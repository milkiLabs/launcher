/**
 * WidgetPickerBottomSheet.kt - Bottom sheet for browsing and selecting home screen widgets
 *
 * This composable displays a ModalBottomSheet containing all installed app widgets
 * on the device, grouped by their parent application. Widget cards are drag-only:
 * long-press + drag starts an external drag session to place the widget onto a
 * specific home-grid cell.
 *
 * STRUCTURE:
 * ┌─────────────────────────────────────────┐
 * │  ▼  Drag handle                         │
 * │ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
 * │  "Widgets" title                         │
 * │ ─────────────────────────────────────── │
 * │  App Name (e.g. "Clock")                 │
 * │   ┌──────────┐  ┌──────────┐            │
 * │   │ preview  │  │ preview  │            │
 * │   │ 4×2      │  │ 2×2      │            │
 * │   │ label    │  │ label    │            │
 * │   └──────────┘  └──────────┘            │
 * │                                          │
 * │  App Name (e.g. "Weather")               │
 * │   ┌──────────┐                           │
 * │   │ preview  │                           │
 * │   │ 4×1      │                           │
 * │   │ label    │                           │
 * │   └──────────┘                           │
 * └─────────────────────────────────────────┘
 *
 * GROUPING:
 * Widgets are grouped by their provider's package name (= the app that provides them).
 * Each group header shows the app's label and icon. Groups are sorted alphabetically
 * by app label so the user can scroll through easily.
 *
 * DRAG FLOW:
 * 1. User long-presses and drags a widget card
 * 2. Platform drag starts with ExternalDragItem.Widget payload
 * 3. Sheet is dismissed as soon as drag starts
 * 4. User drops onto home grid target cell
 * 5. Caller begins bind/configure/place flow only after a valid drop
 */

package com.milki.launcher.ui.components.widget

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.ui.components.dragdrop.startExternalWidgetDrag
import com.milki.launcher.ui.components.grid.GridConfig
import com.milki.launcher.ui.components.grid.detectDragGesture
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * Represents a single widget entry in the picker, including its metadata
 * and pre-computed display data.
 *
 * WHY A SEPARATE CLASS:
 * [AppWidgetProviderInfo] is an Android platform object that doesn't carry
 * user-friendly data directly (labels need a PackageManager to resolve).
 * This holder caches the resolved label, icon, and span so we don't call
 * PackageManager repeatedly during recomposition.
 *
 * @property providerInfo  The raw Android provider info (used to start the bind flow)
 * @property label         The human-readable widget name (e.g. "Analog Clock")
 * @property appLabel      The parent app label (e.g. "Clock")
 * @property appIcon       The parent app's icon drawable
 * @property span          The widget's default size in grid cells
 */
data class WidgetPickerEntry(
    val providerInfo: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val appIcon: Drawable?,
    val span: GridSpan
)

/**
 * A group of widgets belonging to the same app.
 *
 * @property appLabel  The app's display name
 * @property appIcon   The app's icon
 * @property widgets   All widget types this app offers
 */
data class WidgetAppGroup(
    val appLabel: String,
    val appIcon: Drawable?,
    val widgets: List<WidgetPickerEntry>
)

/**
 * The full-screen ModalBottomSheet that shows available widgets grouped by app.
 *
 * @param onDismiss          Called when the sheet should close (back press, scrim tap, swipe down)
 * @param widgetHostManager  Used to query installed providers and compute spans
 * @param onExternalDragStarted Called when the user starts dragging a widget from the picker.
 *                              The sheet should close immediately so the drag can continue
 *                              to the home grid underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerBottomSheet(
    onDismiss: () -> Unit,
    widgetHostManager: WidgetHostManager,
    onExternalDragStarted: () -> Unit = {}
) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    // Build the grouped list once and cache it across recompositions.
    // Performance note: getInstalledProviders() is a Binder call but it's fast
    // enough for the initial sheet open. The `remember` prevents re-querying on
    // every recomposition.
    val appGroups: List<WidgetAppGroup> = remember {
        val allProviders = widgetHostManager.getInstalledProviders()

        // Map each provider to a WidgetPickerEntry with resolved labels.
        val entries = allProviders.map { info ->
            val (minCols, minRows) = widgetHostManager.calculateMinSpan(info)
            WidgetPickerEntry(
                providerInfo = info,
                label = info.loadLabel(packageManager) ?: info.provider.shortClassName,
                appLabel = info.loadLabel(packageManager)?.let {
                    // loadLabel returns the widget label, not the app label.
                    // We need the app label from the PackageManager.
                    try {
                        val appInfo = packageManager.getApplicationInfo(info.provider.packageName, 0)
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        info.provider.packageName
                    }
                } ?: info.provider.packageName,
                appIcon = try {
                    packageManager.getApplicationIcon(info.provider.packageName)
                } catch (_: Exception) {
                    null
                },
                span = GridSpan(columns = minCols, rows = minRows)
            )
        }

        // Group by app, sort groups alphabetically, sort widgets within each group.
        entries.groupBy { it.appLabel }
            .map { (appLabel, widgets) ->
                WidgetAppGroup(
                    appLabel = appLabel,
                    appIcon = widgets.first().appIcon,
                    widgets = widgets.sortedBy { it.label }
                )
            }
            .sortedBy { it.appLabel.lowercase() }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = Dp.Unspecified,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        // Sheet content: title bar + scrollable list of widget groups.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Title bar ──
            Text(
                text = "Widgets",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    horizontal = Spacing.mediumLarge,
                    vertical = Spacing.medium
                )
            )

            // ── Scrollable groups list ──
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.mediumLarge,
                    end = Spacing.mediumLarge,
                    bottom = Spacing.extraLarge
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                appGroups.forEach { group ->
                    // ── App group header ──
                    item(key = "header_${group.appLabel}") {
                        AppGroupHeader(
                            appLabel = group.appLabel,
                            appIcon = group.appIcon
                        )
                    }

                    // ── Widget cards for this app ──
                    items(
                        items = group.widgets,
                        key = { entry ->
                            "${entry.providerInfo.provider.packageName}/${entry.providerInfo.provider.className}"
                        }
                    ) { entry ->
                        WidgetCard(
                            entry = entry,
                            onExternalDragStarted = onExternalDragStarted
                        )
                    }
                }
            }
        }
    }
}

/**
 * Header row for a group of widgets from a single app.
 * Shows the app icon and app name.
 */
@Composable
private fun AppGroupHeader(
    appLabel: String,
    appIcon: Drawable?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.smallMedium),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.smallMedium)
    ) {
        // App icon — convert the platform Drawable to a Compose Image.
        appIcon?.let { drawable ->
            Image(
                bitmap = drawable.toBitmap(
                    width = IconSize.appList.value.toInt(),
                    height = IconSize.appList.value.toInt()
                ).asImageBitmap(),
                contentDescription = appLabel,
                modifier = Modifier.size(IconSize.appList)
            )
        }

        Text(
            text = appLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A single widget card showing the widget's preview, label, and default size.
 *
 * INTERACTION:
 * - **Tap** → intentionally ignored (widget cards are drag-only).
 * - **Long-press + drag** → starts a platform drag-and-drop session so the user can
 *   drag the widget out of the BottomSheet and drop it onto a specific cell on the
 *   home grid. When the drag starts, [onExternalDragStarted] is called to dismiss
 *   the picker immediately so the home grid drop target is visible.
 *
 * The card shows:
 * - A preview image if the widget provides one, otherwise a generic label
 * - The widget name
 * - The default span in "{columns} × {rows}" format
 */
@Composable
private fun WidgetCard(
    entry: WidgetPickerEntry,
    onExternalDragStarted: () -> Unit = {}
) {
    val context = LocalContext.current
    // hostView is needed to start the platform drag-and-drop session.
    val hostView = LocalView.current

    Surface(
        shape = RoundedCornerShape(CornerRadius.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .detectDragGesture(
                key = "${entry.providerInfo.provider.packageName}/${entry.providerInfo.provider.className}",
                dragThreshold = GridConfig.Default.dragThresholdPx,
                // Widget cards are intentionally not clickable.
                // Placement begins only after a successful drop on the home grid.
                onTap = {},
                onLongPress = {
                    // No-op: we wait for drag start to initiate the external drag.
                    // Long-press alone doesn't do anything special for widget cards.
                },
                onLongPressRelease = {
                    // Finger lifted after long-press without dragging — no-op.
                },
                onDragStart = {
                    // User started dragging after long-press.
                    // Initiate platform drag-and-drop so the widget can be dropped
                    // on the home grid (which is in a different window).
                    val dragStarted = startExternalWidgetDrag(
                        hostView = hostView,
                        providerInfo = entry.providerInfo,
                        span = entry.span,
                        dragShadowSize = IconSize.appGrid
                    )

                    if (dragStarted) {
                        // Close the BottomSheet so the home grid becomes visible
                        // and can receive the drop.
                        hostView.post {
                            onExternalDragStarted()
                        }
                    }
                },
                onDrag = { change, _ -> change.consume() },
                onDragEnd = {},
                onDragCancel = {}
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            modifier = Modifier.padding(Spacing.medium)
        ) {
            // Widget preview image (if available).
            val previewDrawable: Drawable? = remember(entry.providerInfo) {
                try {
                    entry.providerInfo.loadPreviewImage(context, 0)
                } catch (_: Exception) {
                    null
                }
            }

            if (previewDrawable != null) {
                Image(
                    bitmap = previewDrawable.toBitmap(
                        width = IconSize.appGrid.value.toInt(),
                        height = IconSize.appGrid.value.toInt()
                    ).asImageBitmap(),
                    contentDescription = entry.label,
                    modifier = Modifier.size(IconSize.appGrid)
                )
            } else {
                // Fallback: show the app icon if no widget preview exists.
                entry.appIcon?.let { icon ->
                    Image(
                        bitmap = icon.toBitmap(
                            width = IconSize.appGrid.value.toInt(),
                            height = IconSize.appGrid.value.toInt()
                        ).asImageBitmap(),
                        contentDescription = entry.label,
                        modifier = Modifier.size(IconSize.appGrid)
                    )
                }
            }

            // Widget label and span info.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${entry.span.columns} × ${entry.span.rows}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
