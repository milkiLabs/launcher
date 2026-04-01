package com.milki.launcher.presentation.drawer

import com.milki.launcher.domain.model.AppInfo

class DrawerListAssembler {

    data class Result(
        val items: List<DrawerAdapterItem>,
        val sections: List<DrawerSection>
    )

    fun assembleNormal(apps: List<AppInfo>): Result {
        return assembleFromApps(apps = apps, sortSectionsAlphabetically = true)
    }

    fun assembleSearch(apps: List<AppInfo>, query: String): Result {
        val queryLower = query.trim().lowercase()
        if (queryLower.isEmpty()) return assembleNormal(apps)

        val ranked = rankAppsForSearch(apps = apps, queryLower = queryLower)
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

    private fun rankAppsForSearch(apps: List<AppInfo>, queryLower: String): List<AppInfo> {
        val exactNameMatches = ArrayList<AppInfo>()
        val startsWithMatches = ArrayList<AppInfo>()
        val wordBoundaryMatches = ArrayList<AppInfo>()
        val containsMatches = ArrayList<AppInfo>()
        val fuzzyMatches = ArrayList<AppInfo>()
        val packageMatches = ArrayList<AppInfo>()

        apps.forEach { app ->
            val name = app.nameLower
            when {
                name == queryLower -> exactNameMatches.add(app)
                name.startsWith(queryLower) -> startsWithMatches.add(app)
                name.contains(" $queryLower") -> wordBoundaryMatches.add(app)
                name.contains(queryLower) -> containsMatches.add(app)
                isSubsequenceMatch(query = queryLower, text = name) -> fuzzyMatches.add(app)
                app.packageLower.contains(queryLower) -> packageMatches.add(app)
            }
        }

        return buildList(
            capacity = exactNameMatches.size +
                startsWithMatches.size +
                wordBoundaryMatches.size +
                containsMatches.size +
                fuzzyMatches.size +
                packageMatches.size
        ) {
            addAll(exactNameMatches)
            addAll(startsWithMatches)
            addAll(wordBoundaryMatches)
            addAll(containsMatches)
            addAll(fuzzyMatches)
            addAll(packageMatches)
        }
    }

    private fun isSubsequenceMatch(query: String, text: String): Boolean {
        if (query.length > text.length) return false

        var queryIndex = 0
        var textIndex = 0

        while (queryIndex < query.length && textIndex < text.length) {
            if (query[queryIndex] == text[textIndex]) {
                queryIndex++
            }
            textIndex++
        }

        return queryIndex == query.length
    }
}
