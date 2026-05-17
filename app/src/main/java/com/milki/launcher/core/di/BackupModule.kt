package com.milki.launcher.core.di

import com.milki.launcher.data.repository.backup.LauncherBackupRepositoryImpl
import com.milki.launcher.domain.repository.LauncherBackupRepository
import org.koin.dsl.module

/**
 * Backup/restore is an app-level workflow: it composes state owned by several
 * features, so it should not live in the foundational core module.
 */
val backupModule = module {
    single<LauncherBackupRepository> {
        LauncherBackupRepositoryImpl(
            appContext = get(),
            settingsRepository = get(),
            homeRepository = get(),
            appRepository = get(),
            widgetHostManager = get(),
            actionShortcutRepository = get()
        )
    }
}
