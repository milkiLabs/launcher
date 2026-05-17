package com.milki.launcher.core.shortcut

import android.content.pm.ShortcutInfo

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
