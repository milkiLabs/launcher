package com.milki.launcher.presentation.drawer

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.search.AppQueryRanker

class DrawerListAssembler {

    data class Result(
        val items: List<DrawerAdapterItem>,
        val sections: List<DrawerSection>
    )

    fun assembleNormal(apps: List<AppInfo>): Result {
        return assembleFromApps(apps = apps, sortSectionsAlphabetically = true)
    }

    fun assembleSearch(apps: List<AppInfo>, query: String): Result {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return assembleNormal(apps)

        val ranked = AppQueryRanker.rank(
            apps = apps,
            query = normalizedQuery,
            includePackageNameMatches = true
        )
        return assembleFromApps(apps = ranked, sortSectionsAlphabetically = false)
    }

    private fun assembleFromApps(
        apps: List<AppInfo>,
        sortSectionsAlphabetically: Boolean
    ): Result {
        val grouped = apps.groupBy { sectionKeyFor(it) }
        val groupedEntries = if (sortSectionsAlphabetically) {
            grouped.entries.sortedBy { keySortToken(it.key) }
        } else {
            grouped.entries.toList()
        }

        val items = mutableListOf<DrawerAdapterItem>()
        val sections = mutableListOf<DrawerSection>()

        groupedEntries.forEach { (key, sectionApps) ->
            val startIndex = items.size
            items += DrawerAdapterItem.SectionHeader(sectionKey = key, title = key)
            sectionApps.forEach { app ->
                items += DrawerAdapterItem.AppEntry(app = app, sectionKey = key)
            }
            sections += DrawerSection(
                key = key,
                title = key,
                startIndex = startIndex,
                count = sectionApps.size
            )
        }

        return Result(items = items, sections = sections)
    }

    private fun sectionKeyFor(app: AppInfo): String {
        val first = app.name.trim().firstOrNull()?.uppercaseChar() ?: '#'
        return if (first.isLetterOrDigit()) first.toString() else "#"
    }

    private fun keySortToken(key: String): String {
        return if (key == "#") "{" else key
    }

}
