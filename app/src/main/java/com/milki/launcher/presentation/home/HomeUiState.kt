package com.milki.launcher.presentation.home

import androidx.compose.runtime.Immutable
import com.milki.launcher.domain.model.HomeItem

/**
 * UI state consumed by the home screen Compose surface.
 */
@Immutable
data class HomeUiState(
    val pinnedItems: List<HomeItem> = emptyList(),
    val isLoading: Boolean = true,
    val isUpdatingPositions: Boolean = false,
    val lastMoveErrorMessage: String? = null,
    val openFolderItem: HomeItem.FolderItem? = null
)
