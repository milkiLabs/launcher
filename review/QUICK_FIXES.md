# Quick Fixes (Under 30 Minutes Each)

This document contains bite-sized improvements that can be implemented quickly for immediate impact.

---

## 🚀 5-Minute Fixes

### 1. Remove SearchUiStateBuilder

**File**: `SearchUiState.kt`

**Action**: Delete lines 96-131 (the entire builder class)

**Verification**: Search project for "SearchUiStateBuilder" - should find 0 usages

**Impact**: -36 lines

---

### 2. Remove filterWithThreshold

**File**: `FilterAppsUseCase.kt`

**Action**: 
1. Delete lines 74-112 (filterWithThreshold method)
2. Delete lines 133-137 (MatchType enum)

**Verification**: Search for "filterWithThreshold" - should find 0 usages

**Impact**: -58 lines

---

### 3. Fix Magic Numbers in SearchViewModel

**File**: `SearchViewModel.kt`

**Current** (line 295):
```kotlin
val limitedApps = filteredApps.take(8)
```

**Fix**:
```kotlin
companion object {
    const val MAX_APP_RESULTS = 8
}

// Then use:
val limitedApps = filteredApps.take(MAX_APP_RESULTS)
```

**Impact**: Self-documenting code

---

### 4. Remove Accessor Methods from AppContainer

**File**: `AppContainer.kt`

**Action**: Delete lines 178-190

```kotlin
// DELETE THESE:
fun provideContactsRepository(): ContactsRepository = contactsRepository
fun provideAppRepository(): AppRepository = appRepository
```

**Verification**: Search for "provideContactsRepository" or "provideAppRepository" in MainActivity

**Impact**: -13 lines

---

### 5. Add Content Descriptions

**Files**: `SearchResultItems.kt`, `AppGridItem.kt`

**Quick fix** for all Icons:
```kotlin
// Change from:
Icon(imageVector = Icons.Default.Search, contentDescription = null)

// To:
Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
```

**Impact**: Accessibility improvement

---

## 🏃 15-Minute Fixes

### 6. Simplify AppRepository

**File**: `AppRepositoryImpl.kt`

**Replace lines 72 and 109-125**:

```kotlin
// Remove this field:
// private val limitedDispatcher = Dispatchers.IO.limitedParallelism(8)

// Replace chunked code with:
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

**Also remove imports**:
- `kotlinx.coroutines.async`
- `kotlinx.coroutines.awaitAll`

**Impact**: -82 lines, simpler code

---

### 7. Consolidate QueryParser

**File**: `QueryParser.kt`

**Replace both functions with**:

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

    val trimmed = input.trim()
    if (trimmed.length == 1 && providers.any { it.config.prefix == trimmed }) {
        return ParsedQuery(null, input, null)
    }

    return ParsedQuery(null, input, null)
}
```

**Impact**: -115 lines (73% reduction)

---

### 8. Add Search Debouncing

**File**: `SearchViewModel.kt`

**Add to init block**:
```kotlin
init {
    loadInstalledApps()
    observeRecentApps()
    setupSearchDebouncing()
}

private fun setupSearchDebouncing() {
    viewModelScope.launch {
        _uiState
            .map { it.query }
            .distinctUntilChanged()
            .debounce(150)
            .collect { query ->
                performSearch(query)
            }
    }
}
```

**Modify onQueryChange**:
```kotlin
fun onQueryChange(newQuery: String) {
    updateState { copy(query = newQuery) }
    // Remove: performSearch(newQuery)
}
```

**Impact**: Better UX, less CPU usage

---

### 9. Create SearchConfig Object

**New File**: `domain/search/SearchConfig.kt`

```kotlin
package com.milki.launcher.domain.search

object SearchConfig {
    const val MAX_APP_RESULTS = 8
    const val MAX_RECENT_APPS = 5
    const val SEARCH_DEBOUNCE_MS = 150L
    const val DIALOG_WIDTH_PERCENT = 0.9f
    const val DIALOG_HEIGHT_PERCENT = 0.8f
}
```

**Then replace magic numbers throughout codebase**

**Impact**: Self-documenting, maintainable

---

## 🎯 30-Minute Fixes

### 10. Create Reusable AppIcon Component

**New File**: `ui/components/AppIcon.kt`

```kotlin
package com.milki.launcher.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.milki.launcher.AppIconRequest

@Composable
fun AppIcon(
    packageName: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = AppIconRequest(packageName),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}
```

**Replace in**:
- `AppGridItem.kt`: Lines 85-95
- `AppListItem.kt`: Lines 78-88

**Impact**: DRY, consistent icon loading

---

### 11. Create Reusable SearchResultItem

**New File**: `ui/components/SearchResultItem.kt`

```kotlin
package com.milki.launcher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun SearchResultItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint)
        },
        modifier = Modifier.clickable { onClick() }
    )
}
```

**Replace in SearchResultItems.kt**:
- WebSearchResultItem
- YouTubeSearchResultItem
- UrlSearchResultItem

**Impact**: ~150 lines removed

---

### 12. Add Search Cancellation

**File**: `SearchViewModel.kt`

**Add field**:
```kotlin
private var searchJob: Job? = null
```

**Modify performSearch**:
```kotlin
private fun performSearch(query: String) {
    searchJob?.cancel()  // Cancel previous
    
    searchJob = viewModelScope.launch {
        // ... existing search logic ...
        
        try {
            val results = parsed.provider.search(parsed.query)
            if (isActive) {  // Only update if not cancelled
                updateState { copy(results = results, isLoading = false) }
            }
        } catch (e: CancellationException) {
            // Expected, ignore
        }
    }
}
```

**Impact**: No race conditions

---

### 13. Add Loading Indicator

**File**: `SearchResultsList.kt`

**Wrap content with Box**:
```kotlin
@Composable
fun SearchResultsList(
    results: List<SearchResult>,
    isLoading: Boolean,
    // ... other params ...
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Existing content
        if (allAppResults) {
            AppResultsGrid(...)
        } else {
            MixedResultsList(...)
        }
        
        // Loading indicator
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
```

**Impact**: Better UX

---

### 14. Hide Keyboard on Result Click

**File**: `AppSearchDialog.kt`

**Add**:
```kotlin
val keyboardController = LocalSoftwareKeyboardController.current

fun handleResultClick(result: SearchResult) {
    keyboardController?.hide()
    onResultClick(result)
}
```

**Use handleResultClick instead of onResultClick in SearchResultsList**

**Impact**: Better UX

---

## 📊 Quick Impact Summary

| Fix | Time | Lines Removed | Impact |
|-----|------|---------------|--------|
| Remove SearchUiStateBuilder | 5 min | 36 | Maintainability |
| Remove filterWithThreshold | 5 min | 58 | Maintainability |
| Remove AppContainer accessors | 5 min | 13 | Simplicity |
| Simplify AppRepository | 15 min | 82 | Performance + Simplicity |
| Consolidate QueryParser | 15 min | 115 | Maintainability |
| Add debouncing | 15 min | 0 | UX + Performance |
| Create AppIcon component | 15 min | 30 | DRY |
| Create SearchResultItem | 30 min | 150 | DRY |
| Add search cancellation | 15 min | 5 | Correctness |
| Add loading indicator | 15 min | 10 | UX |
| Hide keyboard on click | 15 min | 5 | UX |
| Extract constants | 30 min | 0 | Maintainability |
| **TOTAL** | **~3 hours** | **~500** | **Major** |

---

## 🎓 Learning Opportunity

Each quick fix teaches a principle:

1. **Dead code removal**: YAGNI principle
2. **Method consolidation**: DRY principle
3. **Debouncing**: Performance without complexity
4. **Component extraction**: Reusability
5. **Magic numbers**: Self-documenting code
6. **Cancellation**: Correct concurrency

---

## ✅ Verification Steps

After each fix:

1. **Build**: `./gradlew assembleDebug`
2. **Test**: Run app, verify feature works
3. **Check**: No new warnings in logcat
4. **Commit**: Small, focused commits

---

## 🚀 Quick Wins Order

**Do in this order for maximum impact**:

1. Remove dead code (3 fixes) - Immediate clarity
2. Simplify AppRepository - Performance boost
3. Add debouncing - UX improvement
4. Create reusable components - DRY
5. Add cancellation - Correctness
6. Polish UI (loading, keyboard) - UX

**Total time**: ~3 hours
**Total impact**: ~500 lines removed, major UX improvements
