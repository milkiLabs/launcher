package com.milki.launcher.data.search

import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.model.ContactSearchResult
import com.milki.launcher.domain.model.PermissionAccessState
import com.milki.launcher.domain.model.PermissionRequestResult
import com.milki.launcher.domain.model.PhoneNumberSearchResult
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.SearchProviderConfig
import com.milki.launcher.domain.model.SearchResult
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.SearchRequest
import kotlinx.coroutines.flow.first

/**
 * Search provider for device contacts (activated by "c" prefix).
 *
 * Behavior:
 * - Permission not granted → permission prompt (+ phone number result if query looks like a phone)
 * - Blank query → recent contacts
 * - Typed query → search + rank contacts using [QueryRanker]
 */
class ContactsSearchProvider(
    private val contactsRepository: ContactsRepository
) : RecentBackedSearchProvider<Contact>() {

    private companion object {
        const val MIN_PHONE_DIGITS = 3
        val PHONE_QUERY_PATTERN = Regex("""^\+?[0-9][0-9 .()\-]{2,}$""")
    }

    override val config: SearchProviderConfig = SearchProviderConfig(
        providerId = ProviderId.CONTACTS,
        prefix = "c",
        name = "Contacts",
        description = "Search your contacts"
    )

    override fun permissionState(request: SearchRequest): PermissionAccessState =
        request.contactsPermissionState

    override fun preQueryResults(request: SearchRequest): List<SearchResult> =
        listOfNotNull(
            request.query.trim()
                .takeIf(::isPhoneNumberQuery)
                ?.let(::PhoneNumberSearchResult)
        )

    override fun permissionPrompt(state: PermissionAccessState): PermissionRequestResult {
        val requiresSettings = state == PermissionAccessState.REQUIRES_SETTINGS
        return PermissionRequestResult(
            permission = READ_CONTACTS_PERMISSION,
            providerPrefix = config.prefix,
            message = if (requiresSettings) {
                "Contacts access is blocked. Open Settings to search contacts"
            } else {
                "Contacts permission required to search contacts"
            },
            buttonText = if (requiresSettings) "Open Settings" else "Grant Permission"
        )
    }

    override suspend fun searchTypedItems(request: SearchRequest): List<Contact> =
        contactsRepository.searchContacts(query = request.query, maxItems = MAX_SEARCH_RESULTS)

    override suspend fun resolveRecentItems(request: SearchRequest): List<Contact> {
        val recentPhones = contactsRepository.getRecentContacts().first()
        if (recentPhones.isEmpty()) return emptyList()

        val contactsByPhone = contactsRepository.getContactsByPhoneNumbers(recentPhones)

        return recentPhones.mapNotNull { phoneNumber ->
            contactsByPhone[phoneNumber] ?: contactFromPhoneNumber(phoneNumber)
        }
    }

    override val toSearchResult: (Contact) -> SearchResult = { ContactSearchResult(it) }

    override val nameSelector: (Contact) -> String = { it.displayName }

    override val identitySelector: (Contact) -> String = { it.id.toString() }

    private fun isPhoneNumberQuery(query: String): Boolean {
        val digitCount = query.count(Char::isDigit)
        return digitCount >= MIN_PHONE_DIGITS && PHONE_QUERY_PATTERN.matches(query)
    }

    private fun contactFromPhoneNumber(phoneNumber: String): Contact {
        return Contact(
            id = -1,
            displayName = phoneNumber,
            phoneNumbers = listOf(phoneNumber),
            photoUri = null,
            lookupKey = ""
        )
    }
}
