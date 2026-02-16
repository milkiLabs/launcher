# Multi-Mode Search

## Overview

The launcher supports multi-mode search using single-character prefixes. This allows you to search not just installed apps, but also the web, contacts, files, and YouTube directly from the search dialog.

## App Results Grid Layout

### Visual Design

When searching for apps (no prefix), results are displayed in a compact **2×4 grid** layout:

```
┌────────────────────────────────────────────────────────┐
│  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐               │
│  │ 📱  │  │ 💬  │  │ 📷  │  │ 🎵  │               │
│  │Phone │  │Msg   │  │Camera│  │Music │               │
│  └──────┘  └──────┘  └──────┘  └──────┘               │
│  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐               │
│  │ 🌐  │  │ 📧  │  │ 📅  │  │ ⚙️  │               │
│  │Browser│ │Email │  │Cal   │  │Settings│              │
│  └──────┘  └──────┘  └──────┘  └──────┘               │
└────────────────────────────────────────────────────────┘
```

### Grid Configuration

| Property | Value | Reason |
|----------|-------|--------|
| Columns | 4 | Fits most phone screens comfortably |
| Rows | 2 | Shows 8 apps without scrolling |
| Total Items | 8 | Maximum results returned by ViewModel |

### Why a Grid?

1. **More apps visible**: 8 apps vs 4-5 in a traditional list
2. **Faster scanning**: Grid patterns are easier for eyes to scan
3. **Space efficient**: Uses less vertical space
4. **Touch-friendly**: Large tap targets for each app

### When Grid vs List

The UI automatically chooses the best layout:

| Scenario | Layout | Reason |
|----------|--------|--------|
| All app results | Grid | Compact, efficient for apps |
| Mixed results (apps + web + contacts) | List | Different result types need more space |
| Provider search (web, YouTube, contacts) | List | Results have additional info (URLs, phone numbers) |

### Performance Optimizations

The grid layout is optimized for performance:

1. **Limited results**: ViewModel limits app results to 8 (reduces processing)
2. **LazyVerticalGrid**: Only composes visible items (though all 8 are visible)
3. **Stable keys**: Uses package name as key for efficient recomposition
4. **Async icons**: Coil loads icons asynchronously with caching

### Recent Apps

When you open the search dialog without typing:

- Shows your 8 most recently used apps
- Helps you quickly access apps you use frequently
- No typing required for common apps

## How to Use

### Basic Usage

1. **Tap anywhere on the home screen** to open the search dialog
2. **Type your search** with or without a prefix:
    - Without prefix: Searches installed apps
    - With prefix: Activates special search mode
    - Type a URL: Opens directly in browser

### Direct URL Opening

When you type a URL-like query, the launcher automatically detects it and offers to open it directly in your browser:

| What you type      | What happens                                    |
| ------------------ | ----------------------------------------------- |
| `github.com`       | Shows "Open github.com" result → opens in browser |
| `https://google.com` | Opens google.com directly                     |
| `reddit.com/r/android` | Opens the Reddit subreddit                   |

**URL patterns recognized:**
- Full URLs with scheme: `https://example.com`, `http://example.com/path`
- Domain-only: `example.com`, `sub.domain.org`, `github.com/user/repo`
- Common TLDs: `.com`, `.org`, `.net`, `.io`, `.co`, `.edu`, `.gov`, `.dev`, `.app`, etc.

**Note:** If no scheme is provided, `https://` is automatically added.

### Prefix Shortcuts

| Prefix   | Mode           | Example           | What Happens                                   |
| -------- | -------------- | ----------------- | ---------------------------------------------- |
| _(none)_ | **Apps**       | `calculator`      | Searches installed apps                        |
| `s `     | **Web Search** | `s weather today` | Opens browser with search results              |
| `c `     | **Contacts**   | `c mom`           | Searches device contacts (requires permission) |
| `f `     | **Files**      | `f report`        | Searches documents, PDFs, ebooks               |
| `y `     | **YouTube**    | `y lofi music`    | Opens YouTube with search query                |

**Important:** The space after the prefix is required! `s` searches apps, `s ` activates web search.

## Visual Indicators

When you type a prefix followed by a space, the search dialog shows visual feedback:

- **Blue bar + icon**: Web Search mode active
- **Green bar + icon**: Contacts mode active
- **Orange bar + icon**: Files mode active
- **Red bar + icon**: YouTube mode active
- **No bar** (default): App search mode

The placeholder text also changes:

- "Search apps..." (default)
- "Search the web..." (s prefix)
- "Search contacts..." (c prefix)
- "Search documents..." (f prefix)
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

## Files Search with Permission Handling

The files search feature (`f ` prefix) allows you to search for **all files** on your device, excluding images and videos. This includes:
- Documents (PDF, Word, Excel, PowerPoint)
- Ebooks (EPUB, MOBI)
- Archives (ZIP, RAR, 7z)
- APK files
- Code files (Kotlin, Java, Python, etc.)
- Configuration files
- Any other file type

### What's Excluded

Images and videos are excluded from search results because they're better accessed through gallery apps.

### Supported File Types

| Category | Extensions |
|----------|-------------|
| Documents | `.pdf`, `.doc`, `.docx`, `.xls`, `.xlsx`, `.ppt`, `.pptx`, `.odt`, `.ods`, `.odp` |
| Ebooks | `.epub`, `.mobi`, `.azw` |
| Archives | `.zip`, `.rar`, `.7z`, `.tar`, `.gz` |
| Code | `.kt`, `.java`, `.py`, `.js`, `.json`, `.xml`, `.html`, `.css` |
| Text | `.txt`, `.md`, `.rtf`, `.csv` |
| Other | `.apk`, `.exe`, `.iso`, and any other files |

### Partial Matching

The search uses partial matching, so:
- Searching `kot` will match `kotlin_book.pdf`, `kotlintutorial.docx`, etc.
- Searching `repo` will match `report.pdf`, `repository.zip`, etc.

### Permission Flow

The files search feature requires storage permissions to access files on the device. The permission needed depends on your Android version:

**Android 10 (API 29) and below:**
- Requires `READ_EXTERNAL_STORAGE` runtime permission
- User sees a permission dialog when tapping "Grant Permission"

**Android 11+ (API 30+):**
- Requires `MANAGE_EXTERNAL_STORAGE` ("All files access" permission)
- This is a **special permission** that must be granted in Settings
- When tapping "Grant Permission", the app opens Settings to the "All files access" page
- User must toggle the permission ON for the app
- After returning to the app, files become searchable

1. **User types `f `** → Files mode activates (orange bar appears)
2. **If permission NOT granted**:
   - Shows a card with warning icon
   - Displays message: "Storage permission required to search files" (Android 10-)
   - Or: "Allow file access in Settings to search all files" (Android 11+)
   - Shows "Grant Permission" button
   - User taps button → Permission dialog (Android 10-) or Settings (Android 11+)
3. **If permission granted**:
   - Files are searched from device storage
   - Matching files appear in the list
   - Each file shows name, folder, and size

### File Actions

When you tap a file in the search results:

- **Primary action**: Opens the file with an appropriate app
- Uses `ACTION_VIEW` with the file's MIME type
- Shows a chooser dialog if multiple apps can open the file

### Android 11+ and MANAGE_EXTERNAL_STORAGE

On Android 11 (API 30) and above:

**Scoped Storage Limitations:**
- Android's scoped storage restricts which files apps can access
- `MediaStore.Files` can only see files the app created itself
- To search ALL files on the device, the app needs `MANAGE_EXTERNAL_STORAGE` permission

**MANAGE_EXTERNAL_STORAGE Permission:**
- This is a special "All files access" permission
- Cannot be granted via normal runtime permission dialogs
- Must be enabled by the user in Settings → Apps → [App Name] → Permissions
- Or via Settings → Apps → Special app access → All files access

**Why This Permission is Needed:**
- Without it, the app can only see files in its own app-specific directories
- With it, the app can search all documents, downloads, and other files on the device
- This is essential for a launcher app that searches for PDFs, documents, etc.

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
                            +-- FileDocumentSearchResult
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

Sealed class with five implementations:

- **AppSearchResult**: Wraps AppInfo for app results
- **WebSearchResult**: Web search results with URL and engine
- **ContactSearchResult**: Contact search results with Contact object
- **FileDocumentSearchResult**: File search results with FileDocument object
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

#### FileDocument

Located in: `domain/model/FileDocument.kt`

Represents a document file on the device:

- **id**: File ID from MediaStore
- **name**: File name with extension
- **mimeType**: MIME type (e.g., "application/pdf")
- **size**: File size in bytes
- **dateModified**: Last modified timestamp
- **uri**: Content URI for opening the file
- **folderPath**: Folder location for display

Helper extension functions:
- `formattedSize()`: Returns human-readable size (KB, MB, GB)
- `isPdf()`, `isEpub()`, `isWordDocument()`, etc.: Check file type

#### FilesRepository

Located in: `data/repository/FilesRepository.kt`

Handles all file storage interactions:

- `hasFilesPermission()`: Check if permission is granted (MANAGE_EXTERNAL_STORAGE on Android 11+, READ_EXTERNAL_STORAGE on older versions)
- `searchFiles(query)`: Search documents by file name
- `getRecentFiles(limit)`: Get recently modified documents

### Permission Handling

The permission system uses the modern Activity Result API:

#### Contacts Permission

1. **MainActivity** registers a permission launcher in `onCreate()`
2. **Contacts mode activation** checks `hasContactsPermission` state
3. **If not granted**: Shows `PermissionRequestResult` with button
4. **Button click**: Calls `onRequestContactsPermission()`
5. **MainActivity** launches permission request dialog
6. **On result**: Updates `hasContactsPermission` state
7. **UI automatically updates** due to state change

#### Files Permission

1. **MainActivity** checks `hasFilesPermission` using appropriate method:
   - Android 11+: `Environment.isExternalStorageManager()`
   - Android 10 and below: `ContextCompat.checkSelfPermission()`
2. **Files mode activation** checks permission state
3. **If not granted**: Shows `PermissionRequestResult` with button
4. **Button click**: Calls `onRequestFilesPermission()`
5. **MainActivity** handles permission request:
   - Android 11+: Opens Settings for "All files access"
   - Android 10 and below: Launches runtime permission dialog
6. **On result**: Updates `hasFilesPermission` state
7. **On Android 11+**: Permission state is rechecked in `onResume()` when returning from Settings

**Note**: On Android 11+ (API 30+), `MANAGE_EXTERNAL_STORAGE` permission is required because scoped storage restricts `MediaStore.Files` to only app-created files.

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

For files specifically:
1. Files provider checks `hasFilesPermission`:
   - Android 11+: Checks `Environment.isExternalStorageManager()`
   - Android 10 and below: Checks `READ_EXTERNAL_STORAGE` permission
2. If not granted → returns `PermissionRequestResult`
3. If granted → queries `FilesRepository` via MediaStore
4. Returns `FileDocumentSearchResult` for each match

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

<!-- Required for files search on Android 10 and below -->
<uses-permission 
    android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />

<!-- Required for files search on Android 13+ (granular media permissions) -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />

<!-- Required for files search on Android 11+ (all files access) -->
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
```

**Permission Usage Notes:**
- `READ_CONTACTS`: Runtime permission requested via dialog
- `READ_EXTERNAL_STORAGE`: Runtime permission for Android 10 and below
- `MANAGE_EXTERNAL_STORAGE`: Special permission granted via Settings (Android 11+)
- `READ_MEDIA_*`: Granular permissions for Android 13+ (part of media access)

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
- `ui/components/AppSearchDialog.kt` - Main search dialog UI (grid/list switching logic)
- `ui/components/AppGridItem.kt` - Compact grid item for apps
- `ui/components/AppListItem.kt` - Traditional list item for apps
- `presentation/search/SearchViewModel.kt` - Search logic with 8-result limit
- `presentation/search/SearchUiState.kt` - UI state model
- `ui/screens/LauncherScreen.kt` - Screen integration
- `MainActivity.kt` - Permission handling and callbacks
- `docs/multi-mode-search.md` - This documentation
