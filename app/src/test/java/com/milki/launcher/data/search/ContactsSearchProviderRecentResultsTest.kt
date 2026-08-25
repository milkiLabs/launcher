package com.milki.launcher.data.search

import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.model.ContactSearchResult
import com.milki.launcher.domain.model.PermissionAccessState
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.SearchRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ContactsSearchProviderRecentResultsTest {

    @Test
    fun blank_query_with_unresolvable_recents_produces_unique_result_ids() = runBlocking {
        val recents = listOf("+15550001", "+15550002", "+15550003")
        val provider = ContactsSearchProvider(
            FakeContactsRepository(recents = recents, resolved = emptyMap())
        )

        val results = provider.search(searchRequest())

        val ids = results.map { it.id }
        assertEquals(recents.size, ids.size)
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun unresolved_placeholder_ids_differ_from_each_other() {
        val first = ContactSearchResult(Contact.unresolved("+15550001"))
        val second = ContactSearchResult(Contact.unresolved("+15550002"))

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun two_recents_resolving_to_same_contact_are_deduplicated() = runBlocking {
        val sharedContact = Contact(
            id = 42L,
            displayName = "Crash Test",
            phoneNumbers = listOf("+15550001", "+15550002"),
            photoUri = null,
            lookupKey = "lookup42"
        )
        val provider = ContactsSearchProvider(
            FakeContactsRepository(
                recents = listOf("+15550001", "+15550002"),
                resolved = mapOf("+15550001" to sharedContact, "+15550002" to sharedContact)
            )
        )

        val results = provider.search(searchRequest())

        assertEquals(1, results.size)
        assertEquals("contact_42_lookup42", results.single().id)
    }

    private fun searchRequest() = SearchRequest(
        query = "",
        contactsPermissionState = PermissionAccessState.GRANTED
    )

    private class FakeContactsRepository(
        private val recents: List<String>,
        private val resolved: Map<String, Contact>
    ) : ContactsRepository {
        override fun hasContactsPermission(): Boolean = true
        override suspend fun searchContacts(query: String, maxItems: Int) = emptyList<Contact>()
        override suspend fun saveRecentContact(phoneNumber: String) = Unit
        override fun getRecentContacts(): Flow<List<String>> = flowOf(recents)
        override suspend fun getContactsByPhoneNumbers(phoneNumbers: List<String>) =
            phoneNumbers.mapNotNull { phone -> resolved[phone]?.let { phone to it } }.toMap()
    }
}
