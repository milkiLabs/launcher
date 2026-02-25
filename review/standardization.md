# Standardization and Code Duplication Audit Report

**Project:** Milki Launcher  
**Date:** 2026-02-25  
**Auditor:** Code Review

## Executive Summary

This audit identifies code duplication, inconsistency patterns, and opportunities for standardization across the codebase. The project shows good adherence to many best practices, but there are areas where further standardization would improve maintainability and reduce technical debt.

---

## 1. Design System Adherence

### 1.1 Hardcoded DP Values (LOW PRIORITY - Mostly Resolved)

**Status:** ✅ Mostly compliant

The codebase has a well-defined design system in `Spacing.kt` with `Spacing`, `IconSize`, and `CornerRadius` objects. Most components use these centralized values.

**Remaining Violations:**

| File | Line | Current | Should Use |
|------|------|---------|------------|
| `SettingsComponents.kt` | 63 | `20.dp` | `IconSize.small` |
| `SettingsComponents.kt` | 123 | `24.dp` | `IconSize.standard` |
| `SettingsComponents.kt` | 418 | `24.dp` | `IconSize.standard` |

**Code Example:**
```kotlin
// SettingsComponents.kt:63 - Current
.size(20.dp)

// Should be:
.size(IconSize.small)
```

**Recommendation:** Replace remaining hardcoded DP values with design system constants.

**Migration Complexity:** LOW (2-4 hours)

---

## 2. Component Patterns

### 2.1 App Item Components (GOOD - Already Standardized)

**Status:** ✅ Well-standardized

The `AppListItem` and `AppGridItem` components follow consistent patterns:
- Both use `ItemActionMenu` for long-press actions
- Both use `AppIcon` component for icon display
- Both use `createPinAction` and `createAppInfoAction` helpers
- Both use design system spacing values

**No changes needed.**

### 2.2 Search Result Items (GOOD - Already Refactored)

**Status:** ✅ Well-standardized

The `SearchResultItems.kt` file has been refactored to use `SearchResultListItem` wrapper:
- `WebSearchResultItem` - 10 lines (previously ~40)
- `YouTubeSearchResultItem` - 10 lines
- `UrlSearchResultItem` - Uses wrapper with custom trailing content
- `ContactSearchResultItem` - Uses wrapper with dial button
- `FileDocumentSearchResultItem` - Uses wrapper with long-press menu

**Documentation confirms:** "This file has been refactored to use the SearchResultListItem wrapper component. Previously, each result type duplicated the same ListItem structure. Now they all use SearchResultListItem, reducing code by ~80%."

**No changes needed.**

---

## 3. Serialization Pattern Duplication

### 3.1 HomeItem Serialization (HIGH PRIORITY)

**Status:** ⚠️ Duplicated pattern across 3 subclasses

**Files Affected:**
- `domain/model/HomeItem.kt` (lines 89-155, 183-252, 280-347)
- `domain/model/GridPosition.kt` (lines 65-93)

**Duplication Pattern:**
Each `HomeItem` subclass has identical serialization logic:

```kotlin
// PinnedApp (lines 89-155)
override fun toStorageString(): String {
    return "app|$packageName|$activityName|$label|${position.toStorageString()}"
}

companion object {
    fun fromStorageString(str: String): PinnedApp? {
        val parts = str.split("|")
        if (parts.isEmpty() || parts[0] != "app") return null
        // ... parsing logic
    }
}

// PinnedFile (lines 183-252) - SAME PATTERN
// AppShortcut (lines 280-347) - SAME PATTERN
```

**Problems:**
1. Three implementations of the same parsing logic
2. No shared validation or error handling
3. Adding new fields requires updating multiple places
4. Legacy format handling duplicated in each class

**Recommended Solution:** Use kotlinx.serialization (see library-opportunities.md)

**Migration Complexity:** MEDIUM (2-3 days)

---

## 4. Repository Implementation Patterns

### 4.1 DataStore Initialization (GOOD - Correct Pattern)

**Status:** ✅ Correctly implemented

All repositories correctly place DataStore delegate at file top-level:

```kotlin
// HomeRepositoryImpl.kt:39-41
private val Context.homeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "home_items"
)

// SettingsRepositoryImpl.kt:36-38
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "launcher_settings"
)

// AppRepositoryImpl.kt:116
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

// ContactsRepositoryImpl.kt:39-41
private val Context.recentContactsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_contacts"
)
```

**Note:** This follows Google's recommendation for singleton DataStore instances.

**No changes needed.**

### 4.2 Error Handling Pattern (GOOD - Consistent)

**Status:** ✅ Consistent

All repositories use the same error handling pattern:

```kotlin
// HomeRepositoryImpl.kt:86-96
override val pinnedItems: Flow<List<HomeItem>> = context.homeDataStore.data
    .catch { exception ->
        if (exception is IOException) {
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }
    .map { preferences -> deserializeItems(preferences) }
```

**No changes needed.**

---

## 5. ViewModel Patterns

### 5.1 State Management (GOOD - Consistent)

**Status:** ✅ Consistent across all ViewModels

All ViewModels follow the same pattern:

```kotlin
// SearchViewModel
private val _uiState = MutableStateFlow(SearchUiState())
val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

// HomeViewModel
val uiState = homeRepository.pinnedItems
    .map { items -> HomeUiState(pinnedItems = items, isLoading = false) }
    .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = HomeUiState(isLoading = true))

// SettingsViewModel
val settings: StateFlow<LauncherSettings> = settingsRepository.settings
    .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = LauncherSettings())
```

**No changes needed.**

### 5.2 Update Helper Pattern (GOOD - Consistent)

**Status:** ✅ Consistent

Both ViewModels use inline helper for state updates:

```kotlin
// SearchViewModel.kt:476-478
private inline fun updateState(transform: SearchUiState.() -> SearchUiState) {
    _uiState.update { it.transform() }
}

// SettingsViewModel.kt:154-158
private fun updateSetting(transform: (LauncherSettings) -> LauncherSettings) {
    viewModelScope.launch {
        settingsRepository.updateSettings(transform)
    }
}
```

**No changes needed.**

---

## 6. Action Menu Patterns

### 6.1 Menu Action Builders (GOOD - Already Extracted)

**Status:** ✅ Well-standardized

The `ItemActionMenu.kt` provides reusable action builders:

```kotlin
// Used consistently across:
// - AppListItem.kt:87-96
// - AppGridItem.kt:105-115
// - PinnedItem.kt:247-276
// - FileDocumentSearchResultItem.kt:518-527

createPinAction(isPinned, pinAction, unpinAction)
createUnpinAction(itemId)
createAppInfoAction(packageName)
```

**No changes needed.**

---

## 7. Icon Handling Patterns

### 7.1 App Icons (GOOD - Already Extracted)

**Status:** ✅ Centralized

The `AppIcon.kt` component centralizes app icon loading:

```kotlin
// Used in:
// - AppListItem.kt:70-73
// - AppGridItem.kt:83-86
// - PinnedItem.kt:305-309
// - PinnedItem.kt:449-453 (ShortcutIcon)

AppIcon(
    packageName = appInfo.packageName,
    size = IconSize.appList
)
```

**No changes needed.**

### 7.2 File Icons (DUPLICATION - MEDIUM PRIORITY)

**Status:** ⚠️ Duplicated logic

File icon determination logic exists in two places:

**Location 1:** `PinnedItem.kt:387-432` - `getFileIconData()`
```kotlin
@Composable
private fun getFileIconData(mimeType: String, fileName: String): FileIconData {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when {
        mimeType == "application/pdf" || extension == "pdf" -> FileIconData(...)
        mimeType.startsWith("image/") -> FileIconData(...)
        // ... more cases
    }
}
```

**Location 2:** `SearchResultItems.kt:485-493` - Inline in `FileDocumentSearchResultItem`
```kotlin
val fileIcon = when {
    file.isPdf() -> Icons.Outlined.PictureAsPdf
    file.isWordDocument() -> Icons.AutoMirrored.Outlined.Article
    // ... more cases
}
```

**Problems:**
1. Two different implementations of file icon logic
2. Different approaches (FileIconData wrapper vs direct ImageVector)
3. Inconsistency: PinnedItem shows colored background, SearchResultItem doesn't

**Recommended Solution:** Create a shared `FileIcon` component:

```kotlin
// New file: ui/components/FileIcon.kt
@Composable
fun FileIcon(
    mimeType: String,
    fileName: String,
    size: Dp,
    showBackground: Boolean = true,
    modifier: Modifier = Modifier
)

// Extension functions on FileDocument for type checking
fun FileDocument.getFileIcon(): ImageVector
fun FileDocument.getFileIconData(): FileIconData
```

**Migration Complexity:** MEDIUM (1 day)

---

## 8. MIME Type Handling

### 8.1 Type Checking Functions (PARTIAL - Needs Extension)

**Status:** ⚠️ Inconsistent usage

`MimeTypeUtil.kt` provides type checking functions:
```kotlin
fun isPdf(mimeType: String, fileName: String): Boolean
fun isWordDocument(mimeType: String, fileName: String): Boolean
// etc.
```

**But `FileDocument.kt` also has its own checking functions:**
```kotlin
// FileDocument.kt has extension properties
fun FileDocument.isPdf(): Boolean = MimeTypeUtil.isPdf(mimeType, name)
fun FileDocument.isWordDocument(): Boolean = MimeTypeUtil.isWordDocument(mimeType, name)
```

**Assessment:** This is actually a GOOD pattern - FileDocument delegates to MimeTypeUtil, avoiding duplication. However, not all code uses these extensions.

**Recommendation:** Ensure all file type checking goes through `FileDocument` extension properties or `MimeTypeUtil` directly.

---

## 9. Permission Handling

### 9.1 Permission Utilities (GOOD - Centralized)

**Status:** ✅ Well-standardized

Permission checking is centralized in `PermissionUtil.kt`:
```kotlin
object PermissionUtil {
    fun hasContactsPermission(context: Context): Boolean
    fun hasFilesPermission(context: Context): Boolean
}
```

Used consistently in:
- `ContactsRepositoryImpl.kt:88-93`
- `FilesRepositoryImpl.kt:63`
- `PermissionHandler.kt:233`

**No changes needed.**

---

## 10. URL Detection

### 10.1 URL Validation (SINGLE LOCATION - GOOD)

**Status:** ✅ Centralized in one place

URL detection is in `SearchViewModel.kt:389-466`:
```kotlin
private fun detectUrl(query: String): UrlSearchResult?
```

**Recommendation:** Consider extracting to a `UrlValidator` utility class for better testability and reusability, but not urgent.

---

## 11. Dependency Injection

### 11.1 Koin Module Structure (GOOD - Well Organized)

**Status:** ✅ Consistent

`AppModule.kt` follows clear organization:
- Repositories (singletons)
- Search providers (singletons)
- Registry (singleton)
- Use cases (singletons)
- ViewModels (viewModel scope)

All dependencies use `get()` for injection:
```kotlin
single<ContactsRepository> { ContactsRepositoryImpl(get()) }
viewModel { SearchViewModel(appRepository = get(), ...) }
```

**No changes needed.**

---

## 12. Documentation Patterns

### 12.1 Comment Style (GOOD - Consistent)

**Status:** ✅ Consistent comprehensive documentation

All files follow the same documentation pattern:
- File-level KDoc explaining purpose
- Section separators (e.g., `// ========================================================================`)
- Detailed inline comments
- KDoc for all public functions/classes

**Example:**
```kotlin
/**
 * HomeRepositoryImpl.kt - DataStore-backed implementation of HomeRepository
 *
 * Persists pinned home screen items using Jetpack DataStore Preferences.
 * Each item is serialized to a string and stored in a StringSet.
 *
 * WHY DATASTORE INSTEAD OF SHAREDPREFERENCES?
 * ...
 */
```

**No changes needed.**

---

## Summary Table

| Finding | Priority | Files Affected | Complexity | Status |
|---------|----------|----------------|------------|--------|
| Hardcoded DP values | LOW | SettingsComponents.kt | LOW | 3 violations |
| HomeItem serialization | HIGH | HomeItem.kt, GridPosition.kt | MEDIUM | 3x duplication |
| File icon logic | MEDIUM | PinnedItem.kt, SearchResultItems.kt | MEDIUM | 2x duplication |
| MIME type checking | LOW | MimeTypeUtil.kt, FileDocument.kt | LOW | Good pattern |
| Design system | N/A | Multiple | N/A | ✅ Good |
| Action menus | N/A | ItemActionMenu.kt | N/A | ✅ Good |
| ViewModels | N/A | All ViewModels | N/A | ✅ Consistent |
| Repositories | N/A | All Repositories | N/A | ✅ Consistent |
| DI structure | N/A | AppModule.kt | N/A | ✅ Good |
| Documentation | N/A | All files | N/A | ✅ Excellent |

---

## Recommendations

### Immediate (High Priority)
1. **Replace HomeItem serialization with kotlinx.serialization**
   - Eliminates fragile pipe-delimited parsing
   - Single source of truth for serialization
   - Better schema evolution support

### Short-term (Medium Priority)
2. **Unify file icon handling**
   - Create shared `FileIcon` component
   - Add `getFileIcon()` extension to `FileDocument`
   - Ensure consistent visual treatment

3. **Replace hardcoded DP values**
   - Quick fix for 3 remaining violations in SettingsComponents.kt

### Ongoing (Good Practices to Maintain)
- Continue using `Spacing`, `IconSize`, `CornerRadius` design tokens
- Maintain the `SearchResultListItem` wrapper pattern for new result types
- Use `ItemActionMenu` with action builders for all dropdown menus
- Keep comprehensive documentation style

---

## Code Quality Metrics

| Metric | Score | Notes |
|--------|-------|-------|
| Design System Usage | 95% | Only 3 hardcoded DP values |
| Component Reusability | Excellent | AppIcon, SearchResultListItem, ItemActionMenu |
| Documentation | Excellent | Comprehensive KDoc throughout |
| Serialization | Needs Work | Manual parsing should be replaced |
| State Management | Excellent | Consistent StateFlow patterns |
| Error Handling | Excellent | Consistent try-catch and fallback patterns |

---

## Conclusion

The codebase demonstrates strong standardization in most areas:
- ✅ Design system is well-defined and mostly followed
- ✅ Component patterns are consistent (AppIcon, SearchResultListItem, ItemActionMenu)
- ✅ ViewModels follow the same state management pattern
- ✅ Repositories use consistent DataStore patterns
- ✅ Documentation is comprehensive and consistent

The main areas for improvement are:
1. HomeItem serialization (should use kotlinx.serialization)
2. File icon handling (should be unified into a shared component)
3. Minor hardcoded DP values in settings components

Overall, this is a well-structured codebase with good adherence to standardization principles.
