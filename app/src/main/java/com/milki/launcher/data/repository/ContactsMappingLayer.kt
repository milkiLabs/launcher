package com.milki.launcher.data.repository

import com.milki.launcher.domain.model.Contact

/**
 * Mapping layer for contacts feature.
 *
 * This class owns all "how raw query rows become domain Contact objects"
 * decisions. By extracting this from query code, the query layer focuses on
 * SQL/projection/selection while mapping logic stays centralized.
 */
internal class ContactsMappingLayer {

    /**
     * Mutable aggregate that accumulates phone/email rows for one contact ID.
     */
    data class MutableContactAggregate(
        val id: Long,
        val displayName: String,
        val photoUri: String?,
        val lookupKey: String
    ) {
        val phoneNumbers: MutableList<String> = mutableListOf()
        val emails: MutableList<String> = mutableListOf()

        fun addPhoneIfMissing(phone: String) {
            if (phone !in phoneNumbers) {
                phoneNumbers.add(phone)
            }
        }

        fun addEmailIfMissing(email: String) {
            if (email !in emails) {
                emails.add(email)
            }
        }

        fun toContact(): Contact {
            return Contact(
                id = id,
                displayName = displayName,
                phoneNumbers = phoneNumbers.toList(),
                emails = emails.toList(),
                photoUri = photoUri,
                lookupKey = lookupKey
            )
        }
    }

    /**
     * Match quality buckets used for deterministic ordering.
     */
    private enum class MatchType {
        EXACT,
        STARTS_WITH,
        CONTAINS
    }

    /**
     * Builds a Contact from lightweight phone-lookup values.
     *
     * Emails are intentionally empty because phone lookup flows only require
     * contact identity + phone numbers for display/call interactions.
     */
    fun buildPhoneLookupContact(
        contactId: Long,
        displayName: String,
        phoneNumbers: List<String>,
        photoUri: String?,
        lookupKey: String
    ): Contact {
        return Contact(
            id = contactId,
            displayName = displayName,
            phoneNumbers = phoneNumbers,
            emails = emptyList(),
            photoUri = photoUri,
            lookupKey = lookupKey
        )
    }

    /**
     * Sorts and limits contacts using query relevance (exact > startsWith > contains).
     */
    fun sortAndLimitByQueryRelevance(
        contacts: List<Contact>,
        queryLower: String,
        maxItems: Int = 50
    ): List<Contact> {
        return contacts
            .sortedBy { contact ->
                matchType(contact.displayName, queryLower).ordinal
            }
            .take(maxItems)
    }

    /**
     * Creates or retrieves an aggregate for one contact ID.
     *
     * Returns null when mandatory identity fields are missing.
     */
    fun getOrCreateAggregate(
        aggregates: MutableMap<Long, MutableContactAggregate>,
        contactId: Long,
        displayName: String?,
        photoUri: String?,
        lookupKey: String?
    ): MutableContactAggregate? {
        val safeDisplayName = displayName ?: return null
        val safeLookupKey = lookupKey ?: return null

        return aggregates.getOrPut(contactId) {
            MutableContactAggregate(
                id = contactId,
                displayName = safeDisplayName,
                photoUri = photoUri,
                lookupKey = safeLookupKey
            )
        }
    }

    /**
     * Converts aggregates to immutable domain contacts.
     */
    fun toContacts(aggregates: Collection<MutableContactAggregate>): List<Contact> {
        return aggregates.map { it.toContact() }
    }

    /**
     * Computes match category for ordering.
     */
    private fun matchType(displayName: String, queryLower: String): MatchType {
        val nameLower = displayName.lowercase()
        return when {
            nameLower == queryLower -> MatchType.EXACT
            nameLower.startsWith(queryLower) -> MatchType.STARTS_WITH
            else -> MatchType.CONTAINS
        }
    }
}
