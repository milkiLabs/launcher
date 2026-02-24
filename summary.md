# Source Files Summary

| Category         | File                                              | Purpose                                                                |
| ---------------- | ------------------------------------------------- | ---------------------------------------------------------------------- |
| Application      | LauncherApplication.kt                            | Custom Application class, DI container holder, Coil ImageLoader config |
| Activities       | MainActivity.kt                                   | Main launcher home screen                                              |
|                  | SettingsActivity.kt                               | Settings screen                                                        |
| ViewModels       | SearchViewModel.kt                                | Search state management, coordinates search providers                  |
|                  | SettingsViewModel.kt                              | Settings state management                                              |
| DI               | AppContainer.kt                                   | Manual DI container with lazy singletons and ViewModel factories       |
| Repositories     | AppRepository.kt / AppRepositoryImpl.kt           | Installed apps, recent apps                                            |
|                  | ContactsRepository.kt / ContactsRepositoryImpl.kt | Contacts access, recent contacts                                       |
|                  | FilesRepository.kt / FilesRepositoryImpl.kt       | Document file search                                                   |
|                  | SettingsRepository.kt / SettingsRepositoryImpl.kt | Settings persistence                                                   |
| Search Providers | WebSearchProvider.kt                              | "s" prefix - web search                                                |
|                  | ContactsSearchProvider.kt                         | "c" prefix - contacts search                                           |
|                  | FilesSearchProvider.kt                            | "f" prefix - files search                                              |
|                  | YouTubeSearchProvider.kt                          | "y" prefix - YouTube search                                            |
| Domain           | SearchProviderRegistry.kt                         | Registry pattern for providers                                         |
|                  | FilterAppsUseCase.kt                              | App filtering logic                                                    |
|                  | UrlHandlerResolver.kt                             | URL deep link resolution                                               |
|                  | QueryParser.kt                                    | Search query parsing                                                   |
| Presentation     | ActionExecutor.kt                                 | Executes search result actions                                         |
|                  | SearchResultAction.kt                             | Sealed class for action types                                          |
|                  | SearchUiState.kt                                  | UI state data class                                                    |
|                  | LocalSearchActionHandler.kt                       | CompositionLocal for action handling                                   |
| Models           | AppInfo.kt                                        | Installed app data                                                     |
|                  | Contact.kt                                        | Contact data                                                           |
|                  | FileDocument.kt                                   | File data                                                              |
|                  | LauncherSettings.kt                               | Settings data                                                          |
|                  | SearchResult.kt                                   | Sealed class for result types                                          |
|                  | SearchProviderConfig.kt                           | Provider config data                                                   |
| Utilities        | PermissionHandler.kt                              | Permission management                                                  |
|                  | PermissionUtil.kt                                 | Permission checking utilities                                          |
|                  | MimeTypeUtil.kt                                   | MIME type utilities                                                    |
| Coil             | AppIconFetcher.kt                                 | Custom Coil fetcher for app icons                                      |
| UI               | Various Compose files                             | UI components, screens, theme                                          |

---

Prefix Implementation Summary

1. Where Prefixes Are Defined and Used
   Core Configuration File: SearchProviderConfig.kt
   File: /media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/domain/model/SearchProviderConfig.kt
   // Lines 53-59
   data class SearchProviderConfig(
   val prefix: String, // e.g., "s", "c", "y", "f"
   val name: String, // e.g., "Web Search", "Contacts"
   val description: String, // e.g., "Search the web"
   val color: Color, // Visual indicator color
   val icon: ImageVector // Icon for the mode
   )
   Provider Implementations (Prefix Definitions):
   | Provider | Prefix | File Path | Line |
   |----------|--------|-----------|------|
   | Web Search | "s" | data/search/WebSearchProvider.kt | Line 40-46 |
   | Contacts | "c" | data/search/ContactsSearchProvider.kt | Line 44-50 |
   | YouTube | "y" | data/search/YouTubeSearchProvider.kt | Line 37-43 |
   | Files | "f" | data/search/FilesSearchProvider.kt | Line 40-46 |
   Example from WebSearchProvider.kt (Lines 40-46):
   override val config: SearchProviderConfig = SearchProviderConfig(
   prefix = "s",
   name = "Web Search",
   description = "Search the web",
   color = androidx.compose.ui.graphics.Color(0xFF4285F4), // Google Blue
   icon = Icons.Default.Search
   )

---

2. How Search/Prefix Matching Logic Works
   Core Parsing File: QueryParser.kt
   File: /media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/domain/search/QueryParser.kt
   Key Rules (Lines 7-10):

- - A prefix only activates when followed by a space: "s cats" → web search for "cats"
- - Typing just "s" searches apps that start with "s", NOT web search
- - This prevents accidentally triggering providers while typing app names
    Main Parsing Function (Lines 60-124):
    fun parseSearchQuery(
    input: String,
    providers: List<SearchProvider>
    ): ParsedQuery {
    // ... empty check ...
    // Check if input starts with a provider prefix followed by a space
    for (provider in providers) {
    val prefixWithSpace = provider.config.prefix + " "
    if (input.startsWith(prefixWithSpace)) {
    val query = input.substring(prefixWithSpace.length)
    return ParsedQuery(
    provider = provider,
    query = query,
    config = provider.config
    )
    }
    }

        // No provider prefix with space found
        // Check if user typed just a single prefix character (without space)
        // Returns as app search (not provider mode)
        // ...

    }
    ParsedQuery Result (Lines 31-35):
    data class ParsedQuery(
    val provider: SearchProvider?, // null if no prefix matched
    val query: String, // Actual query without prefix
    val config: SearchProviderConfig?
    )
    Provider Registry: SearchProviderRegistry.kt
    File: /media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/domain/search/SearchProviderRegistry.kt
    // Lines 44-120
    class SearchProviderRegistry(
    initialProviders: List<SearchProvider> = emptyList()
    ) {
    // Map of prefix to provider for O(1) lookup
    private val providersByPrefix: MutableMap<String, SearchProvider> = mutableMapOf()
    fun findByPrefix(prefix: String): SearchProvider? {
    return providersByPrefix[prefix]
    }

        fun getAllProviders(): List<SearchProvider> {
            return providersByPrefix.values.toList()
        }

    }
    Search Execution in ViewModel: SearchViewModel.kt
    File: /media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/presentation/search/SearchViewModel.kt
    // Lines 285-347 (performSearch function)
    private fun performSearch(query: String) {
    searchJob = viewModelScope.launch(Dispatchers.Default) {
    val parsed = parseSearchQuery(query, providerRegistry)
    withContext(Dispatchers.Main) {
    updateState { copy(activeProviderConfig = parsed.config) }
    }
    if (parsed.provider != null) {
    // Provider search (prefix was detected)
    val results = parsed.provider.search(parsed.query)
    // ...
    } else {
    // No provider prefix - filter apps
    val filteredApps = filterAppsUseCase(...)
    // ...
    }
    }
    }

---

3. Settings/Configuration System Architecture
   Settings Model: LauncherSettings.kt
   File: /media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/domain/model/LauncherSettings.kt
   // Lines 113-126 - Provider toggle settings
   data class LauncherSettings(
   // ...
   /** Default search engine for web search (prefix "s") \*/
   val defaultSearchEngine: SearchEngine = SearchEngine.GOOGLE,
   /** Whether web search provider is enabled _/
   val webSearchEnabled: Boolean = true,
   /\*\* Whether contacts search provider is enabled _/
   val contactsSearchEnabled: Boolean = true,
   /** Whether YouTube search provider is enabled \*/
   val youtubeSearchEnabled: Boolean = true,
   /** Whether files search provider is enabled \*/
   val filesSearchEnabled: Boolean = true,
   // ...
   )
   Settings ViewModel: SettingsViewModel.kt
   File: /media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/presentation/settings/SettingsViewModel.kt
   // Lines 104-122 - Provider enable/disable functions
   fun setDefaultSearchEngine(engine: SearchEngine) {
   updateSetting { it.copy(defaultSearchEngine = engine) }
   }
   fun setWebSearchEnabled(value: Boolean) {
   updateSetting { it.copy(webSearchEnabled = value) }
   }
   fun setContactsSearchEnabled(value: Boolean) {
   updateSetting { it.copy(contactsSearchEnabled = value) }
   }
   fun setYoutubeSearchEnabled(value: Boolean) {
   updateSetting { it.copy(youtubeSearchEnabled = value) }
   }
   fun setFilesSearchEnabled(value: Boolean) {
   updateSetting { it.copy(filesSearchEnabled = value) }
   }
   Dependency Injection: AppModule.kt
   File: /media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/di/AppModule.kt
   // Lines 209-220 - Provider registration
   single {
   SearchProviderRegistry(
   initialProviders = listOf(
   get<WebSearchProvider>(),
   get<ContactsSearchProvider>(),
   get<FilesSearchProvider>(),
   get<YouTubeSearchProvider>()
   )
   )
   }

---

4. Existing Prefix-Related Constants and Enums
   Search Engine Enum (in LauncherSettings.kt, Lines 18-24):
   enum class SearchEngine(val displayName: String, val urlTemplate: String) {
   GOOGLE("Google", "https://www.google.com/search?q=%s"),
   DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
   BING("Bing", "https://www.bing.com/search?q=%s"),
   BRAVE("Brave Search", "https://search.brave.com/search?q=%s"),
   STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=%s")
   }
   UI State Prefix Hints (in SearchUiState.kt, Lines 96-99):
   val prefixHint: String
   get() = "Prefix shortcuts:\ns - Web search\nc - Contacts\nf - Files\ny - YouTube"
   Placeholder Text by Prefix (in SearchUiState.kt, Lines 80-87):
   val placeholderText: String
   get() = when (activeProviderConfig?.prefix) {
   "s" -> "Search the web..."
   "c" -> "Search contacts..."
   "y" -> "Search YouTube..."
   "f" -> "Search files..."
   else -> "Search apps..."
   }

Key Files Summary
| File | Purpose | Key Lines |
|------|---------|-----------|
| domain/model/SearchProviderConfig.kt | Prefix configuration data class | 53-59 |
| domain/repository/SearchProvider.kt | SearchProvider interface | 56-73 |
| domain/search/QueryParser.kt | Prefix detection logic | 60-124 |
| domain/search/SearchProviderRegistry.kt | Provider registry | 44-120 |
| data/search/WebSearchProvider.kt | "s" prefix definition | 40-46 |
| data/search/ContactsSearchProvider.kt | "c" prefix definition | 44-50 |
| data/search/YouTubeSearchProvider.kt | "y" prefix definition | 37-43 |
| data/search/FilesSearchProvider.kt | "f" prefix definition | 40-46 |
| domain/model/LauncherSettings.kt | Provider enable/disable settings | 113-126 |
| presentation/search/SearchViewModel.kt | Search execution logic | 285-347 |
| presentation/search/SearchUiState.kt | UI state with prefix hints | 80-99 |
| di/AppModule.kt | Provider registration | 209-220 |
