package com.milki.launcher.ui.components.common

import com.milki.launcher.data.contextmenu.AppContextDataCache
import com.milki.launcher.data.icon.AppIconMemoryCache
import androidx.compose.runtime.staticCompositionLocalOf
import com.milki.launcher.data.icon.FaviconCache

/**
 * Composition access to the DI-managed process-wide icon cache.
 *
 * The instances are created by Koin and provided once at the composition root
 * (MainActivity), keeping UI composables decoupled from object singletons while
 * preserving synchronous cache-hit reads for instant icon rendering.
 */
val LocalAppIconMemoryCache = staticCompositionLocalOf<AppIconMemoryCache> {
    error("LocalAppIconMemoryCache not provided at composition root")
}

/**
 * Composition access to the DI-managed process-wide context menu data cache.
 */
val LocalAppContextDataCache = staticCompositionLocalOf<AppContextDataCache> {
    error("LocalAppContextDataCache not provided at composition root")
}

/**
 * Composition access to the DI-managed favicon cache (fetch-once + disk).
 */
val LocalFaviconCache = staticCompositionLocalOf<FaviconCache> {
    error("LocalFaviconCache not provided at composition root")
}
