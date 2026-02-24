# URL Handling Feature

This document explains how the launcher detects and handles URLs, including the ability to open URLs in specialized apps (deep links) instead of just the browser.

## Overview

When a user types a URL into the search bar, the launcher:

1. Detects that the query is a valid URL
2. Queries the Android system to find which installed apps can handle that URL
3. Shows the user which app will open the URL (e.g., "Open in YouTube" for youtube.com URLs)
4. Provides browser as a fallback option

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

### 1. UrlHandlerApp (Data Model)

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
