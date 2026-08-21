package com.milki.launcher.data.repository.contacts

import android.content.Context
import android.provider.ContactsContract
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.milki.launcher.core.permission.PermissionChecker
import com.milki.launcher.data.repository.common.AbstractContentResolverRecentStore
import com.milki.launcher.data.repository.common.RecentListStorage
import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.repository.ContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

private val Context.contactsRecentDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_contacts"
)

class ContactsRepositoryImpl(
    context: Context
) : AbstractContentResolverRecentStore<String>(context), ContactsRepository {

    override val recentStore: RecentListStorage<String> = RecentListStorage(
        dataStore = appContext.contactsRecentDataStore,
        key = stringPreferencesKey("recent_contacts"),
        maxSize = 8,
        encoder = { phoneNumber -> phoneNumber },
        decoder = { raw -> raw }
    )

    override fun hasContactsPermission(): Boolean = PermissionChecker.hasContactsPermission(appContext)

    override fun hasPermission(): Boolean = hasContactsPermission()

    override suspend fun saveRecentContact(phoneNumber: String) {
        saveRecent(phoneNumber)
    }

    override fun getRecentContacts(): Flow<List<String>> {
        return observeRecent().flowOn(Dispatchers.IO)
    }

    override suspend fun searchContacts(query: String, maxItems: Int): List<Contact> {
        if (query.isBlank()) {
            return emptyList()
        }
        val queryLower = query.trim().lowercase()

        return withPermissionOr(
            whenGranted = { queryContactsByName(queryLower, maxItems) },
            whenDenied = { emptyList() }
        ) ?: emptyList()
    }

    override suspend fun getContactsByPhoneNumbers(phoneNumbers: List<String>): Map<String, Contact> {
        if (phoneNumbers.isEmpty()) {
            return emptyMap()
        }

        return withPermissionOr(
            whenGranted = { queryContactsByPhoneNumbers(phoneNumbers) },
            whenDenied = { emptyMap() }
        ) ?: emptyMap()
    }

    private suspend fun queryContactsByName(queryLower: String, maxItems: Int): List<Contact> {
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

        return contactIds.map { id ->
            val info = contactInfo[id]!!
            Contact(
                id = id,
                displayName = info.displayName,
                phoneNumbers = phonesByContactId[id] ?: emptyList(),
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

    private suspend fun queryPhonesForContacts(contactIds: List<Long>): Map<Long, List<String>> {
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

    private suspend fun queryContactsByPhoneNumbers(phoneNumbers: List<String>): Map<String, Contact> {
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

    private suspend fun queryPhonesForContact(contactId: Long): List<String> {
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
