package com.milki.launcher.core.util

import androidx.compose.ui.graphics.Color

private val HexColorRegex = Regex("^#[0-9A-F]{6}$")
private const val HEX_RADIX = 16

/**
 * Parses a `#RRGGBB` hex string into a Compose [Color].
 *
 * Normalizes input by trimming, uppercasing, and prepending `#` if missing.
 * Returns `null` for invalid or empty input.
 */
fun hexToColor(hex: String?): Color? {
    val normalized = hex?.trim()?.uppercase()
    val withHash = normalized?.let { if (it.startsWith("#")) it else "#$it" } ?: return null
    if (!HexColorRegex.matches(withHash)) return null
    return Color(
        red = withHash.substring(1, 3).toInt(HEX_RADIX),
        green = withHash.substring(3, 5).toInt(HEX_RADIX),
        blue = withHash.substring(5, 7).toInt(HEX_RADIX),
    )
}

/**
 * Parses a `#RRGGBB` hex string into a Compose [Color], falling back to [default] when invalid.
 */
fun hexToColorOr(hex: String?, default: Color): Color = hexToColor(hex) ?: default
