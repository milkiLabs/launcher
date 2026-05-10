package com.milki.launcher.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.repository.ContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Contacts repository implementation composed from dedicated layers.
 *
 * ARCHITECTURE SPLIT:
 * - Query layer: ContactsQueryLayer (ContentResolver and cursor traversal)
 * - Mapping layer: ContactsMappingLayer (domain conversion + relevance ordering)
 * - Recent storage: ContactsRecentStorage (DataStore persistence)
 *
 * This facade keeps domain-facing behavior stable while reducing cognitive load
 * in each file for educational readability.
 */
class ContactsRepositoryImpl(
    private val context: Context
) : ContactsRepository {

    /**
     * Dedicated mapper used by query layer to convert raw data to domain models.
     */
    private val mappingLayer = ContactsMappingLayer()

    /**
     * Dedicated query collaborator that encapsulates all ContactsContract calls.
     */
    private val queryLayer = ContactsQueryLayer(
        contentResolver = context.contentResolver,
        mapper = mappingLayer
    )

    /**
     * Dedicated persistence collaborator for recent-contact DataStore behavior.
     */
    private val recentStorage = ContactsRecentStorage(context)

    /**
     * Permission gate for all contacts operations.
     */
    override fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Searches contacts by display name with relevance sorting.
     */
    override suspend fun searchContacts(query: String, maxItems: Int): List<Contact> {
        if (!hasContactsPermission()) {
            throw SecurityException("READ_CONTACTS permission not granted")
        }

        if (query.isBlank()) {
            return emptyList()
        }

        val queryLower = query.trim().lowercase()

        return withContext(Dispatchers.IO) {
            queryLayer.searchContacts(
                queryLower = queryLower,
                maxItems = maxItems
            )
        }
    }

    /**
     * Stores one phone number in recent-contact history.
     */
    override suspend fun saveRecentContact(phoneNumber: String) {
        recentStorage.saveRecentContact(phoneNumber)
    }

    /**
     * Exposes recent-contact phone numbers as a Flow.
     */
    override fun getRecentContacts(): Flow<List<String>> {
        return recentStorage.getRecentContacts().flowOn(Dispatchers.IO)
    }

    /**
     * Resolves one contact by exact phone number.
     */
    override suspend fun getContactByPhoneNumber(phoneNumber: String): Contact? {
        if (!hasContactsPermission()) {
            return null
        }

        return withContext(Dispatchers.IO) {
            queryLayer.getContactByPhoneNumber(phoneNumber)
        }
    }

    /**
     * Resolves multiple contacts by phone numbers in one batch query.
     */
    override suspend fun getContactsByPhoneNumbers(phoneNumbers: List<String>): Map<String, Contact> {
        if (!hasContactsPermission() || phoneNumbers.isEmpty()) {
            return emptyMap()
        }

        return withContext(Dispatchers.IO) {
            queryLayer.getContactsByPhoneNumbers(phoneNumbers)
        }
    }
}
