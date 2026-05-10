package com.milki.launcher.presentation.search

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.PermissionAccessState
import com.milki.launcher.domain.model.SearchProviderConfig
import com.milki.launcher.domain.model.SearchResult
import com.milki.launcher.domain.model.SearchSource

/**
 * Snapshot of slower-changing background inputs used by the search pipeline.
 */
internal data class SearchBackgroundState(
    val installedApps: List<AppInfo> = emptyList(),
    val recentApps: List<AppInfo> = emptyList(),
    val contactsPermissionState: PermissionAccessState = PermissionAccessState.CAN_REQUEST,
    val filesPermissionState: PermissionAccessState = PermissionAccessState.CAN_REQUEST
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
    val autoFocusKeyboard: Boolean = true,
    /** Enabled search sources to surface in the suggested-action chip row. */
    val searchSources: List<SearchSource> = emptyList(),
    /** User's preferred default search engine source ID. */
    val defaultSearchSourceId: String? = null
)
