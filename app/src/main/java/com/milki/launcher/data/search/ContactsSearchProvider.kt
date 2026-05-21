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
import com.milki.launcher.domain.repository.SearchProvider
import com.milki.launcher.domain.search.QueryRanker
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
) : SearchProvider {

    private companion object {
        const val MAX_RESULTS = 10
        const val MIN_PHONE_DIGITS = 3
        val PHONE_QUERY_PATTERN = Regex("""^\+?[0-9][0-9 .()\-]{2,}$""")
    }

    override val config: SearchProviderConfig = SearchProviderConfig(
        providerId = ProviderId.CONTACTS,
        prefix = "c",
        name = "Contacts",
        description = "Search your contacts"
    )

    override suspend fun search(request: SearchRequest): List<SearchResult> {
        val typedPhoneResult = request.query.trim().takeIf(::isPhoneNumberQuery)?.let(::PhoneNumberSearchResult)

        if (!request.contactsPermissionState.isGranted) {
            return listOfNotNull(typedPhoneResult, permissionPrompt(request.contactsPermissionState))
        }

        if (request.query.isBlank()) {
            return resolveRecentContacts()
        }

        val contacts = contactsRepository.searchContacts(query = request.query, maxItems = MAX_RESULTS)
        val recentContacts = resolveRecentContactsForBoosting()

        val ranked = QueryRanker.rank(
            items = contacts,
            query = request.query,
            recentItems = recentContacts,
            nameSelector = { it.displayName },
            identitySelector = { it.id.toString() },
        )

        return buildList {
            typedPhoneResult?.let(::add)
            addAll(ranked.map { ContactSearchResult(it) })
        }.take(MAX_RESULTS)
    }

    private fun permissionPrompt(state: PermissionAccessState): PermissionRequestResult {
        val requiresSettings = state == PermissionAccessState.REQUIRES_SETTINGS
        return PermissionRequestResult(
            permission = android.Manifest.permission.READ_CONTACTS,
            providerPrefix = config.prefix,
            message = if (requiresSettings) {
                "Contacts access is blocked. Open Settings to search contacts"
            } else {
                "Contacts permission required to search contacts"
            },
            buttonText = if (requiresSettings) "Open Settings" else "Grant Permission"
        )
    }

    private fun isPhoneNumberQuery(query: String): Boolean {
        val digitCount = query.count(Char::isDigit)
        return digitCount >= MIN_PHONE_DIGITS && PHONE_QUERY_PATTERN.matches(query)
    }

    private suspend fun resolveRecentContacts(): List<SearchResult> {
        val recentPhones = contactsRepository.getRecentContacts().first()
        if (recentPhones.isEmpty()) return emptyList()

        val contactsByPhone = contactsRepository.getContactsByPhoneNumbers(recentPhones)

        return recentPhones.take(MAX_RESULTS).map { phoneNumber ->
            val contact = contactsByPhone[phoneNumber] ?: Contact(
                id = -1,
                displayName = phoneNumber,
                phoneNumbers = listOf(phoneNumber),
                emails = emptyList(),
                photoUri = null,
                lookupKey = ""
            )
            ContactSearchResult(contact)
        }
    }

    private suspend fun resolveRecentContactsForBoosting(): List<Contact> {
        val recentPhones = contactsRepository.getRecentContacts().first()
        if (recentPhones.isEmpty()) return emptyList()

        val contactsByPhone = contactsRepository.getContactsByPhoneNumbers(recentPhones)

        return recentPhones.mapNotNull { phoneNumber ->
            contactsByPhone[phoneNumber] ?: Contact(
                id = -1,
                displayName = phoneNumber,
                phoneNumbers = listOf(phoneNumber),
                emails = emptyList(),
                photoUri = null,
                lookupKey = ""
            )
        }
    }
}
