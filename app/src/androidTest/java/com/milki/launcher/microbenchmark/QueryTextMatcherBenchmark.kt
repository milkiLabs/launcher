package com.milki.launcher.microbenchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.milki.launcher.domain.search.QueryTextMatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueryTextMatcherBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val mixedCaseQuery = "  HeLLo   Wörld!  "
    private val longLabel = "Google Play Movies & TV Beta Edition"
    private val shortPair = Pair("calc", "calr")
    private val longPair = Pair("extraordinary", "extraordianry")

    @Test
    fun normalize() {
        benchmarkRule.measureRepeated {
            QueryTextMatcher.normalize(mixedCaseQuery)
        }
    }

    @Test
    fun tokenizeLongLabel() {
        benchmarkRule.measureRepeated {
            QueryTextMatcher.tokenize(longLabel)
        }
    }

    @Test
    fun buildAcronymLongLabel() {
        benchmarkRule.measureRepeated {
            QueryTextMatcher.buildAcronym(longLabel)
        }
    }

    @Test
    fun levenshteinShortStrings() {
        benchmarkRule.measureRepeated {
            QueryTextMatcher.levenshtein(shortPair.first, shortPair.second)
        }
    }

    @Test
    fun levenshteinLongStrings() {
        benchmarkRule.measureRepeated {
            QueryTextMatcher.levenshtein(longPair.first, longPair.second)
        }
    }
}
