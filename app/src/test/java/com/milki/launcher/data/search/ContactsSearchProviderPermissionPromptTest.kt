package com.milki.launcher.data.search

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
    fun blocked_permission_shows_open_settings_prompt() = runBlocking {
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
    fun can_request_permission_shows_grant_prompt() = runBlocking {
        val provider = ContactsSearchProvider(FakeContactsRepository())

        val results = provider.search(
            SearchRequest(
                query = "ali",
                contactsPermissionState = PermissionAccessState.CAN_REQUEST
            )
        )

        val prompt = results.single() as PermissionRequestResult
        assertEquals("Grant Permission", prompt.buttonText)
    }

    @Test
    fun phone_query_without_permission_surfaces_number_and_prompt() = runBlocking {
        val provider = ContactsSearchProvider(FakeContactsRepository())

        val results = provider.search(
            SearchRequest(
                query = "+201234567890",
                contactsPermissionState = PermissionAccessState.CAN_REQUEST
            )
        )

        assertEquals(2, results.size)
        assertTrue(results[0] is PhoneNumberSearchResult)
        assertTrue(results[1] is PermissionRequestResult)
    }

    private class FakeContactsRepository : ContactsRepository {
        override fun hasContactsPermission(): Boolean = false
        override suspend fun searchContacts(query: String, maxItems: Int) = emptyList<com.milki.launcher.domain.model.Contact>()
        override suspend fun saveRecentContact(phoneNumber: String) = Unit
        override fun getRecentContacts(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun getContactByPhoneNumber(phoneNumber: String) = null
        override suspend fun getContactsByPhoneNumbers(phoneNumbers: List<String>) = emptyMap<String, com.milki.launcher.domain.model.Contact>()
    }
}
