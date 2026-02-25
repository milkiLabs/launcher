/**
 * FilterAppsUseCase.kt - Use case for filtering and prioritizing app results
 *
 * This file contains the business logic for filtering installed apps
 * based on a search query. It implements the Strategy Pattern for
 * different match types (exact, starts with, word boundary, contains, fuzzy).
 *
 * MATCHING PRIORITY TIERS (in order of relevance):
 * ================================================
 * 1. EXACT MATCH: Query equals app name exactly
 *    Example: "maps" -> "Maps"
 *    Priority: Highest (user knows exactly what they want)
 *
 * 2. STARTS WITH: App name begins with the query
 *    Example: "map" -> "Maps", "MapMyRun"
 *    Priority: Very High (query is the first word)
 *
 * 3. WORD BOUNDARY: Query matches the start of a word within the app name
 *    Example: "map" -> "Google Maps" (matches the word "Maps")
 *    Example: "tube" -> "YouTube" (if stored as "You Tube") or "NewPipe Tube"
 *    Priority: High (query is a distinct word, not partial)
 *    Implementation: Checks for " query" (space before query)
 *
 * 4. CONTAINS: Query appears anywhere in the app name
 *    Example: "map" -> "Bitmap Converter"
 *    Priority: Medium (could be partial match or substring)
 *
 * 5. FUZZY (SUBSEQUENCE): Query characters appear in order within app name
 *    Example: "cod" -> "Call of Duty" (C-o-D appear in sequence)
 *    Example: "gm" -> "Google Maps"
 *    Example: "yt" -> "YouTube"
 *    Priority: Lowest (fallback for acronym/abbreviation matching)
 *    Algorithm: O(N) simple character-by-character matching
 *
 * WHY THIS MATTERS FOR USER EXPERIENCE:
 * =====================================
 * - Typing "map" should rank "Google Maps" above "Bitmap Converter"
 *   because "Map" is a complete word, not a substring
 * - Typing "tube" should rank "NewPipe Tube" above "Redtuber"
 *   because "Tube" is a distinct word
 * - Typing "fb" should find "Facebook" via fuzzy matching
 * - Typing "stg" should find "Settings" via fuzzy matching
 *
 * MEMORY OPTIMIZATION:
 * ====================
 * We use buildList() with pre-calculated capacity instead of the + operator.
 *
 * WHY? The + operator on lists creates intermediate lists:
 *   List A + List B = new List C (allocation 1)
 *   List C + List D = new List E (allocation 2)
 * This causes garbage collection on every keystroke!
 *
 * buildList() creates ONE list with exact capacity needed.
 * This eliminates memory churn while user types quickly.
 */

package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.AppInfo

/**
 * Use case for filtering and prioritizing app search results.
 *
 * This is a pure function use case - it has no side effects.
 * It implements a 5-tier priority matching system for intelligent
 * app search that goes beyond simple string containment.
 *
 * @see isSubsequenceMatch for fuzzy matching algorithm details
 */
class FilterAppsUseCase {

    /**
     * Filter apps based on a search query using multi-tier priority matching.
     *
     * The algorithm categorizes matches into 5 priority levels:
     * 1. Exact matches - name equals query exactly
     * 2. Starts-with matches - name starts with query
     * 3. Word boundary matches - query starts a word within the name
     * 4. Contains matches - query appears anywhere in name
     * 5. Fuzzy matches - query is a subsequence of the name
     *
     * When the query is empty, returns recent apps instead of searching.
     *
     * @param query The search query (will be trimmed and lowercased)
     * @param installedApps All installed apps to search through
     * @param recentApps Recent apps to show when query is empty
     * @return Filtered and prioritized list of apps, ordered by match relevance
     */
    operator fun invoke(
        query: String,
        installedApps: List<AppInfo>,
        recentApps: List<AppInfo>
    ): List<AppInfo> {
        // When search is empty, show recent apps
        // This provides a useful default view without requiring user input
        if (query.isBlank()) {
            return recentApps
        }

        // Normalize the query: trim whitespace and convert to lowercase
        // This ensures case-insensitive matching and ignores leading/trailing spaces
        val queryLower = query.trim().lowercase()

        // ============================================================
        // FIVE LISTS FOR DIFFERENT MATCH PRIORITIES
        // ============================================================
        // We use ArrayList directly for maximum performance.
        // ArrayList has O(1) amortized add() operations.
        val exactMatches = ArrayList<AppInfo>()        // Tier 1: Exact name match
        val startsWithMatches = ArrayList<AppInfo>()   // Tier 2: Name starts with query
        val wordBoundaryMatches = ArrayList<AppInfo>() // Tier 3: Query starts a word in name
        val containsMatches = ArrayList<AppInfo>()     // Tier 4: Query appears anywhere
        val fuzzyMatches = ArrayList<AppInfo>()        // Tier 5: Subsequence match

        // ============================================================
        // CATEGORIZE EACH APP BASED ON MATCH TYPE
        // ============================================================
        // We iterate through all installed apps and classify them
        // into the appropriate priority tier.
        installedApps.forEach { app ->
            // Use the pre-computed lowercase name for comparison
            // This avoids calling lowercased() repeatedly for each app
            val name = app.nameLower

            // Use when expression with ordered conditions
            // The order matters! First match wins.
            // More specific matches must come before general ones.
            when {
                // TIER 1: EXACT MATCH
                // The query exactly equals the app name
                // Example: "maps" matches "Maps"
                // This is the highest priority because the user
                // typed the complete, correct name
                name == queryLower -> {
                    exactMatches.add(app)
                }

                // TIER 2: STARTS WITH MATCH
                // The app name begins with the query
                // Example: "map" matches "Maps", "MapMyRun"
                // This is high priority because the user
                // typed the beginning of the app name
                name.startsWith(queryLower) -> {
                    startsWithMatches.add(app)
                }

                // TIER 3: WORD BOUNDARY MATCH
                // The query matches the start of a word within the name
                // We check for " query" (space followed by query)
                // Example: "map" matches "Google Maps" (contains " map")
                // Example: "tube" matches "NewPipe Tube" (contains " tube")
                //
                // WHY THIS MATTERS:
                // Without this tier, "Google Maps" and "Bitmap Converter"
                // would both be in "contains" for query "map"
                // But "Maps" is a complete word, so it should rank higher!
                //
                // EDGE CASE: What about the first word without a space?
                // First words are caught by startsWith() above.
                // This tier specifically catches words after the first.
                name.contains(" $queryLower") -> {
                    wordBoundaryMatches.add(app)
                }

                // TIER 4: CONTAINS MATCH
                // The query appears anywhere in the app name
                // Example: "map" matches "Bitmap Converter"
                // This catches substring matches that aren't word boundaries
                // Lower priority because it might be a coincidence
                name.contains(queryLower) -> {
                    containsMatches.add(app)
                }

                // TIER 5: FUZZY (SUBSEQUENCE) MATCH
                // The query characters appear in order within the name
                // This enables acronym/abbreviation matching
                // Example: "cod" -> "Call of Duty"
                // Example: "gm" -> "Google Maps"
                // Example: "yt" -> "YouTube"
                //
                // This is a FALLBACK - only checked if no other match
                // because it's more permissive and might return noise
                isSubsequenceMatch(queryLower, name) -> {
                    fuzzyMatches.add(app)
                }
            }
        }

        // ============================================================
        // COMBINE ALL MATCHES IN PRIORITY ORDER
        // ============================================================
        // Use buildList with pre-calculated capacity for memory efficiency.
        //
        // WHY NOT USE + OPERATOR?
        // The + operator creates intermediate lists:
        //   list1 + list2 creates a NEW list (allocation 1)
        //   result + list3 creates ANOTHER new list (allocation 2)
        // This causes garbage collection on every keystroke!
        //
        // buildList() with capacity:
        // - Allocates exactly ONE array of the right size
        // - No intermediate allocations
        // - addAll() copies directly into pre-allocated space
        //
        // For a launcher, this optimization matters because
        // search is triggered on every keystroke.
        return buildList(
            capacity = exactMatches.size + startsWithMatches.size +
                       wordBoundaryMatches.size + containsMatches.size +
                       fuzzyMatches.size
        ) {
            // Add in priority order: highest priority first
            addAll(exactMatches)        // Tier 1: Exact matches
            addAll(startsWithMatches)   // Tier 2: Starts-with matches
            addAll(wordBoundaryMatches) // Tier 3: Word boundary matches
            addAll(containsMatches)     // Tier 4: Contains matches
            addAll(fuzzyMatches)        // Tier 5: Fuzzy matches
        }
    }

    /**
     * Checks if the query string is a subsequence of the text.
     *
     * A subsequence means all characters of query appear in text
     * in the same order, but not necessarily consecutively.
     *
     * EXAMPLES:
     * - query="cod", text="call of duty" -> TRUE
     *   (c matches 'c'all, o matches 'o'f, d matches 'd'uty)
     * - query="gm", text="google maps" -> TRUE
     *   (g matches 'g'oogle, m matches 'm'aps)
     * - query="cat", text="calculator" -> TRUE
     *   (c matches 'c'alculator, a matches c'a'lculator, t matches calcula't'or)
     * - query="xyz", text="calculator" -> FALSE
     *   (x, y, z don't all appear in text)
     *
     * ALGORITHM: Two-Pointer Technique
     * ================================
     * We use two indices: queryIndex for the query, textIndex for the text.
     *
     * 1. If characters at both indices match, advance queryIndex
     * 2. Always advance textIndex
     * 3. If queryIndex reaches the end of query, all chars were found
     *
     * TIME COMPLEXITY: O(N) where N is text.length
     * We scan through the text at most once.
     *
     * SPACE COMPLEXITY: O(1)
     * We only use a few integer variables, no extra data structures.
     *
     * WHY THIS IS FAST:
     * - No memory allocations (no substrings, no lists)
     * - Simple integer comparisons
     * - Early termination if query is longer than text
     *
     * @param query The search query to find as a subsequence
     * @param text The text to search within
     * @return true if query is a subsequence of text, false otherwise
     */
    private fun isSubsequenceMatch(query: String, text: String): Boolean {
        // OPTIMIZATION: If query is longer than text, impossible to match
        // This avoids unnecessary iteration for clearly impossible cases
        if (query.length > text.length) return false

        // Two pointers: one for query, one for text
        var queryIndex = 0  // How many characters of query we've matched
        var textIndex = 0   // Current position in text

        // Iterate through both strings simultaneously
        while (queryIndex < query.length && textIndex < text.length) {
            // Check if characters at current positions match
            if (query[queryIndex] == text[textIndex]) {
                // Match found! Move to next character in query
                // We've "consumed" this character of the query
                queryIndex++
            }
            // ALWAYS move to next character in text
            // Even if we matched, we need to continue searching
            // for the next query character in the remaining text
            textIndex++
        }

        // If queryIndex equals query.length, we found ALL characters
        // If queryIndex is less, we didn't find all characters
        // (the loop ended because textIndex reached text.length)
        return queryIndex == query.length
    }
}
