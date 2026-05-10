package com.milki.launcher.data.search

import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.model.PermissionAccessState
import com.milki.launcher.domain.model.PermissionRequestResult
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
                maxResults = 8,
                contactsPermissionState = PermissionAccessState.REQUIRES_SETTINGS
            )
        )

        val prompt = results.single() as PermissionRequestResult

        assertEquals("Open Settings", prompt.buttonText)
        assertTrue(prompt.message.contains("blocked"))
    }

    private class FakeContactsRepository : ContactsRepository {
        override fun hasContactsPermission(): Boolean = false

        override suspend fun searchContacts(query: String, maxItems: Int): List<Contact> = emptyList()

        override suspend fun saveRecentContact(phoneNumber: String) = Unit

        override fun getRecentContacts(): Flow<List<String>> = flowOf(emptyList())

        override suspend fun getContactByPhoneNumber(phoneNumber: String): Contact? = null

        override suspend fun getContactsByPhoneNumbers(phoneNumbers: List<String>): Map<String, Contact> =
            emptyMap()
    }
}
