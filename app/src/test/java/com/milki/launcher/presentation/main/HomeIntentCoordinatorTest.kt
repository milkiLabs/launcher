package com.milki.launcher.presentation.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [HomeIntentCoordinator].
 *
 * WHY THESE TESTS EXIST:
 * This coordinator now owns HOME-button orchestration that used to live in MainActivity.
 * We lock behavior with tests so future Activity refactors do not accidentally change
 * launcher navigation policy.
 */
class HomeIntentCoordinatorTest {

    /**
     * If a layered surface consumes HOME press, policy decision must not execute.
     */
    @Test
    fun layered_surface_consumption_short_circuits_policy() {
        var appliedDecision: HomeButtonPolicy.Decision? = null

        val coordinator = HomeIntentCoordinator(
            homeButtonPolicy = HomeButtonPolicy(),
            isHomescreenMenuOpen = { false },
            consumeLayeredHomePress = { true },
            applyDecision = { decision ->
                appliedDecision = decision
            }
        )

        coordinator.onResume()
        coordinator.onHomeButtonPressed(
            isSearchVisible = true,
            hasSearchQuery = true
        )

        assertEquals(null, appliedDecision)
    }

    /**
     * On first return to launcher (not already on homescreen), policy should reset transient UI.
     */
    @Test
    fun first_home_press_after_background_requests_reset_transient_ui() {
        var appliedDecision: HomeButtonPolicy.Decision? = null

        val coordinator = HomeIntentCoordinator(
            homeButtonPolicy = HomeButtonPolicy(),
            isHomescreenMenuOpen = { false },
            consumeLayeredHomePress = { false },
            applyDecision = { decision ->
                appliedDecision = decision
            }
        )

        coordinator.onStop()
        coordinator.onHomeButtonPressed(
            isSearchVisible = false,
            hasSearchQuery = false
        )

        assertEquals(HomeButtonPolicy.Decision.RESET_TRANSIENT_UI, appliedDecision)
    }

    /**
     * When already on homescreen and menu is open, menu close has priority.
     */
    @Test
    fun menu_open_state_prioritizes_close_menu_decision() {
        var appliedDecision: HomeButtonPolicy.Decision? = null

        val coordinator = HomeIntentCoordinator(
            homeButtonPolicy = HomeButtonPolicy(),
            isHomescreenMenuOpen = { true },
            consumeLayeredHomePress = { false },
            applyDecision = { decision ->
                appliedDecision = decision
            }
        )

        coordinator.onResume()
        coordinator.onHomeButtonPressed(
            isSearchVisible = false,
            hasSearchQuery = false
        )

        assertEquals(HomeButtonPolicy.Decision.CLOSE_MENU, appliedDecision)
    }

    /**
     * Search-hidden state should open search when already on homescreen.
     */
    @Test
    fun search_hidden_state_opens_search() {
        var appliedDecision: HomeButtonPolicy.Decision? = null

        val coordinator = HomeIntentCoordinator(
            homeButtonPolicy = HomeButtonPolicy(),
            isHomescreenMenuOpen = { false },
            consumeLayeredHomePress = { false },
            applyDecision = { decision ->
                appliedDecision = decision
            }
        )

        coordinator.onResume()
        coordinator.onHomeButtonPressed(
            isSearchVisible = false,
            hasSearchQuery = false
        )

        assertEquals(HomeButtonPolicy.Decision.OPEN_SEARCH, appliedDecision)
    }

    /**
     * Search-visible + query state should clear query first.
     */
    @Test
    fun search_visible_with_query_clears_query() {
        var appliedDecision: HomeButtonPolicy.Decision? = null

        val coordinator = HomeIntentCoordinator(
            homeButtonPolicy = HomeButtonPolicy(),
            isHomescreenMenuOpen = { false },
            consumeLayeredHomePress = { false },
            applyDecision = { decision ->
                appliedDecision = decision
            }
        )

        coordinator.onResume()
        coordinator.onHomeButtonPressed(
            isSearchVisible = true,
            hasSearchQuery = true
        )

        assertEquals(HomeButtonPolicy.Decision.CLEAR_QUERY, appliedDecision)
    }

    /**
     * Search-visible + empty query should hide search.
     */
    @Test
    fun search_visible_without_query_hides_search() {
        var appliedDecision: HomeButtonPolicy.Decision? = null

        val coordinator = HomeIntentCoordinator(
            homeButtonPolicy = HomeButtonPolicy(),
            isHomescreenMenuOpen = { false },
            consumeLayeredHomePress = { false },
            applyDecision = { decision ->
                appliedDecision = decision
            }
        )

        coordinator.onResume()
        coordinator.onHomeButtonPressed(
            isSearchVisible = true,
            hasSearchQuery = false
        )

        assertEquals(HomeButtonPolicy.Decision.HIDE_SEARCH, appliedDecision)
    }

    /**
     * Safety check that onResume/onStop transitions are accepted repeatedly.
     */
    @Test
    fun lifecycle_marker_can_toggle_multiple_times_without_crash() {
        val coordinator = HomeIntentCoordinator(
            homeButtonPolicy = HomeButtonPolicy(),
            isHomescreenMenuOpen = { false },
            consumeLayeredHomePress = { false },
            applyDecision = { _ -> }
        )

        coordinator.onResume()
        coordinator.onStop()
        coordinator.onResume()
        coordinator.onHomeButtonPressed(
            isSearchVisible = false,
            hasSearchQuery = false
        )

        assertTrue(true)
    }
}
