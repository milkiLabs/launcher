# Code Review - SearchUiState.kt

## File
`app/src/main/java/com/milki/launcher/presentation/search/SearchUiState.kt`

## Current Issues

### 1. CRITICAL: SearchUiStateBuilder is Dead Code

**Lines 96-131**: Builder class is never used anywhere in the codebase.

```kotlin
// COMPLETELY UNUSED - REMOVE THIS ENTIRE CLASS
class SearchUiStateBuilder {
    private var query: String = ""
    private var isSearchVisible: Boolean = false
    private var results: List<SearchResult> = emptyList()
    private var activeProviderConfig: SearchProviderConfig? = null
    private var isLoading: Boolean = false
    private var recentApps: List<AppInfo> = emptyList()
    private var installedApps: List<AppInfo> = emptyList()
    private var hasContactsPermission: Boolean = false

    fun query(query: String) = apply { this.query = query }
    fun isSearchVisible(visible: Boolean) = apply { this.isSearchVisible = visible }
    fun results(results: List<SearchResult>) = apply { this.results = results }
    fun activeProviderConfig(config: SearchProviderConfig?) = apply { this.activeProviderConfig = config }
    fun isLoading(loading: Boolean) = apply { this.isLoading = loading }
    fun recentApps(apps: List<AppInfo>) = apply { this.recentApps = apps }
    fun installedApps(apps: List<AppInfo>) = apply { this.installedApps = apps }
    fun hasContactsPermission(hasPermission: Boolean) = apply { this.hasContactsPermission = hasPermission }

    fun build() = SearchUiState(
        query = query,
        isSearchVisible = isSearchVisible,
        results = results,
        activeProviderConfig = activeProviderConfig,
        isLoading = isLoading,
        recentApps = recentApps,
        installedApps = installedApps,
        hasContactsPermission = hasContactsPermission
    )
}
```

**Why it exists**: Probably copied from a pattern example, but Kotlin data classes provide `copy()` which is superior.

**Current usage in codebase**: None. Search for `SearchUiStateBuilder` returns 0 results.

---

## Simplified Version

```kotlin
package com.milki.launcher.presentation.search

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.SearchProviderConfig
import com.milki.launcher.domain.model.SearchResult

data class SearchUiState(
    val query: String = "",
    val isSearchVisible: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val activeProviderConfig: SearchProviderConfig? = null,
    val isLoading: Boolean = false,
    val recentApps: List<AppInfo> = emptyList(),
    val installedApps: List<AppInfo> = emptyList(),
    val hasContactsPermission: Boolean = false
) {
    val hasResults: Boolean
        get() = results.isNotEmpty()

    val isQueryEmpty: Boolean
        get() = query.isBlank()

    val displayQuery: String
        get() = if (activeProviderConfig != null) {
            query.removePrefix("${activeProviderConfig.prefix} ")
        } else {
            query
        }

    val placeholderText: String
        get() = when (activeProviderConfig?.prefix) {
            "s" -> "Search the web..."
            "c" -> "Search contacts..."
            "y" -> "Search YouTube..."
            else -> "Search apps..."
        }

    val showPermissionPrompt: Boolean
        get() = activeProviderConfig?.prefix == "c" && !hasContactsPermission

    val prefixHint: String
        get() = "Prefix shortcuts:\ns - Web search\nc - Contacts\ny - YouTube"
}
```

**Lines reduced**: 131 → 47 lines (64 lines removed, 49% reduction)

---

## Usage Comparison

### With Builder (Unnecessary)
```kotlin
val state = SearchUiStateBuilder()
    .query("test")
    .isSearchVisible(true)
    .results(emptyList())
    .build()
```

### With Copy (Standard Kotlin)
```kotlin
val state = SearchUiState().copy(
    query = "test",
    isSearchVisible = true
)
```

**Winner**: `copy()` is more concise, idiomatic, and doesn't require maintaining a builder class.

---

## Action Items

- [ ] Delete `SearchUiStateBuilder` class entirely
- [ ] Verify no tests reference the builder
- [ ] Ensure ViewModel uses `.copy()` for state updates

## Verification

After removal, the following should still work:
1. Search dialog opens/closes
2. Query updates propagate to UI
3. State transformations in ViewModel
4. All existing UI behavior preserved

**Risk**: Zero. Builder is completely unused.
