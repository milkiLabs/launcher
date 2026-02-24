/**
 * ContactsRepository.kt - Domain interface for contacts data access
 *
 * This interface defines the contract for accessing contacts data.
 *
 * The implementation is in data/repository/ContactsRepositoryImpl.kt
 */

package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.Contact
import kotlinx.coroutines.flow.Flow

/**
 * Interface for accessing device contacts.
 *
 * Implementations should handle:
 * - Permission checking
 * - Database queries via ContentResolver
 * - Data mapping to domain models
 * - Recent contacts storage (for quick access to frequently called contacts)
 */
interface ContactsRepository {
    /**
     * Check if the app has permission to read contacts.
     *
     * @return True if READ_CONTACTS permission is granted
     */
    fun hasContactsPermission(): Boolean

    /**
     * Search contacts by name, phone, or email.
     *
     * @param query The search string
     * @return List of matching contacts
     * @throws SecurityException if permission is not granted
     */
    suspend fun searchContacts(query: String): List<Contact>

    /**
     * Save a phone number to recent contacts.
     *
     * This is called after making a call (either direct or via dialer).
     * The phone number is stored in a local cache (DataStore), not in
     * the system contacts database.
     *
     * Recent contacts are shown when the user types "c " with no query,
     * similar to how recent apps are shown on empty search.
     *
     * @param phoneNumber The phone number to save
     */
    suspend fun saveRecentContact(phoneNumber: String)

    /**
     * Get recent contacts as a Flow.
     *
     * Returns a list of phone numbers that were recently called,
     * ordered by most recent first. Max 8 items.
     *
     * The caller is responsible for looking up contact names
     * using getContactByPhoneNumber() if needed.
     *
     * @return Flow emitting list of recent phone numbers
     */
    fun getRecentContacts(): Flow<List<String>>

    /**
     * Get a contact by phone number.
     *
     * This is used to look up contact names for recent contacts.
     * Returns null if no contact matches the phone number.
     *
     * @param phoneNumber The phone number to look up
     * @return Contact if found, null otherwise
     */
    suspend fun getContactByPhoneNumber(phoneNumber: String): Contact?
}
