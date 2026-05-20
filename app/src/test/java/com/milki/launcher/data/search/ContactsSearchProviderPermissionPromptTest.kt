package com.milki.launcher.data.search

import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.model.PermissionAccessState
import com.milki.launcher.domain.model.PermissionRequestResult
import com.milki.launcher.domain.model.PhoneNumberSearchResult
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.SearchRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsSearchProviderPermissionPromptTest {

    @Test
    fun blocked_contacts_permission_surfaces_open_settings_prompt() = runBlocking {
        val provider = ContactsSearchProvider(FakeContactsRepository())

        val results = provider.search(
            SearchRequest(
                query = "ali",
                contactsPermissionState = PermissionAccessState.REQUIRES_SETTINGS
            )
        )

        val prompt = results.single() as PermissionRequestResult

        assertEquals("Open Settings", prompt.buttonText)
        assertTrue(prompt.message.contains("blocked"))
    }

    @Test
    fun phone_number_query_surfaces_call_or_save_result_before_contact_matches() = runBlocking {
        val provider = ContactsSearchProvider(
            FakeContactsRepository(
                searchResults = listOf(
                    Contact(
                        id = 1,
                        displayName = "Ali",
                        phoneNumbers = listOf("+201234567890"),
                        emails = emptyList(),
                        photoUri = null,
                        lookupKey = "ali"
                    )
                )
            )
        )

        val results = provider.search(
            SearchRequest(
                query = "+20 123 456 7890",
                contactsPermissionState = PermissionAccessState.GRANTED
            )
        )

        val phoneNumberResult = results.first() as PhoneNumberSearchResult

        assertEquals("+20 123 456 7890", phoneNumberResult.phoneNumber)
        assertEquals(2, results.size)
    }

    @Test
    fun phone_number_query_is_available_without_contacts_permission() = runBlocking {
        val provider = ContactsSearchProvider(FakeContactsRepository())

        val results = provider.search(
            SearchRequest(
                query = "123456",
                contactsPermissionState = PermissionAccessState.CAN_REQUEST
            )
        )

        assertTrue(results[0] is PhoneNumberSearchResult)
        assertTrue(results[1] is PermissionRequestResult)
    }

    private class FakeContactsRepository(
        private val searchResults: List<Contact> = emptyList()
    ) : ContactsRepository {
        override fun hasContactsPermission(): Boolean = false

        override suspend fun searchContacts(query: String, maxItems: Int): List<Contact> = searchResults

        override suspend fun saveRecentContact(phoneNumber: String) = Unit

        override fun getRecentContacts(): Flow<List<String>> = flowOf(emptyList())

        override suspend fun getContactByPhoneNumber(phoneNumber: String): Contact? = null

        override suspend fun getContactsByPhoneNumbers(phoneNumbers: List<String>): Map<String, Contact> =
            emptyMap()
    }
}
