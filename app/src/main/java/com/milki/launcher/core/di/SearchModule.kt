package com.milki.launcher.core.di

import com.milki.launcher.data.repository.contacts.ContactsRepositoryImpl
import com.milki.launcher.data.repository.files.FilesRepositoryImpl
import com.milki.launcher.data.search.ConfigurableUrlSearchProvider
import com.milki.launcher.data.search.ContactsSearchProvider
import com.milki.launcher.data.search.FilesSearchProvider
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.FilesRepository
import com.milki.launcher.domain.search.SuggestionResolver
import com.milki.launcher.domain.search.FilterAppsUseCase
import com.milki.launcher.domain.search.SearchProviderFactory
import com.milki.launcher.domain.search.SearchProviderRegistry
import com.milki.launcher.presentation.search.SearchViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val searchModule = module {
    single<ContactsRepository> {
        ContactsRepositoryImpl(get())
    }

    single<FilesRepository> {
        FilesRepositoryImpl(get())
    }

    single {
        ContactsSearchProvider(get())
    }

    single {
        FilesSearchProvider(get())
    }

    single {
        SearchProviderRegistry(
            initialProviders = listOf(
                get<ContactsSearchProvider>(),
                get<FilesSearchProvider>()
            )
        )
    }

    single<SearchProviderFactory> {
        SearchProviderFactory { source -> ConfigurableUrlSearchProvider(source) }
    }

    single {
        FilterAppsUseCase()
    }

    single {
        SuggestionResolver(
            context = get(),
            urlHandlerResolver = get()
        )
    }

    viewModel {
        SearchViewModel(
            appRepository = get(),
            settingsRepository = get(),
            providerRegistry = get(),
            searchProviderFactory = get(),
            filterAppsUseCase = get(),
            suggestionResolver = get()
        )
    }
}
