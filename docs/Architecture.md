# Architecture Overview

## Table of Contents

1. [Architecture Layers](#architecture-layers)
2. [Dependency Flow](#dependency-flow)
3. [Key Patterns](#key-patterns)
4. [Adding New Features](#adding-new-features)
5. [File Structure](#file-structure)

---

## Architecture Layers

The app follows Clean Architecture principles with three main layers:

```
┌─────────────────────────────────────────────────────────────────────┐
│                       PRESENTATION LAYER                            │
│                                                                     │
│  ┌──────────────────┐   ┌───────────────────┐  ┌─────────────────┐  │
│  │  SearchViewModel │   │   LauncherScreen  │  │ AppSearchDialog │  │
│  │  (State + Events)│   │   (Compose UI)    │  │ (Compose UI)    │  │
│  └──────────────────┘   └───────────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              │ observes state, emits events
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         DOMAIN LAYER                                │
│                                                                     │
│  ┌──────────────────┐  ┌───────────────────┐    ┌─────────────────┐ │
│  │  SearchProvider  │  │   Use Cases       │    │    Models       │ │
│  │    (interface)   │  │  - FilterApps     │    │ - SearchResult  │ │
│  │                  │  │  - QueryParser    │    │ - AppInfo       │ │
│  └──────────────────┘  └───────────────────┘    │ - Contact       │ │
│                                                 └─────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              │ implements interfaces
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          DATA LAYER                                  │
│                                                                      │
│  ┌──────────────────┐  ┌───────────────────┐  ┌─────────────────┐  │
│  │  WebSearchProvider│  │ ContactsProvider   │  │ YouTubeProvider │  │
│  │  (prefix "s")     │  │ (prefix "c")       │  │ (prefix "y")    │  │
│  └──────────────────┘  └───────────────────┘  └─────────────────┘  │
│                                                                      │
│  ┌──────────────────┐  ┌───────────────────┐                        │
│  │ AppRepositoryImpl │  │ ContactsRepoImpl  │                        │
│  │ (PackageManager)  │  │ (ContentResolver) │                        │
│  └──────────────────┘  └───────────────────┘                        │
└─────────────────────────────────────────────────────────────────────┘
```

### Layer Responsibilities

| Layer        | Responsibility                     | Example Files                                       |
| ------------ | ---------------------------------- | --------------------------------------------------- |
| Presentation | UI rendering, user input handling  | `SearchViewModel.kt`, `LauncherScreen.kt`           |
| Domain       | Business logic, interfaces, models | `SearchProvider.kt`, `FilterAppsUseCase.kt`         |
| Data         | Data access, external systems      | `WebSearchProvider.kt`, `ContactsRepositoryImpl.kt` |

---

## Dependency Flow

### Unidirectional Data Flow (UDF)

```
┌─────────────────────────────────────────────────────────────────┐
│                        User Action                               │
│                    (tap, type, click)                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        ViewModel                                 │
│                                                                  │
│  1. Process action                                               │
│  2. Call use case/repository                                     │
│  3. Update StateFlow<UiState>                                    │
│  4. Emit SharedFlow<Action> (if needed)                          │
└─────────────────────────────────────────────────────────────────┘
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
┌─────────────────────────────────────────────────────────────────┐
│                           UI                                     │
│  - Recompose with new state                                      │
│  - Execute action (Activity handles navigation)                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Key Patterns

### 1. Plugin Pattern (Search Providers)

Search providers are pluggable components that can be added without modifying existing code.

**To add a new search provider:**

```kotlin
// 1. Create the provider implementation
class RedditSearchProvider : SearchProvider {
    override val config = SearchProviderConfig(
        prefix = "r",
        name = "Reddit",
        description = "Search Reddit",
        color = Color(0xFFFF4500),
        icon = Icons.Default.Forum
    )

    override suspend fun search(query: String): List<SearchResult> {
        // Return RedditSearchResult objects
    }
}

// 2. Register in AppContainer
private val redditSearchProvider: RedditSearchProvider by lazy {
    RedditSearchProvider()
}

private val searchProviderRegistry: SearchProviderRegistry by lazy {
    SearchProviderRegistry(
        initialProviders = listOf(
            webSearchProvider,
            contactsSearchProvider,
            youTubeSearchProvider,
            redditSearchProvider  // Add here
        )
    )
}
```

### 2. Use Case Pattern

Use cases encapsulate single business operations.

```kotlin
// FilterAppsUseCase handles app filtering logic
val filteredApps = filterAppsUseCase(
    query = "calc",
    installedApps = allApps,
    recentApps = recentApps
)
```

### 3. Repository Pattern

Repositories abstract data sources.

```kotlin
// Interface in domain layer
interface ContactsRepository {
    fun hasContactsPermission(): Boolean
    suspend fun searchContacts(query: String): List<Contact>
}

// Implementation in data layer
class ContactsRepositoryImpl(context: Context) : ContactsRepository {
    // Uses ContentResolver internally
}
```

### 4. Sealed Classes for State and Events

**State (UiState):** Represents the current UI state

```kotlin
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false
    // ...
)
```

**Events (Actions):** One-time events

```kotlin
sealed class SearchAction {
    data class LaunchApp(val appInfo: AppInfo) : SearchAction()
    data class OpenWebSearch(val url: String) : SearchAction()
    // ...
}
```

---

## Adding New Features

### Adding a New Search Provider

1. **Create the SearchResult type** (if needed):

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

2. **Create the provider implementation**:

   ```kotlin
   // data/search/RedditSearchProvider.kt
   class RedditSearchProvider : SearchProvider { ... }
   ```

3. **Register in AppContainer**:

   ```kotlin
   // di/AppContainer.kt
   private val redditSearchProvider: RedditSearchProvider by lazy { ... }
   ```

4. **Handle the action in MainActivity**:

   ```kotlin
   // presentation/search/SearchAction.kt
   data class OpenRedditSearch(val subreddit: String, val query: String) : SearchAction()

   // MainActivity.kt
   is SearchAction.OpenRedditSearch -> { /* open Reddit */ }
   ```

5. **Add UI component** (if needed):
   ```kotlin
   // ui/components/AppSearchDialog.kt
   is RedditSearchResult -> RedditSearchResultItem(...)
   ```

### Adding a New UI State Property

1. **Add to SearchUiState**:

   ```kotlin
   data class SearchUiState(
       // ... existing properties
       val isNewFeature: Boolean = false
   )
   ```

2. **Update in ViewModel**:

   ```kotlin
   fun toggleNewFeature() {
       updateState { copy(isNewFeature = !isNewFeature) }
   }
   ```

3. **Use in UI**:
   ```kotlin
   if (uiState.isNewFeature) {
       NewFeatureComponent()
   }
   ```

---

## File Structure

```
app/src/main/java/com/milki/launcher/
├── MainActivity.kt              # Entry point, handles navigation
├── LauncherApplication.kt       # App class, initializes DI container
├── AppIconFetcher.kt            # Coil icon loader
│
├── di/
│   └── AppContainer.kt          # Manual DI container
│
├── domain/
│   ├── model/
│   │   ├── AppInfo.kt           # App data model
│   │   ├── Contact.kt           # Contact data model
│   │   ├── SearchResult.kt      # Search result types (no callbacks!)
│   │   ├── SearchProviderConfig.kt  # Provider display config
│   │   └── AppIconRequest.kt    # Icon loading request
│   │
│   ├── repository/
│   │   ├── AppRepository.kt     # App data interface
│   │   ├── ContactsRepository.kt # Contacts data interface
│   │   └── SearchProvider.kt    # Search provider interface
│   │
│   └── search/
│       ├── SearchProviderRegistry.kt  # Provider registry
│       ├── QueryParser.kt        # Query parsing logic
│       └── FilterAppsUseCase.kt  # App filtering logic
│
├── data/
│   ├── repository/
│   │   ├── AppRepositoryImpl.kt  # App data implementation
│   │   └── ContactsRepositoryImpl.kt # Contacts implementation
│   │
│   └── search/
│       ├── WebSearchProvider.kt   # "s" prefix provider
│       ├── ContactsSearchProvider.kt # "c" prefix provider
│       └── YouTubeSearchProvider.kt  # "y" prefix provider
│
├── presentation/
│   └── search/
│       ├── SearchViewModel.kt    # Search state management
│       ├── SearchUiState.kt      # UI state model
│       └── SearchAction.kt       # Navigation/action events
│
└── ui/
    ├── screens/
    │   └── LauncherScreen.kt     # Main screen
    ├── components/
    │   ├── AppSearchDialog.kt    # Search dialog
    │   └── AppListItem.kt        # App list item
    └── theme/
        ├── Theme.kt
        ├── Color.kt
        └── Type.kt
```

---

## SOLID Principles Applied

### S - Single Responsibility

- `SearchViewModel`: Only manages search state
- `FilterAppsUseCase`: Only filters apps
- `WebSearchProvider`: Only provides web search results

### O - Open/Closed

- Add new providers by implementing `SearchProvider` interface
- No need to modify existing code

### L - Liskov Substitution

- Any `SearchProvider` implementation can be used interchangeably
- All repositories implement their interfaces correctly

### I - Interface Segregation

- `SearchProvider` interface is minimal (config + search)
- `ContactsRepository` has only contacts-specific methods

### D - Dependency Inversion

- ViewModel depends on `SearchProviderRegistry` (interface), not concrete providers
- Use cases depend on repository interfaces, not implementations

---

## Testing Strategy

The architecture makes testing straightforward:

```kotlin
// Unit test for FilterAppsUseCase
@Test
fun `filterApps returns exact matches first`() {
    val useCase = FilterAppsUseCase()
    val result = useCase(
        query = "calc",
        installedApps = testApps,
        recentApps = emptyList()
    )

    assertEquals("Calculator", result.first().name)
}

// Unit test for SearchViewModel with mock repository
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
