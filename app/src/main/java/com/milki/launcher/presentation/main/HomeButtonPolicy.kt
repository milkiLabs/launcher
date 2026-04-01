package com.milki.launcher.presentation.main

/**
 * HomeButtonPolicy.kt - Pure policy engine for launcher home-button behavior.
 *
 * WHY THIS FILE EXISTS:
 * The launcher's home-button behavior used to live directly inside MainActivity.
 * That made behavior harder to test because UI/lifecycle concerns were mixed with
 * decision logic.
 *
 * This policy class is intentionally PURE:
 * - No Android framework dependencies.
 * - No direct ViewModel dependencies.
 * - No side effects.
 *
 * It only takes simple state inputs and returns an action describing what the
 * caller should do.
 *
 * BENEFITS:
 * 1. Unit-test friendly logic (can test with plain Kotlin).
 * 2. Deterministic, explicit priority ordering.
 * 3. MainActivity becomes a thin host instead of a policy owner.
 */
class HomeButtonPolicy {

    /**
     * Simple snapshot of the UI/session state needed to decide home-button action.
     *
     * @property isAlreadyOnHomescreen Whether launcher was already foregrounded.
     * @property isHomescreenMenuOpen Whether homescreen long-press dropdown is visible.
     * @property isSearchVisible Whether search dialog is currently visible.
     * @property hasSearchQuery Whether search query currently has user-entered text.
     */
    data class InputState(
        val isAlreadyOnHomescreen: Boolean,
        val isHomescreenMenuOpen: Boolean,
        val isSearchVisible: Boolean,
        val hasSearchQuery: Boolean
    )

    /**
     * Resulting action the caller should execute.
     */
    enum class Decision {
        /**
         * Launcher came to foreground from another task: close overlays and hide search.
         */
        RESET_TRANSIENT_UI,

        /**
         * A homescreen dropdown/menu is open; close it before opening search.
         */
        CLOSE_MENU,

        /**
         * Open search dialog.
         */
        OPEN_SEARCH,

        /**
         * Clear current query while keeping search visible.
         */
        CLEAR_QUERY,

        /**
         * Hide search dialog.
         */
        HIDE_SEARCH
    }

    /**
     * Resolve the single action that should run for a home-button press.
     *
     * PRIORITY ORDER:
     * 1) Not already on homescreen -> reset transient UI
    * 2) Menu open -> close menu, then open search
     * 3) Search hidden -> open search
     * 4) Search visible + has query -> clear query
     * 5) Search visible + empty query -> hide search
     */
    fun resolve(input: InputState): Decision {
        if (!input.isAlreadyOnHomescreen) {
            return Decision.RESET_TRANSIENT_UI
        }

        if (input.isHomescreenMenuOpen) {
            return Decision.CLOSE_MENU
        }

        return when {
            !input.isSearchVisible -> Decision.OPEN_SEARCH
            input.hasSearchQuery -> Decision.CLEAR_QUERY
            else -> Decision.HIDE_SEARCH
        }
    }
}
