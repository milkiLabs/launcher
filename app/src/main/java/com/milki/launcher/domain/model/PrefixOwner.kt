/**
 * PrefixOwner.kt - Unified model for anything that owns search prefixes
 *
 * WHY THIS EXISTS:
 * Previously, search source prefixes and local provider prefixes were managed
 * through completely separate systems with duplicated logic. This interface
 * unifies them under a single concept: anything that maps prefixes to search
 * behavior is a "prefix owner".
 *
 * Both SearchSource (custom web searches) and local providers (Contacts, Files)
 * implement this contract, enabling:
 * - One repository for all prefix operations
 * - One UI component for editing prefixes
 * - Consistent reset-to-default behavior
 */

package com.milki.launcher.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Common contract for any entity that owns activation prefixes.
 */
interface PrefixOwner {
    val id: String
    val name: String
    val prefixes: List<String>
    val defaultPrefixes: List<String>

    val isDefault: Boolean
        get() = prefixes == defaultPrefixes

    val canReset: Boolean
        get() = prefixes != defaultPrefixes && prefixes.isNotEmpty() && defaultPrefixes.isNotEmpty()
}

/**
 * A local provider (Contacts, Files) as a prefix owner.
 */
@Immutable
data class ProviderPrefixOwner(
    override val id: String,
    override val name: String,
    override val prefixes: List<String>,
    override val defaultPrefixes: List<String>,
    val icon: ImageVector,
    val accentColor: Color
) : PrefixOwner

/**
 * A search source as a prefix owner.
 *
 * Delegates to an underlying SearchSource for prefix data.
 */
@Immutable
data class SourcePrefixOwner(
    val source: SearchSource
) : PrefixOwner {
    override val id: String get() = source.id
    override val name: String get() = source.name
    override val prefixes: List<String> get() = source.prefixes
    override val defaultPrefixes: List<String> get() = source.defaultPrefixes
}
