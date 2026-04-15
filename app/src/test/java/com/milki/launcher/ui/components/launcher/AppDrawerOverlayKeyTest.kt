package com.milki.launcher.ui.components.launcher

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.presentation.drawer.DrawerAdapterItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppDrawerOverlayKeyTest {

    @Test
    fun drawerGridItemKey_repeated_section_headers_produce_unique_keys() {
        val first = drawerGridItemKey(
            index = 0,
            item = DrawerAdapterItem.SectionHeader(sectionKey = "C", title = "C")
        )
        val repeated = drawerGridItemKey(
            index = 3,
            item = DrawerAdapterItem.SectionHeader(sectionKey = "C", title = "C")
        )

        assertNotEquals(first, repeated)
    }

    @Test
    fun drawerGridItemKey_app_entries_use_component_identity() {
        val app = AppInfo(
            name = "Calendar",
            packageName = "com.android.calendar",
            activityName = "com.android.calendar.LaunchActivity"
        )

        val key = drawerGridItemKey(
            index = 99,
            item = DrawerAdapterItem.AppEntry(app = app, sectionKey = "C")
        )

        assertEquals("app:com.android.calendar/com.android.calendar.LaunchActivity", key)
    }
}
