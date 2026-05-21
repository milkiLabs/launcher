package com.milki.launcher.data.repository.contacts

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.repository.ContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class ContactsRepositoryImpl(
    private val context: Context
) : ContactsRepository {

    private val contentResolver: ContentResolver = context.contentResolver
    private val recentStorage = ContactsRecentStorage(context)

    override fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun searchContacts(query: String, maxItems: Int): List<Contact> {
        if (!hasContactsPermission()) {
            throw SecurityException("READ_CONTACTS permission not granted")
        }

        if (query.isBlank()) {
            return emptyList()
        }

        val queryLower = query.trim().lowercase()

        return withContext(Dispatchers.IO) {
            queryContactsByName(queryLower, maxItems)
        }
    }

    override suspend fun saveRecentContact(phoneNumber: String) {
        recentStorage.saveRecent(phoneNumber)
    }

    override fun getRecentContacts(): Flow<List<String>> {
        return recentStorage.observeRecent().flowOn(Dispatchers.IO)
    }

    override suspend fun getContactByPhoneNumber(phoneNumber: String): Contact? {
        if (!hasContactsPermission()) {
            return null
        }

        return withContext(Dispatchers.IO) {
            queryContactByPhoneNumber(phoneNumber)
        }
    }

    override suspend fun getContactsByPhoneNumbers(phoneNumbers: List<String>): Map<String, Contact> {
        if (!hasContactsPermission() || phoneNumbers.isEmpty()) {
            return emptyMap()
        }

        return withContext(Dispatchers.IO) {
            queryContactsByPhoneNumbers(phoneNumbers)
        }
    }

    private fun queryContactsByName(queryLower: String, maxItems: Int): List<Contact> {
        val contactIds = mutableListOf<Long>()
        val contactInfo = mutableMapOf<Long, ContactInfo>()

        val contactsProjection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.LOOKUP_KEY
        )

        val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val selectionArgs = arrayOf("%$queryLower%")
        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC LIMIT $maxItems"

        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            contactsProjection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val displayName = cursor.getString(1)
                val photoUri = cursor.getString(2)
                val lookupKey = cursor.getString(3)

                if (displayName != null && lookupKey != null) {
                    contactIds.add(id)
                    contactInfo[id] = ContactInfo(displayName, photoUri, lookupKey)
                }
            }
        }

        if (contactIds.isEmpty()) {
            return emptyList()
        }

        val phonesByContactId = queryPhonesForContacts(contactIds)
        val emailsByContactId = queryEmailsForContacts(contactIds)

        return contactIds.map { id ->
            val info = contactInfo[id]!!
            Contact(
                id = id,
                displayName = info.displayName,
                phoneNumbers = phonesByContactId[id] ?: emptyList(),
                emails = emailsByContactId[id] ?: emptyList(),
                photoUri = info.photoUri,
                lookupKey = info.lookupKey
            )
        }.sortedBy { contact ->
            val nameLower = contact.displayName.lowercase()
            when {
                nameLower == queryLower -> 0
                nameLower.startsWith(queryLower) -> 1
                else -> 2
            }
        }
    }

    private fun queryPhonesForContacts(contactIds: List<Long>): Map<Long, List<String>> {
        val result = mutableMapOf<Long, MutableList<String>>()
        val placeholders = contactIds.joinToString(",") { "?" }
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN ($placeholders)"

        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            selection,
            contactIds.map { it.toString() }.toTypedArray(),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(0)
                val number = cursor.getString(1)
                if (number != null) {
                    result.getOrPut(contactId) { mutableListOf() }.add(number)
                }
            }
        }

        return result
    }

    private fun queryEmailsForContacts(contactIds: List<Long>): Map<Long, List<String>> {
        val result = mutableMapOf<Long, MutableList<String>>()
        val placeholders = contactIds.joinToString(",") { "?" }
        val selection = "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} IN ($placeholders)"

        contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.ADDRESS
            ),
            selection,
            contactIds.map { it.toString() }.toTypedArray(),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(0)
                val address = cursor.getString(1)
                if (address != null) {
                    result.getOrPut(contactId) { mutableListOf() }.add(address)
                }
            }
        }

        return result
    }

    private fun queryContactByPhoneNumber(phoneNumber: String): Contact? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
        )

        val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?"

        return contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            arrayOf(phoneNumber),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val contactId = cursor.getLong(0)
                val displayName = cursor.getString(1)
                val photoUri = cursor.getString(2)
                val lookupKey = cursor.getString(3)

                if (displayName != null && lookupKey != null) {
                    val phones = queryPhonesForContact(contactId)
                    Contact(
                        id = contactId,
                        displayName = displayName,
                        phoneNumbers = phones,
                        emails = emptyList(),
                        photoUri = photoUri,
                        lookupKey = lookupKey
                    )
                } else null
            } else null
        }
    }

    private fun queryContactsByPhoneNumbers(phoneNumbers: List<String>): Map<String, Contact> {
        val placeholders = phoneNumbers.joinToString(",") { "?" }
        val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} IN ($placeholders)"

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
        )

        val builtContacts = mutableMapOf<Long, Contact>()
        val phoneToContactId = mutableMapOf<String, Long>()

        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            phoneNumbers.toTypedArray(),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(0)
                val matchedPhone = cursor.getString(1)
                val displayName = cursor.getString(2)
                val photoUri = cursor.getString(3)
                val lookupKey = cursor.getString(4)

                if (matchedPhone != null && displayName != null && lookupKey != null) {
                    phoneToContactId[matchedPhone] = contactId

                    if (contactId !in builtContacts) {
                        val phones = queryPhonesForContact(contactId)
                        builtContacts[contactId] = Contact(
                            id = contactId,
                            displayName = displayName,
                            phoneNumbers = phones,
                            emails = emptyList(),
                            photoUri = photoUri,
                            lookupKey = lookupKey
                        )
                    }
                }
            }
        }

        return buildMap {
            for ((phone, contactId) in phoneToContactId) {
                builtContacts[contactId]?.let { put(phone, it) }
            }
        }
    }

    private fun queryPhonesForContact(contactId: Long): List<String> {
        val phones = mutableListOf<String>()
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"

        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            selection,
            arrayOf(contactId.toString()),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { phone ->
                    if (phone !in phones) {
                        phones.add(phone)
                    }
                }
            }
        }

        return phones
    }

    private data class ContactInfo(
        val displayName: String,
        val photoUri: String?,
        val lookupKey: String
    )
}
