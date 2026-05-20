package com.milki.launcher.core.util

/**
 * Parses a comma-separated string into a list of non-empty entries.
 */
fun parseCsv(csv: String): List<String> =
    csv.split(",").filter { it.isNotEmpty() }

/**
 * Joins a list of strings into a comma-separated string.
 */
fun List<String>.toCsv(): String =
    joinToString(",")
