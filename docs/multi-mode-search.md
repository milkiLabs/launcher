# Multi-Mode Search

## Overview

The launcher supports multi-mode search using single-character prefixes. This allows you to search not just installed apps, but also the web, contacts, and YouTube directly from the search dialog.

## How to Use

### Basic Usage

1. **Tap anywhere on the home screen** to open the search dialog
2. **Type your search** with or without a prefix:
   - Without prefix: Searches installed apps
   - With prefix: Activates special search mode

### Prefix Shortcuts

| Prefix   | Mode           | Example           | What Happens                                   |
| -------- | -------------- | ----------------- | ---------------------------------------------- |
| _(none)_ | **Apps**       | `calculator`      | Searches installed apps                        |
| `s `     | **Web Search** | `s weather today` | Opens browser with search results              |
| `c `     | **Contacts**   | `c mom`           | Searches device contacts (requires permission) |
| `y `     | **YouTube**    | `y lofi music`    | Opens YouTube with search query                |

**Important:** The space after the prefix is required! `s` searches apps, `s ` activates web search.

## Visual Indicators

When you type a prefix followed by a space, the search dialog shows visual feedback:

- **Blue bar + icon**: Web Search mode active
- **Green bar + icon**: Contacts mode active
- **Red bar + icon**: YouTube mode active
- **No bar** (default): App search mode

The placeholder text also changes:

- "Search apps..." (default)
- "Search the web..." (s prefix)
- "Search contacts..." (c prefix)
- "Search YouTube..." (y prefix)

## YouTube Search Details

The YouTube search (`y `) has a smart fallback system:

1. **ReVanced YouTube** (`app.revanced.android.youtube`) - Preferred modded client
2. **Official YouTube** (`com.google.android.youtube`) - Standard YouTube app
3. **Browser** - Falls back to youtube.com website

This ensures that even if you have a modded YouTube client, it will be used first.

## Contacts Search with Permission Handling

### Permission Flow

The contacts search feature requires the `READ_CONTACTS` permission. Here's how it works:

1. **User types `c `** → Contacts mode activates (green bar appears)
2. **If permission NOT granted**:
   - Shows a card with warning icon
   - Displays message: "Contacts permission required to search contacts"
   - Shows "Grant Permission" button
   - User taps button → System permission dialog appears
3. **If permission granted**:
   - Contacts are searched from device database
   - Matching contacts appear in the list
   - Each contact shows name and primary phone number

### Contact Actions

When you tap a contact in the search results:

- **Primary action**: Initiates a phone call using `ACTION_DIAL`
- The system dialer opens with the phone number pre-filled
- User can then confirm and place the call

**Note**: Using `ACTION_DIAL` instead of `ACTION_CALL` to avoid needing the `CALL_PHONE` permission. This gives users control over whether to place the call.

## Architecture

### Components

```
MainActivity
    |
    +-- LauncherScreen
            |
            +-- AppSearchDialog
                    |
                    +-- SearchProvider (data class)
                    +-- SearchResult (sealed class)
                            +-- AppSearchResult
                            +-- WebSearchResult
                            +-- ContactSearchResult
                            +-- PermissionRequestResult
```

### Key Classes

#### SearchProvider

Located in: `domain/model/SearchProvider.kt`

Defines a searchable category with:

- **prefix**: Single character trigger (e.g., "s")
- **name**: Display name (e.g., "Web Search")
- **description**: Short description shown in UI
- **color**: Visual indicator color
- **icon**: Icon for the mode
- **search**: Lambda that performs the search

The contacts provider accepts additional parameters:

- `hasPermission`: Whether READ_CONTACTS is granted
- `onRequestPermission`: Callback to request permission
- `searchContacts`: Function to search contacts
- `onCallContact`: Callback when contact is tapped

#### SearchResult

Sealed class with four implementations:

- **AppSearchResult**: Wraps AppInfo for app results
- **WebSearchResult**: Web search results with URL and engine
- **ContactSearchResult**: Contact search results with Contact object
- **PermissionRequestResult**: Shows permission request button

#### Contact

Located in: `domain/model/Contact.kt`

Represents a device contact with:

- **id**: Contact ID from Android Contacts Provider
- **displayName**: Contact's display name
- **phoneNumbers**: List of phone numbers
- **emails**: List of email addresses
- **photoUri**: Optional photo URI
- **lookupKey**: Stable lookup key

#### ContactsRepository

Located in: `data/repository/ContactsRepository.kt`

Handles all contacts database interactions:

- `hasContactsPermission()`: Check if permission is granted
- `searchContacts(query)`: Search contacts by name/phone
- `getPhoneNumbers(contactId)`: Get phone numbers for a contact
- `getEmails(contactId)`: Get emails for a contact

### Permission Handling

The permission system uses the modern Activity Result API:

1. **MainActivity** registers a permission launcher in `onCreate()`
2. **Contacts mode activation** checks `hasContactsPermission` state
3. **If not granted**: Shows `PermissionRequestResult` with button
4. **Button click**: Calls `onRequestContactsPermission()`
5. **MainActivity** launches permission request dialog
6. **On result**: Updates `hasContactsPermission` state
7. **UI automatically updates** due to state change

### Parsing Logic

The `parseSearchQuery()` function:

```kotlin
// "s cats" → activates web search with query "cats"
// "s" → treats as app search (no space, so no prefix)
// "calculator" → app search
```

**Key rule:** Prefix must be followed by a space to activate. This prevents accidentally switching modes when searching for apps that start with 's', 'c', or 'y'.

## Extending with Custom Providers

### Adding a New Provider

Users can add custom search providers by modifying the `searchProviders` list in `AppSearchDialog.kt`:

```kotlin
val searchProviders = remember {
    listOf(
        SearchProviders.webProvider(onSearchWeb),
        SearchProviders.contactsProvider(
            hasPermission = hasContactsPermission,
            onRequestPermission = onRequestContactsPermission,
            searchContacts = searchContacts,
            onCallContact = onCallContact
        ),
        SearchProviders.youtubeProvider(onSearchYouTube),

        // Add your custom provider here
        SearchProvider(
            prefix = "w",
            name = "Wikipedia",
            description = "Search Wikipedia",
            color = Color.Black,
            icon = Icons.Default.Search,
            search = { query ->
                listOf(
                    WebSearchResult(
                        title = "Search \"$query\" on Wikipedia",
                        url = "https://en.wikipedia.org/wiki/Special:Search?search=$query",
                        engine = "Wikipedia",
                        onClick = { onSearchWeb(query, "wikipedia") }
                    )
                )
            }
        )
    )
}
```

### Adding Handler in MainActivity

1. Add a callback parameter to `LauncherScreen`
2. Implement the handler method in `MainActivity`
3. Route the search to appropriate app/browser

Example handler:

```kotlin
private fun performWikipediaSearch(query: String) {
    searchQuery = ""
    showSearch = false

    val url = "https://en.wikipedia.org/wiki/Special:Search?search=${Uri.encode(query)}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    }
}
```

## Implementation Details

### State Flow

1. User types in search field
2. `parseSearchQuery()` detects prefix (if any)
3. If provider found:
   - UI updates with provider's color/icon
   - Provider's `search()` lambda called
   - Results displayed
4. If no provider found:
   - App filtering logic runs
   - App results displayed

For contacts specifically: 5. Contacts provider checks `hasContactsPermission` 6. If not granted → returns `PermissionRequestResult` 7. If granted → queries `ContactsRepository` 8. Returns `ContactSearchResult` for each match

### Keyboard Actions

- **Done button**: Launches first result (app or provider result)
- **Back button**: Closes search dialog
- **Clear button (X)**: Clears current search text

### Empty States

- **Empty search**: Shows "No recent apps" + shows prefix shortcuts hint
- **Provider active, no query**: Shows "Type your [mode] query"
- **Provider active with query, no results**: Shows "No [mode] results found"
- **App search, no matches**: Shows "No apps found"
- **Contacts, no permission**: Shows permission request card

## Permissions Required

The multi-mode search feature requires these permissions:

### AndroidManifest.xml

```xml
<!-- Required for contacts search -->
<uses-permission android:name="android.permission.READ_CONTACTS" />
```

**Note**: `CALL_PHONE` permission is NOT needed because we use `ACTION_DIAL` which opens the dialer without directly placing the call.

## Future Enhancements

Potential improvements:

1. **Configuration UI**: Allow users to add/remove providers via settings
2. **More providers**: Maps, Spotify, Reddit, etc.
3. **Contact actions**: Edit, delete, message contacts
4. **Search history**: Remember recent web/contacts searches
5. **Quick actions**: Show "Call", "Message" buttons directly in results
6. **Fuzzy matching**: Better typo tolerance for app and contact names
7. **Contact photos**: Show contact photos in search results

## Files Involved

- `domain/model/SearchProvider.kt` - Search provider definitions
- `domain/model/Contact.kt` - Contact data model
- `data/repository/ContactsRepository.kt` - Contacts database access
- `ui/components/AppSearchDialog.kt` - Main search dialog UI
- `ui/screens/LauncherScreen.kt` - Screen integration
- `MainActivity.kt` - Permission handling and callbacks
- `docs/multi-mode-search.md` - This documentation
