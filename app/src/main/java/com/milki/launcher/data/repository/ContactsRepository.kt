/**
 * ContactsRepository.kt - Repository for querying device contacts
 * 
 * This repository handles all interactions with the Android Contacts Provider.
 * It provides methods to search contacts and check permissions.
 * 
 * The repository uses ContentResolver to query the contacts database,
 * which requires the READ_CONTACTS permission.
 */

package com.milki.launcher.data.repository

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.milki.launcher.domain.model.Contact

/**
 * Repository for accessing device contacts.
 * 
 * This class encapsulates all contacts-related operations including:
 * - Checking if contacts permission is granted
 * - Searching contacts by name or phone number
 * - Querying contact details
 * 
 * @property context The application context used to access ContentResolver
 * 
 * Example usage:
 * ```kotlin
 * val repository = ContactsRepository(context)
 * 
 * if (repository.hasContactsPermission()) {
 *     val results = repository.searchContacts("john")
 * }
 * ```
 */
class ContactsRepository(
    private val context: Context
) {
    /**
     * ContentResolver instance for querying contacts.
     * ContentResolver is the Android API for accessing content providers.
     */
    private val contentResolver: ContentResolver = context.contentResolver
    
    // ========================================================================
    // PERMISSION CHECKING
    // ========================================================================
    
    /**
     * Checks if the app has permission to read contacts.
     * 
     * Uses ContextCompat.checkSelfPermission() which works on all API levels.
     * Returns true if permission is granted, false otherwise.
     * 
     * @return Boolean indicating if READ_CONTACTS permission is granted
     */
    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    // ========================================================================
    // CONTACT SEARCHING
    // ========================================================================
    
    /**
     * Searches contacts by query string.
     * 
     * Searches in contact display names and phone numbers.
     * Returns contacts where the name or phone contains the query.
     * 
     * Note: This method requires READ_CONTACTS permission to be granted.
     * Call hasContactsPermission() before calling this method.
     * 
     * @param query The search query string
     * @return List of matching contacts
     * @throws SecurityException if permission is not granted
     */
    fun searchContacts(query: String): List<Contact> {
        // Check permission first
        if (!hasContactsPermission()) {
            throw SecurityException("READ_CONTACTS permission not granted")
        }
        
        if (query.isBlank()) {
            return emptyList()
        }
        
        val contacts = mutableListOf<Contact>()
        val queryLower = query.lowercase()
        
        // Query to search contacts by display name
        // Using SELECTION to filter contacts server-side for efficiency
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val selectionArgs = arrayOf("%$queryLower%")
        
        // Query the contacts
        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            CONTACT_PROJECTION,
            selection,
            selectionArgs,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"  // Sort by name
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoUriIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
            val lookupKeyIndex = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
            val hasPhoneNumberIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
            
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idIndex)
                val displayName = cursor.getString(nameIndex) ?: continue
                val photoUri = cursor.getString(photoUriIndex)
                val lookupKey = cursor.getString(lookupKeyIndex) ?: continue
                val hasPhoneNumber = cursor.getInt(hasPhoneNumberIndex) > 0
                
                // Get phone numbers for this contact
                val phoneNumbers = if (hasPhoneNumber) {
                    getPhoneNumbers(contactId)
                } else {
                    emptyList()
                }
                
                // Get emails for this contact
                val emails = getEmails(contactId)
                
                contacts.add(
                    Contact(
                        id = contactId,
                        displayName = displayName,
                        phoneNumbers = phoneNumbers,
                        emails = emails,
                        photoUri = photoUri,
                        lookupKey = lookupKey
                    )
                )
            }
        }
        
        return contacts
    }
    
    /**
     * Gets all phone numbers for a specific contact.
     * 
     * Queries the Phone content URI with the contact ID.
     * 
     * @param contactId The contact ID to get phone numbers for
     * @return List of phone numbers
     */
    private fun getPhoneNumbers(contactId: Long): List<String> {
        val phoneNumbers = mutableListOf<String>()
        
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            PHONE_PROJECTION,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            
            while (cursor.moveToNext()) {
                cursor.getString(numberIndex)?.let { number ->
                    phoneNumbers.add(number)
                }
            }
        }
        
        return phoneNumbers
    }
    
    /**
     * Gets all email addresses for a specific contact.
     * 
     * Queries the Email content URI with the contact ID.
     * 
     * @param contactId The contact ID to get emails for
     * @return List of email addresses
     */
    private fun getEmails(contactId: Long): List<String> {
        val emails = mutableListOf<String>()
        
        contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            EMAIL_PROJECTION,
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { cursor ->
            val emailIndex = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Email.ADDRESS
            )
            
            while (cursor.moveToNext()) {
                cursor.getString(emailIndex)?.let { email ->
                    emails.add(email)
                }
            }
        }
        
        return emails
    }
    
    // ========================================================================
    // PROJECTIONS (Columns to query)
    // ========================================================================
    
    companion object {
        /**
         * Projection for main contacts query.
         * Only request columns we actually need for efficiency.
         */
        private val CONTACT_PROJECTION = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )
        
        /**
         * Projection for phone numbers query.
         */
        private val PHONE_PROJECTION = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        
        /**
         * Projection for email query.
         */
        private val EMAIL_PROJECTION = arrayOf(
            ContactsContract.CommonDataKinds.Email.ADDRESS
        )
    }
}

/**
 * Sealed class representing the result of a permission check.
 */
sealed class PermissionResult {
    /**
     * Permission is granted and operation can proceed.
     */
    object Granted : PermissionResult()
    
    /**
     * Permission is denied and should be requested.
     * @property rationale Optional rationale to show user before requesting
     */
    data class Denied(
        val shouldShowRationale: Boolean,
        val rationale: String = "Contacts permission is needed to search your contacts"
    ) : PermissionResult()
}
