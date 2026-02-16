# Dead Code, Duplicate Code, and Over-Engineering Report

**Date**: 2026-02-16  
**Project**: Milki Launcher  
**Scope**: app/src/main/java/com/milki/launcher/

---

## Summary

After analyzing all 35 Kotlin files in the Milki Launcher codebase, I found several areas of concern ranging from unused code to potential over-engineering. The codebase is well-structured overall but has some redundancy and complexity that could be simplified.

---

## 1. DEAD CODE

### 1.3 Potentially Unused Extension Functions

**File:** `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/domain/model/Contact.kt`

- **Lines 108-114:** `primaryPhoneNumber()` and `primaryEmail()` extension functions
- These are defined but searching the codebase shows they may not be actively used (the ViewModel uses `phoneNumbers.firstOrNull()` directly on line 184)

**File:** `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/domain/model/AppInfo.kt`

- **Lines 45-48:** `matchesQuery()` extension function
- The filtering logic is duplicated in `FilterAppsUseCase.kt` instead of using this extension

---

## 3. OVER-ENGINEERING

### 3.2 Over-Engineered App Loading in AppRepositoryImpl

**File:** `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/data/repository/AppRepositoryImpl.kt` (lines 59-72, 90-127)

The code uses a custom `limitedDispatcher` with `chunked(8)` and `async/awaitAll` for loading apps. This is likely unnecessary complexity:

- Android's `PackageManager` already caches app info
- Modern devices can handle 150+ simple object creations without batching
- The chunking adds code complexity for minimal performance gain

### 3.4 Extensive Sealed Class Hierarchy for Search Results

**File:** `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/domain/model/SearchResult.kt`

The sealed class structure with 7 different result types (AppSearchResult, WebSearchResult, ContactSearchResult, PermissionRequestResult, YouTubeSearchResult, UrlSearchResult, FileDocumentSearchResult) could potentially be simplified. Each requires its own UI handler, composable, and action handler.

### 3.5 SearchProviderRegistry Complexity

**File:** `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/domain/search/SearchProviderRegistry.kt`

The registry pattern adds abstraction for only 4 providers. A simple list with `find { it.config.prefix == prefix }` would be simpler and more readable.

### 3.6 AppContainer with Excessive Lazy Delegates

**File:** `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/di/AppContainer.kt`

Every dependency uses `by lazy` (9 instances, lines 85-171). Since most dependencies are lightweight and created at app startup anyway, direct instantiation would be simpler:

```kotlin
// Current (verbose):
private val appRepository: AppRepository by lazy { AppRepositoryImpl(application) }

// Simpler:
private val appRepository: AppRepository = AppRepositoryImpl(application)
```

### 3.7 Empty State Pattern with Fake Results

**Files:** ContactsSearchProvider.kt and FilesSearchProvider.kt both create fake "hint" results with id=-1 instead of using proper empty state handling in the UI layer. This mixes display logic with search logic.

---

## 4. COMMENTED-OUT CODE

### 4.1 Theme.kt - Example Code Comments

**Lines 237-247:** Extensive commented examples showing how to use the theme
These could be moved to documentation rather than inline comments.

### 4.2 Type.kt - Commented Typography Styles

**Lines 101-120:** Block of commented-out alternative typography styles

---

## 5. RECOMMENDATIONS SUMMARY

### High Priority

1. **Consolidate MIME type handling** - Create a single source of truth for file type mappings
2. **Remove or replace example tests** - They're just noise in the codebase
3. **Merge duplicate QueryParser functions** - Simplify to one implementation

### Medium Priority

4. **Simplify AppRepositoryImpl** - Remove unnecessary chunked loading
5. **Reduce documentation bloat** - Move educational comments to docs/ folder
6. **Consolidate permission checking** - Single utility for version-aware checks

### Low Priority

7. **Simplify SearchProviderRegistry** - Consider direct list usage
8. **Review lazy delegates** - Many could be direct instantiation
9. **Remove unused extension functions** - If truly not used

---

## File Locations Summary

| Issue Type               | File Path                                      | Line Numbers         |
| ------------------------ | ---------------------------------------------- | -------------------- |
| Unused import            | Theme.kt                                       | 24                   |
| ~~Duplicate MIME types~~ | ~~MainActivity.kt, FileDocument.kt~~           | ~~264-275, 147-182~~ |
| Duplicate parsing        | QueryParser.kt                                 | 49-156               |
| Over-documentation       | Color.kt, Type.kt, AppIconFetcher.kt           | Entire files         |
| Over-engineered loading  | AppRepositoryImpl.kt                           | 59-127               |
| Unnecessary lazy         | AppContainer.kt                                | 85-171               |
| Unused test files        | ExampleUnitTest.kt, ExampleInstrumentedTest.kt | 1-24                 |
| TODO marker              | AppRepository.kt                               | 32-33                |

**Note:** Strikethrough items have been fixed.

---

## Estimated Impact

**Lines that can be removed/simplified:**

- Dead code: ~100 lines
- Duplicate code consolidation: ~80 lines
- Documentation bloat reduction: ~400 lines
- **Total potential reduction: ~580 lines**
