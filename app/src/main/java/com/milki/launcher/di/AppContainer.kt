/**
 * AppContainer.kt - Manual Dependency Injection Container
 *
 * This file implements the Service Locator pattern for dependency injection.
 * It creates and provides all the dependencies needed by the app.
 *
 * WHY MANUAL DI INSTEAD OF HILT/DAGGER?
 * - Simpler to understand for beginners
 * - No annotation processing (faster builds)
 * - Easy to see all dependencies in one place
 * - Perfect for educational purposes
 *
 * ARCHITECTURE:
 * ┌─────────────────────────────────────────────────────────────┐
 * │                     AppContainer                            │
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
 * │  │              SearchViewModelFactory                  │   │
 * │  └─────────────────────────────────────────────────────┘   │
 * └─────────────────────────────────────────────────────────────┘
 *
 * LIFECYCLE:
 * - Created once in LauncherApplication
 * - Lives for the entire app session
 * - Provides singletons for repositories and use cases
 */

package com.milki.launcher.di

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.milki.launcher.data.repository.AppRepositoryImpl
import com.milki.launcher.data.repository.ContactsRepositoryImpl
import com.milki.launcher.data.repository.FilesRepositoryImpl
import com.milki.launcher.data.search.ContactsSearchProvider
import com.milki.launcher.data.search.FilesSearchProvider
import com.milki.launcher.data.search.WebSearchProvider
import com.milki.launcher.data.search.YouTubeSearchProvider
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.FilesRepository
import com.milki.launcher.domain.search.FilterAppsUseCase
import com.milki.launcher.domain.search.SearchProviderRegistry
import com.milki.launcher.presentation.search.SearchViewModel

/**
 * Application-wide dependency container.
 *
 * This container holds all the app's dependencies and provides
 * factory methods for creating ViewModels.
 *
 * USAGE:
 * ```kotlin
 * // In LauncherApplication
 * val container = AppContainer(this)
 *
 * // In Activity
 * val viewModel = container.searchViewModelFactory.create(SearchViewModel::class.java)
 * ```
 *
 * @property application The Application instance for context access
 */
class AppContainer(private val application: Application) {

    // ========================================================================
    // REPOSITORIES
    // ========================================================================

    /**
     * Repository for app data (installed apps, recent apps).
     * Singleton - created lazily on first access.
     */
    private val appRepository: AppRepository by lazy {
        AppRepositoryImpl(application)
    }

    /**
     * Repository for contacts data.
     * Singleton - created lazily on first access.
     */
    private val contactsRepository: ContactsRepository by lazy {
        ContactsRepositoryImpl(application)
    }

    /**
     * Repository for files/documents data.
     * Singleton - created lazily on first access.
     */
    private val filesRepository: FilesRepository by lazy {
        FilesRepositoryImpl(application)
    }

    // ========================================================================
    // SEARCH PROVIDERS
    // ========================================================================

    /**
     * Web search provider (prefix "s").
     * Singleton - created lazily on first access.
     */
    private val webSearchProvider: WebSearchProvider by lazy {
        WebSearchProvider()
    }

    /**
     * YouTube search provider (prefix "y").
     * Singleton - created lazily on first access.
     */
    private val youTubeSearchProvider: YouTubeSearchProvider by lazy {
        YouTubeSearchProvider()
    }

    /**
     * Contacts search provider (prefix "c").
     * Depends on contactsRepository.
     * Singleton - created lazily on first access.
     */
    private val contactsSearchProvider: ContactsSearchProvider by lazy {
        ContactsSearchProvider(contactsRepository)
    }

    /**
     * Files search provider (prefix "f").
     * Depends on filesRepository.
     * Singleton - created lazily on first access.
     */
    private val filesSearchProvider: FilesSearchProvider by lazy {
        FilesSearchProvider(filesRepository)
    }

    // ========================================================================
    // REGISTRY
    // ========================================================================

    /**
     * Registry for all search providers.
     * Singleton - created lazily on first access.
     */
    private val searchProviderRegistry: SearchProviderRegistry by lazy {
        SearchProviderRegistry(
            initialProviders = listOf(
                webSearchProvider,
                contactsSearchProvider,
                filesSearchProvider,
                youTubeSearchProvider
            )
        )
    }

    // ========================================================================
    // USE CASES
    // ========================================================================

    /**
     * Use case for filtering apps.
     * Singleton - created lazily on first access.
     */
    private val filterAppsUseCase: FilterAppsUseCase by lazy {
        FilterAppsUseCase()
    }

    // ========================================================================
    // VIEWMODEL FACTORY
    // ========================================================================

    /**
     * Factory for creating SearchViewModel instances.
     *
     * This factory provides all dependencies to the ViewModel,
     * following the Factory pattern for object creation.
     */
    val searchViewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
                return SearchViewModel(
                    appRepository = appRepository,
                    providerRegistry = searchProviderRegistry,
                    filterAppsUseCase = filterAppsUseCase
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    // ========================================================================
    // PUBLIC ACCESSORS
    // ========================================================================

    /**
     * Provide the contacts repository for permission checking.
     * Used by Activity to check permission status.
     */
    fun provideContactsRepository(): ContactsRepository = contactsRepository

    /**
     * Provide the files repository for permission checking.
     * Used by Activity to check permission status.
     */
    fun provideFilesRepository(): FilesRepository = filesRepository

    /**
     * Provide the app repository for saving recent apps.
     */
    fun provideAppRepository(): AppRepository = appRepository
}
