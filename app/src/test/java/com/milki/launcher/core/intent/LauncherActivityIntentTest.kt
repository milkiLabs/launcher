package com.milki.launcher.core.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherActivityIntentTest {

    @Test
    fun parsesHomeBenchmarkRequest() {
        val request = parseLauncherBenchmarkRequest(
            targetName = LauncherBenchmarkTarget.HOME.name,
            seedHome = false
        )

        assertEquals(
            LauncherBenchmarkRequest(target = LauncherBenchmarkTarget.HOME, seedHome = false),
            request
        )
    }

    @Test
    fun parsesDrawerBenchmarkRequestWithSeeding() {
        val request = parseLauncherBenchmarkRequest(
            targetName = LauncherBenchmarkTarget.DRAWER.name,
            seedHome = true
        )

        assertEquals(
            LauncherBenchmarkRequest(target = LauncherBenchmarkTarget.DRAWER, seedHome = true),
            request
        )
    }

    @Test
    fun returnsNullForUnknownBenchmarkTarget() {
        val request = parseLauncherBenchmarkRequest(
            targetName = "SEARCH",
            seedHome = false
        )

        assertNull(request)
    }

    @Test
    fun preservesSeedHomeFlag() {
        val request = parseLauncherBenchmarkRequest(
            targetName = LauncherBenchmarkTarget.HOME.name,
            seedHome = false
        )

        assertFalse(request!!.seedHome)
    }
}
