/**
 * CoreModule.kt - Core Koin Dependency Injection Module
 *
 * Provides shared, cross-feature dependencies used by multiple features.
 *
 * WHAT BELONGS HERE:
 * - Repositories used by 2+ features
 * - Cross-cutting services and utilities
 * - Shared domain interfaces and their implementations
 *
 * DEPENDENCY DIRECTION RULE:
 * Feature modules → coreModule (never the reverse)
 */

package com.milki.launcher.core.di

import com.milki.launcher.data.repository.ActionShortcutRepositoryImpl
import com.milki.launcher.data.repository.AppRepositoryImpl
import com.milki.launcher.data.repository.apps.PackageChangeMonitor
import com.milki.launcher.data.repository.settings.SettingsRepositoryImpl
import com.milki.launcher.domain.repository.ActionShortcutRepository
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.HiddenAppsRepository
import com.milki.launcher.domain.repository.HomeTriggerRepository
import com.milki.launcher.domain.repository.PrefixConfigurationRepository
import com.milki.launcher.domain.repository.SearchSourceRepository
import com.milki.launcher.domain.repository.SettingsReader
import com.milki.launcher.domain.search.UrlHandlerResolver
import org.koin.dsl.module

/**
 * Core module — shared, cross-feature dependencies.
 */
val coreModule = module {

    // ========================================================================
    // SHARED REPOSITORIES
    // ========================================================================

    single {
        PackageChangeMonitor(get())
    }

    single<AppRepository> {
        AppRepositoryImpl(
            application = get(),
            packageChangeMonitor = get()
        )
    }

    /**
     * Settings implementation — provided under all focused interfaces.
     * Single instance shared across the app.
     */
    single<SettingsReader> {
        SettingsRepositoryImpl(get())
    }
    single<SearchSourceRepository> { get<SettingsReader>() as SettingsRepositoryImpl }
    single<PrefixConfigurationRepository> { get<SettingsReader>() as SettingsRepositoryImpl }
    single<HomeTriggerRepository> { get<SettingsReader>() as SettingsRepositoryImpl }
    single<HiddenAppsRepository> { get<SettingsReader>() as SettingsRepositoryImpl }

    single<ActionShortcutRepository> {
        ActionShortcutRepositoryImpl(get())
    }

    single {
        UrlHandlerResolver(
            context = get(),
            packageChangeMonitor = get()
        )
    }
}
