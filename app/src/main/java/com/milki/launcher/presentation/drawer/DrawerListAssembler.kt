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
        if (apps.isEmpty()) {
            return Result(items = emptyList(), sections = emptyList())
        }

        val orderedApps = if (sortSectionsAlphabetically) {
            apps.sortedWith(
                compareBy<AppInfo> { keySortToken(sectionKeyFor(it)) }
                    .thenBy { it.nameLower }
                    .thenBy { it.packageName }
                    .thenBy { it.activityName }
            )
        } else {
            apps
        }

        val items = ArrayList<DrawerAdapterItem>(orderedApps.size + 8)
        val sections = ArrayList<DrawerSection>()

        var currentSectionKey: String? = null
        var currentSectionStartIndex = 0
        var currentSectionCount = 0

        fun flushSection() {
            val key = currentSectionKey ?: return
            sections += DrawerSection(
                key = key,
                title = key,
                startIndex = currentSectionStartIndex,
                count = currentSectionCount
            )
        }

        orderedApps.forEach { app ->
            val sectionKey = sectionKeyFor(app)
            if (sectionKey != currentSectionKey) {
                flushSection()
                currentSectionKey = sectionKey
                currentSectionStartIndex = items.size
                currentSectionCount = 0
                items += DrawerAdapterItem.SectionHeader(
                    sectionKey = sectionKey,
                    title = sectionKey
                )
            }

            items += DrawerAdapterItem.AppEntry(app = app, sectionKey = sectionKey)
            currentSectionCount += 1
        }

        flushSection()
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
