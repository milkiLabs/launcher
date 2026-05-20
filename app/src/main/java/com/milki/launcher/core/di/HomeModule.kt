package com.milki.launcher.core.di

import android.content.Context
import com.milki.launcher.data.repository.home.HomeRepositoryImpl
import com.milki.launcher.domain.repository.HomeRepository
import com.milki.launcher.presentation.home.HomeIconWarmupCoordinator
import com.milki.launcher.presentation.home.HomeViewModel
import com.milki.launcher.presentation.home.prune.HomeAvailabilityPruner
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val homeModule = module {
    single<HomeRepository> {
        HomeRepositoryImpl(get())
    }

    factory {
        HomeAvailabilityPruner(
            appRepository = get(),
            contentResolver = get<Context>().contentResolver
        )
    }

    factory {
        HomeIconWarmupCoordinator(
            homeRepository = get(),
            appContext = get<Context>()
        )
    }

    viewModel {
        HomeViewModel(
            homeRepository = get(),
            availabilityPruner = get(),
            iconWarmupCoordinator = get(),
            widgetHostManager = get()
        )
    }
}
