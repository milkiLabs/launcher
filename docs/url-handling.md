# URL Handling Feature

This document explains how the launcher detects and handles URLs, including the ability to open URLs in specialized apps (deep links) instead of just the browser.

## Overview

When a user types a URL into the search bar, the launcher:

1. Detects that the query is a valid URL
2. Queries the Android system to find which installed apps can handle that URL
3. Shows the user which app will open the URL (e.g., "Open in YouTube" for youtube.com URLs)
4. Provides browser as a fallback option

The same URL handling stack is also reused by the clipboard smart suggestion feature:

- On search dialog open, clipboard text is read once
- If clipboard content looks like a URL/domain, the launcher resolves handler app exactly like typed URL flow
- The UI shows a bottom chip (`From clipboard`) with the resolved action
- Tapping the chip launches handler app or browser fallback

## Query Suggestion Feature

In addition to clipboard suggestions, the launcher also provides **query suggestions** when the user starts typing in the search field. This provides a consistent UX with the clipboard chip, but applies to the actively typed query instead of past clipboard content.

### How It Works

1. User types in the search field
2. Query text is analyzed in real-time
3. If the query matches a pattern (URL, phone, email, location), a suggestion chip appears
4. The chip shows a quick action: "Open in YouTube", "Call +123456", "Email user@example.com", etc.
5. For plain text queries, the chip shows "Search with Google"

### Mutual Exclusivity

The clipboard chip and query chip are mutually exclusive:

| Condition | Chip Shown |
|-----------|------------|
| Query is BLANK | Clipboard chip (if clipboard has content) |
| Query is NOT BLANK | Query suggestion chip |

This prevents UI clutter and provides a clear, focused suggestion.

### Suggestion Types

The `QuerySuggestionResolver` classifies query text into one of these types:

1. **OpenUrl**: Query looks like a URL (e.g., "youtube.com" → "Open in YouTube")
2. **DialNumber**: Query looks like a phone number (e.g., "+1234567890" → "Call +1234567890")
3. **ComposeEmail**: Query looks like an email address (e.g., "user@example.com" → "Email user@example.com")
4. **OpenMapLocation**: Query looks like a location (e.g., "1600 Amphitheatre Parkway" → "Open in maps")
5. **SearchWeb**: Query is plain text (e.g., "how to make pasta" → "Search with Google")

### Priority Order

Suggestions are resolved in priority order (highest to lowest):

1. URL detection (most specific, indicates intent to visit a website)
2. Phone number detection (strong signal for calling)
3. Email address detection (clear intent to send email)
4. Map/location heuristics (could be an address or coordinates)
5. Plain text search (fallback for everything else)

This ensures the most likely intended action is suggested.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              URL HANDLING FLOW                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  User types "youtube.com/watch?v=xyz"                                       │
│                    │                                                        │
│                    ▼                                                        │
│  ┌─────────────────────────────────────┐                                   │
│  │      SearchViewModel.detectUrl()    │                                   │
│  │  - Validates URL format             │                                   │
│  │  - Normalizes (adds https://)       │                                   │
│  └──────────────────┬──────────────────┘                                   │
│                     │                                                       │
│                     ▼                                                       │
│  ┌─────────────────────────────────────┐                                   │
│  │   UrlHandlerResolver.resolveUrl()   │                                   │
│  │  - Queries PackageManager           │                                   │
│  │  - Finds apps that can handle URL   │                                   │
│  │  - Returns handler app info         │                                   │
│  └──────────────────┬──────────────────┘                                   │
│                     │                                                       │
│                     ▼                                                       │
│  ┌─────────────────────────────────────┐                                   │
│  │        UrlSearchResult              │                                   │
│  │  - url: "https://youtube.com/..."   │                                   │
│  │  - handlerApp: YouTube app info     │                                   │
│  │  - title: "Open in YouTube"         │                                   │
│  └──────────────────┬──────────────────┘                                   │
│                     │                                                       │
│                     ▼                                                       │
│  ┌─────────────────────────────────────┐                                   │
│  │      UrlSearchResultItem (UI)       │                                   │
│  │  - Shows "Open in YouTube"          │                                   │
│  │  - Shows URL as supporting text     │                                   │
│  │  - Arrow icon indicates app launch  │                                   │
│  └──────────────────┬──────────────────┘                                   │
│                     │                                                       │
│                     ▼                                                       │
│  User taps → SearchAction.OpenUrlWithApp                                   │
│                     │                                                       │
│                     ▼                                                       │
│  ┌─────────────────────────────────────┐                                   │
│  │    ActionHandler.handleOpenUrl...   │                                   │
│  │  - Creates ACTION_VIEW intent       │                                   │
│  │  - Sets package to YouTube          │                                   │
│  │  - Opens YouTube app                │                                   │
│  └─────────────────────────────────────┘                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. UrlValidator (Utility)

Location: `util/UrlValidator.kt`

This utility provides centralized URL validation and normalization. It replaced the complex multi-stage URL detection logic that was previously in SearchViewModel.

**Why UrlValidator Exists:**
- URL detection logic was complex and hard to maintain
- Multiple regex patterns made testing difficult
- No centralized place for URL normalization
- Separation of concerns: validation logic separate from ViewModel

**Validation Strategy:**
1. **Fast-fail**: Empty strings or strings with spaces are not URLs
2. **Normalize prefixes**: Handle http://, https://, and www.
3. **Validate format**: Use Android's Patterns.WEB_URL + fallback regex
4. **Ensure scheme**: All URLs need https:// for browser intent

```kotlin
val result = UrlValidator.validateUrl("youtube.com")
// result.url = "https://youtube.com"
// result.displayUrl = "youtube.com"

val invalid = UrlValidator.validateUrl("hello world")
// invalid = null (has spaces)
```

### 2. UrlHandlerApp (Data Model)

Location: `domain/model/SearchResult.kt`

Represents an app that can handle a URL:

```kotlin
data class UrlHandlerApp(
    val packageName: String,      // e.g., "com.google.android.youtube"
    val activityName: String,     // Specific activity that handles the URL
    val label: String,            // e.g., "YouTube"
    val isDefault: Boolean = false // Whether this is the system default
)
```

### 2. UrlHandlerResolver (Service)

Location: `domain/search/UrlHandlerResolver.kt`

This service queries Android's PackageManager to find apps that can handle URLs:

- `resolveUrlHandler(url)`: Returns the default handler for a URL
- `getAllUrlHandlers(url)`: Returns all apps that can handle a URL
- `isDeepLink(url)`: Checks if a URL will open in a specific app (not browser)

#### How It Works

```kotlin
// 1. Create an ACTION_VIEW intent for the URL
val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

// 2. Query PackageManager for apps that can handle it
val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)

// 3. Extract app information
val handlerApp = UrlHandlerApp(
    packageName = resolveInfo.activityInfo.packageName,
    label = resolveInfo.loadLabel(packageManager).toString()
)
```

### 3. UrlSearchResult (Data Model)

Location: `domain/model/SearchResult.kt`

Enhanced to include handler app information:

```kotlin
data class UrlSearchResult(
    val url: String,                    // The complete URL
    val displayUrl: String,             // What the user typed
    val handlerApp: UrlHandlerApp? = null, // App that will handle it
    val browserFallback: Boolean = true    // Always true - browser is always available
) : SearchResult() {
    override val title: String = if (handlerApp != null) {
        "Open in ${handlerApp.label}"   // e.g., "Open in YouTube"
    } else {
        "Open $displayUrl"              // e.g., "Open github.com"
    }
}
```

### 4. SearchViewModel Updates

Location: `presentation/search/SearchViewModel.kt`

The `detectUrl()` function now resolves the handler app:

```kotlin
private fun detectUrl(query: String): UrlSearchResult? {
    // ... URL validation logic ...
    
    // Resolve which app can handle this URL
    val handlerApp = urlHandlerResolver.resolveUrlHandler(url)
    
    return UrlSearchResult(
        url = url,
        displayUrl = displayUrl,
        handlerApp = handlerApp,
        browserFallback = true
    )
}
```

### 4.1 ClipboardSuggestionResolver (One-shot URL Classification)

Location: `domain/search/ClipboardSuggestionResolver.kt`

This resolver reuses `UrlValidator` + `UrlHandlerResolver` to interpret clipboard text.

Behavior details:

1. Read clipboard text once when search opens
2. Run URL validation with the same normalization rules (`https://` auto-prefix)
3. Resolve deep-link handler app through PackageManager
4. Return a typed clipboard suggestion (`OpenUrl`) used by bottom chip UI

Important: this resolver does **not** subscribe to clipboard change events.

### 4.2 QuerySuggestionResolver (Real-time Query Analysis)

Location: `domain/search/QuerySuggestionResolver.kt`

This resolver analyzes the current search query and provides actionable suggestions.

Behavior details:

1. Observe query changes in real-time
2. Run the same classification logic as clipboard (URL, phone, email, location, web search)
3. Resolve URL handler apps through PackageManager
4. Return a typed query suggestion used by bottom chip UI

The resolver is similar to `ClipboardSuggestionResolver` but:
- Takes the query text as input (not clipboard)
- Called on every query change (not just on dialog open)
- Returns `QuerySuggestion` instead of `ClipboardSuggestion`

### 4.3 QuerySuggestion (Data Model)

Location: `domain/search/QuerySuggestion.kt`

Sealed class representing different types of query suggestions:

```kotlin
sealed class QuerySuggestion {
    abstract val rawQuery: String
    
    data class OpenUrl(val urlResult: UrlSearchResult, ...) : QuerySuggestion()
    data class DialNumber(val phoneNumber: String, ...) : QuerySuggestion()
    data class ComposeEmail(val emailAddress: String, ...) : QuerySuggestion()
    data class OpenMapLocation(val locationQuery: String, ...) : QuerySuggestion()
    data class SearchWeb(val searchQuery: String, ...) : QuerySuggestion()
}
```

### 5. SearchAction Updates

Location: `presentation/search/SearchAction.kt`

New actions for URL handling:

```kotlin
// Open URL with a specific app (deep link)
data class OpenUrlWithApp(
    val url: String,
    val handlerApp: UrlHandlerApp
) : SearchAction()

// Open URL explicitly in browser
data class OpenUrlInBrowser(val url: String) : SearchAction()
```

### 6. ActionHandler Updates

Location: `handlers/ActionHandler.kt`

New handler for opening URLs in specific apps:

```kotlin
private fun handleOpenUrlWithApp(action: SearchAction.OpenUrlWithApp) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).apply {
        // Force the specific app to handle the URL
        `package` = action.handlerApp.packageName
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Fallback to browser if app can't handle it
        openUrlInBrowser(action.url)
    }
}
```

### 7. UI Updates

Location: `ui/components/SearchResultItems.kt`

The `UrlSearchResultItem` now shows the handler app:

- Uses `Icons.Default.Language` for deep links (indicates opening in another app)
- Uses `Icons.Default.Info` for browser fallback
- Shows an arrow icon to indicate navigation to another app

## Android Manifest Configuration

For Android 11+ (API 30+), you must declare which URL schemes you want to query:

```xml
<queries>
    <!-- Query apps that can handle https URLs -->
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:scheme="https" />
    </intent>
    
    <!-- Query apps that can handle http URLs -->
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:scheme="http" />
    </intent>
</queries>
```

Without this declaration, `PackageManager.queryIntentActivities()` would return an empty list on Android 11+.

## Example Scenarios

### Scenario 0: Clipboard URL on Search Open

Clipboard contains: `youtube.com/watch?v=dQw4w9WgXcQ`

1. User opens search dialog
2. Clipboard snapshot is read once
3. URL is normalized and deep-link handler is resolved
4. Bottom chip appears: `From clipboard` + `Open in YouTube`
5. User taps chip → YouTube app opens (or browser fallback)

### Scenario 0.5: Query Suggestion While Typing

User types: `youtube.com/watch?v=dQw4w9WgXcQ`

1. User opens search dialog (clipboard chip shows if applicable)
2. User starts typing the URL
3. Clipboard chip disappears (query is not blank)
4. Query suggestion chip appears: `Suggested action` + `Open in YouTube`
5. User taps chip → YouTube app opens (or browser fallback)

This provides a seamless experience: whether the user pastes from clipboard or types manually, they get the same smart suggestion.

### Scenario 1: YouTube URL

User types: `youtube.com/watch?v=dQw4w9WgXcQ`

1. URL is detected and normalized: `https://youtube.com/watch?v=dQw4w9WgXcQ`
2. Resolver finds YouTube app can handle this URL
3. UI shows: "Open in YouTube"
4. User taps → Opens in YouTube app

### Scenario 2: Generic URL

User types: `github.com/user/repo`

1. URL is detected: `https://github.com/user/repo`
2. Resolver finds no specific app (only browser)
3. UI shows: "Open github.com"
4. User taps → Opens in default browser

### Scenario 3: Maps URL

User types: `maps.google.com/?q=coffee`

1. URL is detected: `https://maps.google.com/?q=coffee`
2. Resolver finds Google Maps can handle this URL
3. UI shows: "Open in Google Maps"
4. User taps → Opens in Google Maps app

## Browser Detection

The `UrlHandlerResolver` maintains a list of known browser packages to distinguish between:

- **Deep links**: URLs that open in specific apps (YouTube, Twitter, etc.)
- **Browser links**: URLs that open in web browsers

Known browsers include:
- Chrome (`com.android.chrome`)
- Firefox (`org.mozilla.firefox`)
- Edge (`com.microsoft.emmx`)
- Brave (`com.brave.browser`)
- Samsung Internet (`com.sec.android.app.sbrowser`)
- And more...

## Performance Considerations

### Threading

URL resolution happens on `Dispatchers.Default` (background thread) because:
- `PackageManager.queryIntentActivities()` is an I/O operation
- Multiple apps might need to be queried
- Prevents UI thread blocking

### Caching

Currently, URL handler resolution is done on every URL detection. Future improvements could include:
- Caching handler app info for common domains
- Pre-resolving popular URL patterns
- Storing user preferences for URL handlers

## Testing URL Handling

To test this feature:

1. **Install YouTube app**: Type `youtube.com` - should show "Open in YouTube"
2. **Install Twitter/X app**: Type `twitter.com` - should show "Open in X"
3. **No app installed**: Type `example.com` - should show "Open example.com" (browser)

## Future Enhancements

1. **Secondary action**: Add "Open in Browser" as a secondary option when a deep link is detected
2. **User preferences**: Allow users to choose preferred handlers (e.g., always use browser for YouTube)
3. **Handler selection**: Show all available handlers and let user choose
4. **Custom URL schemes**: Support for app-specific URL schemes (e.g., `twitter://user/123`)
