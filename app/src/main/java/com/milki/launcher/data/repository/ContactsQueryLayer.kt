package com.milki.launcher.data.repository

import android.content.ContentResolver
import android.database.Cursor
import android.provider.ContactsContract
import com.milki.launcher.domain.model.Contact

private val CONTACT_SEARCH_MIME_TYPES = arrayOf(
    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
)

/**
 * Query layer for contact data access via ContentResolver.
 *
 * This class owns only query mechanics:
 * - URI/projection/selection/sort construction
 * - cursor iteration
 * - coordinating with mapping layer
 */
internal class ContactsQueryLayer(
    private val contentResolver: ContentResolver,
    private val mapper: ContactsMappingLayer
) {

    /**
     * Executes contact search with one Data-table query and relevance sorting.
     */
    fun searchContacts(queryLower: String, maxItems: Int): List<Contact> {
        val aggregates = mutableMapOf<Long, ContactsMappingLayer.MutableContactAggregate>()
        val rawRowLimit = maxOf(maxItems * RAW_ROW_LIMIT_MULTIPLIER, MIN_RAW_ROW_LIMIT)

        populateSearchAggregates(
            queryLower = queryLower,
            rawRowLimit = rawRowLimit,
            aggregates = aggregates
        )

        val contacts = mapper.toContacts(aggregates.values)
        return mapper.sortAndLimitByQueryRelevance(
            contacts = contacts,
            queryLower = queryLower,
            maxItems = maxItems
        )
    }

    private fun populateSearchAggregates(
        queryLower: String,
        rawRowLimit: Int,
        aggregates: MutableMap<Long, ContactsMappingLayer.MutableContactAggregate>
    ) {
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            DATA_PROJECTION,
            SEARCH_SELECTION,
            buildSearchSelectionArgs(queryLower),
            buildSearchSortOrder(rawRowLimit)
        )?.use { cursor ->
            val indices = SearchContactCursorIndices.create(cursor)
            while (cursor.moveToNext()) {
                appendSearchRow(
                    cursor = cursor,
                    indices = indices,
                    aggregates = aggregates
                )
            }
        }
    }

    private fun appendSearchRow(
        cursor: Cursor,
        indices: SearchContactCursorIndices,
        aggregates: MutableMap<Long, ContactsMappingLayer.MutableContactAggregate>
    ) {
        val contactId = cursor.getLong(indices.contactIdIndex)
        val aggregate = mapper.getOrCreateAggregate(
            aggregates = aggregates,
            contactId = contactId,
            displayName = cursor.getString(indices.displayNameIndex),
            photoUri = cursor.getString(indices.photoUriIndex),
            lookupKey = cursor.getString(indices.lookupKeyIndex)
        )

        if (aggregate != null) {
            when (cursor.getString(indices.mimetypeIndex)) {
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                    cursor.getString(indices.phoneNumberIndex)?.let(aggregate::addPhoneIfMissing)
                }

                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                    cursor.getString(indices.emailAddressIndex)?.let(aggregate::addEmailIfMissing)
                }
            }
        }
    }

    /**
     * Finds one contact by exact phone number match.
     */
    fun getContactByPhoneNumber(phoneNumber: String): Contact? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?"
        val selectionArgs = arrayOf(phoneNumber)

        return contentResolver.query(
            uri,
            PHONE_LOOKUP_PROJECTION,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use null
            }

            val contactId = cursor.getLong(0)
            val displayName = cursor.getString(1) ?: return@use null
            val photoUri = cursor.getString(2)
            val lookupKey = cursor.getString(3) ?: return@use null
            val phoneNumbers = getPhoneNumbersForContact(contactId)

            mapper.buildPhoneLookupContact(
                contactId = contactId,
                displayName = displayName,
                phoneNumbers = phoneNumbers,
                photoUri = photoUri,
                lookupKey = lookupKey
            )
        }
    }

    /**
     * Batch-resolves multiple phone numbers to contacts using a single IN query.
     */
    fun getContactsByPhoneNumbers(phoneNumbers: List<String>): Map<String, Contact> {
        if (phoneNumbers.isEmpty()) {
            return emptyMap()
        }

        val result = mutableMapOf<String, Contact>()
        val placeholders = phoneNumbers.joinToString(",") { "?" }
        val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} IN ($placeholders)"

        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            PHONE_BATCH_PROJECTION,
            selection,
            phoneNumbers.toTypedArray(),
            null
        )?.use { cursor ->
            val builtContacts = mutableMapOf<Long, Contact>()
            val phoneToContactId = mutableMapOf<String, Long>()

            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(0)
                val matchedPhone = cursor.getString(1) ?: continue
                val displayName = cursor.getString(2) ?: continue
                val photoUri = cursor.getString(3)
                val lookupKey = cursor.getString(4) ?: continue

                phoneToContactId[matchedPhone] = contactId

                if (contactId !in builtContacts) {
                    val allPhoneNumbers = getPhoneNumbersForContact(contactId)
                    builtContacts[contactId] = mapper.buildPhoneLookupContact(
                        contactId = contactId,
                        displayName = displayName,
                        phoneNumbers = allPhoneNumbers,
                        photoUri = photoUri,
                        lookupKey = lookupKey
                    )
                }
            }

            for ((phone, contactId) in phoneToContactId) {
                builtContacts[contactId]?.let { contact ->
                    result[phone] = contact
                }
            }
        }

        return result
    }

    /**
     * Resolves all phone numbers for one contact.
     */
    private fun getPhoneNumbersForContact(contactId: Long): List<String> {
        val phoneNumbers = mutableListOf<String>()
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId.toString())

        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            PHONE_NUMBER_ONLY_PROJECTION,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { phone ->
                    if (phone !in phoneNumbers) {
                        phoneNumbers.add(phone)
                    }
                }
            }
        }

        return phoneNumbers
    }

    companion object {
        private const val RAW_ROW_LIMIT_MULTIPLIER = 8
        private const val MIN_RAW_ROW_LIMIT = 80

        private val SEARCH_SELECTION = """
            ${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?
            AND ${ContactsContract.Data.MIMETYPE} IN (?, ?, ?)
        """.trimIndent()

        private val DATA_PROJECTION = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Email.ADDRESS
        )

        private val PHONE_LOOKUP_PROJECTION = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
        )

        private val PHONE_BATCH_PROJECTION = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
        )

        private val PHONE_NUMBER_ONLY_PROJECTION = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
    }
}

private data class SearchContactCursorIndices(
    val contactIdIndex: Int,
    val lookupKeyIndex: Int,
    val displayNameIndex: Int,
    val photoUriIndex: Int,
    val mimetypeIndex: Int,
    val phoneNumberIndex: Int,
    val emailAddressIndex: Int
) {
    companion object {
        fun create(cursor: Cursor): SearchContactCursorIndices {
            return SearchContactCursorIndices(
                contactIdIndex = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID),
                lookupKeyIndex = cursor.getColumnIndex(ContactsContract.Data.LOOKUP_KEY),
                displayNameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                photoUriIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI),
                mimetypeIndex = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE),
                phoneNumberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER),
                emailAddressIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            )
        }
    }
}

private fun buildSearchSelectionArgs(queryLower: String): Array<String> {
    return arrayOf("%$queryLower%", *CONTACT_SEARCH_MIME_TYPES)
}

private fun buildSearchSortOrder(rawRowLimit: Int): String {
    return """
        ${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC,
        ${ContactsContract.Data.MIMETYPE} ASC
        LIMIT $rawRowLimit
    """.trimIndent()
}
