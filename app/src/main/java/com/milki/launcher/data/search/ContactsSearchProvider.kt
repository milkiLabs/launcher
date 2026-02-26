/**
 * ContactsSearchProvider.kt - Search provider for device contacts
 *
 * This provider searches the device's contacts database when the user
 * uses the "c" prefix. It handles permission checking and returns
 * either contact results or a permission request placeholder.
 *
 * RESPONSIBILITIES:
 * - Define the "c" prefix configuration
 * - Check contacts permission status
 * - Return contact results or permission request placeholder
 * - NOT responsible for actually making calls (that's SearchResultAction)
 *
 * PERMISSION HANDLING:
 * If permission is not granted, returns a PermissionRequestResult
 * instead of actual contacts. The UI will show a permission request button.
 */

package com.milki.launcher.data.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import com.milki.launcher.domain.model.*
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.SearchProvider
import kotlinx.coroutines.flow.first

/**
 * Search provider for device contacts.
 *
 * This provider has special behavior:
 * - Checks permission status before searching
 * - Returns permission request placeholder if permission not granted
 * - When query is blank, shows recent contacts (like recent apps on empty search)
 * - Searches contacts by name when query exists
 *
 * @property contactsRepository Repository for accessing contacts data
 * @property config Display configuration for this provider
 */
class ContactsSearchProvider(
    private val contactsRepository: ContactsRepository
) : SearchProvider {

    override val config: SearchProviderConfig = SearchProviderConfig(
        providerId = ProviderId.CONTACTS,
        prefix = "c",
        name = "Contacts",
        description = "Search your contacts",
        color = androidx.compose.ui.graphics.Color(0xFF34A853), // Contacts Green
        icon = Icons.Default.Person
    )

    /**
     * Search contacts or return permission request.
     *
     * Flow:
     * 1. Check if contacts permission is granted
     * 2. If not granted, return PermissionRequestResult
     * 3. If granted and query is blank, return recent contacts
     * 4. If granted and query exists, search contacts by name
     *
     * RECENT CONTACTS BEHAVIOR:
     * When query is blank (user types "c " without a search term),
     * we show the 8 most recently called contacts. This is similar to
     * how recent apps are shown when the search query is empty.
     *
     * This provides quick access to frequently called contacts without
     * having to search for them.
     *
     * @param query The search query (without the "c " prefix)
     * @return List of ContactSearchResult or PermissionRequestResult, or empty list
     */
    override suspend fun search(query: String): List<SearchResult> {
        // Check permission first
        if (!contactsRepository.hasContactsPermission()) {
            // Permission not granted - return permission request placeholder
            return listOf(
                PermissionRequestResult(
                    permission = android.Manifest.permission.READ_CONTACTS,
                    providerPrefix = config.prefix,
                    message = "Contacts permission required to search contacts",
                    buttonText = "Grant Permission"
                )
            )
        }

        // Permission granted - check for empty query
        if (query.isBlank()) {
            // Empty query - show recent contacts
            return getRecentContactsResults()
        }

        // Search contacts and map to results
        // If no contacts found, returns empty list (UI handles empty state)
        val contacts = contactsRepository.searchContacts(query)
        return contacts.map { contact ->
            ContactSearchResult(contact = contact)
        }
    }

    /**
     * Get recent contacts as search results.
     *
     * This method fetches the list of recently called phone numbers
     * and looks up each one to get the contact information.
     *
     * PERFORMANCE OPTIMIZATION:
     * Uses batch lookup (getContactsByPhoneNumbers) instead of individual
     * lookups. This reduces N database queries to 1 query for N contacts.
     *
     * FALLBACK BEHAVIOR:
     * If a phone number is in recent contacts but no longer matches
     * a contact in the device's contacts database (e.g., contact was deleted),
     * we create a minimal Contact with just the phone number.
     *
     * @return List of ContactSearchResult for recent contacts (max 8)
     */
    private suspend fun getRecentContactsResults(): List<SearchResult> {
        // Get recent phone numbers from DataStore
        val recentPhones = contactsRepository.getRecentContacts().first()

        if (recentPhones.isEmpty()) {
            return emptyList()
        }

        /**
         * BATCH LOOKUP:
         * Query all phone numbers in a single database call.
         * This is significantly faster than N individual queries.
         *
         * The returned map only contains phone numbers that matched a contact.
         * Phone numbers without matching contacts will be handled in the fallback.
         */
        val contactsByPhone = contactsRepository.getContactsByPhoneNumbers(recentPhones)

        // Build results, using batch lookup results when available
        return recentPhones.map { phoneNumber ->
            // Check if we found a contact for this phone number
            val contact = contactsByPhone[phoneNumber]

            if (contact != null) {
                // Found a matching contact from batch lookup
                ContactSearchResult(contact = contact)
            } else {
                // No matching contact - create a minimal contact with just the phone number
                // This handles the case where a contact was deleted after being added to recent
                ContactSearchResult(
                    contact = Contact(
                        id = -1, // Invalid ID to indicate this is not a real contact
                        displayName = phoneNumber,
                        phoneNumbers = listOf(phoneNumber),
                        emails = emptyList(),
                        photoUri = null,
                        lookupKey = ""
                    )
                )
            }
        }
    }
}
