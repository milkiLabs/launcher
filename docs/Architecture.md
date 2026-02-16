# Milki Launcher - Architecture Documentation

## Overview

Milki Launcher follows **Clean Architecture** principles combined with **MVVM (Model-View-ViewModel)** pattern. This architecture ensures:

- **Separation of Concerns**: Each layer has a single responsibility
- **Testability**: Business logic can be tested without Android framework
- **Maintainability**: Changes in one layer don't cascade to others
- **Scalability**: New features can be added without modifying existing code

---

## Architecture Layers

The app is organized into four distinct layers, following the **Dependency Rule**: dependencies point inward. Outer layers depend on inner layers, never the reverse.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       PRESENTATION LAYER (UI)                               │
│                                                                             │
│  ┌──────────────────┐   ┌───────────────────┐  ┌─────────────────────────┐  │
│  │  MainActivity    │   │  LauncherScreen   │  │   AppSearchDialog       │  │
│  │  - Activity      │   │  - Main screen    │  │  - Search UI            │  │
│  │    lifecycle     │   │  - Coordination   │  │  - Grid/List results    │  │
│  └──────────────────┘   └───────────────────┘  └─────────────────────────┘  │
├─────────────────────────────────────────────────────────────────────────────┤
│                       PRESENTATION LAYER (ViewModel)                        │
│                                                                             │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  SearchViewModel                                                       │ │
│  │  - UI state management (SearchUiState)                                 │ │
│  │  - Search logic coordination                                           │ │
│  │  - Action emission (SearchAction)                                      │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────────┤
│                       DOMAIN LAYER (Business Logic)                         │
│                                                                             │
│  ┌──────────────────┐  ┌───────────────────┐  ┌─────────────────────────┐   │
│  │  Models          │  │  Use Cases        │  │  Repository Interfaces  │   │
│  │  - AppInfo       │  │  - FilterApps     │  │  - AppRepository        │   │
│  │  - Contact       │  │  - QueryParser    │  │  - ContactsRepository   │   │
│  │  - SearchResult  │  │                   │  │  - SearchProvider       │   │
│  └──────────────────┘  └───────────────────┘  └─────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────────────┤
│                       DATA LAYER (Implementation)                           │
│                                                                             │
│  ┌──────────────────┐  ┌───────────────────┐  ┌─────────────────────────┐   │
│  │  Repositories    │  │  Search Providers │  │  Registry               │   │
│  │  - AppRepository │  │  - WebSearch      │  │  - SearchProvider       │   │
│  │    Impl          │  │  - ContactsSearch │  │    Registry             │   │
│  │  - ContactsRepo  │  │  - YouTubeSearch  │  │                         │   │
│  │    Impl          │  │                   │  │                         │   │
│  └──────────────────┘  └───────────────────┘  └─────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Layer Responsibilities

### 1. Presentation Layer (UI)

**Location**: `ui/screens/`, `ui/components/`, `MainActivity.kt`

**Responsibilities**:
- Display UI using Jetpack Compose
- Handle user interactions (taps, typing, scrolling)
- Observe state from ViewModel
- Emit user actions to ViewModel

**Key Components**:
- **MainActivity**: Entry point, handles navigation actions, manages permission requests
- **LauncherScreen**: Main home screen with black background and tap-to-search
- **AppSearchDialog**: Full-featured search dialog with text field and results
- **AppGridItem/AppListItem**: Individual app displays (grid for apps, list for mixed results)
- **SearchResultsList**: Container for search results with different layouts

**State Hoisting Pattern**:
```kotlin
// State lives in ViewModel, passed down to UI
SearchViewModel (holds SearchUiState)
    ↓
LauncherScreen (receives state, passes to children)
    ↓
AppSearchDialog (receives query, results, callbacks)
    ↓
SearchResultsList (displays results based on type)
```

---

### 2. Presentation Layer (ViewModel)

**Location**: `presentation/search/SearchViewModel.kt`

**Responsibilities**:
- Manage UI state (SearchUiState)
- Coordinate between UI and Domain layer
- Handle user actions and update state
- Emit one-time actions (SearchAction) for navigation

**State Management**:
```kotlin
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val activeProvider: SearchProvider? = null,
    val errorMessage: String? = null
)
```

**Actions (One-time Events)**:
```kotlin
sealed class SearchAction {
    data class LaunchApp(val appInfo: AppInfo) : SearchAction()
    data class OpenWebSearch(val url: String) : SearchAction()
    data class OpenYouTubeSearch(val query: String) : SearchAction()
    data class CallContact(val phoneNumber: String) : SearchAction()
    data class RequestPermission(val permission: String) : SearchAction()
}
```

**Why StateFlow + SharedFlow?**
- **StateFlow**: For UI state that persists (search query, results list)
- **SharedFlow**: For one-time actions (navigation, permission requests)

---

### 3. Domain Layer

**Location**: `domain/model/`, `domain/repository/`, `domain/search/`

**Responsibilities**:
- Define business logic
- Define data models
- Define repository interfaces (contracts)
- Implement use cases
- **No Android framework dependencies** (except Intent)

**Models**:

#### AppInfo
```kotlin
data class AppInfo(
    val name: String,
    val packageName: String,
    val launchIntent: Intent?
) {
    // Pre-computed lowercase for fast search
    val nameLower: String by lazy { name.lowercase() }
    val packageLower: String by lazy { packageName.lowercase() }
}
```

#### Contact
```kotlin
data class Contact(
    val id: String,
    val displayName: String,
    val phoneNumbers: List<String>,
    val emails: List<String>,
    val photoUri: String?,
    val lookupKey: String
)
```

#### SearchResult (Sealed Class)
```kotlin
sealed class SearchResult {
    abstract val id: String
    abstract val title: String
    
    data class AppResult(val appInfo: AppInfo) : SearchResult()
    data class WebSearchResult(val query: String, val url: String) : SearchResult()
    data class ContactResult(val contact: Contact) : SearchResult()
    data class YouTubeResult(val query: String) : SearchResult()
    data class PermissionRequest(val permission: String, val rationale: String) : SearchResult()
    data class UrlResult(val url: String) : SearchResult()
}
```

**Repository Interfaces**:

```kotlin
// AppRepository.kt
interface AppRepository {
    suspend fun getInstalledApps(): List<AppInfo>
    fun getRecentApps(): Flow<List<AppInfo>>
    suspend fun saveRecentApp(packageName: String)
}

// ContactsRepository.kt
interface ContactsRepository {
    fun hasContactsPermission(): Boolean
    suspend fun searchContacts(query: String): List<Contact>
}

// SearchProvider.kt
interface SearchProvider {
    val config: SearchProviderConfig
    suspend fun search(query: String): List<SearchResult>
    fun canHandle(query: String): Boolean
}
```

**Use Cases**:

```kotlin
// FilterAppsUseCase.kt
class FilterAppsUseCase {
    operator fun invoke(
        query: String,
        installedApps: List<AppInfo>,
        recentApps: List<AppInfo>
    ): List<SearchResult> {
        // Three-tier matching: exact → startsWith → contains
        // Returns up to 8 results for grid display
    }
}

// QueryParser.kt
// Two entry points: one accepts List<SearchProvider>, the other accepts SearchProviderRegistry
// The registry version delegates to the list version for a single implementation

fun parseSearchQuery(input: String, providers: List<SearchProvider>): ParsedQuery {
    // Main implementation with all parsing logic
    // Detects prefixes like "s ", "c ", "y " (must be followed by space)
    // Returns provider type and actual query
}

fun parseSearchQuery(input: String, registry: SearchProviderRegistry): ParsedQuery {
    // Convenience overload that delegates to the main implementation
    // Extracts providers from registry and calls the list version above
    return parseSearchQuery(input, registry.getAllProviders())
}
```

---

### 4. Data Layer

**Location**: `data/repository/`, `data/search/`

**Responsibilities**:
- Implement repository interfaces
- Access external systems (PackageManager, ContentResolver)
- Implement search providers
- Handle data persistence (DataStore)

**Repository Implementations**:

```kotlin
// AppRepositoryImpl.kt
class AppRepositoryImpl(private val context: Context) : AppRepository {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("launcher_prefs")
    private val limitedDispatcher = Dispatchers.IO.limitedParallelism(8)
    
    override suspend fun getInstalledApps(): List<AppInfo> {
        // Query PackageManager with controlled parallelism
        // Process in chunks of 8 to avoid memory spikes
    }
    
    override fun getRecentApps(): Flow<List<AppInfo>> {
        // Read from DataStore, convert package names to AppInfo
    }
    
    override suspend fun saveRecentApp(packageName: String) {
        // Save to DataStore with 5-app limit
    }
}

// ContactsRepositoryImpl.kt
class ContactsRepositoryImpl(private val context: Context) : ContactsRepository {
    override fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    override suspend fun searchContacts(query: String): List<Contact> {
        // Query Contacts ContentProvider
        // Search by name and phone number
    }
}
```

**Search Providers**:

```kotlin
// WebSearchProvider.kt (prefix "s ")
class WebSearchProvider : SearchProvider {
    override val config = SearchProviderConfig(
        prefix = "s",
        name = "Web Search",
        description = "Search the web",
        color = Color(0xFF2196F3), // Blue
        icon = Icons.Default.Search
    )
    
    override suspend fun search(query: String): List<SearchResult> {
        return listOf(
            WebSearchResult(
                query = query,
                url = "https://www.google.com/search?q=${Uri.encode(query)}"
            )
        )
    }
}

// ContactsSearchProvider.kt (prefix "c ")
class ContactsSearchProvider(
    private val contactsRepository: ContactsRepository
) : SearchProvider {
    override val config = SearchProviderConfig(
        prefix = "c",
        name = "Contacts",
        description = "Search contacts",
        color = Color(0xFF4CAF50), // Green
        icon = Icons.Default.Person
    )
    
    override suspend fun search(query: String): List<SearchResult> {
        return if (!contactsRepository.hasContactsPermission()) {
            listOf(PermissionRequest(Manifest.permission.READ_CONTACTS, "..."))
        } else {
            contactsRepository.searchContacts(query)
                .map { SearchResult.ContactResult(it) }
        }
    }
}

// YouTubeSearchProvider.kt (prefix "y ")
class YouTubeSearchProvider(private val context: Context) : SearchProvider {
    override val config = SearchProviderConfig(
        prefix = "y",
        name = "YouTube",
        description = "Search YouTube",
        color = Color(0xFFFF0000), // Red
        icon = Icons.Default.PlayArrow
    )
    
    override suspend fun search(query: String): List<SearchResult> {
        return listOf(YouTubeResult(query))
    }
}
```

---

## Dependency Injection

**Location**: `di/AppContainer.kt`

The app uses a simple **Service Locator** pattern for dependency injection (manual DI without Hilt/Koin for educational clarity):

```kotlin
class AppContainer(private val context: Context) {
    // Repositories
    val appRepository: AppRepository by lazy { AppRepositoryImpl(context) }
    val contactsRepository: ContactsRepository by lazy { ContactsRepositoryImpl(context) }
    
    // Search Providers
    private val webSearchProvider = WebSearchProvider()
    private val contactsSearchProvider = ContactsSearchProvider(contactsRepository)
    private val youTubeSearchProvider = YouTubeSearchProvider(context)
    
    // Registry
    val searchProviderRegistry: SearchProviderRegistry by lazy {
        SearchProviderRegistry(
            listOf(webSearchProvider, contactsSearchProvider, youTubeSearchProvider)
        )
    }
    
    // Use Cases
    val filterAppsUseCase = FilterAppsUseCase()
}
```

**Why Manual DI?**
- Educational clarity for beginners
- No external dependencies
- Easy to understand the flow

**Production Alternative**: Use Hilt or Koin for automatic DI

---

## Data Flow

### 1. Loading Installed Apps

```
MainActivity.onCreate()
    ↓
ViewModel is created
    ↓
SearchViewModel.init
    ↓
Load apps via AppRepository
    ↓
AppRepositoryImpl.getInstalledApps()
    ↓
PackageManager.queryIntentActivities()
    ↓
Process in chunks of 8
    ↓
Update SearchUiState
    ↓
UI recomposes with app list
```

### 2. User Types in Search

```
User types "cal"
    ↓
AppSearchDialog.onQueryChange
    ↓
SearchViewModel.onQueryChange("cal")
    ↓
parseSearchQuery("cal", registry) → No prefix, app search
    ↓
FilterAppsUseCase(query, allApps, recentApps)
    ↓
Three-tier matching (exact → startsWith → contains)
    ↓
Update SearchUiState with results
    ↓
UI displays results in GRID (8 apps max)
```

### 3. User Uses Prefix Search

```
User types "s weather"
    ↓
SearchViewModel.onQueryChange("s weather")
    ↓
parseSearchQuery("s weather", registry) → Web provider, query "weather"
    ↓
SearchProviderRegistry.findProvider("s")
    ↓
WebSearchProvider.search("weather")
    ↓
Update SearchUiState with web result
    ↓
UI displays in LIST (provider results use list)
```

### 4. User Taps App

```
User taps app
    ↓
AppGridItem.onClick
    ↓
SearchViewModel.onAppClicked(app)
    ↓
Emit SearchAction.LaunchApp(app)
    ↓
MainActivity observes action
    ↓
startActivity(app.launchIntent)
    ↓
Save to recent apps via repository
```

### 5. User Searches Contacts Without Permission

```
User types "c mom"
    ↓
ContactsSearchProvider.search("mom")
    ↓
Check permission → Not granted
    ↓
Return PermissionRequest result
    ↓
UI shows permission card
    ↓
User taps "Grant Permission"
    ↓
Emit SearchAction.RequestPermission
    ↓
MainActivity requests permission
    ↓
On result, refresh search
```

---

## Unidirectional Data Flow (UDF)

```
┌─────────────────────────────────────────────────────────────┐
│                        User Action                           │
│                    (tap, type, click)                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     SearchViewModel                          │
│                                                              │
│  1. Process action                                           │
│  2. Call use case or provider                                │
│  3. Update StateFlow<SearchUiState>                         │
│  4. Emit SharedFlow<SearchAction> (if needed)               │
└─────────────────────────────────────────────────────────────┘
                              │
            ┌─────────────────┴─────────────────┐
            ▼                                   ▼
┌───────────────────────┐         ┌─────────────────────────────┐
│   UI State Update     │         │     Action Event            │
│   (StateFlow)         │         │   (SharedFlow)              │
│   - New results       │         │   - Navigate to URL         │
│   - Loading state     │         │   - Launch app              │
│   - Error message     │         │   - Request permission      │
└───────────────────────┘         └─────────────────────────────┘
            │                                   │
            ▼                                   ▼
┌─────────────────────────────────────────────────────────────┐
│                           UI                                 │
│  - Recompose with new state                                  │
│  - Execute action (Activity handles navigation)              │
└─────────────────────────────────────────────────────────────┘
```

---

## Key Patterns

### 1. Plugin Pattern (Search Providers)

Search providers are pluggable - you can add new ones without modifying existing code:

```kotlin
// Step 1: Create provider
class RedditSearchProvider : SearchProvider {
    override val config = SearchProviderConfig(
        prefix = "r",
        name = "Reddit",
        description = "Search Reddit",
        color = Color(0xFFFF4500),
        icon = Icons.Default.Forum
    )
    
    override suspend fun search(query: String): List<SearchResult> {
        // Return RedditSearchResult
    }
}

// Step 2: Register in AppContainer
private val redditSearchProvider = RedditSearchProvider()

val searchProviderRegistry = SearchProviderRegistry(
    listOf(webSearchProvider, contactsSearchProvider, youTubeSearchProvider, redditSearchProvider)
)

// Step 3: Handle action in MainActivity
is SearchAction.OpenRedditSearch -> {
    val url = "https://reddit.com/search?q=${Uri.encode(action.query)}"
    openUrl(url)
}
```

### 2. Repository Pattern

Abstract data sources behind interfaces:

```kotlin
// Domain layer - interface
interface AppRepository {
    suspend fun getInstalledApps(): List<AppInfo>
}

// Data layer - implementation
class AppRepositoryImpl(context: Context) : AppRepository {
    override suspend fun getInstalledApps(): List<AppInfo> {
        // Use PackageManager
    }
}
```

Benefits:
- ViewModel doesn't know about PackageManager
- Can swap implementation (add caching, etc.)
- Easy to mock for testing

### 3. Use Case Pattern

Encapsulate single business operations:

```kotlin
class FilterAppsUseCase {
    operator fun invoke(
        query: String,
        installedApps: List<AppInfo>,
        recentApps: List<AppInfo>
    ): List<SearchResult> {
        // Single responsibility: filter apps
    }
}
```

### 4. Sealed Classes for State and Events

**SearchResult sealed class**:
- Compile-time safety - when expression covers all cases
- Type-specific data - each result type holds relevant info
- Extensible - add new types without breaking existing code

**SearchAction sealed class**:
- One-time events - actions that shouldn't survive recomposition
- Navigation events - handled by Activity
- Permission requests - handled by Activity

---

## SOLID Principles Applied

### S - Single Responsibility

- **SearchViewModel**: Only manages search state
- **FilterAppsUseCase**: Only filters apps
- **WebSearchProvider**: Only provides web search results
- **AppRepositoryImpl**: Only handles data access

### O - Open/Closed

- Add new search providers by implementing SearchProvider interface
- No need to modify existing code
- Registry pattern allows runtime extension

### L - Liskov Substitution

- Any SearchProvider implementation can be used interchangeably
- All repository implementations work with their interfaces
- Substituting implementations doesn't break ViewModel

### I - Interface Segregation

- SearchProvider interface is minimal (config + search + canHandle)
- ContactsRepository has only contacts-specific methods
- AppRepository doesn't expose DataStore details

### D - Dependency Inversion

- ViewModel depends on SearchProviderRegistry (abstraction), not concrete providers
- Use cases depend on repository interfaces, not implementations
- UI depends on ViewModel abstractions (StateFlow), not internals

---

## File Structure

```
app/src/main/java/com/milki/launcher/
├── MainActivity.kt                    # Entry point, handles actions
├── LauncherApplication.kt             # App class, DI container init
├── AppIconFetcher.kt                  # Coil icon loader
│
├── di/
│   └── AppContainer.kt                # Manual DI container
│
├── domain/                            # Business logic
│   ├── model/
│   │   ├── AppInfo.kt                 # App data model
│   │   ├── Contact.kt                 # Contact data model
│   │   ├── SearchResult.kt            # Search result sealed class
│   │   ├── SearchProviderConfig.kt    # Provider display config
│   │   └── AppIconRequest.kt          # Icon loading request
│   │
│   ├── repository/
│   │   ├── AppRepository.kt           # App data interface
│   │   ├── ContactsRepository.kt      # Contacts data interface
│   │   └── SearchProvider.kt          # Search provider interface
│   │
│   └── search/
│       ├── SearchProviderRegistry.kt  # Provider registry
│       ├── QueryParser.kt             # Query parsing logic
│       └── FilterAppsUseCase.kt       # App filtering logic
│
├── data/                              # Implementation
│   ├── repository/
│   │   ├── AppRepositoryImpl.kt       # App data implementation
│   │   └── ContactsRepositoryImpl.kt  # Contacts implementation
│   │
│   └── search/
│       ├── WebSearchProvider.kt       # "s" prefix provider
│       ├── ContactsSearchProvider.kt  # "c" prefix provider
│       └── YouTubeSearchProvider.kt   # "y" prefix provider
│
├── presentation/                      # ViewModels
│   └── search/
│       ├── SearchViewModel.kt         # Search state management
│       ├── SearchUiState.kt           # UI state model
│       └── SearchAction.kt            # Navigation/action events
│
└── ui/                                # UI layer
    ├── screens/
    │   └── LauncherScreen.kt          # Main screen
    │
    ├── components/
    │   ├── AppSearchDialog.kt         # Search dialog
    │   ├── AppGridItem.kt             # Grid item for apps
    │   ├── AppListItem.kt             # List item for apps
    │   ├── SearchResultsList.kt       # Results container
    │   └── SearchResultItems.kt       # Different result types
    │
    └── theme/
        ├── Theme.kt
        ├── Color.kt
        └── Type.kt
```

---

## Testing Strategy

The architecture makes testing straightforward:

### Unit Test for Use Case

```kotlin
@Test
fun `filterApps returns exact matches first`() {
    val useCase = FilterAppsUseCase()
    val result = useCase(
        query = "calc",
        installedApps = testApps,
        recentApps = emptyList()
    )
    
    assertEquals("Calculator", result.first().title)
}
```

### Unit Test for ViewModel

```kotlin
@Test
fun `onQueryChange updates state`() = runTest {
    val viewModel = SearchViewModel(
        appRepository = mockAppRepository,
        providerRegistry = mockRegistry,
        filterAppsUseCase = FilterAppsUseCase()
    )
    
    viewModel.onQueryChange("test")
    
    assertEquals("test", viewModel.uiState.value.query)
}
```

### Unit Test for Provider

```kotlin
@Test
fun `webSearchProvider returns google url`() = runTest {
    val provider = WebSearchProvider()
    val results = provider.search("kotlin")
    
    assertEquals(1, results.size)
    assertTrue(results[0] is SearchResult.WebSearchResult)
    assertTrue((results[0] as SearchResult.WebSearchResult).url.contains("google.com"))
}
```

---

## Performance Optimizations

### 1. Controlled Parallelism

When loading 150+ apps, process in chunks of 8:

```kotlin
resolveInfos.chunked(8).flatMap { chunk ->
    chunk.map { resolveInfo ->
        async { /* load app info */ }
    }.awaitAll()
}
```

Prevents memory spikes and keeps app responsive.

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

Coil is configured to use 15% of available memory for caching app icons.

### 4. Limited Results

ViewModel limits app results to 8 for grid display:

```kotlin
// Grid can comfortably show 8 apps (2 rows × 4 columns)
return (exactMatches + startsWithMatches + containsMatches).take(8)
```

### 5. Efficient Search Algorithm

Three-tier matching ensures relevant results appear first:
1. Exact matches (highest priority)
2. Starts-with matches (medium priority)
3. Contains matches (lowest priority)

---

## Multi-Mode Search Feature

The launcher supports searching different data sources using single-character prefixes:

| Prefix | Mode | Example | Result |
|--------|------|---------|--------|
| _(none)_ | Apps | `calc` | Installed apps (grid layout) |
| `s ` | Web Search | `s weather` | Opens browser with search |
| `c ` | Contacts | `c mom` | Device contacts (requires permission) |
| `y ` | YouTube | `y music` | Opens YouTube with search |

**Note**: The space after prefix is required! `s` searches for apps starting with "s", `s ` activates web search.

### Visual Indicators

When a provider is active, the search dialog shows:
- **Colored bar** at the top (blue for web, green for contacts, red for YouTube)
- **Provider icon** in the search field
- **Updated placeholder** (e.g., "Search the web...")

### Layout Switching

The UI automatically chooses the best layout:

| Scenario | Layout | Reason |
|----------|--------|--------|
| All app results | **Grid** | Compact, efficient for apps (2×4 = 8 apps) |
| Mixed results | **List** | Different result types need more space |
| Provider results | **List** | URLs, phone numbers need horizontal space |

---

## Adding New Features

### Adding a New Search Provider

1. **Create SearchResult type** (if needed):
```kotlin
// domain/model/SearchResult.kt
data class RedditSearchResult(
    val subreddit: String,
    val query: String
) : SearchResult() {
    override val id = "reddit_${subreddit}_$query"
    override val title = "Search r/$subreddit for \"$query\""
}
```

2. **Create provider**:
```kotlin
// data/search/RedditSearchProvider.kt
class RedditSearchProvider : SearchProvider {
    override val config = SearchProviderConfig(
        prefix = "r",
        name = "Reddit",
        description = "Search Reddit",
        color = Color(0xFFFF4500),
        icon = Icons.Default.Forum
    )
    
    override suspend fun search(query: String): List<SearchResult> {
        return listOf(RedditSearchResult("all", query))
    }
}
```

3. **Register in AppContainer**:
```kotlin
private val redditSearchProvider = RedditSearchProvider()

val searchProviderRegistry = SearchProviderRegistry(
    listOf(..., redditSearchProvider)
)
```

4. **Add action**:
```kotlin
// presentation/search/SearchAction.kt
data class OpenRedditSearch(val subreddit: String, val query: String) : SearchAction()
```

5. **Handle in MainActivity**:
```kotlin
is SearchAction.OpenRedditSearch -> {
    val url = "https://reddit.com/r/${action.subreddit}/search?q=${Uri.encode(action.query)}"
    openUrl(url)
}
```

---

## Summary

This architecture provides:

1. **Clear Separation**: Each layer has distinct responsibilities
2. **Testability**: Business logic is isolated and testable
3. **Extensibility**: New features added without modifying existing code
4. **Maintainability**: Changes are localized to specific layers
5. **Performance**: Optimized search, caching, and controlled parallelism

The combination of Clean Architecture + MVVM + Plugin Pattern creates a robust, scalable launcher that can easily accommodate new features while remaining easy to understand and maintain.
