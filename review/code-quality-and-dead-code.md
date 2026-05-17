# Code Quality, Dead Code & Kotlin Best Practices Review

> Analysis of dead code, duplicated logic, unnecessary complexity, Kotlin idiomatic violations, error handling, naming conventions, magic numbers, and test coverage.

---

## 1. Dead Code

### 1.1 Unused Extension Functions

| File:Line | Function | Issue | Severity |
|-----------|----------|-------|----------|
| `FileDocument.kt:94-96` | `FileDocument.matchesQuery(query: String)` | Repository does its own filtering; never called | MEDIUM |
| `AppInfo.kt:33-36` | `AppInfo.matchesQuery(query: String)` | `AppQueryRanker` does its own scoring; never called | MEDIUM |
| `Contact.kt:83-86` | `Contact.matchesQuery(query: String)` | Repository uses its own query layer; never called | MEDIUM |
| `Contact.kt:108-114` | `Contact.primaryPhoneNumber()` / `primaryEmail()` | Never called outside this file | LOW |

**Recommendation:** Delete all unused extension functions.

### 1.2 Unused Types

| File:Line | Type | Issue | Severity |
|-----------|------|-------|----------|
| `SearchResult.kt:127-132` | `YouTubeSearchResult` | No provider constructs this type | MEDIUM |
| `SourcePrefixMutationResult.kt:9` | `SourcePrefixMigrationResult` typealias | Backward-compatible alias with no callers | LOW |

### 1.3 Dead Code Branches

| File:Line | Issue | Severity |
|-----------|-------|----------|
| `SettingsMutationStore.kt:98-99` | Redundant existence check — already returned at line 87 | LOW |
| `HomeItemSerializer.kt:26` | Empty string case returns `emptyList()` — inconsistent with null case | LOW |

### 1.4 Inefficient Patterns

| File:Line | Issue | Severity |
|-----------|-------|----------|
| `UrlValidator.kt:254` | `Regex("^[a-zA-Z]...")` compiled on every call | MEDIUM |
| `LauncherBackupRepositoryImpl.kt:394` | `ids += item.appWidgetId` creates new list each time | LOW |

**Fix for UrlValidator:**
```kotlin
private val HAS_EXPLICIT_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*$")
```

---

## 2. Duplicated Logic

### 2.1 CSV Splitting — 6 Copies

The pattern `split(",").filter { it.isNotEmpty() }` appears in:

| File:Line | Context |
|-----------|---------|
| `RecentAppsStore.kt:25-27` | Parsing recent app IDs |
| `RecentAppsStore.kt:36-38` | Parsing recent app IDs (write path) |
| `RecentAppsStore.kt:57-59` | Parsing recent app IDs (clear path) |
| `ContactsRecentStorage.kt:59-61` | Parsing recent contact IDs |
| `FilesRecentStorage.kt:52-54` | Parsing recent file IDs |
| `FilesRecentStorage.kt:68-70` | Parsing recent file IDs (write path) |

**Recommendation:**
```kotlin
fun String.parseCsvList(): List<String> = split(",").filter(String::isNotEmpty)
```

### 2.2 DataStore IOException Recovery — 3 Copies

The `catch { if (exception is IOException) emit(emptyPreferences()) else throw exception }` pattern:

| File:Line | Context |
|-----------|---------|
| `SettingsRepositoryImpl.kt:42-48` | Settings DataStore |
| `HomeSnapshotStore.kt:27-31` | Home DataStore |
| `ActionShortcutRepositoryImpl.kt:36-40` | Action Shortcuts DataStore |

**Recommendation:**
```kotlin
fun PreferencesFlow.catchIoErrors(): Flow<Preferences> =
    catch { if (it is IOException) emit(emptyPreferences()) else throw it }
```

### 2.3 `homeRoleManagerOrNull` — 2 Copies

| File:Line | Issue |
|-----------|-------|
| `LauncherDefaultState.kt:22-27` | Near-identical to |
| `LauncherDefaultRequest.kt:48-53` | Near-identical to |

**Recommendation:** Move to a shared file in `core/launcher/`.

### 2.4 Shortcut Sorting — 2 Copies

| File:Line | Pattern |
|-----------|---------|
| `AppLauncher.kt:215-217` | `compareBy<ShortcutInfo>({ !it.isDynamic }, { !it.isDeclaredInManifest }, { it.rank })` |
| `AppContextDataCache.kt:109-111` | Same comparator |

**Recommendation:** Extract to `fun ShortcutInfoComparator() = compareBy<ShortcutInfo>...`

### 2.5 Default Docs Shortcut — 2 Copies

| File:Line | Value |
|-----------|-------|
| `HomeItemSerializer.kt:20-23` | `"action:milki_docs"` |
| `ActionShortcutRepositoryImpl.kt:96-99` | `"action:milki_docs"` |

**Recommendation:** Extract to `object DefaultShortcuts { const val DOCS_ID = "action:milki_docs" }`

---

## 3. Unnecessary Complexity

### 3.1 Large Files

| File | Lines | Issue | Severity |
|------|-------|-------|----------|
| `HomeModelWriter.kt` | 724 | 17 nested Command classes + 15 methods; violates SRP | HIGH |
| `SettingsRepositoryImpl.kt` | 619 | 20+ methods; diff writing is 68 lines | MEDIUM |
| `FileFilterConfig.kt` | 561 | Excessive educational comments | MEDIUM |
| `FilesRepositoryImpl.kt` | 524 | Cursor handling + filtering + logging | MEDIUM |
| `PermissionHandler.kt` | 545 | 4 permission types + settings navigation | MEDIUM |
| `HomeItem.kt` | 609 | 6 sealed subclasses with factories | MEDIUM |
| `UrlHandlerResolver.kt` | 417 | URL resolution + browser detection + caching | MEDIUM |
| `LauncherBackupRepositoryImpl.kt` | 433 | Import sanitization for 7 item types | LOW |

### 3.2 Over-Engineered Areas

| File | Issue | Severity |
|------|-------|----------|
| `AppQueryRanker.kt` | 283 lines of scoring constants with no tests for weights | MEDIUM |
| `SearchProviderRegistry.kt` | 352 lines with excessive ASCII diagrams and tutorials | LOW |
| `WidgetHostManager.kt` | 475 lines; file ends with orphaned KDoc comment | LOW |

### 3.3 SRP Violations

| File | Responsibilities | Recommendation |
|------|-----------------|----------------|
| `PermissionHandler` | Contacts, call, files, storage permissions + settings navigation + toast + state persistence | Split into per-permission handlers |
| `FilesRepositoryImpl` | MediaStore queries + filtering + recent storage + permission checks + cursor parsing | Extract cursor parsing to `FileCursorMapper` |
| `ContactsQueryLayer` | Search queries + phone lookups + batch lookups + cursor index resolution + sort order | Split query construction from cursor reading |
| `HomeModelWriter` | Add + move + remove + folder CRUD + widget frame + display mode + popup expand | Split into `FolderWriter`, `WidgetWriter`, `ItemPlacementWriter` |

---

## 4. Kotlin Idiomatic Violations

### 4.1 `lateinit var` Overuse

| File:Line | Issue | Severity |
|-----------|-------|----------|
| `PermissionHandler.kt:67,79,90,104` | Four `lateinit var` properties for Activity Result launchers | LOW |

Acceptable since Activity Result API requires registration in `onCreate`, but consider a registration helper.

### 4.2 Manual Tokenization vs `split`

| File:Line | Issue | Severity |
|-----------|-------|----------|
| `AppQueryRanker.kt:145-161` | Manual character-by-character tokenization | LOW |

The manual version is faster but less readable. Add a comment explaining why.

### 4.3 `LauncherSettings` God Data Class

| File:Line | Issue | Severity |
|-----------|-------|----------|
| `LauncherSettings.kt` | 348 lines, 13 properties | MEDIUM |

Consider grouping into sub-objects: `SearchSettings`, `AppearanceSettings`, `HomeScreenSettings`.

---

## 5. Error Handling Quality

### 5.1 Swallowed Exceptions

| File:Line | Issue | Severity |
|-----------|-------|----------|
| `AppLauncher.kt:162-168` | Three empty catch blocks (intentional but should use `_`) | LOW (already uses `_`) |
| `InstalledAppsCatalog.kt:111` | Silently returns 0 for timestamp on `NameNotFoundException` | MEDIUM |
| `UrlHandlerResolver.kt:120-122` | Logs but returns null; caller may not know why | MEDIUM |
| `PinnedFileAvailability.kt:41-42` | Catches generic `Exception` with policy fallback | MEDIUM |

### 5.2 Redundant CancellationException Re-throw

| File:Line | Issue | Severity |
|-----------|-------|----------|
| `FilesRepositoryImpl.kt:153-154` | `catch (e: CancellationException) { throw e }` is redundant | LOW |

`CancellationException` is never caught by generic `catch (e: Exception)` in Kotlin. Same pattern at lines 238-239, 298-299, 365-366, 425-426.

---

## 6. Naming Conventions

### 6.1 Inconsistent Naming

| Issue | Files | Severity |
|-------|-------|----------|
| `ReorderRejectReason` vs `RejectReason` — similar concepts, different names | `ReorderPlan.kt`, `DropDecision.kt` | LOW |
| `HomeItem` factory methods: `fromX` vs `create` | `HomeItem.kt:133,179,226,277,313,448` | LOW |
| `ProviderId` uses `const val` instead of enum | `PrefixConfig.kt:135-150` | LOW |

### 6.2 Magic Numbers

| Constant | File:Line | Value | Issue |
|----------|-----------|-------|-------|
| `EXACT_MATCH` | `AppQueryRanker.kt:264` | 10_000 | No explanation of weight |
| `PREFIX_MATCH` | `AppQueryRanker.kt:265` | 9_000 | Why gap of 1000? |
| `MAX_SHORTCUTS_PER_APP` | `AppContextDataCache.kt:38` | 4 | Also hardcoded in `AppLauncher.kt` |
| `MAX_RECENT_APPS` | `AppStorageSchema.kt:22` | 8 | Also hardcoded as `.take(8)` in 2 other files |
| `MAX_ENTRIES` | `ShortcutIconMemoryCache.kt:14` | 160 | No reasoning documented |
| `HOST_ID` | `WidgetHostManager.kt:69` | 100 | Comment explains but still magic |
| `DEFAULT_BITMAP_SIZE_PX` | `AppIconDiskSnapshotStore.kt:24` | 192 | No comment explaining why |
| `MIN_PHONE_DIGITS` | `ContactsSearchProvider.kt:52` | 3 | No comment explaining threshold |
| `maxRows` default | `HomeGridOccupancyPolicy.kt:69` | 100 | Arbitrary |

---

## 7. Comment Quality

### 7.1 Excessive Comments

| File | Issue | Severity |
|------|-------|----------|
| `FileFilterConfig.kt:55-67` | "EDUCATIONAL NOTE FOR NEW ANDROID DEVELOPERS" explains what `setOf()` is | MEDIUM |
| `SearchProviderRegistry.kt` | ASCII art diagram and extensive Javadoc | LOW |
| DI modules | 60%+ comments in `SearchModule.kt` | LOW |

**Recommendation:** Move educational content and detailed documentation to `docs/` directory.

### 7.2 Orphaned Comments

| File:Line | Issue | Severity |
|-----------|-------|----------|
| `WidgetHostManager.kt:462-474` | KDoc for `dpToCells` describes function moved to `WidgetHostSizingSupport.kt` | LOW |

### 7.3 Missing KDoc

| File | Issue | Severity |
|------|-------|----------|
| `AsyncSnapshotCache` | No KDoc on class | LOW |
| `GridReorderEngine` | No KDoc at all | LOW |
| `DropTargetRegistry` | No KDoc at all | LOW |

---

## 8. Collection Operation Inefficiency

| File:Line | Issue | Severity |
|-----------|-------|----------|
| `AppQueryRanker.kt:25-43` | `recentApps.withIndex().associate { ... }` creates full map even if empty | MEDIUM |
| `ContactsQueryLayer.kt:138` | IN clause not chunked; could exceed SQLite's 999 variable limit | MEDIUM |
| `HomeGridOccupancyPolicy.kt:19-33` | Full occupancy map rebuilt on every call during drag | MEDIUM |
| `SearchProviderRegistry.kt:204-206` | `sortedWith` creates comparator on every provider change | LOW |
| `AppQueryRanker.kt:233-260` | Levenshtein allocates IntArrays per call | MEDIUM |
| `FilesRepositoryImpl.kt:348` | New `mutableListOf` for every cursor row | LOW |
| `SettingsSearchSourceStorageCodec.kt:79-114` | Two-pass normalization and deduplication | LOW |
| `normalizeAndValidateSearchSources` | Could combine into single pass | LOW |

---

## 9. String Handling

### 9.1 String Concatenation in Logs

| File:Line | Issue | Severity |
|-----------|-------|----------|
| `FilesRepositoryImpl.kt:416-419` | `"Found file: ${...}, " + "mimeType: ${...}"` — should be single template | LOW |

### 9.2 Good Patterns

Most of the codebase correctly uses string templates (`"$packageName/$activityName"`) rather than concatenation.

---

## 10. Unnecessary Nullability

| File:Line | Issue | Severity |
|-----------|-------|----------|
| `ShortcutIconMemoryCache.kt:17` | `LruCache<String, Drawable.ConstantState?>` — nullable value type | LOW |
| `AppIconMemoryCache.kt:67-68` | Same pattern | LOW |

The nullability in the cache type is unnecessary since `preload` returns early if `constantState` is null.

---

## 11. Test Coverage Gaps

### 11.1 Untested Critical Paths (40+ files)

The following files have **no tests**:

| Category | Files |
|----------|-------|
| URL/Validation | `UrlValidator.kt`, `MimeTypeUtil.kt` |
| Availability | `PinnedFileAvailability.kt` |
| Icon Caching | `AppIconMemoryCache.kt`, `AppIconDiskSnapshotStore.kt`, `ShortcutIconLoader.kt` |
| Widget | `WidgetHostManager.kt`, `WidgetPickerCatalogStore.kt` |
| URL Handling | `UrlHandlerResolver.kt` |
| Search | `SuggestionResolver.kt`, `SuggestionPatternMatcher.kt`, `QueryTextMatcher.kt`, `FilterAppsUseCase.kt`, `ConfigurableUrlSearchProvider.kt` |
| Home Grid | `HomeGridOccupancyPolicy.kt`, `HomeSnapshotStore.kt`, `HomeItemSerializer.kt` |
| Contacts | `ContactsQueryLayer.kt`, `ContactsMappingLayer.kt` |
| Files | `FilesRepositoryImpl.kt` |
| Backup | `LauncherBackupRepositoryImpl.kt` |
| Settings | `SettingsRepositoryImpl.kt`, `ActionShortcutRepositoryImpl.kt` |
| Recent Storage | `RecentAppsStore.kt`, `ContactsRecentStorage.kt`, `FilesRecentStorage.kt` |
| Apps | `AppLabelCache.kt`, `InstalledAppsCatalog.kt`, `PackageChangeMonitor.kt` |
| Permission | `PermissionHandler.kt`, `PermissionUtil.kt`, `PermissionAccessStateStore.kt` |
| Domain | `HomeGraph.kt`, `OccupancyBuilder.kt`, `parseSearchQuery` |
| Launcher | `LauncherDefaultState.kt`, `LauncherDefaultRequest.kt` |
| Intent | `FileOpener.kt`, `UrlOpener.kt` |

### 11.2 Existing Test Quality

| Test File | Coverage | Issue |
|-----------|----------|-------|
| `SettingsMutationStorePrefixConflictTest.kt` | Only prefix conflicts | Missing CRUD tests |
| `ContactsSearchProviderPermissionPromptTest.kt` | Only permission prompts | Narrow scope |
| `FilesSearchProviderPermissionPromptTest.kt` | Only permission prompts | Narrow scope |
| `ExampleInstrumentedTest.kt` | Default template only | No real UI tests |

### 11.3 Instrumented Tests

Only 1 instrumented test exists — the default template. No real UI or integration tests.

---

## 12. Priority Summary

| Priority | Finding | File | Impact |
|----------|---------|------|--------|
| P1 | 8+ dead code items (unused functions, types) | Multiple | Code bloat |
| P1 | 6 copies of CSV splitting logic | Multiple | Duplication |
| P1 | 3 copies of DataStore IOException recovery | Multiple | Duplication |
| P2 | `HomeModelWriter` 724 lines, violates SRP | `HomeModelWriter.kt` | Maintainability |
| P2 | 40+ files with no test coverage | Multiple | Reliability |
| P2 | `FileFilterConfig` educational comments inappropriate | `FileFilterConfig.kt` | File bloat |
| P3 | 15+ magic numbers without documentation | Multiple | Maintainability |
| P3 | Collection inefficiencies in hot paths | Multiple | Performance |
| P3 | Orphaned KDoc comment | `WidgetHostManager.kt` | Confusion |
