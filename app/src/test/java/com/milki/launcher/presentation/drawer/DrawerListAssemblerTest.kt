package com.milki.launcher.presentation.drawer

import com.milki.launcher.domain.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerListAssemblerTest {

    private val assembler = DrawerListAssembler()

    private val apps = listOf(
        AppInfo(name = "Calendar", packageName = "com.android.calendar"),
        AppInfo(name = "Maps", packageName = "com.google.android.apps.maps"),
        AppInfo(name = "YouTube", packageName = "com.google.android.youtube")
    )

    @Test
    fun assemble_search_filters_by_app_name_case_insensitive() {
        val result = assembler.assembleSearch(apps = apps, query = "mAp")

        val entries = result.filterIsInstance<DrawerAdapterItem.AppEntry>()
        assertEquals(1, entries.size)
        assertEquals("Maps", entries.first().app.name)
    }

    @Test
    fun assemble_search_filters_by_package_name() {
        val result = assembler.assembleSearch(apps = apps, query = "android.apps.maps")

        val entries = result.filterIsInstance<DrawerAdapterItem.AppEntry>()
        assertEquals(1, entries.size)
        assertEquals("Maps", entries.first().app.name)
    }

    @Test
    fun assemble_search_keeps_only_matching_sections() {
        val result = assembler.assembleSearch(apps = apps, query = "ca")

        val sectionHeaders = result.filterIsInstance<DrawerAdapterItem.SectionHeader>()
        val entries = result.filterIsInstance<DrawerAdapterItem.AppEntry>()

        assertEquals(1, sectionHeaders.size)
        assertEquals("C", sectionHeaders.first().title)
        assertEquals(1, entries.size)
        assertEquals("Calendar", entries.first().app.name)
    }

    @Test
    fun assemble_search_prioritizes_starts_with_before_contains() {
        val rankingApps = listOf(
            AppInfo(name = "Axiom Notes", packageName = "com.example.axiom"),
            AppInfo(name = "X Player", packageName = "com.example.xplayer")
        )

        val result = assembler.assembleSearch(apps = rankingApps, query = "x")
        val entries = result.filterIsInstance<DrawerAdapterItem.AppEntry>()

        assertEquals(2, entries.size)
        assertEquals("X Player", entries[0].app.name)
        assertEquals("Axiom Notes", entries[1].app.name)
    }
}
