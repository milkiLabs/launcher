package com.milki.launcher.microbenchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.milki.launcher.domain.search.parseSearchQuery
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchQueryParsingBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val registry = SyntheticProviders.registry(PROVIDER_COUNT)

    @Test
    fun parseProviderHit() {
        benchmarkRule.measureRepeated {
            parseSearchQuery("p7 nova music", registry)
        }
    }

    @Test
    fun parseNoPrefixMatch() {
        benchmarkRule.measureRepeated {
            parseSearchQuery("calculator", registry)
        }
    }

    @Test
    fun parsePartialPrefix() {
        benchmarkRule.measureRepeated {
            parseSearchQuery("p", registry)
        }
    }

    private companion object {
        const val PROVIDER_COUNT = 15
    }
}
