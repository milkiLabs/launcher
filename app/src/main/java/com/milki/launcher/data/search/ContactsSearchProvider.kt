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
 * - NOT responsible for actually making calls (that's SearchAction)
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

/**
 * Search provider for device contacts.
 *
 * This provider has special behavior:
 * - Checks permission status before searching
 * - Returns permission request placeholder if permission not granted
 * - Searches contacts by name when permission is granted
 *
 * @property contactsRepository Repository for accessing contacts data
 * @property config Display configuration for this provider
 */
class ContactsSearchProvider(
    private val contactsRepository: ContactsRepository
) : SearchProvider {

    override val config: SearchProviderConfig = SearchProviderConfig(
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
     * 3. If granted and query is blank, return empty
     * 4. If granted and query exists, search contacts
     *
     * @param query The search query (without the "c " prefix)
     * @return List of ContactSearchResult or PermissionRequestResult
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

        // Permission granted - perform actual search
        if (query.isBlank()) {
            // Show hint for contacts mode (no query yet)
            return listOf(
                ContactSearchResult(
                    contact = Contact(
                        id = -1,
                        displayName = "Type to search contacts",
                        phoneNumbers = emptyList(),
                        emails = emptyList(),
                        photoUri = null,
                        lookupKey = "hint"
                    )
                )
            )
        }

        // Search contacts
        val contacts = contactsRepository.searchContacts(query)

        return if (contacts.isEmpty()) {
            // No contacts found
            listOf(
                ContactSearchResult(
                    contact = Contact(
                        id = -1,
                        displayName = "No contacts found for \"$query\"",
                        phoneNumbers = emptyList(),
                        emails = emptyList(),
                        photoUri = null,
                        lookupKey = "empty"
                    )
                )
            )
        } else {
            // Return found contacts
            contacts.map { contact ->
                ContactSearchResult(contact = contact)
            }
        }
    }
}
