package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AppQueryRankerTest {

    @Test
    fun rank_boosts_recent_apps_when_match_quality_is_close() {
        val maps = app("Maps", "com.google.android.apps.maps")
        val mail = app("Mail", "com.example.mail")
        val music = app("Music", "com.example.music")

        val result = AppQueryRanker.rank(
            apps = listOf(maps, mail, music),
            query = "m",
            includePackageNameMatches = false,
            recentApps = listOf(music, mail)
        )

        assertEquals(listOf("Music", "Mail", "Maps"), result.map { it.name })
    }

    @Test
    fun rank_keeps_stronger_text_match_above_recent_weaker_match() {
        val maps = app("Maps", "com.google.android.apps.maps")
        val messaging = app("Messaging", "com.example.messaging")

        val result = AppQueryRanker.rank(
            apps = listOf(maps, messaging),
            query = "maps",
            includePackageNameMatches = false,
            recentApps = listOf(messaging)
        )

        assertEquals(listOf("Maps"), result.map { it.name })
    }

    @Test
    fun rank_matches_app_name_acronyms() {
        val playStore = app("Google Play Store", "com.android.vending")
        val photos = app("Google Photos", "com.google.android.apps.photos")

        val result = AppQueryRanker.rank(
            apps = listOf(photos, playStore),
            query = "gps",
            includePackageNameMatches = false
        )

        assertEquals(listOf("Google Play Store", "Google Photos"), result.map { it.name })
    }

    @Test
    fun rank_tolerates_small_typos_for_app_names() {
        val calendar = app("Calendar", "com.android.calendar")
        val camera = app("Camera", "com.android.camera")

        val result = AppQueryRanker.rank(
            apps = listOf(camera, calendar),
            query = "calender",
            includePackageNameMatches = false
        )

        assertEquals(listOf("Calendar"), result.map { it.name })
    }

    private fun app(
        name: String,
        packageName: String,
        activityName: String = "$packageName.Main"
    ): AppInfo {
        return AppInfo(
            name = name,
            packageName = packageName,
            activityName = activityName
        )
    }
}
