package com.milki.launcher.core.shortcut

import android.content.pm.ShortcutInfo
import com.milki.launcher.domain.model.HomeItem

/**
 * Comparator that sorts shortcuts by:
 * 1. Dynamic shortcuts first (before manifest shortcuts)
 * 2. Manifest-declared shortcuts before non-declared
 * 3. Lower rank first
 */
val ShortcutInfoComparator: Comparator<ShortcutInfo> = compareBy(
    { !it.isDynamic },
    { !it.isDeclaredInManifest },
    { it.rank }
)

/**
 * Converts a [ShortcutInfo] to a [HomeItem.AppShortcut].
 */
fun ShortcutInfo.toAppShortcut(): HomeItem.AppShortcut {
    return HomeItem.AppShortcut.fromShortcutInfo(
        packageName = `package`,
        shortcutId = id,
        shortLabel = shortLabel?.toString().orEmpty(),
        longLabel = longLabel?.toString() ?: shortLabel?.toString().orEmpty()
    )
}
