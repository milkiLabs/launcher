/**
 * Contact.kt - Domain model representing a device contact
 * 
 * This file defines the Contact data class which represents a single contact
 * from the device's contacts database. It includes contact details like
 * name, phone numbers, and email addresses.
 * 
 * Used by the contacts search feature to display and interact with contacts.
 */

package com.milki.launcher.domain.model

/**
 * Represents a single contact from the device.
 * 
 * @property id Unique contact ID from the Contacts Provider
 * @property displayName The primary display name of the contact
 * @property phoneNumbers List of phone numbers associated with this contact
 * @property emails List of email addresses associated with this contact
 * @property photoUri Optional URI to the contact's photo (null if no photo)
 * @property lookupKey Lookup key for retrieving the contact later
 * 
 * Example:
 * ```kotlin
 * val contact = Contact(
 *     id = 123,
 *     displayName = "John Doe",
 *     phoneNumbers = listOf("+1 555-1234"),
 *     emails = listOf("john@example.com"),
 *     lookupKey = "1234i567..."
 * )
 * ```
 */
data class Contact(
    /**
     * Unique contact ID from the Contacts Provider (_ID column).
     * This ID can change if the contact is synced or modified.
     */
    val id: Long,
    
    /**
     * The display name of the contact (DISPLAY_NAME_PRIMARY).
     * This is the name shown in the contacts app.
     */
    val displayName: String,
    
    /**
     * List of all phone numbers associated with this contact.
     * Retrieved from the Phone content URI.
     * Can be empty if contact has no phone numbers.
     */
    val phoneNumbers: List<String>,
    
    /**
     * List of all email addresses associated with this contact.
     * Retrieved from the Email content URI.
     * Can be empty if contact has no emails.
     */
    val emails: List<String>,
    
    /**
     * Optional URI to the contact's photo.
     * This is a content:// URI that can be used to load the photo.
     * Null if the contact has no photo.
     */
    val photoUri: String?,
    
    /**
     * Lookup key for this contact.
     * The lookup key is more stable than the ID and survives syncs.
     * Used to retrieve the contact later or create contact URIs.
     */
    val lookupKey: String
)

/**
 * Extension function to check if a contact matches a search query.
 * Searches in both display name and phone numbers.
 * 
 * @param query The search query
 * @return True if the contact matches the query
 */
fun Contact.matchesQuery(query: String): Boolean {
    val queryLower = query.lowercase()
    
    // Check display name
    if (displayName.lowercase().contains(queryLower)) {
        return true
    }
    
    // Check phone numbers
    if (phoneNumbers.any { it.contains(queryLower) }) {
        return true
    }
    
    // Check emails
    if (emails.any { it.lowercase().contains(queryLower) }) {
        return true
    }
    
    return false
}

/**
 * Extension function to get the primary phone number.
 * Returns the first phone number, or null if none exist.
 */
fun Contact.primaryPhoneNumber(): String? = phoneNumbers.firstOrNull()

/**
 * Extension function to get the primary email.
 * Returns the first email, or null if none exist.
 */
fun Contact.primaryEmail(): String? = emails.firstOrNull()
