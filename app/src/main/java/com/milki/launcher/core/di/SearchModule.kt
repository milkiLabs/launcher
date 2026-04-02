/**
 * SearchModule.kt - Search Feature Koin Dependency Injection Module
 *
 * This module provides all dependencies related to the SEARCH feature:
 * - Search-specific repositories (Contacts, Files)
 * - Search providers (ContactsSearchProvider, FilesSearchProvider)
 * - Search provider registry
 * - Search use cases (FilterAppsUseCase, UrlHandlerResolver, ClipboardSuggestionResolver)
 * - SearchViewModel
 *
 * WHY A SEPARATE SEARCH MODULE?
 * The search feature is the most complex feature in the launcher. It has its own
 * repositories (Contacts, Files), its own provider system (SearchProviderRegistry),
 * and multiple use cases. Isolating these dependencies in their own module:
 * 1. Makes it clear which dependencies belong to the search feature.
 * 2. Prevents other features from accidentally depending on search internals.
 * 3. Makes it easier to test the search feature in isolation.
 * 4. Prepares the codebase for potential future modularization (multi-module Gradle project).
 *
 * WHAT BELONGS HERE:
 * - ContactsRepository and FilesRepository — only used by search providers
 * - ContactsSearchProvider and FilesSearchProvider — search-specific data sources
 * - SearchProviderRegistry — coordinates all search providers
 * - FilterAppsUseCase, UrlHandlerResolver, ClipboardSuggestionResolver — search use cases
 * - SearchViewModel — the search feature's presentation layer
 *
 * DEPENDENCIES ON OTHER MODULES:
 * This module depends on coreModule for:
 * - AppRepository (SearchViewModel needs it to search installed apps)
 * - SettingsRepository (SearchViewModel needs it to read prefix configurations)
 *
 * DEPENDENCY DIRECTION:
 *   searchModule → coreModule (allowed)
 *   coreModule → searchModule (NEVER — would create circular dependency)
 *
 * For a full explanation of Koin concepts, see: docs/KoinDependencyInjection.md
 */

package com.milki.launcher.core.di

import com.milki.launcher.data.repository.ContactsRepositoryImpl
import com.milki.launcher.data.repository.FilesRepositoryImpl
import com.milki.launcher.data.search.ContactsSearchProvider
import com.milki.launcher.data.search.FilesSearchProvider
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.FilesRepository
import com.milki.launcher.domain.search.ClipboardSuggestionResolver
import com.milki.launcher.domain.search.FilterAppsUseCase
import com.milki.launcher.domain.search.QuerySuggestionResolver
import com.milki.launcher.domain.search.SearchProviderRegistry
import com.milki.launcher.domain.search.UrlHandlerResolver
import com.milki.launcher.presentation.search.SearchViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Search module — all search feature dependencies.
 *
 * This module is loaded alongside coreModule. It can call get() to retrieve
 * AppRepository and SettingsRepository from coreModule because Koin merges
 * all modules into one global container at startup. The get() calls resolve
 * across module boundaries automatically.
 */
val searchModule = module {

    // ========================================================================
    // SEARCH-SPECIFIC REPOSITORIES - SINGLETONS
    // ========================================================================
    // These repositories are ONLY used within the search feature (by search
    // providers), so they belong here rather than in coreModule.

    /**
     * ContactsRepository - Provides access to contacts and recent contacts.
     *
     * SINGLETON: Yes — we want one recent contacts list across the app.
     *
     * USED BY: ContactsSearchProvider (in this module), SearchViewModel (for recent contacts)
     *
     * DEPENDENCY: Android Context (for ContentResolver and DataStore)
     */
    single<ContactsRepository> {
        ContactsRepositoryImpl(get())
    }

    /**
     * FilesRepository - Provides access to document files on the device.
     *
     * SINGLETON: Yes — we want consistent file search behavior.
     *
     * USED BY: FilesSearchProvider (in this module)
     *
     * DEPENDENCY: Android Context (for MediaStore queries)
     */
    single<FilesRepository> {
        FilesRepositoryImpl(get())
    }

    // ========================================================================
    // SEARCH PROVIDERS - SINGLETONS
    // ========================================================================
    // Search providers translate user queries into search results from specific
    // data sources (contacts, files, etc.). They are singletons because they
    // are stateless — they delegate to their repository for actual data access.

    /**
     * ContactsSearchProvider - Handles "c" prefix searches by querying contacts.
     *
     * SINGLETON: Yes — stateless, delegates to ContactsRepository.
     *
     * DEPENDENCY: ContactsRepository (resolved via get() from this module)
     */
    single {
        // get() resolves ContactsRepository defined above in this same module
        ContactsSearchProvider(get())
    }

    /**
     * FilesSearchProvider - Handles "f" prefix searches by querying device files.
     *
     * SINGLETON: Yes — stateless, delegates to FilesRepository.
     *
     * DEPENDENCY: FilesRepository (resolved via get() from this module)
     */
    single {
        // get() resolves FilesRepository defined above in this same module
        FilesSearchProvider(get())
    }

    // ========================================================================
    // REGISTRY - SINGLETON
    // ========================================================================

    /**
     * SearchProviderRegistry - Central registry that holds all available search providers.
     *
     * When the user types a query with a prefix (e.g., "c john" for contacts),
     * the registry looks up which provider handles that prefix and delegates
     * the search to it.
     *
     * SINGLETON: Yes — we want one registry with all providers registered.
     *
     * DEPENDENCIES: All search providers (resolved via get<Type>())
     */
    single {
        // get<ContactsSearchProvider>() and get<FilesSearchProvider>() resolve
        // the provider singletons defined above. Koin uses the type parameter
        // to find the correct instance in the container.
        SearchProviderRegistry(
            initialProviders = listOf(
                get<ContactsSearchProvider>(),
                get<FilesSearchProvider>()
            )
        )
    }

    // ========================================================================
    // SEARCH USE CASES - SINGLETONS
    // ========================================================================
    // Use cases contain pure business logic. They are singletons because they
    // are stateless — they just transform inputs into outputs.

    /**
     * FilterAppsUseCase - Filters installed apps based on a search query string.
     *
     * Contains the matching algorithm (fuzzy match, prefix match, etc.) that
     * determines which apps match the user's typed query.
     *
     * SINGLETON: Yes — stateless, just business logic.
     *
     * DEPENDENCY: None (pure function-like class)
     */
    single {
        FilterAppsUseCase()
    }

    /**
     * UrlHandlerResolver - Determines which installed apps can handle a given URL.
     *
     * Used to show "Open in Chrome / Open in YouTube" suggestions when the
     * user types or pastes a URL in the search bar.
     *
     * SINGLETON: Yes — uses PackageManager which is always available.
     *
     * DEPENDENCY: Android Context (for PackageManager lookups)
     */
    single {
        UrlHandlerResolver(get())
    }

    /**
     * ClipboardSuggestionResolver - Reads the clipboard and creates a search suggestion.
     *
     * When the user opens the search bar, this checks the clipboard for content
     * (URLs, phone numbers, text) and suggests a relevant action (open link,
     * call number, search text).
     *
     * SINGLETON: Yes — stateless between calls.
     *
     * DEPENDENCIES:
     * - Android Context (to access ClipboardManager system service)
     * - UrlHandlerResolver (to resolve URLs to handler apps)
     */
    single {
        ClipboardSuggestionResolver(
            context = get(),
            urlHandlerResolver = get()
        )
    }

    /**
     * QuerySuggestionResolver - Analyzes the current query and creates a suggestion.
     *
     * When the user types in the search bar, this analyzes the query text
     * and suggests relevant actions (open URL, call number, search web).
     *
     * This is similar to ClipboardSuggestionResolver but analyzes the actively
     * typed query instead of clipboard content.
     *
     * SINGLETON: Yes — stateless between calls.
     *
     * DEPENDENCY: UrlHandlerResolver (to resolve URLs to handler apps)
     */
    single {
        QuerySuggestionResolver(
            urlHandlerResolver = get()
        )
    }

    // ========================================================================
    // VIEWMODEL - MANAGED BY KOIN
    // ========================================================================

    /**
     * SearchViewModel - Manages the entire search UI state and coordinates search execution.
     *
     * This is the most complex ViewModel in the app. It orchestrates:
     * - App search (via AppRepository + FilterAppsUseCase)
     * - Provider-based search (via SearchProviderRegistry for contacts, files, etc.)
     * - URL handling (via UrlHandlerResolver)
     * - Clipboard suggestions (via ClipboardSuggestionResolver)
     * - Query suggestions (via QuerySuggestionResolver)
     * - Recent items (via AppRepository, ContactsRepository)
     * - Settings-driven behavior (via SettingsRepository for prefix configs)
     *
     * LIFECYCLE: Scoped to the Activity/Composable that requests it.
     *            Survives configuration changes (screen rotation).
     *            Cleared when the lifecycle owner is destroyed.
     *
     * DEPENDENCIES (8 total):
     * - AppRepository (from coreModule — installed apps and recent apps)
     * - ContactsRepository (from this module — recent contacts)
     * - SettingsRepository (from coreModule — prefix configurations)
     * - SearchProviderRegistry (from this module — search providers)
     * - FilterAppsUseCase (from this module — app filtering logic)
     * - UrlHandlerResolver (from this module — URL handler resolution)
     * - ClipboardSuggestionResolver (from this module — clipboard suggestions)
     * - QuerySuggestionResolver (from this module — query suggestions)
     */
    viewModel {
        // Each get() call resolves a dependency from Koin's global container.
        // Some come from coreModule (AppRepository, SettingsRepository),
        // and the rest come from this searchModule.
        SearchViewModel(
            appRepository = get(),
            settingsRepository = get(),
            providerRegistry = get(),
            filterAppsUseCase = get(),
            clipboardSuggestionResolver = get(),
            querySuggestionResolver = get()
        )
    }
}
