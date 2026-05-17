/**
 * SettingsModule.kt - Settings Feature Koin Dependency Injection Module
 *
 * Provides the SettingsViewModel with all focused settings interfaces it needs.
 */

package com.milki.launcher.core.di

import com.milki.launcher.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Settings module — settings feature ViewModel.
 */
val settingsModule = module {

    /**
     * SettingsViewModel - Manages settings UI state and user interactions.
     *
     * Depends on focused interfaces rather than a god interface.
     */
    viewModel {
        SettingsViewModel(
            settingsReader = get(),
            searchSourceRepository = get(),
            prefixConfigRepository = get(),
            homeTriggerRepository = get(),
            hiddenAppsRepository = get(),
            appRepository = get(),
            actionShortcutRepository = get(),
            launcherBackupRepository = get(),
            urlHandlerResolver = get()
        )
    }
}
