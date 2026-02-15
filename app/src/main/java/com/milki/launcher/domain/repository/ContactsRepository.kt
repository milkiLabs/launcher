/**
 * ContactsRepository.kt - Domain interface for contacts data access
 *
 * This interface defines the contract for accessing contacts data.
 * It follows the Dependency Inversion Principle - the domain layer
 * defines what it needs, and the data layer provides implementations.
 *
 * WHY AN INTERFACE?
 * - Allows different implementations (real, mock for testing, etc.)
 * - Decouples domain from Android-specific APIs
 * - Makes unit testing possible without Android framework
 *
 * The implementation is in data/repository/ContactsRepositoryImpl.kt
 */

package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.Contact

/**
 * Interface for accessing device contacts.
 *
 * Implementations should handle:
 * - Permission checking
 * - Database queries via ContentResolver
 * - Data mapping to domain models
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
}
