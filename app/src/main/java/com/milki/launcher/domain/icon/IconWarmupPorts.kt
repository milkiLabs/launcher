package com.milki.launcher.domain.icon

import com.milki.launcher.domain.model.HomeItem

/**
 * Ports used by home icon warmup so the coordinator stays decoupled from
 * concrete cache singletons.
 */
interface IconPriorityStore {

    /**
     * Updates which package names are treated as home-priority cache entries.
     */
    fun updateHomePriorityPackages(packageNames: Set<String>)
}

interface IconPreloader {

    /** Preloads missing app icons for the given packages. */
    fun preloadMissingAppIcons(packageNames: Set<String>)

    /** Preloads missing icons for the given shortcuts. */
    fun preloadMissingShortcutIcons(shortcuts: List<HomeItem.AppShortcut>)
}
