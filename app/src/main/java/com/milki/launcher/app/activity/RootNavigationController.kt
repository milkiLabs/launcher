package com.milki.launcher.app.activity

/**
 * Seam between the composition-owned root back stack and intent handling
 * (`onNewIntent`) that runs outside composition.
 *
 * Implemented by a holder bound while the root navigation composable is
 * alive; defaults are no-ops so intent handling never dereferences nulls
 * when the UI is not composed yet (or already disposed).
 */
interface RootNavigationController {
    /** Whether the root back stack currently shows the home entry. */
    fun isAtHome(): Boolean

    /** Pops any non-home entries and clears transient launcher routes. */
    fun resetToHome()
}

/**
 * Default implementation whose behavior is bound from composition via
 * [bind] and released on dispose.
 */
class BindableRootNavigationController : RootNavigationController {
    private var isAtHomeCheck: () -> Boolean = { true }
    private var resetToHomeAction: () -> Unit = {}

    fun bind(isAtHome: () -> Boolean, resetToHome: () -> Unit) {
        isAtHomeCheck = isAtHome
        resetToHomeAction = resetToHome
    }

    fun unbind() {
        isAtHomeCheck = { true }
        resetToHomeAction = {}
    }

    override fun isAtHome(): Boolean = isAtHomeCheck()

    override fun resetToHome() = resetToHomeAction()
}
