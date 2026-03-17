package com.milki.launcher.presentation.drawer

import com.milki.launcher.domain.model.AppInfo
import java.util.Locale

class DrawerListAssembler {

    data class Result(
        val items: List<DrawerAdapterItem>,
        val sections: List<DrawerSection>
    )

    fun assembleNormal(apps: List<AppInfo>): Result {
        return assembleFromApps(apps)
    }

    fun assembleSearch(apps: List<AppInfo>, query: String): Result {
        val queryLower = query.trim().lowercase(Locale.getDefault())
        if (queryLower.isEmpty()) return assembleNormal(apps)

        val filtered = apps.filter { app ->
            app.nameLower.contains(queryLower) || app.packageLower.contains(queryLower)
        }
        return assembleFromApps(filtered)
    }

    private fun assembleFromApps(apps: List<AppInfo>): Result {
        val grouped = apps.groupBy { sectionKeyFor(it) }
            .toSortedMap(compareBy<String> { keySortToken(it) }.thenBy { it })

        val items = mutableListOf<DrawerAdapterItem>()
        val sections = mutableListOf<DrawerSection>()

        grouped.forEach { (key, sectionApps) ->
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
