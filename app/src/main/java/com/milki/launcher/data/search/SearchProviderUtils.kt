package com.milki.launcher.data.search

import android.net.Uri
import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.model.ContactSearchResult
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.FileDocumentSearchResult
import com.milki.launcher.domain.model.SearchResult

/**
 * Shared utility for generating common search result types.
 *
 * Handles creation of "Hint" and "Empty" state results for local search providers
 * like Contacts and Files. These results use a consistent ID (-1) and structure.
 */
object SearchProviderUtils {
    private const val DUMMY_ID = -1L
    
    /**
     * Creates a "Hint" result for Contacts search.
     * Shown when the user has typed the prefix "c" but no query yet.
     */
    fun createContactHint(): List<SearchResult> {
        return listOf(
            ContactSearchResult(
                contact = Contact(
                    id = DUMMY_ID,
                    displayName = "Type to search contacts",
                    phoneNumbers = emptyList(),
                    emails = emptyList(),
                    photoUri = null,
                    lookupKey = "hint"
                )
            )
        )
    }

    /**
     * Creates an "Empty" result for Contacts search.
     * Shown when the search yielded no results.
     */
    fun createContactEmpty(query: String): List<SearchResult> {
        return listOf(
            ContactSearchResult(
                contact = Contact(
                    id = DUMMY_ID,
                    displayName = "No contacts found for \"$query\"",
                    phoneNumbers = emptyList(),
                    emails = emptyList(),
                    photoUri = null,
                    lookupKey = "empty"
                )
            )
        )
    }

    /**
     * Creates a "Hint" result for Files search.
     * Shown when the user has typed the prefix "f" but no query yet.
     */
    fun createFileHint(): List<SearchResult> {
        return listOf(
            FileDocumentSearchResult(
                file = FileDocument(
                    id = DUMMY_ID,
                    name = "Type to search all files",
                    mimeType = "text/plain",
                    size = 0,
                    dateModified = System.currentTimeMillis(),
                    uri = Uri.EMPTY,
                    folderPath = ""
                )
            )
        )
    }

    /**
     * Creates an "Empty" result for Files search.
     * Shown when the search yielded no results.
     */
    fun createFileEmpty(query: String): List<SearchResult> {
        return listOf(
            FileDocumentSearchResult(
                file = FileDocument(
                    id = DUMMY_ID,
                    name = "No files found for \"$query\"",
                    mimeType = "text/plain",
                    size = 0,
                    dateModified = System.currentTimeMillis(),
                    uri = Uri.EMPTY,
                    folderPath = ""
                )
            )
        )
    }
}
