/**
 * HomeModule.kt - Home Screen Feature Koin Dependency Injection Module
 *
 * This module provides all dependencies related to the HOME SCREEN feature:
 * - HomeRepository (manages pinned items, their order, and grid positions)
 * - HomeViewModel (presentation logic for the home screen grid)
 *
 * WHY A SEPARATE HOME MODULE?
 * The home screen is a self-contained feature. Its repository (HomeRepository)
 * is not used by any other feature — only HomeViewModel reads and writes pinned
 * items. Keeping these dependencies together in one module:
 * 1. Makes the home feature's dependency boundary explicit.
 * 2. Allows testing the home feature in isolation (load only homeModule + coreModule).
 * 3. Prevents accidental coupling between home and other features.
 *
 * DEPENDENCIES ON OTHER MODULES:
 * This module has NO dependencies on other feature modules.
 * It does NOT depend on coreModule either — HomeRepository stands alone.
 *
 * DEPENDENCY DIRECTION:
 *   HomeViewModel → HomeRepository → Context
 *   (presentation → domain → data, as per architecture rules)
 *
 * For a full explanation of Koin concepts, see: docs/KoinDependencyInjection.md
 */

package com.milki.launcher.di

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
        )
    }
}
