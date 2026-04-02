/**
 * FolderIcon.kt - Visual representation of a folder on the home screen
 *
 * This file contains the composable used to display a [HomeItem.FolderItem] as an
 * icon on the launcher home grid. It replaces the single large icon that standalone
 * items use and instead shows a two-by-two preview grid of the folder's contents.
 *
 * VISUAL DESIGN:
 * ┌──────────────────────┐
 * │  ╔════╦════╗         │  ← Frosted rounded-rect background
 * │  ║ 🔵 ║ 🟢 ║         │     (surfaceVariant at 55% alpha)
 * │  ╠════╬════╣         │
 * │  ║ 🔴 ║ 🟡 ║         │  ← Up to 4 mini app icons in a 2×2 grid
 * │  ╚════╩════╝         │
 * │    Folder Name       │  ← Label shown below the icon, same style as PinnedItem
 * └──────────────────────┘
 *
 * ICON PREVIEW LOGIC:
 * Up to 4 items from [folder.children] are rendered as mini icons.
 * - PinnedApp → uses AppIcon (loads from package manager, memory-cached)
 * - AppShortcut → uses AppIcon (shows parent app icon as fallback)
 * - PinnedFile → uses a colored square icon matching the file MIME type
 * - PinnedContact → uses a person silhouette icon
 * If there are fewer than 4 children, empty cells are left blank (transparent).
 *
 * SIZE CONSTANTS (all from Spacing.kt / IconSize.kt — no hardcoded dp values):
 * - Folder icon container: [IconSize.appGrid] = 56dp  (same as other home-screen icons)
 * - Inner padding:         [Spacing.smallMedium] = 8dp on all sides
 * - Each mini icon:        [IconSize.small] = 20dp    ((56 - 2×8) / 2 = 20dp ✓)
 * - Corner radius:         [CornerRadius.medium] = 12dp
 */

package com.milki.launcher.ui.components.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.common.AppIcon
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

// ============================================================================
// PUBLIC COMPOSABLE
// ============================================================================

/**
 * FolderIcon - Renders a [HomeItem.FolderItem] as a home-screen icon cell.
 *
 * Shows a frosted rounded-rect containing a 2×2 grid of child-item mini-previews,
 * plus the folder name below. This composable is used inside [PinnedItem] when the
 * item type is [HomeItem.FolderItem].
 *
 * USAGE:
 * Called from [PinnedItemContent] / [PinnedItem] when item is [HomeItem.FolderItem].
 * The parent size is determined by the home grid cell (typically [IconSize.appGrid]).
 *
 * @param folder The folder whose name and children should be rendered.
 * @param modifier Optional modifier applied to the root column (cell area).
 */
@Composable
fun FolderIcon(
    folder: HomeItem.FolderItem,
    modifier: Modifier = Modifier
) {
    // The root column mirrors the layout of PinnedItemContent so the folder
    // icon aligns correctly with other home-screen items.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.medium, horizontal = Spacing.smallMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ─── Folder icon container ───────────────────────────────────────────
        // A fixed-size box matching the standard app icon area ([IconSize.appGrid]).
        // The frosted surface uses [surfaceVariant] at reduced alpha so the
        // wallpaper peeks through slightly, maintaining the "launcher glass" aesthetic.
        Box(
            modifier = Modifier
                .size(IconSize.appGrid)
                .clip(RoundedCornerShape(CornerRadius.medium))
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(CornerRadius.medium)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Inner 2×2 mini-icon grid.
            // Padding keeps mini icons away from the rounded corners so they
            // don't visually clip against the background shape.
            FolderMiniGrid(children = folder.children)
        }

        // ─── Folder label ────────────────────────────────────────────────────
        // Same style as other PinnedItem labels for visual consistency.
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.smallMedium)
        )
    }
}

// ============================================================================
// PRIVATE HELPERS
// ============================================================================

/**
 * FolderMiniGrid renders up to 4 children of a folder in a 2×2 grid.
 *
 * Only the first 4 children are previewed. Empty cells are left blank.
 *
 * LAYOUT:
 * ```
 *   Row 1: [child0]  [child1]
 *   Row 2: [child2]  [child3]
 * ```
 *
 * @param children The folder's children list (only the first 4 are rendered).
 */
@Composable
private fun FolderMiniGrid(children: List<HomeItem>) {
    // Take at most 4 items for the preview. If fewer are available,
    // the remaining cells are filled with null (rendered as blank space).
    val previews: List<HomeItem?> = (0 until 4).map { i ->
        children.getOrNull(i)
    }

    // Two rows, each containing two mini-icon slots.
    Column(
        modifier = Modifier.padding(Spacing.smallMedium),
        verticalArrangement = Arrangement.spacedBy(Spacing.none),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1: slots 0 and 1
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.none),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FolderMiniIconSlot(item = previews[0])
            FolderMiniIconSlot(item = previews[1])
        }
        // Row 2: slots 2 and 3
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.none),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FolderMiniIconSlot(item = previews[2])
            FolderMiniIconSlot(item = previews[3])
        }
    }
}

/**
 * FolderMiniIconSlot renders a single mini-icon cell within the folder preview.
 *
 * SIZE: [IconSize.small] = 20dp
 * Each slot is exactly 20dp × 20dp, filling a quarter of the 40dp inner area
 * (the 56dp container minus 8dp padding on each side).
 *
 * ITEM TYPE DISPATCH:
 * - null         → blank transparent Box (empty slot)
 * - PinnedApp    → [AppIcon] at mini size
 * - AppShortcut  → [AppIcon] using the parent app's package (best available icon)
 * - PinnedFile   → small colored square derived from MIME type (inline; no code import)
 * - PinnedContact → Material [Icons.Default.Person] on a subtle background
 *
 * @param item The home item to render, or null for an empty slot.
 */
@Composable
private fun FolderMiniIconSlot(item: HomeItem?) {
    // All slots occupy IconSize.small so the 2×2 grid stays perfectly square.
    Box(
        modifier = Modifier.size(IconSize.small),
        contentAlignment = Alignment.Center
    ) {
        when (item) {
            null -> {
                // Empty slot — render nothing (transparent box, size already set).
            }
            is HomeItem.PinnedApp -> {
                // Reuse the same AppIcon composable used on the home screen.
                // The in-memory icon cache means this is fast and doesn't cause recomposition churn.
                AppIcon(
                    packageName = item.packageName,
                    size = IconSize.small,
                    modifier = Modifier.size(IconSize.small)
                )
            }
            is HomeItem.AppShortcut -> {
                // App shortcuts don't have their own icon API at this size;
                // show the parent app's icon as the best available proxy.
                AppIcon(
                    packageName = item.packageName,
                    size = IconSize.small,
                    modifier = Modifier.size(IconSize.small)
                )
            }
            is HomeItem.PinnedFile -> {
                // Inline a small colored square for files (avoids importing the private
                // getFileIconData composable from PinnedItem.kt).
                // The color gives a quick MIME-category visual cue.
                MiniFileIconSlot(item = item)
            }
            is HomeItem.PinnedContact -> {
                // Show a small person silhouette on a subtle background.
                MiniContactIconSlot()
            }
            is HomeItem.FolderItem -> {
                // Nested FolderItems should never appear as children (the nesting
                // guard in the repository prevents this). But if somehow they do,
                // render a neutral placeholder to avoid a crash.
            }
            is HomeItem.WidgetItem -> {
                // Widgets cannot be placed inside folders, so this branch should
                // never be reached. Present only for exhaustiveness.
            }
        }
    }
}

/**
 * MiniFileIconSlot renders a small colored file-type indicator.
 *
 * Uses a rounded square background whose color is chosen from the file's MIME type.
 * This mirrors the color scheme used in the full-size [PinnedItem] file icons,
 * providing a consistent visual language at the mini preview size.
 */
@Composable
private fun MiniFileIconSlot(item: HomeItem.PinnedFile) {
    // Pick a background color that communicates the file category at a glance.
    val backgroundColor = when {
        item.mimeType == "application/pdf" -> Color(0xFFE53935)        // Red for PDF
        item.mimeType.startsWith("image/") -> Color(0xFF43A047)         // Green for images
        item.mimeType.startsWith("video/") -> Color(0xFFFB8C00)         // Orange for video
        item.mimeType.startsWith("audio/") -> Color(0xFF8E24AA)         // Purple for audio
        item.mimeType.contains("spreadsheet") -> Color(0xFF43A047)      // Green for spreadsheet
        item.mimeType.contains("document") -> Color(0xFF1E88E5)         // Blue for document
        item.mimeType.contains("zip") || item.mimeType.contains("archive") -> Color(0xFF757575)
        else -> Color(0xFF9E9E9E)                                        // Grey for unknown
    }

    Box(
        modifier = Modifier
            .size(IconSize.small)
            .clip(RoundedCornerShape(CornerRadius.extraSmall))
            .background(color = backgroundColor.copy(alpha = 0.8f))
    )
}

/**
 * MiniContactIconSlot renders a small person silhouette for pinned contacts.
 */
@Composable
private fun MiniContactIconSlot() {
    Surface(
        modifier = Modifier.size(IconSize.small),
        shape = RoundedCornerShape(CornerRadius.extraSmall),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                // Icon fills about 60% of the slot to maintain a comfortable visual weight.
                modifier = Modifier.size(IconSize.extraSmall)
            )
        }
    }
}
