package com.milki.launcher.presentation.search

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.SearchProviderConfig
import com.milki.launcher.domain.model.SearchResult

/**
 * Snapshot of slower-changing background inputs used by the search pipeline.
 */
internal data class SearchBackgroundState(
    val installedApps: List<AppInfo> = emptyList(),
    val recentApps: List<AppInfo> = emptyList(),
    val hasContactsPermission: Boolean = false,
    val hasFilesPermission: Boolean = false
)

/**
 * Output container for the asynchronous search pipeline.
 */
internal data class SearchPipelineOutput(
    val results: List<SearchResult> = emptyList(),
    val activeProviderConfig: SearchProviderConfig? = null,
    val isLoading: Boolean = false
)

internal data class SearchRuntimeSettings(
    val maxSearchResults: Int = 8,
    val showRecentApps: Boolean = true,
    val maxRecentApps: Int = 5,
    val autoFocusKeyboard: Boolean = true
)
