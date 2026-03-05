# Milki Launcher - Project Documentation

Welcome to the Milki Launcher documentation! This is an educational Android launcher app built with modern Android development practices.

## Quick Links

| Document | Description |
|----------|-------------|
| **[Architecture.md](Architecture.md)** | Complete architecture guide (Clean Architecture + MVVM) |
| **[custom-source-search.md](custom-source-search.md)** | Unified external search via configurable custom sources |
| **[search-settings-contacts-modularization.md](search-settings-contacts-modularization.md)** | Detailed walkthrough of the March 2026 file modularization refactor |
| **[multi-mode-search.md](multi-mode-search.md)** | Multi-mode search feature documentation |
| **[app-icon-memory-cache.md](app-icon-memory-cache.md)** | Launcher-optimized app icon caching and rendering flow |
| **[DataStoreHotPathOptimizations.md](DataStoreHotPathOptimizations.md)** | Reducing DataStore read/write churn in settings/home hot paths |
| **[LauncherApplication.md](LauncherApplication.md)** | Application class and Koin startup configuration |
| **[Theme.md](Theme.md)** | Material Design 3 theming explained |
| **[BuildConfiguration.md](BuildConfiguration.md)** | Gradle build files explained |
| **[AndroidManifest.md](AndroidManifest.md)** | App configuration explained |

---

## Project Overview

**Milki Launcher** is a custom Android Launcher app that replaces the device's home screen. It features a searchable app drawer with multi-mode search capabilities.

### Key Features

- **Multi-Mode Search**: Search apps, local providers, and custom external sources
    - No prefix: Search installed apps only
    - `c `: Contacts search (requires permission)
    - `f `: Files search
    - Custom source prefixes (e.g. `yt `, `ig `, `x `) are user-configurable in Settings
    - Clipboard smart suggestion: one bottom chip on search open (URL/app, browser fallback, phone, email, maps, or text search)
  
- **Smart Layout**: 
  - Grid layout for apps (2×4 = 8 apps visible)
  - List layout for provider results (URLs, phone numbers)
  
- **O(n) Search Algorithm**: Linear search optimized for 200+ apps
  - Three-tier matching: exact → startsWith → contains
  - Pre-computed lowercase strings for performance
  
- **Controlled Parallelism**: Processes apps in chunks of 8 to avoid memory spikes

- **Launcher Icon Cache**: App icons preloaded from PackageManager into an in-memory LRU cache

- **Recent Apps**: Saves and displays 8 most recently launched apps

---

## Technology Stack

| Category | Technology |
|----------|------------|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose with Material Design 3 |
| **Architecture** | MVVM + Clean Architecture |
| **Dependency Injection** | Manual (Service Locator in AppContainer) |
| **Image Loading** | PackageManager + in-memory LRU icon cache |
| **Data Persistence** | DataStore Preferences |
| **Async Operations** | Kotlin Coroutines + Flow |
| **Minimum SDK** | API 24 (Android 7.0) |
| **Target/Compile SDK** | API 36 (Android 16) |

---

## Architecture Highlights

The app follows **Clean Architecture** with four layers:

1. **Presentation Layer (UI)**: Composable functions, screens, components
2. **Presentation Layer (ViewModel)**: State management, action handling
3. **Domain Layer**: Business logic, models, repository interfaces, use cases
4. **Data Layer**: Repository implementations, search providers, external APIs

**Key Patterns**:
- **MVVM**: Unidirectional data flow with ViewModel
- **Repository Pattern**: Abstract data sources behind interfaces
- **Plugin Pattern**: Pluggable search providers
- **Use Case Pattern**: Single-responsibility business operations
- **Sealed Classes**: Type-safe state and action handling

See **[Architecture.md](Architecture.md)** for complete details.

---

## Project Structure

```
app/src/main/java/com/milki/launcher/
├── MainActivity.kt                    # Entry point, handles actions
├── LauncherApplication.kt             # App class, DI container init
│
├── di/
│   └── AppContainer.kt                # Manual DI container
│
├── domain/                            # Business logic (no Android deps)
│   ├── model/                         # Data models (AppInfo, Contact, etc.)
│   ├── repository/                    # Repository interfaces
│   └── search/                        # Use cases and query parsing
│
├── data/                              # Implementation
│   ├── icon/                          # App icon memory cache
│   ├── repository/                    # Repository implementations
│   └── search/                        # Search providers (Web, Contacts, YouTube)
│
├── presentation/                      # ViewModels
│   └── search/                        # SearchViewModel, UiState, Actions
│
└── ui/                                # UI layer (Compose)
    ├── screens/                       # Full screens
    ├── components/                    # Reusable components
    └── theme/                         # Material 3 theme
```

---

## Multi-Mode Search

The launcher supports searching different data sources using single-character prefixes:

| Prefix | Mode | Example | What Happens |
|--------|------|---------|--------------|
| _(none)_ | **Apps** | `calculator` | Searches installed apps (grid layout) |
| `s ` | **Web Search** | `s weather today` | Opens browser with search results |
| `c ` | **Contacts** | `c mom` | Searches device contacts (list layout) |
| `y ` | **YouTube** | `y lofi music` | Opens YouTube with search query |

**Important**: The space after the prefix is required! `s` searches apps, `s ` activates web search.

### Visual Indicators

When a provider is active:
- **Colored bar** appears at the top (blue=web, green=contacts, red=YouTube)
- **Provider icon** shows in the search field
- **Placeholder text** changes (e.g., "Search the web...")

### Clipboard Smart Suggestion

When search opens with an empty query, the launcher reads clipboard text once and may show one action chip at the bottom of the dialog:

- `Open in <App>` or `Open in browser` for URLs
- `Call <number>` for phone numbers
- `Email <address>` for email addresses
- `Open in maps` for location text
- `Search text` for generic text

The chip is shown only in default app-search mode and disappears once the user starts typing.

### Permission Handling

Contacts search requires `READ_CONTACTS` permission:
1. User types `c ` → Contacts mode activates
2. If permission not granted → Shows permission request card
3. User taps "Grant Permission" → System dialog appears
4. After grant → Contacts are searched automatically

See **[multi-mode-search.md](multi-mode-search.md)** for complete details.

---

## Performance Features

### 1. Controlled Parallelism

When loading 150+ apps, we process them in chunks of 8:

```kotlin
resolveInfos.chunked(8).flatMap { chunk ->
    chunk.map { resolveInfo ->
        async { /* load app info */ }
    }.awaitAll()
}
```

Prevents memory spikes and keeps the app responsive.

### 2. Cached Lowercase Strings

AppInfo pre-computes lowercase versions:

```kotlin
data class AppInfo(...) {
    val nameLower: String by lazy { name.lowercase() }
    val packageLower: String by lazy { packageName.lowercase() }
}
```

Avoids calling `lowercase()` on every search keystroke.

### 3. Memory Cache for Icons

App icons are preloaded into a dedicated in-memory LRU cache during app discovery,
so icon composables can usually render immediately from memory.

### 4. Smart Search Algorithm

Three-tier matching system:
1. **Exact matches** first (highest priority)
2. **Starts-with matches** second
3. **Contains matches** last

Gives users the most relevant results first.

---

## Getting Started

### Prerequisites

- Android Studio (latest stable version)
- JDK 11 or higher
- Android SDK (API 24 - 36)

### Building the Project

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Run on device or emulator (API 24+)

### Setting as Default Launcher

1. Install the app
2. Press Home button
3. Select "Milki Launcher" when prompted
4. Choose "Always" to set as default

---

## Documentation Guide

This project is extensively documented for educational purposes. Each major component has its own detailed documentation:

### Core Architecture
- **[Architecture.md](Architecture.md)** - Complete architecture guide with diagrams, patterns, and best practices
- **[search-settings-contacts-modularization.md](search-settings-contacts-modularization.md)** - Search/settings/contacts split-by-concern refactor guide

### Features
- **[multi-mode-search.md](multi-mode-search.md)** - Multi-mode search feature with usage examples and extension guide

### Technical Components
- **[app-icon-memory-cache.md](app-icon-memory-cache.md)** - How launcher icon caching and rendering works
- **[DataStoreHotPathOptimizations.md](DataStoreHotPathOptimizations.md)** - How hot-path repository writes were optimized
- **[LauncherApplication.md](LauncherApplication.md)** - Application class and startup initialization
- **[Theme.md](Theme.md)** - Material Design 3 theming explained

### Configuration
- **[BuildConfiguration.md](BuildConfiguration.md)** - Understanding Gradle build files
- **[AndroidManifest.md](AndroidManifest.md)** - App configuration and launcher setup

---

## Code Examples

### Adding a New Search Provider

```kotlin
// 1. Create the provider
class RedditSearchProvider : SearchProvider {
    override val config = SearchProviderConfig(
        providerId = "reddit",
        prefix = "r",
        name = "Reddit",
        description = "Search Reddit"
    )
    
    override suspend fun search(query: String): List<SearchResult> {
        return listOf(RedditSearchResult("all", query))
    }
}

// 2. Register in AppContainer
private val redditSearchProvider = RedditSearchProvider()

val searchProviderRegistry = SearchProviderRegistry(
    listOf(webSearchProvider, contactsSearchProvider, youTubeSearchProvider, redditSearchProvider)
)

// 3. Handle action in MainActivity
is SearchAction.OpenRedditSearch -> {
    val url = "https://reddit.com/search?q=${Uri.encode(action.query)}"
    openUrl(url)
}

// Provider visuals are mapped in the presentation layer by providerId
// (icon + themed color), not stored in domain config.
```

See **[Architecture.md](Architecture.md)** for more examples.

---

## Common Issues

### Apps Not Showing (Android 11+)

**Symptom**: Empty app list on Android 11+ devices

**Cause**: Missing `<queries>` section in AndroidManifest.xml

**Fix**: Ensure manifest includes:
```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
    </intent>
</queries>
```

### Contacts Permission Not Working

**Symptom**: Contacts search shows permission card repeatedly

**Cause**: Permission not declared in manifest

**Fix**: Add to AndroidManifest.xml:
```xml
<uses-permission android:name="android.permission.READ_CONTACTS" />
```

### Icons Not Loading

**Symptom**: App icons show as default

**Cause**: LauncherApplication not registered in manifest

**Fix**: Ensure manifest has:
```xml
<application
    android:name=".LauncherApplication"
    ... >
```

---

## License

This project is for educational purposes. Feel free to use, modify, and learn from it!

---

## Contributing

This is an educational project. Contributions that improve clarity, add documentation, or fix bugs are welcome!

---

## Key Takeaways

1. **Clean Architecture**: Separates concerns into layers with inward-pointing dependencies
2. **MVVM**: Unidirectional data flow with ViewModel as the bridge between UI and data
3. **Multi-Mode Search**: Plugin pattern allows extensible search providers
4. **Performance**: Controlled parallelism, caching, and efficient algorithms
5. **Educational Focus**: Extensive documentation for learning Android development

Happy learning! 🚀
