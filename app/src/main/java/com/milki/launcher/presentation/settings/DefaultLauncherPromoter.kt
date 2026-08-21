package com.milki.launcher.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.milki.launcher.core.launcher.isAppDefaultLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the default-launcher detection and "set as default" prompt lifecycle.
 *
 * Living in a ViewModel keeps the once-per-foreground-session prompt guard and
 * the current role state intact across configuration changes, where
 * Activity-held state would be reset and the prompt would re-show after e.g.
 * a rotation.
 */
class DefaultLauncherPromoter(
    private val appContext: Context
) : ViewModel() {

    private val _isDefaultLauncher =
        MutableStateFlow(isAppDefaultLauncher(appContext))
    val isDefaultLauncher: StateFlow<Boolean> = _isDefaultLauncher.asStateFlow()

    private val _showSetDefaultLauncherPrompt = MutableStateFlow(false)
    val showSetDefaultLauncherPrompt: StateFlow<Boolean> =
        _showSetDefaultLauncherPrompt.asStateFlow()

    private var hasPromptedInForegroundSession = false

    /** Call from [androidx.lifecycle.Lifecycle.Event.ON_START]: opens a new prompt session. */
    fun onForegroundSessionStarted() {
        hasPromptedInForegroundSession = false
    }

    /**
     * Call from [androidx.lifecycle.Lifecycle.Event.ON_RESUME] (and after a
     * role request resolves): re-checks the home role and arms the prompt at
     * most once per foreground session while the launcher is not default.
     */
    fun refresh() {
        val isDefault = isAppDefaultLauncher(appContext)
        _isDefaultLauncher.value = isDefault

        if (isDefault) {
            _showSetDefaultLauncherPrompt.value = false
            return
        }

        if (!hasPromptedInForegroundSession) {
            hasPromptedInForegroundSession = true
            _showSetDefaultLauncherPrompt.value = true
        }
    }

    fun dismissPrompt() {
        _showSetDefaultLauncherPrompt.value = false
    }
}
