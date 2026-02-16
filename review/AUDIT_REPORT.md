# Codebase Audit Report

**Date**: 2026-02-16  
**Project**: Milki Launcher  
**Scope**: app/src/main/java/com/milki/launcher/

---

## Executive Summary

The Milki Launcher codebase demonstrates solid Clean Architecture foundations with proper separation of concerns. However, it suffers from **dead code**, **over-engineering**, and **excessive documentation** that obscures the actual implementation.

**Estimated cleanup**: ~400 lines can be removed without losing functionality.

---

## 🔴 CRITICAL - Remove Dead Code

### 1. SearchUiStateBuilder (presentation/search/SearchUiState.kt:96-131)

**Issue**: Completely unused builder class

```kotlin
// UNUSED - Remove entirely
class SearchUiStateBuilder {
    private var query: String = ""
    // ... 30+ lines of builder methods ...
    fun build() = SearchUiState(...)
}
```

**Why Remove**: 
- Never instantiated anywhere in the codebase
- Data class `copy()` provides same functionality
- Creates confusion for beginners

**Replacement**:
```kotlin
// Use existing data class copy function:
val newState = currentState.copy(query = "new query")
```

---

## 🟡 HIGH - Simplify Over-Engineering

### 4. Chunked Parallelism in AppRepositoryImpl

**File**: data/repository/AppRepositoryImpl.kt:72, 109-125

**Current** (over-engineered):
```kotlin
private val limitedDispatcher = Dispatchers.IO.limitedParallelism(8) // Line 72

// Lines 109-125
resolveInfos.chunked(8).flatMap { chunk ->
    chunk.map { resolveInfo ->
        async {
            AppInfo(
                name = resolveInfo.loadLabel(pm).toString(),
                packageName = resolveInfo.activityInfo.packageName,
                launchIntent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
            )
        }
    }.awaitAll()
}.sortedBy { it.nameLower }
```

**Problems**:
- Premature optimization - PackageManager ops are already cached
- Creates 150+ async tasks for simple data mapping
- Magic number "8" appears without context

**Simplified**:
```kotlin
override suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = application.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    
    pm.queryIntentActivities(mainIntent, 0).map { resolveInfo ->
        AppInfo(
            name = resolveInfo.loadLabel(pm).toString(),
            packageName = resolveInfo.activityInfo.packageName,
            launchIntent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
        )
    }.sortedBy { it.nameLower }
}
```

**Also Remove**: 
- `limitedDispatcher` field (line 72)
- Import statements for `async` and `awaitAll`

---

### 5. QueryParser Duplicate Code

**File**: domain/search/QueryParser.kt:49-100 vs 112-156

**Issue**: 90% identical logic in two overloaded functions

**Consolidated Version**:
```kotlin
fun parseSearchQuery(
    input: String,
    registry: SearchProviderRegistry
): ParsedQuery {
    return parseSearchQuery(input, registry.getAllProviders())
}

fun parseSearchQuery(
    input: String,
    providers: List<SearchProvider>
): ParsedQuery {
    if (input.isEmpty()) {
        return ParsedQuery(null, "", null)
    }

    // Check for provider prefix + space
    for (provider in providers) {
        val prefixWithSpace = provider.config.prefix + " "
        if (input.startsWith(prefixWithSpace)) {
            return ParsedQuery(
                provider = provider,
                query = input.substring(prefixWithSpace.length),
                config = provider.config
            )
        }
    }

    // Check single char prefix without space (treat as app search)
    val trimmed = input.trim()
    if (trimmed.length == 1 && providers.any { it.config.prefix == trimmed }) {
        return ParsedQuery(null, input, null)
    }

    return ParsedQuery(null, input, null)
}
```

**Benefit**: ~50 lines reduced, single source of truth.

---

## 🟠 MEDIUM - Architecture Improvements

### 6. Extract URL Detection to Use Case

**File**: presentation/search/SearchViewModel.kt:333-381

**Issue**: Complex URL logic in ViewModel violates Single Responsibility Principle

**Create New File**: domain/search/DetectUrlUseCase.kt

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

**Update ViewModel**:
```kotlin
class SearchViewModel(
    private val appRepository: AppRepository,
    private val providerRegistry: SearchProviderRegistry,
    private val filterAppsUseCase: FilterAppsUseCase,
    private val detectUrlUseCase: DetectUrlUseCase = DetectUrlUseCase() // Default for simplicity
) : ViewModel() {
    // ...
    private fun performSearch(query: String) {
        // ...
        val urlResult = detectUrlUseCase(parsed.query) // Much cleaner!
        // ...
    }
}
```

---

### 7. Add Search Debouncing

**File**: presentation/search/SearchViewModel.kt:162-164

**Issue**: Every keystroke immediately triggers search

**Problems**:
- Wasted CPU cycles on fast typers
- Potential race conditions
- Battery drain

**Fix**:
```kotlin
class SearchViewModel(...) : ViewModel() {
    
    init {
        loadInstalledApps()
        observeRecentApps()
        setupSearchDebouncing() // Add this
    }
    
    private fun setupSearchDebouncing() {
        viewModelScope.launch {
            _uiState
                .map { it.query }
                .distinctUntilChanged()
                .debounce(150) // 150ms wait after last keystroke
                .collect { query ->
                    performSearch(query)
                }
        }
    }
    
    fun onQueryChange(newQuery: String) {
        updateState { copy(query = newQuery) }
        // Remove: performSearch(newQuery) - now handled by Flow
    }
}
```

---

## 🟢 LOW - Code Quality

### 8. Extract Magic Numbers to Constants

**Create**: domain/search/SearchConfig.kt

```kotlin
package com.milki.launcher.domain.search

object SearchConfig {
    const val MAX_APP_RESULTS = 8
    const val MAX_RECENT_APPS = 5
    const val APP_GRID_COLUMNS = 4
    const val PARALLELISM_LIMIT = 8
    const val SEARCH_DEBOUNCE_MS = 150L
    const val DIALOG_WIDTH_PERCENT = 0.9f
    const val DIALOG_HEIGHT_PERCENT = 0.8f
}
```

**Update usages**:
```kotlin
// In SearchViewModel
val limitedApps = filteredApps.take(SearchConfig.MAX_APP_RESULTS)

// In AppRepositoryImpl
preferences[recentAppsKey] = recentPackages.take(SearchConfig.MAX_RECENT_APPS).joinToString(",")

// In AppSearchDialog
.fillMaxWidth(SearchConfig.DIALOG_WIDTH_PERCENT)
.fillMaxHeight(SearchConfig.DIALOG_HEIGHT_PERCENT)
```

---

### 9. Fix Fake Contact Objects

**File**: data/search/ContactsSearchProvider.kt:79-91, 98-109

**Issue**: Creating fake Contact objects for UI hints violates type safety

**Current**:
```kotlin
// Creating fake contacts for hints - BAD PRACTICE
return listOf(
    ContactSearchResult(
        contact = Contact(
            id = -1,
            displayName = "Type to search contacts",
            phoneNumbers = emptyList(),
            // ... fake data
        )
    )
)
```

**Better - Add to SearchResult sealed class**:
```kotlin
// domain/model/SearchResult.kt
sealed class SearchResult {
    // ... existing types ...
    
    object ContactHint : SearchResult() // No fake contact needed
    
    data class ContactEmpty(val query: String) : SearchResult()
}
```

**Update Provider**:
```kotlin
override suspend fun search(query: String): List<SearchResult> {
    if (!contactsRepository.hasContactsPermission()) {
        return listOf(PermissionRequestResult(...))
    }
    
    if (query.isBlank()) {
        return listOf(SearchResult.ContactHint) // Clean!
    }
    
    val contacts = contactsRepository.searchContacts(query)
    return if (contacts.isEmpty()) {
        listOf(SearchResult.ContactEmpty(query))
    } else {
        contacts.map { ContactSearchResult(it) }
    }
}
```

**Update UI**:
```kotlin
// In SearchResultItems.kt
is SearchResult.ContactHint -> Text("Type to search contacts...")
is SearchResult.ContactEmpty -> Text("No contacts found for \"${it.query}\"")
```

---

### 10. Move Hardcoded Strings to Resources

**Files with hardcoded strings**:
- `SearchUiState.kt:93`: `"Prefix shortcuts:\ns - Web search..."`
- `ContactsSearchProvider.kt:70-71`: Permission messages
- `SearchResultItems.kt`: Empty state messages

**Fix**: Move to `res/values/strings.xml`:
```xml
<string name="search_prefix_hint">Prefix shortcuts:\ns - Web search\nc - Contacts\ny - YouTube</string>
<string name="contacts_permission_required">Contacts permission required to search contacts</string>
<string name="grant_permission">Grant Permission</string>
<string name="no_recent_apps">No recent apps</string>
<string name="no_apps_found">No apps found</string>
```

---

## 📊 Documentation Issues

### 11. Excessive Tutorial Comments

**Problem**: Paragraph-length explanations of basic Kotlin/Android concepts

**Examples**:
- Explaining what `suspend` means
- Explaining what a data class is
- Explaining imports

**Recommendation**:

| Keep | Remove |
|------|--------|
| Architecture decisions | Explanations of standard Kotlin features |
| "Why" comments | "What" comments (code is self-explanatory) |
| Non-obvious behavior | Basic language concepts |
| Launcher-specific logic | Generic Android tutorials |

**Example Cleanup**:

**Before** (Theme.kt):
```kotlin
/**
 * FontFamily.Default:
 * - Uses the device's default font (Roboto on most Android devices)
 * - Ensures consistency with system UI
 * - Respects user font preferences
 *
 * FontWeight.Normal:
 * - Regular weight (400)
 * - Other options: Bold (700), Light (300), Medium (500)
 */
fontFamily = FontFamily.Default
```

**After**:
```kotlin
// Using system default font for consistency with device theme
fontFamily = FontFamily.Default
```

---

## 📈 Metrics

| File | Current Lines | After Cleanup | Reduction |
|------|--------------|---------------|-----------|
| SearchUiState.kt | 131 | ~70 | -61 |
| FilterAppsUseCase.kt | 72 | ~34 | -38 |
| QueryParser.kt | 157 | ~60 | -97 |
| AppRepositoryImpl.kt | 212 | ~160 | -52 |
| AppContainer.kt | 192 | ~165 | -27 |
| **Total** | **~1000** | **~600** | **~400 lines** |

---

## 🎯 Implementation Priority

### Phase 1: Quick Wins (Immediate)
1. Delete `SearchUiStateBuilder`
2. Remove accessor methods from `AppContainer`
3. Simplify `AppRepositoryImpl.getInstalledApps()`
4. Consolidate `QueryParser` functions

### Phase 2: Architecture (Next Sprint)
1. Create `DetectUrlUseCase`
2. Add search debouncing
3. Extract constants to `SearchConfig`
4. Fix fake Contact objects

### Phase 3: Polish (Ongoing)
1. Move strings to resources
2. Clean up tutorial-style comments
3. Add unit tests for use cases

---

## ✅ Validation Checklist

After implementing changes:

- [ ] App compiles without errors
- [ ] Search works for apps
- [ ] Prefix search works (s, c, y)
- [ ] URL detection works
- [ ] Contacts permission flow works
- [ ] Recent apps display correctly
- [ ] Grid layout shows max 8 apps
- [ ] All existing functionality preserved

---

## Summary

The core architecture is **solid**. These recommendations focus on:

1. **Removing dead code** that creates confusion
2. **Simplifying over-engineered** solutions
3. **Extracting responsibilities** to appropriate layers
4. **Improving maintainability** with constants and type safety

**Bottom line**: Less code, same functionality, easier to understand.
