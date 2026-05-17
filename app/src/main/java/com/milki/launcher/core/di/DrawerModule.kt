package com.milki.launcher.core.di

import com.milki.launcher.presentation.drawer.DrawerListAssembler
import com.milki.launcher.presentation.drawer.AppDrawerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val drawerModule = module {
    single {
        DrawerListAssembler()
    }

    viewModel {
        AppDrawerViewModel(
            appRepository = get(),
            drawerListAssembler = get()
        )
    }
}
