package com.milki.launcher.ui.components.search

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.milki.launcher.ui.components.common.AppIcon
import com.milki.launcher.ui.components.common.DrawableIcon
import com.milki.launcher.ui.components.common.LocalFaviconCache
import com.milki.launcher.ui.theme.IconSize

/**
 * Initial-letter monogram fallback for suggestion pills.
 *
 * Mirrors the placeholder Surface pattern from ActionShortcutIcon
 * (PinnedItem.kt): colored circle + centered content, no IPC, no network.
 */
@Composable
fun MonogramIcon(
    text: String,
    accentColor: Color?,
    modifier: Modifier = Modifier,
    size: Dp = IconSize.small
) {
    val initials = remember(text) { monogramInitials(text) }
    val container = accentColor ?: MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (accentColor != null) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = container
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

internal fun monogramInitials(text: String): String {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return if (words.isEmpty()) {
        "?"
    } else if (words.size == 1) {
        val word = words[0]
        // Prefer first alphanumeric; fall back to first char.
        val alphaNum = word.firstOrNull { it.isLetterOrDigit() } ?: word.first()
        val second = word.firstOrNull { it != alphaNum && it.isLetterOrDigit() }
        if (second != null) "$alphaNum$second".uppercase() else alphaNum.uppercaseChar().toString()
    } else {
        words.take(2).mapNotNull { w ->
            w.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
        }.joinToString("").ifEmpty { "?" }
    }
}

/**
 * Single decision point for suggestion pill leading icons.
 *
 * Chain (first hit wins, never blocks composition):
 * - packageName != null → real app icon via shared AppIcon (cache-first,
 *   async miss fill, same contract as ShortcutIcon/ActionShortcutIcon).
 * - faviconHost != null → fetch-once favicon ([FaviconIcon]); while loading
 *   or on failure the monogram below shows instead.
 * - null → instant MonogramIcon, never a blocking load.
 */
@Composable
fun AppOrMonogramIcon(
    packageName: String?,
    fallbackText: String,
    accentColor: Color?,
    modifier: Modifier = Modifier,
    size: Dp = IconSize.small,
    faviconHost: String? = null
) {
    if (packageName != null) {
        AppIcon(
            packageName = packageName,
            modifier = modifier,
            size = size
        )
    } else if (faviconHost != null) {
        FaviconIcon(
            host = faviconHost,
            fallbackText = fallbackText,
            accentColor = accentColor,
            modifier = modifier,
            size = size
        )
    } else {
        MonogramIcon(
            text = fallbackText,
            accentColor = accentColor,
            modifier = modifier,
            size = size
        )
    }
}

/**
 * Fetch-once website icon with instant monogram placeholder.
 *
 * Mirrors the AppIcon contract: synchronous memory-cache read for the first
 * frame, background [FaviconCache.getOrLoad] on miss (network + disk on IO),
 * monogram rendered meanwhile and permanently on failure. Failures are
 * negatively cached in-process so recomposition never refetches.
 */
@Composable
fun FaviconIcon(
    host: String,
    fallbackText: String,
    accentColor: Color?,
    modifier: Modifier = Modifier,
    size: Dp = IconSize.small
) {
    val context = LocalContext.current
    val faviconCache = LocalFaviconCache.current

    var faviconDrawable by remember(host) {
        mutableStateOf(
            faviconCache.getCached(host)?.let { BitmapDrawable(context.resources, it) }
        )
    }

    LaunchedEffect(host) {
        if (faviconDrawable == null) {
            faviconCache.getOrLoad(host)?.let { bitmap ->
                faviconDrawable = BitmapDrawable(context.resources, bitmap)
            }
        }
    }

    val drawable = faviconDrawable
    if (drawable != null) {
        DrawableIcon(
            drawable = drawable,
            modifier = modifier,
            size = size
        )
    } else {
        MonogramIcon(
            text = fallbackText,
            accentColor = accentColor,
            modifier = modifier,
            size = size
        )
    }
}
