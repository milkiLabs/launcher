package com.milki.launcher.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryRankerTierOrderTest {

    @Test
    fun scoring_constants_follow_documented_hierarchy() {
        val constants = QueryRanker::class.java.declaredFields
            .filter { it.type == java.lang.Integer.TYPE }
            .onEach { it.isAccessible = true }
            .associate { it.name to it.getInt(null) }

        val documentedOrder = listOf(
            "EXACT_MATCH",
            "PREFIX_MATCH",
            "WORD_PREFIX_MATCH",
            "CONTAINS_MATCH",
            "ACRONYM_MATCH",
            "TOKEN_MATCH",
            "SUBSEQUENCE_MATCH",
            "TYPO_MATCH",
        )

        documentedOrder.forEachIndexed { index, name ->
            assertTrue("Missing scoring constant: $name", constants.containsKey(name))
            if (index > 0) {
                val previous = constants[documentedOrder[index - 1]]
                val current = constants[name]
                assertTrue(
                    "$name (${constants[name]}) must rank below ${documentedOrder[index - 1]} ($previous)",
                    current != null && previous != null && current < previous
                )
            }
        }

        assertEquals(5_100, constants["TYPO_MATCH"])
    }

    @Test
    fun rank_orders_subsequence_match_above_typo_match() {
        val result = rank(listOf("Xya", "Ax yz b"), query = "xyz")

        assertEquals(listOf("Ax yz b", "Xya"), result)
    }

    private fun rank(names: List<String>, query: String): List<String> {
        return QueryRanker.rank(
            items = names,
            query = query,
            nameSelector = { it },
            identitySelector = { it },
        )
    }
}
