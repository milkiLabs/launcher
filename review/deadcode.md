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

### 1.4 TODO Comment Indicating Unused/Questionable Code

**File:** `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/domain/repository/AppRepository.kt`

- **Line 32-33:** `TODO: is this actually needed?` - Questioning if `getRecentApps(): Flow<List<AppInfo>>` needs to return a Flow

---

## 2. DUPLICATE CODE

### 2.1 MIME Type Definitions (FIXED ✓)

**Status:** Resolved - Created `MimeTypeUtil.kt` to centralize all MIME type operations.

**Changes Made:**
- Created `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/util/MimeTypeUtil.kt` with:
  - All MIME type constants (e.g., `MIME_PDF`, `MIME_WORD_DOC`, etc.)
  - Extension-to-MIME type mapping
  - Helper functions: `getMimeTypeFromExtension()`, `isPdf()`, `isEpub()`, `isWordDocument()`, `isExcelSpreadsheet()`, `isPowerPoint()`, `isTextFile()`
- Updated `MainActivity.kt:263-276` to use `MimeTypeUtil.getMimeTypeFromExtension()`
- Updated `FileDocument.kt:135-182` to delegate to `MimeTypeUtil` functions

**Impact:** Eliminated ~35 lines of duplicate code across 2 files. All MIME type logic now in one place.

### 2.2 Query Parsing Logic (MEDIUM PRIORITY)

**File:** `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/domain/search/QueryParser.kt`

- **Lines 49-100:** `parseSearchQuery(input, registry)`
- **Lines 112-156:** `parseSearchQuery(input, providers)`

These two functions are nearly identical - one takes a `SearchProviderRegistry`, the other takes a `List<SearchProvider>`. The registry version could simply delegate to the list version by calling `registry.getAllProviders()`.

### 2.3 Permission Checking Code (MEDIUM PRIORITY)

Permission checks for files are duplicated across:

**File 1:** `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/data/repository/FilesRepositoryImpl.kt` (lines 51-74)
**File 2:** `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/MainActivity.kt` (lines 355-369)

Both check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.R` and `Environment.isExternalStorageManager()` vs `ContextCompat.checkSelfPermission()`.

### 2.4 Search Provider Hint/Empty State Pattern (LOW PRIORITY)

**Files:**

- `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/data/search/ContactsSearchProvider.kt` (lines 78-90, 96-115)
- `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/data/search/FilesSearchProvider.kt` (lines 77-91, 95-113)

Both providers follow the exact same pattern for creating "hint" and "empty" results with id=-1. This could be extracted to a shared utility.

### 2.5 Similar Intent Creation Code

**File:** `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/MainActivity.kt`

- **Lines 179-184:** `handleOpenWebSearch()`
- **Lines 190-195:** `handleOpenUrl()`

Both create nearly identical browser intents. Could be refactored to share common intent-building logic.

### 2.6 Duplicate Icon Loading Pattern

**Files:**

- `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/ui/components/AppGridItem.kt` (lines 124-126)
- `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/ui/components/AppListItem.kt` (lines 66-68)

Both use identical `rememberAsyncImagePainter(model = AppIconRequest(appInfo.packageName))` code.

---

## 3. OVER-ENGINEERING

### 3.1 Excessive Documentation (Documentation Bloat)

Many files have documentation that far exceeds the complexity of the code:

**Example 1:** `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/ui/theme/Color.kt`

- 127 lines for 6 color constants
- Lines 110-126 explain hex color format (basic Android knowledge)

**Example 2:** `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/ui/theme/Type.kt`

- 135 lines for a simple Typography configuration
- Comments explaining what `sp` means (basic Compose knowledge)

**Example 3:** `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/AppIconFetcher.kt`

- 145 lines for a simple Coil Fetcher
- Extensive comments explaining basic Kotlin concepts like `suspend` and `Factory` pattern

### 3.2 Over-Engineered App Loading in AppRepositoryImpl

**File:** `/media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/data/repository/AppRepositoryImpl.kt` (lines 59-72, 90-127)

The code uses a custom `limitedDispatcher` with `chunked(8)` and `async/awaitAll` for loading apps. This is likely unnecessary complexity:

- Android's `PackageManager` already caches app info
- Modern devices can handle 150+ simple object creations without batching
- The chunking adds code complexity for minimal performance gain

### 3.3 QueryParser Double Implementation

As noted in duplicate code section, having two nearly identical parsing functions adds unnecessary complexity.

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

| Issue Type              | File Path                                      | Line Numbers       |
| ----------------------- | ---------------------------------------------- | ------------------ |
| Unused import           | Theme.kt                                       | 24                 |
| ~~Duplicate MIME types~~| ~~MainActivity.kt, FileDocument.kt~~           | ~~264-275, 147-182~~ |
| Duplicate parsing       | QueryParser.kt                                 | 49-156             |
| Over-documentation      | Color.kt, Type.kt, AppIconFetcher.kt           | Entire files       |
| Over-engineered loading | AppRepositoryImpl.kt                           | 59-127             |
| Unnecessary lazy        | AppContainer.kt                                | 85-171             |
| Unused test files       | ExampleUnitTest.kt, ExampleInstrumentedTest.kt | 1-24               |
| TODO marker             | AppRepository.kt                               | 32-33              |

**Note:** Strikethrough items have been fixed.

---

## Estimated Impact

**Lines that can be removed/simplified:**

- Dead code: ~100 lines
- Duplicate code consolidation: ~80 lines
- Documentation bloat reduction: ~400 lines
- **Total potential reduction: ~580 lines**
