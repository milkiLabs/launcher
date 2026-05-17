/**
 * HomeModule.kt - Home Screen Feature Koin Dependency Injection Module
 *
 * This module provides all dependencies related to the HOME SCREEN feature:
 * - HomeRepository (manages pinned items, their order, and grid positions)
 * - HomeViewModel (presentation logic for the home screen grid)
 *
 * DEPENDENCY DIRECTION:
 *   HomeViewModel → HomeRepository → Context
 *   (presentation → domain → data)
 */

package com.milki.launcher.core.di

import com.milki.launcher.data.repository.HomeRepositoryImpl
import com.milki.launcher.domain.repository.HomeRepository
import com.milki.launcher.presentation.home.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Home module — home screen feature dependencies.
 *
 * This is one of the simplest feature modules because the home screen
 * has a clean, narrow dependency chain: HomeViewModel → HomeRepository → Context.
 */
val homeModule = module {

    // ========================================================================
    // HOME REPOSITORY - SINGLETON
    // ========================================================================

    /**
     * HomeRepository - Provides access to pinned home screen items.
     *
     * Manages the persistent storage of home screen items (pinned apps,
     * folders, widgets, shortcuts) and their grid positions. Uses DataStore
     * for persistence.
     *
     * SINGLETON: Yes — we want one source of truth for the home screen layout.
     *            Multiple instances would cause data races and inconsistent state.
     *
     * USED BY: HomeViewModel (in this module only)
     *
     * DEPENDENCY: Android Context (for DataStore access)
     */
    single<HomeRepository> {
        HomeRepositoryImpl(get())
    }

    // ========================================================================
    // VIEWMODEL - MANAGED BY KOIN
    // ========================================================================

    /**
     * HomeViewModel - Manages home screen state (pinned items, drag-and-drop, grid layout).
     *
     * Handles all home screen interactions:
     * - Loading and displaying pinned items in a grid
     * - Adding/removing pinned apps, folders, and widgets
     * - Drag-and-drop reordering
     * - Folder management (create, rename, add/remove items)
     *
     * LIFECYCLE: Scoped to the Activity/Composable that requests it.
     *            Survives configuration changes (screen rotation).
     *            Cleared when the lifecycle owner is destroyed.
     *
     * DEPENDENCY: HomeRepository (from this module — pinned items data access)
     */
    viewModel {
        HomeViewModel(
            homeRepository = get(),
            appRepository = get(),
            appContext = get(),
        )
    }
}
