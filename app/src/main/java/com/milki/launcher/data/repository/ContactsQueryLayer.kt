package com.milki.launcher.data.repository

import android.content.ContentResolver
import android.provider.ContactsContract
import com.milki.launcher.domain.model.Contact

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
        val rawRowLimit = maxOf(maxItems * 8, 80)

        val selection = """
            ${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?
            AND ${ContactsContract.Data.MIMETYPE} IN (?, ?, ?)
        """.trimIndent()

        val selectionArgs = arrayOf(
            "%$queryLower%",
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
        )

        val sortOrder = """
            ${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC,
            ${ContactsContract.Data.MIMETYPE} ASC
            LIMIT $rawRowLimit
        """.trimIndent()

        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            DATA_PROJECTION,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val contactIdIndex = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val lookupKeyIndex = cursor.getColumnIndex(ContactsContract.Data.LOOKUP_KEY)
            val displayNameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoUriIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
            val mimetypeIndex = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
            val phoneNumberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val emailAddressIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)

            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(contactIdIndex)
                val aggregate = mapper.getOrCreateAggregate(
                    aggregates = aggregates,
                    contactId = contactId,
                    displayName = cursor.getString(displayNameIndex),
                    photoUri = cursor.getString(photoUriIndex),
                    lookupKey = cursor.getString(lookupKeyIndex)
                ) ?: continue

                when (cursor.getString(mimetypeIndex)) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        cursor.getString(phoneNumberIndex)?.let { phone ->
                            aggregate.addPhoneIfMissing(phone)
                        }
                    }

                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        cursor.getString(emailAddressIndex)?.let { email ->
                            aggregate.addEmailIfMissing(email)
                        }
                    }
                }
            }
        }

        val contacts = mapper.toContacts(aggregates.values)
        return mapper.sortAndLimitByQueryRelevance(
            contacts = contacts,
            queryLower = queryLower,
            maxItems = maxItems
        )
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
