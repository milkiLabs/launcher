/**
 * AppModule.kt - Koin Dependency Injection Module
 *
 * This file defines the Koin module that provides all dependencies for the app.
 * It replaces the manual AppContainer approach with Koin's DSL for dependency injection.
 *
 * WHY KOIN?
 * - Declarative dependency definitions using DSL
 * - Automatic lifecycle management (singletons, factories)
 * - Easy testing with module overrides
 * - No annotation processing (faster builds than Hilt/Dagger)
 * - Simple and readable code
 *
 * MODULE STRUCTURE:
 * ┌─────────────────────────────────────────────────────────────┐
 * │                     AppModule                               │
 * │                                                             │
 * │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
 * │  │ Repositories    │  │ SearchProviders │  │ UseCases    │ │
 * │  │ - AppRepository │  │ - WebProvider   │  │ - FilterApps│ │
 * │  │ - ContactsRepo  │  │ - YouTube       │  │             │ │
 * │  │                 │  │ - Contacts      │  │             │ │
 * │  └─────────────────┘  └─────────────────┘  └─────────────┘ │
 * │                                                             │
 * │  ┌─────────────────────────────────────────────────────┐   │
 * │  │              SearchProviderRegistry                  │   │
 * │  └─────────────────────────────────────────────────────┘   │
 * │                                                             │
 * │  ┌─────────────────────────────────────────────────────┐   │
 * │  │              ViewModels (viewModel)                  │   │
 * │  └─────────────────────────────────────────────────────┘   │
 * └─────────────────────────────────────────────────────────────┘
 *
 * SINGLETON vs FACTORY:
 * - single { }: Creates ONE instance for the entire app (singleton pattern)
 * - factory { }: Creates a NEW instance every time it's requested
 * - viewModel { }: Creates a ViewModel scoped to the lifecycle (Koin manages this)
 *
 * DEPENDENCY RESOLUTION:
 * When a dependency needs another dependency, we use get() to retrieve it:
 *   single { ContactsSearchProvider(get()) }  // get() returns ContactsRepository
 *
 * Koin automatically resolves the dependency graph and provides instances
 * in the correct order based on the dependency chain.
 */

package com.milki.launcher.di

import com.milki.launcher.data.repository.AppRepositoryImpl
import com.milki.launcher.data.repository.ContactsRepositoryImpl
import com.milki.launcher.data.repository.FilesRepositoryImpl
import com.milki.launcher.data.repository.SettingsRepositoryImpl
import com.milki.launcher.data.search.ContactsSearchProvider
import com.milki.launcher.data.search.FilesSearchProvider
import com.milki.launcher.data.search.WebSearchProvider
import com.milki.launcher.data.search.YouTubeSearchProvider
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.FilesRepository
import com.milki.launcher.domain.repository.SettingsRepository
import com.milki.launcher.domain.search.FilterAppsUseCase
import com.milki.launcher.domain.search.SearchProviderRegistry
import com.milki.launcher.domain.search.UrlHandlerResolver
import com.milki.launcher.presentation.search.SearchViewModel
import com.milki.launcher.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The main Koin module for the application.
 *
 * This module defines all dependencies needed by the app:
 * - Repositories (data layer)
 * - Search providers (data layer)
 * - Use cases (domain layer)
 * - ViewModels (presentation layer)
 *
 * USAGE:
 * ```kotlin
 * // In LauncherApplication
 * startKoin {
 *     androidContext(this@LauncherApplication)
 *     modules(appModule)
 * }
 *
 * // In Activity or Composable
 * val viewModel: SearchViewModel by viewModel()
 * val repository: AppRepository by inject()
 * ```
 */
val appModule = module {

    // ========================================================================
    // REPOSITORIES - SINGLETONS
    // ========================================================================
    // Repositories are singletons because they hold data and state
    // that should persist for the entire app session.
    // We want the same instance everywhere in the app.

    /**
     * AppRepository - Provides access to installed apps and recent apps.
     *
     * SINGLETON: Yes - we want one cache of installed apps and one recent apps list.
     *
     * DEPENDENCY: Android Context (provided by Koin's androidContext())
     */
    single<AppRepository> {
        // AppRepositoryImpl needs the Application context
        // get() retrieves the Context that was set in startKoin { androidContext() }
        AppRepositoryImpl(get())
    }

    /**
     * ContactsRepository - Provides access to contacts and recent contacts.
     *
     * SINGLETON: Yes - we want one recent contacts list across the app.
     *
     * DEPENDENCY: Android Context (for ContentResolver and DataStore)
     */
    single<ContactsRepository> {
        ContactsRepositoryImpl(get())
    }

    /**
     * FilesRepository - Provides access to document files.
     *
     * SINGLETON: Yes - we want consistent file search behavior.
     *
     * DEPENDENCY: Android Context (for MediaStore)
     */
    single<FilesRepository> {
        FilesRepositoryImpl(get())
    }

    /**
     * SettingsRepository - Provides access to launcher settings.
     *
     * SINGLETON: Yes - we want one source of truth for settings.
     *
     * DEPENDENCY: Android Context (for DataStore)
     */
    single<SettingsRepository> {
        SettingsRepositoryImpl(get())
    }

    // ========================================================================
    // SEARCH PROVIDERS - SINGLETONS
    // ========================================================================
    // Search providers are singletons because they don't hold state
    // that needs to be different per request.

    /**
     * WebSearchProvider - Handles "s" prefix searches.
     *
     * SINGLETON: Yes - no state needed, just URL generation.
     *
     * DEPENDENCY: None
     */
    single {
        WebSearchProvider()
    }

    /**
     * YouTubeSearchProvider - Handles "y" prefix searches.
     *
     * SINGLETON: Yes - no state needed, just URL generation.
     *
     * DEPENDENCY: None
     */
    single {
        YouTubeSearchProvider()
    }

    /**
     * ContactsSearchProvider - Handles "c" prefix searches.
     *
     * SINGLETON: Yes - uses ContactsRepository which is already a singleton.
     *
     * DEPENDENCY: ContactsRepository (resolved via get())
     */
    single {
        // get() retrieves the ContactsRepository singleton defined above
        ContactsSearchProvider(get())
    }

    /**
     * FilesSearchProvider - Handles "f" prefix searches.
     *
     * SINGLETON: Yes - uses FilesRepository which is already a singleton.
     *
     * DEPENDENCY: FilesRepository (resolved via get())
     */
    single {
        // get() retrieves the FilesRepository singleton defined above
        FilesSearchProvider(get())
    }

    // ========================================================================
    // REGISTRY - SINGLETON
    // ========================================================================

    /**
     * SearchProviderRegistry - Registry of all search providers.
     *
     * SINGLETON: Yes - we want one registry with all providers.
     *
     * DEPENDENCY: All search providers (resolved via get())
     */
    single {
        // get() retrieves each search provider singleton defined above
        // Koin automatically provides the correct type for each get() call
        SearchProviderRegistry(
            initialProviders = listOf(
                get<WebSearchProvider>(),
                get<ContactsSearchProvider>(),
                get<FilesSearchProvider>(),
                get<YouTubeSearchProvider>()
            )
        )
    }

    // ========================================================================
    // USE CASES - SINGLETONS
    // ========================================================================
    // Use cases are singletons because they're stateless - they just
    // contain business logic that operates on their inputs.

    /**
     * FilterAppsUseCase - Filters apps based on query.
     *
     * SINGLETON: Yes - stateless, just business logic.
     *
     * DEPENDENCY: None
     */
    single {
        FilterAppsUseCase()
    }

    /**
     * UrlHandlerResolver - Determines which apps can handle URLs.
     *
     * SINGLETON: Yes - uses PackageManager which is always available.
     *
     * DEPENDENCY: Android Context (for PackageManager)
     */
    single {
        UrlHandlerResolver(get())
    }

    // ========================================================================
    // VIEWMODELS - MANAGED BY KOIN
    // ========================================================================
    // ViewModels are declared using viewModel { } which:
    // 1. Creates a new instance when first requested by a lifecycle owner
    // 2. Survives configuration changes (screen rotation)
    // 3. Is cleared when the lifecycle owner is destroyed
    // 4. Can be injected in Activities with by viewModel() or in Compose with koinViewModel()

    /**
     * SearchViewModel - Manages search state and coordinates search providers.
     *
     * LIFECYCLE: Scoped to the Activity/Composable that requests it.
     *
     * DEPENDENCIES:
     * - AppRepository (for installed apps and recent apps)
     * - ContactsRepository (for recent contacts)
     * - SearchProviderRegistry (for search providers)
     * - FilterAppsUseCase (for filtering apps)
     * - UrlHandlerResolver (for URL handling)
     */
    viewModel {
        // get() retrieves each dependency from Koin's container
        // Koin automatically provides the correct type based on the parameter
        SearchViewModel(
            appRepository = get(),
            contactsRepository = get(),
            providerRegistry = get(),
            filterAppsUseCase = get(),
            urlHandlerResolver = get()
        )
    }

    /**
     * SettingsViewModel - Manages settings state.
     *
     * LIFECYCLE: Scoped to the Activity/Composable that requests it.
     *
     * DEPENDENCIES:
     * - SettingsRepository (for reading/writing settings)
     */
    viewModel {
        SettingsViewModel(
            settingsRepository = get()
        )
    }
}
