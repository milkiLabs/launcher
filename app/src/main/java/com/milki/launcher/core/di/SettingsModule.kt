/**
 * SettingsModule.kt - Settings Feature Koin Dependency Injection Module
 *
 * This module provides all dependencies related to the SETTINGS feature:
 * - SettingsViewModel (presentation logic for the settings screen)
 *
 * NOTE: SettingsRepository is defined in coreModule, not here, because it is
 * shared between settingsModule (SettingsViewModel) and searchModule
 * (SearchViewModel reads prefix configurations). Only the ViewModel that
 * is exclusively used by the settings screen lives here.
 *
 * WHY A SEPARATE SETTINGS MODULE?
 * Even though this module currently contains just one ViewModel, separating it:
 * 1. Makes the feature boundary explicit (settings UI logic is isolated).
 * 2. Follows the same pattern as other feature modules for consistency.
 * 3. Provides a natural place to add settings-specific use cases in the future.
 *
 * DEPENDENCIES ON OTHER MODULES:
 * This module depends on coreModule for:
 * - SettingsRepository (SettingsViewModel needs it to read and write settings)
 *
 * DEPENDENCY DIRECTION:
 *   SettingsViewModel → SettingsRepository (from coreModule)
 *   (presentation → domain, as per architecture rules)
 *
 * For a full explanation of Koin concepts, see: docs/KoinDependencyInjection.md
 */

package com.milki.launcher.core.di

import com.milki.launcher.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Settings module — settings feature ViewModel.
 *
 * SettingsRepository comes from coreModule. This module only contains
 * the SettingsViewModel, which is the only consumer of SettingsRepository
 * from the settings feature's perspective.
 */
val settingsModule = module {

    // ========================================================================
    // VIEWMODEL - MANAGED BY KOIN
    // ========================================================================

    /**
     * SettingsViewModel - Manages settings UI state and user interactions.
     *
     * Handles:
     * - Reading current settings values (theme, prefix configurations, etc.)
     * - Writing updated settings when the user changes them
     * - Validating settings inputs
     *
     * LIFECYCLE: Scoped to the Activity/Composable that requests it.
     *            Survives configuration changes (screen rotation).
     *            Cleared when the lifecycle owner is destroyed.
     *
     * DEPENDENCY: SettingsRepository (from coreModule — settings data access)
     */
    viewModel {
        SettingsViewModel(
            settingsRepository = get()
        )
    }
}
