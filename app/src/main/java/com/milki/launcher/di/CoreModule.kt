/**
 * CoreModule.kt - Core Koin Dependency Injection Module
 *
 * This module provides the SHARED, cross-feature dependencies that multiple
 * feature modules depend on. These are the "foundation" dependencies —
 * repositories and services used by more than one feature.
 *
 * WHY A SEPARATE CORE MODULE?
 * When a dependency is used by multiple features (e.g., AppRepository is used
 * by both Search and App Drawer), it belongs in the core module rather than
 * in any single feature module. This prevents circular dependencies and makes
 * the dependency graph clear.
 *
 * WHAT BELONGS HERE:
 * - Repositories used by 2+ features (AppRepository, SettingsRepository)
 * - Cross-cutting services and utilities
 * - Shared domain interfaces and their implementations
 *
 * WHAT DOES NOT BELONG HERE:
 * - Feature-specific repositories (e.g., HomeRepository → homeModule)
 * - Feature-specific ViewModels (they go in their own feature module)
 * - Feature-specific use cases
 *
 * DEPENDENCY DIRECTION RULE:
 * Feature modules (search, home, etc.) can depend on coreModule,
 * but coreModule must NEVER depend on any feature module.
 * This ensures a clean, one-way dependency direction:
 *
 *   presentation (ViewModels) → domain (interfaces/use cases) → data (implementations)
 *
 *   Feature modules → coreModule (never the reverse)
 *
 * SINGLETON vs FACTORY reminder:
 * - single { }: Creates ONE instance for the entire app (singleton pattern)
 * - factory { }: Creates a NEW instance every time it's requested
 *
 * For a full explanation of Koin concepts, see: docs/KoinDependencyInjection.md
 */

package com.milki.launcher.di

import com.milki.launcher.data.repository.AppRepositoryImpl
import com.milki.launcher.data.repository.SettingsRepositoryImpl
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.SettingsRepository
import org.koin.dsl.module

/**
 * Core module — shared, cross-feature dependencies.
 *
 * These singletons are requested by multiple feature modules:
 * - AppRepository is used by searchModule (SearchViewModel) and drawerModule (AppDrawerViewModel).
 * - SettingsRepository is used by searchModule (SearchViewModel) and settingsModule (SettingsViewModel).
 *
 * Because they are shared, they live here in the core module so that no single
 * feature "owns" them, and every feature that needs them simply calls get().
 */
val coreModule = module {

    // ========================================================================
    // SHARED REPOSITORIES - SINGLETONS
    // ========================================================================
    // These repositories are used by more than one feature, so they belong
    // in the core module rather than in any single feature module.

    /**
     * AppRepository - Provides access to installed apps and recent apps.
     *
     * SINGLETON: Yes — we want one cache of installed apps shared across features.
     *
     * USED BY:
     * - searchModule → SearchViewModel (to search installed apps)
     * - drawerModule → AppDrawerViewModel (to list all installed apps)
     *
     * DEPENDENCY: Android Context (provided by Koin's androidContext())
     */
    single<AppRepository> {
        // AppRepositoryImpl needs the Application context.
        // get() retrieves the Context that was set in startKoin { androidContext() }
        AppRepositoryImpl(get())
    }

    /**
     * SettingsRepository - Provides access to launcher settings (prefix configs, theme, etc.).
     *
     * SINGLETON: Yes — we want one source of truth for settings across the app.
     *
     * USED BY:
     * - searchModule → SearchViewModel (reads prefix configurations for search routing)
     * - settingsModule → SettingsViewModel (reads and writes all settings)
     *
     * DEPENDENCY: Android Context (for DataStore)
     */
    single<SettingsRepository> {
        SettingsRepositoryImpl(get())
    }
}
