package com.milki.launcher.core.di

import com.milki.launcher.data.clipboard.AndroidClipboardReader
import com.milki.launcher.data.repository.contacts.ContactsRepositoryImpl
import com.milki.launcher.data.repository.files.FilesRepositoryImpl
import com.milki.launcher.data.search.ConfigurableUrlSearchProvider
import com.milki.launcher.data.search.ContactsSearchProvider
import com.milki.launcher.data.search.FilesSearchProvider
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.FilesRepository
import com.milki.launcher.domain.search.SuggestionResolver
import com.milki.launcher.domain.search.ClipboardReader
import com.milki.launcher.domain.search.SearchProviderFactory
import com.milki.launcher.domain.search.SearchProviderRegistry
import com.milki.launcher.domain.search.UrlHandlerPort
import com.milki.launcher.presentation.search.SearchViewModel
import kotlinx.coroutines.flow.Flow
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

    single<ClipboardReader> {
        AndroidClipboardReader(get())
    }

    single {
        SuggestionResolver(
            clipboardReader = get(),
            urlHandlerPort = get<UrlHandlerPort>()
        )
    }

    viewModel { (isSearchVisible: Flow<Boolean>) ->
        SearchViewModel(
            appRepository = get(),
            settingsRepository = get(),
            providerRegistry = get(),
            searchProviderFactory = get(),
            suggestionResolver = get(),
            isSearchVisible = isSearchVisible
        )
    }
}
