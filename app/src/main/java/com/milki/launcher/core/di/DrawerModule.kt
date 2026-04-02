/**
 * DrawerModule.kt - App Drawer Feature Koin Dependency Injection Module
 *
 * This module provides all dependencies related to the APP DRAWER feature:
 * - AppDrawerViewModel (presentation logic for the full app list drawer)
 *
 * NOTE: AppRepository is defined in coreModule, not here, because it is
 * shared between drawerModule (AppDrawerViewModel) and searchModule
 * (SearchViewModel searches installed apps). Only the ViewModel that
 * is exclusively used by the app drawer lives here.
 *
 * WHY A SEPARATE DRAWER MODULE?
 * Even though this module currently contains just one ViewModel, separating it:
 * 1. Makes the feature boundary explicit (drawer UI logic is isolated).
 * 2. Follows the same pattern as other feature modules for consistency.
 * 3. Provides a natural place to add drawer-specific use cases in the future
 *    (e.g., app categorization, section headers, or fast-jump navigation).
 *
 * DEPENDENCIES ON OTHER MODULES:
 * This module depends on coreModule for:
 * - AppRepository (AppDrawerViewModel needs it to load the full installed app list)
 *
 * DEPENDENCY DIRECTION:
 *   AppDrawerViewModel → AppRepository (from coreModule)
 *   (presentation → domain, as per architecture rules)
 *
 * For a full explanation of Koin concepts, see: docs/KoinDependencyInjection.md
 */

package com.milki.launcher.core.di

import com.milki.launcher.domain.drawer.DrawerAppStore
import com.milki.launcher.presentation.drawer.DrawerListAssembler
import com.milki.launcher.presentation.drawer.AppDrawerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Drawer module — app drawer feature ViewModel.
 *
 * AppRepository comes from coreModule. This module only contains
 * the AppDrawerViewModel, which is the only consumer of AppRepository
 * from the drawer feature's perspective.
 */
val drawerModule = module {

    single {
        DrawerAppStore()
    }

    single {
        DrawerListAssembler()
    }

    // ========================================================================
    // VIEWMODEL - MANAGED BY KOIN
    // ========================================================================

    /**
    * AppDrawerViewModel - Manages the app drawer's app list and loading state.
     *
     * Handles:
     * - Loading the full list of installed apps from AppRepository
    * - Exposing a stable app list snapshot to the drawer UI
    * - Tracking loading state for initial render
     *
     * LIFECYCLE: Scoped to the Activity/Composable that requests it.
     *            Survives configuration changes (screen rotation).
     *            Cleared when the lifecycle owner is destroyed.
     *
     * DEPENDENCY: AppRepository (from coreModule — installed apps data access)
     */
    viewModel {
        AppDrawerViewModel(
            appRepository = get(),
            drawerAppStore = get(),
            drawerListAssembler = get()
        )
    }
}
