/**
 * AppModule.kt - Koin Dependency Injection Module Aggregator
 *
 * This file aggregates all feature-specific Koin modules into a single list
 * that can be loaded by the Application class. Instead of defining all
 * dependencies in one monolithic module, dependencies are now split by feature:
 *
 * MODULE STRUCTURE (after split):
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │                     allModules (aggregator)                        │
 * │                                                                    │
 * │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
 * │  │ coreModule   │  │ searchModule │  │ homeModule   │             │
 * │  │ AppRepository│  │ ContactsRepo │  │ HomeRepo     │             │
 * │  │ SettingsRepo │  │ FilesRepo    │  │ HomeViewModel│             │
 * │  │              │  │ Providers    │  │              │             │
 * │  │              │  │ UseCases     │  │              │             │
 * │  │              │  │ SearchVM     │  │              │             │
 * │  └──────────────┘  └──────────────┘  └──────────────┘             │
 * │                                                                    │
 * │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
 * │  │ widgetModule │  │ settingsModule│ │ drawerModule │             │
 * │  │ WidgetHost   │  │ SettingsVM   │  │ DrawerVM     │             │
 * │  │   Manager    │  │              │  │              │             │
 * │  └──────────────┘  └──────────────┘  └──────────────┘             │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * WHY SPLIT INTO MULTIPLE MODULES?
 * 1. Feature isolation: Each feature's dependencies are defined together.
 * 2. Clear dependency direction: Feature modules depend on coreModule, never the reverse.
 * 3. Easier testing: Load only the modules you need for a specific test.
 * 4. Better onboarding: New developers can understand one feature module at a time.
 * 5. Future-proof: Prepares for multi-module Gradle project if the app grows.
 *
 * DEPENDENCY DIRECTION RULES:
 * - Feature modules (search, home, settings, drawer, widget) → coreModule (allowed)
 * - coreModule → any feature module (NEVER — would create circular dependency)
 * - Feature module → another feature module (NEVER — use coreModule as intermediary)
 * - All modules follow: presentation → domain → data
 *
 * MODULE FILES:
 * - CoreModule.kt     → Shared repositories (AppRepository, SettingsRepository)
 * - SearchModule.kt   → Search feature (ContactsRepo, FilesRepo, providers, use cases, SearchVM)
 * - HomeModule.kt     → Home screen feature (HomeRepository, HomeViewModel)
 * - WidgetModule.kt   → Widget infrastructure (WidgetHostManager)
 * - SettingsModule.kt → Settings feature (SettingsViewModel)
 * - DrawerModule.kt   → App drawer feature (AppDrawerViewModel)
 *
 * USAGE:
 * ```kotlin
 * // In LauncherApplication
 * startKoin {
 *     androidContext(this@LauncherApplication)
 *     modules(allModules)
 * }
 * ```
 *
 * For a full explanation of Koin concepts, see: docs/KoinDependencyInjection.md
 */

package com.milki.launcher.core.di

/**
 * All Koin modules aggregated into a single list for convenient loading.
 *
 * This is the only thing that LauncherApplication needs to import.
 * The order does not matter — Koin resolves dependencies lazily when they
 * are first requested, not at module registration time. However, we list
 * coreModule first for readability since it provides the foundation that
 * other modules build on.
 *
 * LOADING ORDER (for readability, not dependency resolution):
 * 1. coreModule     — shared repositories (foundation layer)
 * 2. searchModule   — search feature (depends on coreModule)
 * 3. homeModule     — home screen feature (standalone)
 * 4. widgetModule   — widget infrastructure (standalone)
 * 5. settingsModule — settings feature (depends on coreModule)
 * 6. drawerModule   — app drawer feature (depends on coreModule)
 */
val allModules = listOf(
    coreModule,
    searchModule,
    homeModule,
    widgetModule,
    settingsModule,
    drawerModule
)
