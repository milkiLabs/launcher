package com.milki.launcher.data.repository.contacts

import android.content.Context
import android.provider.ContactsContract
import androidx.datastore.preferences.core.stringPreferencesKey
import com.milki.launcher.core.content.forEachRow
import com.milki.launcher.core.content.sqlInSelection
import com.milki.launcher.core.permission.PermissionChecker
import com.milki.launcher.data.repository.common.AbstractContentResolverRecentStore
import com.milki.launcher.data.repository.common.RecentListStorage
import com.milki.launcher.data.repository.common.contactsRecentDataStore
import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.repository.ContactsRepository
import kotlinx.coroutines.flow.Flow

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
        return observeRecent()
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
        // No SQL LIMIT: SQLite's ASCII-only LIKE ordering could evict better
        // matches; rank fully in memory and cap afterwards instead.
        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"

        contentResolver.forEachRow(
            uri = ContactsContract.Contacts.CONTENT_URI,
            projection = contactsProjection,
            selection = selection,
            selectionArgs = selectionArgs,
            sortOrder = sortOrder
        ) { cursor ->
            val id = cursor.getLong(0)
            val displayName = cursor.getString(1)
            val photoUri = cursor.getString(2)
            val lookupKey = cursor.getString(3)

            if (displayName != null && lookupKey != null) {
                contactIds.add(id)
                contactInfo[id] = ContactInfo(displayName, photoUri, lookupKey)
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
        }.take(maxItems)
    }

    private fun queryPhonesForContacts(contactIds: List<Long>): Map<Long, List<String>> {
        val selection = sqlInSelection(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, contactIds)
            ?: return emptyMap()

        val result = mutableMapOf<Long, MutableList<String>>()
        contentResolver.forEachRow(
            uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            selection = selection.first,
            selectionArgs = selection.second
        ) { cursor ->
            val number = cursor.getString(1) ?: return@forEachRow
            result.getOrPut(cursor.getLong(0)) { mutableListOf() }.add(number)
        }

        return result
    }

    private suspend fun queryContactsByPhoneNumbers(phoneNumbers: List<String>): Map<String, Contact> {
        // Stored NUMBER values keep their display formatting ("+962 79 ...",
        // "(021) ..."), so exact-string matching misses. Compare on normalized
        // forms instead; PHONE_NORMALIZED_NUMBER is preferred when populated,
        // with our own formatting-stripped form as fallback.
        val requestedByNormalized = mutableMapOf<String, MutableList<String>>()
        for (phone in phoneNumbers) {
            requestedByNormalized.getOrPut(normalizePhoneNumber(phone)) {
                mutableListOf()
            }.add(phone)
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
        )

        val contactIdByNormalized = mutableMapOf<String, Long>()
        val infoByContactId = mutableMapOf<Long, ContactInfo>()

        contentResolver.forEachRow(
            uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection = projection
        ) { cursor ->
            val raw = cursor.getString(1) ?: cursor.getString(2) ?: return@forEachRow
            val normalized = normalizePhoneNumber(raw)
            if (normalized !in requestedByNormalized) return@forEachRow

            val contactId = cursor.getLong(0)
            contactIdByNormalized.putIfAbsent(normalized, contactId)
            // The first Phone row seen for a contact may have a null display
            // name or lookup key; keep scanning later rows until one carries
            // the info, otherwise the whole contact would be dropped.
            if (contactId !in infoByContactId &&
                cursor.getString(3) != null && cursor.getString(5) != null
            ) {
                infoByContactId[contactId] = ContactInfo(
                    displayName = cursor.getString(3),
                    photoUri = cursor.getString(4),
                    lookupKey = cursor.getString(5)
                )
            }
        }
        if (infoByContactId.isEmpty()) {
            return emptyMap()
        }

        val phonesByContactId = queryPhonesForContacts(infoByContactId.keys.toList())

        val contactByPhone = mutableMapOf<String, Contact>()
        for ((normalized, contactId) in contactIdByNormalized) {
            val info = infoByContactId[contactId] ?: continue
            val contact = Contact(
                id = contactId,
                displayName = info.displayName,
                phoneNumbers = phonesByContactId[contactId].orEmpty().distinct(),
                photoUri = info.photoUri,
                lookupKey = info.lookupKey
            )
            for (requested in requestedByNormalized.getValue(normalized)) {
                contactByPhone[requested] = contact
            }
        }
        return contactByPhone
    }

    private fun normalizePhoneNumber(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        return if (raw.trimStart().startsWith("+")) "+$digits" else digits
    }

    private data class ContactInfo(
        val displayName: String,
        val photoUri: String?,
        val lookupKey: String
    )
}
