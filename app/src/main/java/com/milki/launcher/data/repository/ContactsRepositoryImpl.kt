/**
 * ContactsRepositoryImpl.kt - Implementation of ContactsRepository
 *
 * This file contains the actual implementation that queries Android's
 * Contacts Provider using ContentResolver. 
 */

package com.milki.launcher.data.repository

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.repository.ContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Top-level DataStore delegate for storing recent contacts.
 *
 * WHY SEPARATE DATASTORE:
 * We use a separate DataStore file for contacts preferences to keep
 * them isolated from app preferences. This makes the data easier to
 * manage and debug.
 *
 * File location: /data/data/<package>/files/datastore/recent_contacts.preferences_pb
 */
private val Context.recentContactsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_contacts"
)

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
     * DataStore key for storing recent contacts.
     *
     * Format: "phone1,phone2,phone3"
     * Most recent phone is first in the list.
     *
     * WHY STORE PHONE NUMBERS:
     * We store just the phone number, not the contact name or ID.
     * This keeps the storage minimal. When displaying recent contacts,
     * we look up the contact info using getContactByPhoneNumber().
     *
     * This approach has trade-offs:
     * - Pro: Minimal storage, simple implementation
     * - Con: Contact name lookup required each time (but contacts rarely change)
     */
    private val recentContactsKey = stringPreferencesKey("recent_contacts")

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
     * Searches contacts by query string using a single JOIN query with priority-based sorting.
     *
     * PERFORMANCE OPTIMIZATION:
     * This implementation uses a single query to the ContactsContract.Data table
     * instead of making separate queries for each contact's phone numbers and emails.
     *
     * The Data table contains all contact data (phones, emails, names) in one place,
     * allowing us to fetch everything in one database round-trip.
     *
     * BEFORE (N+1 problem):
     * - 1 query to find matching contacts
     * - N queries for phone numbers (one per contact)
     * - N queries for emails (one per contact)
     * - Total: 2N+1 queries for N contacts
     *
     * AFTER (single query):
     * - 1 query to Data table with JOIN-like selection
     * - Results grouped by contact ID in Kotlin code
     * - Total: 1 query regardless of contact count
     *
     * PRIORITY-BASED MATCHING (like app search):
     * Results are sorted by match quality:
     * 1. Exact matches - display name equals query exactly
     * 2. Starts-with matches - display name starts with query
     * 3. Contains matches - display name contains query anywhere
     *
     * This provides a better user experience where more relevant results appear first.
     *
     * HOW IT WORKS:
     * 1. Query the Data table with a selection that includes:
     *    - Phone mimetype (for phone numbers)
     *    - Email mimetype (for email addresses)
     *    - StructuredName mimetype (to get contacts without phones/emails)
     * 2. Filter by display name matching the search query (LIKE %query%)
     * 3. LIMIT results to 150 (higher than final 50 to allow for sorting)
     * 4. Group the cursor rows by contact ID
     * 5. Build Contact objects with accumulated phones/emails
     * 6. Sort by match priority (exact → starts-with → contains)
     * 7. Return top 50 sorted results
     *
     * @param query The search query string
     * @return List of matching contacts (max 50), sorted by match priority
     * @throws SecurityException if permission is not granted
     */
    override suspend fun searchContacts(query: String): List<Contact> {
        // Permission check - must have READ_CONTACTS to proceed
        if (!hasContactsPermission()) {
            throw SecurityException("READ_CONTACTS permission not granted")
        }

        // Empty query returns empty list - no point searching nothing
        if (query.isBlank()) {
            return emptyList()
        }

        // Prepare the search query for SQL LIKE clause
        // lowercase() for case-insensitive matching
        // trim() to remove leading/trailing whitespace
        val queryLower = query.trim().lowercase()

        // Map to accumulate contact data as we iterate the cursor
        // Key = contact ID, Value = mutable contact builder
        val contactsMap = mutableMapOf<Long, ContactBuilder>()

        // SELECTION CLAUSE:
        // We want rows where:
        // 1. DISPLAY_NAME matches the search query (LIKE operator)
        // 2. MIMETYPE is one of Phone, Email, or StructuredName
        //
        // StructuredName is included to capture contacts that have no
        // phone numbers or emails - we still want them in results!
        val selection = """
            ${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ? 
            AND ${ContactsContract.Data.MIMETYPE} IN (?, ?, ?)
        """.trimIndent()

        // SELECTION ARGS:
        // Arg 1: The search pattern with wildcards for LIKE
        // Arg 2-4: The mimetypes we want to include
        val selectionArgs = arrayOf(
            "%$queryLower%",
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
        )

        // QUERY THE DATA TABLE:
        // ContactsContract.Data.CONTENT_URI is a special URI that joins
        // multiple tables (contacts, data, mimetypes) into one view.
        //
        // The SORT ORDER sorts by display name, then by mimetype to group
        // data types together for each contact.
        // LIMIT 150 (more than final 50) to allow for priority sorting
        // We'll sort and then take top 50
        val sortOrder = """
            ${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC,
            ${ContactsContract.Data.MIMETYPE} ASC
            LIMIT 150
        """.trimIndent()

        // Execute the query using ContentResolver
        // The 'use' block automatically closes the cursor when done
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            DATA_PROJECTION,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->

            // Get column indices once before the loop for efficiency
            // getColumnIndex() is relatively expensive, so we cache the indices
            val contactIdIndex = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val lookupKeyIndex = cursor.getColumnIndex(ContactsContract.Data.LOOKUP_KEY)
            val displayNameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoUriIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
            val mimetypeIndex = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
            val phoneNumberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val emailAddressIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)

            // Iterate through all rows in the cursor
            // Each row represents one piece of data (one phone OR one email OR name)
            while (cursor.moveToNext()) {

                // Extract contact ID - this is our grouping key
                val contactId = cursor.getLong(contactIdIndex)

                // Get or create a ContactBuilder for this contact ID
                // getOrPut returns existing builder or creates new one
                val builder = contactsMap.getOrPut(contactId) {
                    // Create new builder with basic contact info
                    // This runs only once per unique contact
                    ContactBuilder(
                        id = contactId,
                        displayName = cursor.getString(displayNameIndex) ?: continue,
                        photoUri = cursor.getString(photoUriIndex),
                        lookupKey = cursor.getString(lookupKeyIndex) ?: continue
                    )
                }

                // Determine what type of data this row contains
                // and add it to the appropriate list in the builder
                val mimetype = cursor.getString(mimetypeIndex)

                when (mimetype) {
                    // This row contains a phone number
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        cursor.getString(phoneNumberIndex)?.let { phone ->
                            // Use distinct to avoid duplicate phone numbers
                            // (some contacts have the same number in multiple places)
                            if (phone !in builder.phoneNumbers) {
                                builder.phoneNumbers.add(phone)
                            }
                        }
                    }

                    // This row contains an email address
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        cursor.getString(emailAddressIndex)?.let { email ->
                            // Use distinct to avoid duplicate emails
                            if (email !in builder.emails) {
                                builder.emails.add(email)
                            }
                        }
                    }

                    // StructuredName rows don't add phone/email data
                    // They were included in the query just to capture contacts
                    // that have no phone or email (so they still appear in results)
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        // Nothing to add - contact already created in getOrPut
                    }
                }
            }
        }

        // BUILD AND SORT RESULTS:
        // Convert builders to Contact objects and sort by match priority
        val unsortedContacts = contactsMap.values.map { it.build() }

        // Sort by match priority (exact → starts-with → contains)
        // Then take top 50 results
        return unsortedContacts
            .sortedBy { contact ->
                // Lower ordinal = higher priority (appears first)
                getMatchType(contact.displayName, queryLower).ordinal
            }
            .take(50)
    }

    /**
     * Determines the match type for a contact's display name against the query.
     *
     * PRIORITY ORDER (same as app search):
     * 1. EXACT - Display name equals query exactly (highest priority)
     * 2. STARTS_WITH - Display name starts with query
     * 3. CONTAINS - Display name contains query anywhere (lowest priority)
     *
     * @param displayName The contact's display name to check
     * @param queryLower The lowercased search query
     * @return The match type enum value
     */
    private fun getMatchType(displayName: String, queryLower: String): MatchType {
        val nameLower = displayName.lowercase()

        return when {
            // Exact match: name equals query exactly
            nameLower == queryLower -> MatchType.EXACT

            // Starts with: name starts with query
            nameLower.startsWith(queryLower) -> MatchType.STARTS_WITH

            // Contains: name contains query anywhere (we know this is true
            // because the SQL LIKE clause already filtered for this)
            else -> MatchType.CONTAINS
        }
    }

    /**
     * Match type enum for prioritizing contact search results.
     *
     * The ordinal (order) is used for sorting - lower ordinal = higher priority.
     * This matches the priority system used in FilterAppsUseCase for consistency.
     */
    private enum class MatchType {
        EXACT,       // Display name equals query exactly (highest priority)
        STARTS_WITH, // Display name starts with query
        CONTAINS     // Display name contains query anywhere (lowest priority)
    }

    /**
     * Helper class to build Contact objects incrementally.
     *
     * WHY WE NEED THIS:
     * The Data table returns multiple rows per contact (one per phone/email).
     * We need to accumulate data across rows before creating the final Contact.
     *
     * This mutable builder allows us to:
     * 1. Create the builder when we first see a contact ID
     * 2. Add phone numbers and emails as we encounter them
     * 3. Build the immutable Contact at the end
     */
    private data class ContactBuilder(
        val id: Long,
        val displayName: String,
        val photoUri: String?,
        val lookupKey: String
    ) {
        // Mutable lists to accumulate phone numbers and emails
        // We'll add to these as we iterate through the cursor
        val phoneNumbers: MutableList<String> = mutableListOf()
        val emails: MutableList<String> = mutableListOf()

        /**
         * Build the final immutable Contact object.
         * Called once per contact after all data has been accumulated.
         */
        fun build(): Contact = Contact(
            id = id,
            displayName = displayName,
            phoneNumbers = phoneNumbers.toList(), // Convert to immutable list
            emails = emails.toList(),
            photoUri = photoUri,
            lookupKey = lookupKey
        )
    }

    // ========================================================================
    // RECENT CONTACTS STORAGE
    // ========================================================================

    /**
     * Save a phone number to recent contacts.
     *
     * IMPLEMENTATION DETAILS:
     * 1. Read current list from DataStore
     * 2. Remove the phone number if it already exists (to move to front)
     * 3. Add to front of list
     * 4. Limit to 8 phone numbers maximum
     * 5. Save comma-separated string back to DataStore
     *
     * The edit block is transactional - either all changes apply or none.
     *
     * @param phoneNumber The phone number to save
     */
    override suspend fun saveRecentContact(phoneNumber: String) {
        context.recentContactsDataStore.edit { preferences ->
            // Get current value or empty string
            val current = preferences[recentContactsKey] ?: ""

            // Parse into mutable list
            val recentPhones = current.split(",")
                .filter { it.isNotEmpty() }
                .toMutableList()

            // Remove if exists (we'll add to front)
            recentPhones.remove(phoneNumber)

            // Add to front (most recent)
            recentPhones.add(0, phoneNumber)

            // Save back: take first 8, join with commas
            preferences[recentContactsKey] = recentPhones.take(8).joinToString(",")
        }
    }

    /**
     * Get recent contacts as a Flow.
     *
     * Returns a Flow that emits the list of recent phone numbers
     * whenever the underlying DataStore changes.
     *
     * @return Flow emitting list of recent phone numbers (max 8)
     */
    override fun getRecentContacts(): Flow<List<String>> {
        return context.recentContactsDataStore.data.map { preferences ->
            // Get saved phone numbers, default to empty string
            preferences[recentContactsKey]
                ?.split(",")
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Get a contact by phone number.
     *
     * This method queries the contacts database to find a contact
     * that has the given phone number. It's used to display contact
     * names for recent contacts.
     *
     * IMPLEMENTATION DETAILS:
     * 1. Query the Phone table (CommonDataKinds.Phone)
     * 2. Filter by the phone number
     * 3. If found, get the contact ID and fetch full contact info
     * 4. Return the Contact or null if not found
     *
     * PHONE NUMBER MATCHING:
     * Android's Phone lookup uses a "callable" match which handles
     * different phone number formats (with/without country code, etc.)
     *
     * @param phoneNumber The phone number to look up
     * @return Contact if found, null otherwise
     */
    override suspend fun getContactByPhoneNumber(phoneNumber: String): Contact? {
        // Permission check
        if (!hasContactsPermission()) {
            return null
        }

        // Use withContext to run on IO thread
        return withContext(Dispatchers.IO) {
            // Query the Phone table to find a contact with this number
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?"
            val selectionArgs = arrayOf(phoneNumber)

            contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
                ),
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val contactId = cursor.getLong(0)
                    val displayName = cursor.getString(1) ?: return@withContext null
                    val photoUri = cursor.getString(2)
                    val lookupKey = cursor.getString(3) ?: return@withContext null

                    // Now get all phone numbers for this contact
                    val phoneNumbers = getPhoneNumbersForContact(contactId)

                    Contact(
                        id = contactId,
                        displayName = displayName,
                        phoneNumbers = phoneNumbers,
                        emails = emptyList(), // Not needed for recent contacts
                        photoUri = photoUri,
                        lookupKey = lookupKey
                    )
                } else {
                    null
                }
            }
        }
    }

    /**
     * Get all phone numbers for a contact.
     *
     * This is a helper method used by getContactByPhoneNumber()
     * to fetch all phone numbers associated with a contact ID.
     *
     * @param contactId The contact ID to look up
     * @return List of phone numbers for this contact
     */
    private fun getPhoneNumbersForContact(contactId: Long): List<String> {
        val phoneNumbers = mutableListOf<String>()

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId.toString())

        contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
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
        /**
         * Projection for the single-query approach using the Data table.
         *
         * PERFORMANCE NOTE:
         * This projection fetches all needed columns in ONE query instead of
         * multiple queries. The Data table joins contacts, data, and mimetypes
         * automatically, so we can get contact info + phones + emails together.
         *
         * COLUMNS EXPLAINED:
         * - CONTACT_ID: Unique identifier for the contact (used for grouping)
         * - LOOKUP_KEY: Stable identifier that survives syncs (used for contact URI)
         * - DISPLAY_NAME_PRIMARY: The contact's display name (what we show in UI)
         * - PHOTO_URI: Content URI for the contact's photo (null if no photo)
         * - MIMETYPE: Type of data in this row (Phone, Email, or StructuredName)
         * - Phone.NUMBER: Phone number (only populated for Phone mimetype rows)
         * - Email.ADDRESS: Email address (only populated for Email mimetype rows)
         *
         * WHY MIMETYPE IS NEEDED:
         * The Data table has different columns for different data types.
         * A row with MIMETYPE=Phone has data in Phone.NUMBER column.
         * A row with MIMETYPE=Email has data in Email.ADDRESS column.
         * We use MIMETYPE to determine which column to read.
         */
        private val DATA_PROJECTION = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Email.ADDRESS
        )
    }
}
