package com.milki.launcher.microbenchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.search.AppQueryRanker
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchRankingBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val apps: List<AppInfo> = SyntheticApps.installed(APP_COUNT)
    private val recentApps: List<AppInfo> = apps.take(RECENT_COUNT)

    @Test
    fun rankPrefixMatch() {
        benchmarkRule.measureRepeated {
            AppQueryRanker.rank(apps, "cal", includePackageNameMatches = true)
        }
    }

    @Test
    fun rankContainsMatch() {
        benchmarkRule.measureRepeated {
            AppQueryRanker.rank(apps, "tube", includePackageNameMatches = true)
        }
    }

    @Test
    fun rankAcronymMatch() {
        benchmarkRule.measureRepeated {
            AppQueryRanker.rank(apps, "gm", includePackageNameMatches = true)
        }
    }

    @Test
    fun rankMultiTokenMatch() {
        benchmarkRule.measureRepeated {
            AppQueryRanker.rank(apps, "nova music", includePackageNameMatches = true)
        }
    }

    @Test
    fun rankTypoMatch() {
        benchmarkRule.measureRepeated {
            AppQueryRanker.rank(apps, "whatsap", includePackageNameMatches = false)
        }
    }

    @Test
    fun rankSubsequenceWorstCase() {
        benchmarkRule.measureRepeated {
            AppQueryRanker.rank(apps, "xyzzy", includePackageNameMatches = false)
        }
    }

    @Test
    fun rankNoMatchTypoWorstCase() {
        benchmarkRule.measureRepeated {
            AppQueryRanker.rank(apps, "qqqqqq", includePackageNameMatches = false)
        }
    }

    @Test
    fun rankSingleCharWithRecents() {
        benchmarkRule.measureRepeated {
            AppQueryRanker.rank(apps, "a", includePackageNameMatches = true, recentApps = recentApps)
        }
    }

    private companion object {
        const val APP_COUNT = 500
        const val RECENT_COUNT = 10
    }
}
