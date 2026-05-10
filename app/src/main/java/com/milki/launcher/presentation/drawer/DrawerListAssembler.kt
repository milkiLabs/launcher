package com.milki.launcher.presentation.drawer

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.search.AppQueryRanker

class DrawerListAssembler {
    fun assembleNormal(apps: List<AppInfo>): List<DrawerAdapterItem> {
        return assembleItems(apps = apps, preserveInputOrder = false)
    }

    fun selectRecentlyUpdatedOrInstalled(apps: List<AppInfo>, limit: Int): List<AppInfo> {
        if (limit <= 0 || apps.isEmpty()) return emptyList()

        return apps.asSequence()
            .sortedWith(
                compareByDescending<AppInfo> { it.installedOrUpdatedAtMillis }
                    .thenBy { it.nameLower }
                    .thenBy { it.packageName }
                    .thenBy { it.activityName }
            )
            .distinctBy { it.packageName }
            .take(limit)
            .toList()
    }

    fun assembleSearch(apps: List<AppInfo>, query: String): List<DrawerAdapterItem> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return assembleNormal(apps)

        val ranked = AppQueryRanker.rank(
            apps = apps,
            query = normalizedQuery,
            includePackageNameMatches = true
        )
        return assembleItems(apps = ranked, preserveInputOrder = true)
    }

    private fun assembleItems(
        apps: List<AppInfo>,
        preserveInputOrder: Boolean
    ): List<DrawerAdapterItem> {
        if (apps.isEmpty()) return emptyList()

        val orderedApps = if (preserveInputOrder) {
            apps
        } else {
            apps.sortedWith(
                compareBy<AppInfo> { keySortToken(sectionKeyFor(it)) }
                    .thenBy { it.nameLower }
                    .thenBy { it.packageName }
                    .thenBy { it.activityName }
            )
        }

        return buildList(orderedApps.size + 8) {
            var currentSectionKey: String? = null

            orderedApps.forEach { app ->
                val sectionKey = sectionKeyFor(app)
                if (sectionKey != currentSectionKey) {
                    currentSectionKey = sectionKey
                    add(DrawerAdapterItem.SectionHeader(title = sectionKey))
                }

                add(DrawerAdapterItem.AppEntry(app = app))
            }
        }
    }

    private fun sectionKeyFor(app: AppInfo): String {
        val first = app.name.trim().firstOrNull()?.uppercaseChar() ?: '#'
        return if (first.isLetterOrDigit()) first.toString() else "#"
    }

    private fun keySortToken(key: String): String {
        return if (key == "#") "{" else key
    }
}
