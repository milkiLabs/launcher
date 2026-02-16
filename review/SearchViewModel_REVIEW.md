# Code Review - SearchViewModel.kt

## File
`app/src/main/java/com/milki/launcher/presentation/search/SearchViewModel.kt`

## Current Issues

### 1. MEDIUM: Missing Search Debouncing

**Lines 162-165**: Every keystroke immediately triggers search

```kotlin
fun onQueryChange(newQuery: String) {
    updateState { copy(query = newQuery) }
    performSearch(newQuery) // Called immediately on every keystroke!
}
```

**Problems**:
- Wasted CPU cycles (searches for "c", "ca", "cal", "calc" when user types "calc")
- Potential race conditions (older search overwriting newer results)
- Battery drain on fast typers
- Unnecessary provider queries

---

### 2. MEDIUM: URL Detection in ViewModel

**Lines 333-381**: Complex URL logic violates Single Responsibility Principle

```kotlin
private fun detectUrl(query: String): UrlSearchResult? {
    // 50 lines of URL parsing logic
    // Should be in a use case
}
```

**Problem**: ViewModel should coordinate, not implement business logic.

---

### 3. LOW: Magic Numbers

**Line 295**: Hardcoded limit

```kotlin
val limitedApps = filteredApps.take(8) // What is 8? Why 8?
```

---

## Improved Version

```kotlin
package com.milki.launcher.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.domain.model.*
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.search.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SearchViewModel(
    private val appRepository: AppRepository,
    private val providerRegistry: SearchProviderRegistry,
    private val filterAppsUseCase: FilterAppsUseCase,
    private val detectUrlUseCase: DetectUrlUseCase = DetectUrlUseCase()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _action = MutableSharedFlow<SearchAction>()
    val action: SharedFlow<SearchAction> = _action.asSharedFlow()

    init {
        loadInstalledApps()
        observeRecentApps()
        setupSearchDebouncing()
    }

    // ====================================================================
    // SETUP
    // ====================================================================

    private fun setupSearchDebouncing() {
        viewModelScope.launch {
            _uiState
                .map { it.query }
                .distinctUntilChanged()
                .debounce(SearchConfig.SEARCH_DEBOUNCE_MS)
                .collect { query ->
                    performSearch(query)
                }
        }
    }

    // ====================================================================
    // DATA LOADING
    // ====================================================================

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = appRepository.getInstalledApps()
            updateState { copy(installedApps = apps) }
        }
    }

    private fun observeRecentApps() {
        viewModelScope.launch {
            appRepository.getRecentApps()
                .collect { recentApps ->
                    updateState { copy(recentApps = recentApps) }
                }
        }
    }

    // ====================================================================
    // PUBLIC API
    // ====================================================================

    fun showSearch() {
        updateState { copy(isSearchVisible = true) }
        // Empty query will trigger debounced search via Flow
    }

    fun hideSearch() {
        updateState {
            copy(
                isSearchVisible = false,
                query = "",
                results = emptyList(),
                activeProviderConfig = null
            )
        }
    }

    fun onQueryChange(newQuery: String) {
        updateState { copy(query = newQuery) }
        // Search happens automatically via debounced Flow
    }

    fun onResultClick(result: SearchResult) {
        val action = when (result) {
            is AppSearchResult -> SearchAction.LaunchApp(result.appInfo)
            is WebSearchResult -> SearchAction.OpenWebSearch(
                url = result.url,
                query = result.query,
                engine = result.engine
            )
            is YouTubeSearchResult -> SearchAction.OpenYouTubeSearch(result.query)
            is UrlSearchResult -> SearchAction.OpenUrl(result.url)
            is ContactSearchResult -> {
                val phone = result.contact.phoneNumbers.firstOrNull()
                if (phone != null) {
                    SearchAction.CallContact(result.contact, phone)
                } else {
                    SearchAction.CloseSearch
                }
            }
            is PermissionRequestResult -> SearchAction.RequestContactsPermission
        }

        emitAction(action)

        if (action.shouldCloseSearch()) {
            hideSearch()
        }
    }

    fun updateContactsPermission(hasPermission: Boolean) {
        updateState { copy(hasContactsPermission = hasPermission) }
        
        val currentState = _uiState.value
        if (currentState.activeProviderConfig?.prefix == "c") {
            performSearch(currentState.query)
        }
    }

    fun saveRecentApp(packageName: String) {
        viewModelScope.launch {
            appRepository.saveRecentApp(packageName)
        }
    }

    fun clearQuery() {
        updateState {
            copy(query = "", results = emptyList(), activeProviderConfig = null)
        }
    }

    // ====================================================================
    // SEARCH LOGIC
    // ====================================================================

    private fun performSearch(query: String) {
        val parsed = parseSearchQuery(query, providerRegistry)

        updateState {
            copy(activeProviderConfig = parsed.config)
        }

        viewModelScope.launch {
            if (parsed.provider != null) {
                updateState { copy(isLoading = true) }

                try {
                    val results = parsed.provider.search(parsed.query)
                    updateState {
                        copy(results = results, isLoading = false)
                    }
                } catch (e: Exception) {
                    // TODO: Emit error action
                    updateState { copy(isLoading = false, results = emptyList()) }
                }
            } else {
                val state = _uiState.value
                
                val urlResult = detectUrlUseCase(parsed.query)
                
                val filteredApps = filterAppsUseCase(
                    query = parsed.query,
                    installedApps = state.installedApps,
                    recentApps = state.recentApps
                )

                val limitedApps = filteredApps.take(SearchConfig.MAX_APP_RESULTS)

                val appResults = limitedApps.map { app ->
                    AppSearchResult(appInfo = app)
                }

                val results = if (urlResult != null) {
                    listOf(urlResult) + appResults
                } else {
                    appResults
                }

                updateState { copy(results = results) }
            }
        }
    }

    // ====================================================================
    // HELPERS
    // ====================================================================

    private inline fun updateState(transform: SearchUiState.() -> SearchUiState) {
        _uiState.update { it.transform() }
    }

    private fun emitAction(action: SearchAction) {
        viewModelScope.launch {
            _action.emit(action)
        }
    }
}
```

**Key Changes**:
1. Added `setupSearchDebouncing()` with 150ms debounce
2. Removed `performSearch()` call from `onQueryChange()`
3. Extracted URL detection to `DetectUrlUseCase`
4. Used `SearchConfig.MAX_APP_RESULTS` constant
5. Cleaner separation of concerns

---

## New Files to Create

### 1. DetectUrlUseCase.kt

```kotlin
package com.milki.launcher.domain.search

import android.util.Patterns
import com.milki.launcher.domain.model.UrlSearchResult

class DetectUrlUseCase {
    
    private val commonTlds = listOf(
        ".com", ".org", ".net", ".io", ".co", ".edu", ".gov",
        ".dev", ".app", ".me", ".tech", ".xyz", ".info", ".biz"
    )
    
    private val allowedSchemes = listOf("http", "https")

    operator fun invoke(query: String): UrlSearchResult? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null

        // Full URL with scheme
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return if (isValidUrl(trimmed)) {
                UrlSearchResult(url = trimmed, displayUrl = trimmed)
            } else null
        }

        // Domain-only check
        if (commonTlds.any { trimmed.contains(it, ignoreCase = true) }) {
            val urlWithScheme = "https://$trimmed"
            if (isValidUrl(urlWithScheme)) {
                return UrlSearchResult(url = urlWithScheme, displayUrl = trimmed)
            }
        }

        return null
    }

    private fun isValidUrl(url: String): Boolean {
        return Patterns.WEB_URL.matcher(url).matches() &&
               allowedSchemes.any { url.startsWith("$it://") }
    }
}
```

### 2. SearchConfig.kt

```kotlin
package com.milki.launcher.domain.search

object SearchConfig {
    const val MAX_APP_RESULTS = 8
    const val MAX_RECENT_APPS = 5
    const val APP_GRID_COLUMNS = 4
    const val SEARCH_DEBOUNCE_MS = 150L
    const val DIALOG_WIDTH_PERCENT = 0.9f
    const val DIALOG_HEIGHT_PERCENT = 0.8f
}
```

---

## Debounce Flow Diagram

```
User types: "c-a-l-c"

Without Debounce:
t=0ms: Type 'c' → Search "c" → 20 results
  ↓
t=50ms: Type 'a' → Search "ca" → 15 results (overwrites)
  ↓
t=100ms: Type 'l' → Search "cal" → 10 results (overwrites)
  ↓
t=150ms: Type 'c' → Search "calc" → 3 results (overwrites)
  ↓
Total: 4 searches, 3 wasted, race condition risk

With Debounce (150ms):
t=0ms: Type 'c' → Queue search
  ↓
t=50ms: Type 'a' → Reset timer
  ↓
t=100ms: Type 'l' → Reset timer
  ↓
t=150ms: Type 'c' → Reset timer
  ↓
t=300ms: Timer expires → Search "calc" → 3 results
  ↓
Total: 1 search, accurate results, no race conditions
```

---

## Testing Debouncing

```kotlin
@Test
fun `search is debounced`() = runTest {
    val viewModel = SearchViewModel(...)
    
    viewModel.onQueryChange("c")
    viewModel.onQueryChange("ca")
    viewModel.onQueryChange("calc")
    
    // Immediately - no search yet
    assertTrue(viewModel.uiState.value.isLoading.not())
    
    // Advance time by 150ms
    advanceTimeBy(150)
    
    // Now search should run
    assertTrue(viewModel.uiState.value.isLoading)
}
```

---

## Action Items

- [ ] Create `DetectUrlUseCase` class
- [ ] Create `SearchConfig` object
- [ ] Add debouncing to ViewModel
- [ ] Remove `detectUrl()` method from ViewModel
- [ ] Update AppContainer to provide `DetectUrlUseCase`
- [ ] Add unit tests for debouncing
- [ ] Add unit tests for URL detection

## Verification

After changes:
1. Typing "calc" quickly only searches once for "calc" (not c, ca, cal, calc)
2. URL detection still works
3. No functional changes to search behavior
4. Performance improved on fast typing

**Risk**: Medium. Ensure debounce timing feels responsive (150ms is good default).
