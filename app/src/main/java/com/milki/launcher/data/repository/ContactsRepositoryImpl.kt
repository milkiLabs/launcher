/**
 * ContactsRepositoryImpl.kt - Implementation of ContactsRepository
 *
 * This file contains the actual implementation that queries Android's
 * Contacts Provider using ContentResolver. It was moved from the old
 * ContactsRepository.kt file to separate interface from implementation.
 *
 * ARCHITECTURE NOTE:
 * The domain layer defines the ContactsRepository interface.
 * This data layer implements it with Android-specific code.
 * This follows the Dependency Inversion Principle.
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
import com.milki.launcher.domain.repository.ContactsRepository

/**
 * Implementation of ContactsRepository using Android ContentResolver.
 *
 * This class handles all the low-level details of querying the contacts
 * database, including:
 * - Permission checking
 * - Cursor management
 * - Data extraction and mapping
 *
 * @property context Application context for accessing ContentResolver
 */
class ContactsRepositoryImpl(
    private val context: Context
) : ContactsRepository {

    /**
     * ContentResolver instance for querying contacts.
     * ContentResolver is the Android API for accessing content providers.
     */
    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Checks if the app has permission to read contacts.
     *
     * Uses ContextCompat.checkSelfPermission() which works on all API levels.
     *
     * @return Boolean indicating if READ_CONTACTS permission is granted
     */
    override fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Searches contacts by query string.
     *
     * Searches in contact display names.
     * Returns contacts where the name contains the query.
     *
     * @param query The search query string
     * @return List of matching contacts
     * @throws SecurityException if permission is not granted
     */
    override suspend fun searchContacts(query: String): List<Contact> {
        if (!hasContactsPermission()) {
            throw SecurityException("READ_CONTACTS permission not granted")
        }

        if (query.isBlank()) {
            return emptyList()
        }

        val contacts = mutableListOf<Contact>()
        val queryLower = query.lowercase()

        val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val selectionArgs = arrayOf("%$queryLower%")

        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            CONTACT_PROJECTION,
            selection,
            selectionArgs,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
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

                val phoneNumbers = if (hasPhoneNumber) {
                    getPhoneNumbers(contactId)
                } else {
                    emptyList()
                }

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
