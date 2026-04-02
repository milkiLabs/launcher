package com.milki.launcher.presentation.launcher

/**
 * HomeIntentCoordinator.kt - Coordinates HOME-button intent behavior for launcher root.
 *
 * WHY THIS FILE EXISTS:
 * MainActivity previously mixed three concerns in one place:
 * 1. lifecycle-driven "already on homescreen" state tracking
 * 2. layered surface pre-consumption rules (drawer/widget/folder)
 * 3. HomeButtonPolicy decision execution
 *
 * This coordinator centralizes that orchestration so MainActivity can remain a host shell.
 */
interface HomeIntentCoordinatorContract {

    /**
     * Marks launcher as foregrounded.
     */
    fun onResume()

    /**
     * Marks launcher as backgrounded.
     */
    fun onStop()

    /**
     * Handles one HOME-button press after the caller confirms it is a launcher HOME intent.
     */
    fun onHomeButtonPressed(
        isSearchVisible: Boolean,
        hasSearchQuery: Boolean,
        shouldClearSearchQueryOnHomePress: Boolean
    )
}

/**
 * Default coordinator implementation.
 *
 * Dependency direction is intentionally callback-based so this class stays pure Kotlin
 * and unit-test friendly.
 */
class HomeIntentCoordinator(
    private val homeButtonPolicy: HomeButtonPolicy,
    private val isHomescreenMenuOpen: () -> Boolean,
    private val consumeLayeredHomePress: () -> Boolean,
    private val applyDecision: (HomeButtonPolicy.Decision) -> Unit
) : HomeIntentCoordinatorContract {

    /**
     * Mirrors historical MainActivity flag that distinguishes first return to launcher
     * from repeated HOME presses while already on launcher.
     */
    private var wasAlreadyOnHomescreen = false

    /**
     * Called when launcher enters foreground.
     */
    override fun onResume() {
        wasAlreadyOnHomescreen = true
    }

    /**
     * Called when launcher leaves foreground.
     */
    override fun onStop() {
        wasAlreadyOnHomescreen = false
    }

    /**
     * Handles HOME-button policy orchestration.
     *
     * FLOW:
     * 1. Try layered-surface consumption first (drawer/widget/folder).
     * 2. If not consumed, build policy input snapshot.
     * 3. Resolve a deterministic decision through HomeButtonPolicy.
     * 4. Delegate side effects to decision applier.
     */
    override fun onHomeButtonPressed(
        isSearchVisible: Boolean,
        hasSearchQuery: Boolean,
        shouldClearSearchQueryOnHomePress: Boolean
    ) {
        if (consumeLayeredHomePress()) {
            return
        }

        val decision = homeButtonPolicy.resolve(
            HomeButtonPolicy.InputState(
                isAlreadyOnHomescreen = wasAlreadyOnHomescreen,
                isHomescreenMenuOpen = isHomescreenMenuOpen(),
                isSearchVisible = isSearchVisible,
                hasSearchQuery = hasSearchQuery,
                shouldClearSearchQueryOnHomePress = shouldClearSearchQueryOnHomePress
            )
        )

        applyDecision(decision)
    }
}
