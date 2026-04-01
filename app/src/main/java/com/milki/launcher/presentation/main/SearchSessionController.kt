package com.milki.launcher.presentation.main

import com.milki.launcher.presentation.search.SearchViewModel

/**
 * SearchSessionController.kt - Applies high-level search/menu session actions.
 *
 * WHY THIS FILE EXISTS:
 * MainActivity should not directly encode search session state transitions.
 * Instead, it should ask a small controller to apply transitions consistently.
 *
 * RESPONSIBILITIES:
 * - Execute home-button decisions returned by HomeButtonPolicy.
 * - Keep all "close menu / open search / clear query" transitions in one place.
 *
 * NON-RESPONSIBILITIES:
 * - It does not decide WHICH action to take (HomeButtonPolicy does that).
 * - It does not own lifecycle or Intent parsing (MainActivity does that).
 */
class SearchSessionController(
    private val searchViewModel: SearchViewModel
) {

    /**
     * Applies a previously-resolved home-button decision.
     *
     * @param decision The policy decision to execute.
     * @param closeHomescreenMenu Callback used to close homescreen menu state.
     */
    fun applyHomeButtonDecision(
        decision: HomeButtonPolicy.Decision,
        dismissContextMenus: () -> Unit,
        closeHomescreenMenu: () -> Unit
    ) {
        dismissContextMenus()

        when (decision) {
            HomeButtonPolicy.Decision.RESET_TRANSIENT_UI -> {
                closeHomescreenMenu()
                searchViewModel.hideSearch()
            }

            HomeButtonPolicy.Decision.CLOSE_MENU -> {
                closeHomescreenMenu()
                searchViewModel.showSearch()
            }

            HomeButtonPolicy.Decision.OPEN_SEARCH -> {
                searchViewModel.showSearch()
            }

            HomeButtonPolicy.Decision.CLEAR_QUERY -> {
                searchViewModel.clearQuery()
            }

            HomeButtonPolicy.Decision.HIDE_SEARCH -> {
                searchViewModel.hideSearch()
            }
        }
    }
}
