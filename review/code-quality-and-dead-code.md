# Code Quality, Dead Code & Kotlin Best Practices Review

> Analysis of dead code, duplicated logic, unnecessary complexity, Kotlin idiomatic violations, error handling, naming conventions, magic numbers, and test coverage.

---

## 3. Unnecessary Complexity

### 3.2 Over-Engineered Areas

| File                | Issue                                                    | Severity |
| ------------------- | -------------------------------------------------------- | -------- |
| `AppQueryRanker.kt` | 283 lines of scoring constants with no tests for weights | MEDIUM   |

### 3.3 SRP Violations

| File                  | Responsibilities                                                                             | Recommendation                                                   |
| --------------------- | -------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| `PermissionHandler`   | Contacts, call, files, storage permissions + settings navigation + toast + state persistence | Split into per-permission handlers                               |
| `FilesRepositoryImpl` | MediaStore queries + filtering + recent storage + permission checks + cursor parsing         | Extract cursor parsing to `FileCursorMapper`                     |
| `ContactsQueryLayer`  | Search queries + phone lookups + batch lookups + cursor index resolution + sort order        | Split query construction from cursor reading                     |
| `HomeModelWriter`     | Add + move + remove + folder CRUD + widget frame + display mode + popup expand               | Split into `FolderWriter`, `WidgetWriter`, `ItemPlacementWriter` |

---

## 4. Kotlin Idiomatic Violations

### 4.1 `lateinit var` Overuse

| File:Line                           | Issue                                                        | Severity |
| ----------------------------------- | ------------------------------------------------------------ | -------- |
| `PermissionHandler.kt:67,79,90,104` | Four `lateinit var` properties for Activity Result launchers | LOW      |

Acceptable since Activity Result API requires registration in `onCreate`, but consider a registration helper.

### 4.2 Manual Tokenization vs `split`

| File:Line                   | Issue                                      | Severity |
| --------------------------- | ------------------------------------------ | -------- |
| `AppQueryRanker.kt:145-161` | Manual character-by-character tokenization | LOW      |

The manual version is faster but less readable. Add a comment explaining why.

### 4.3 `LauncherSettings` God Data Class

| File:Line             | Issue                    | Severity |
| --------------------- | ------------------------ | -------- |
| `LauncherSettings.kt` | 348 lines, 13 properties | MEDIUM   |

Consider grouping into sub-objects: `SearchSettings`, `AppearanceSettings`, `HomeScreenSettings`.

---

## 5. Error Handling Quality

### 5.1 Swallowed Exceptions

| File:Line                         | Issue                                                       | Severity               |
| --------------------------------- | ----------------------------------------------------------- | ---------------------- |
| `AppLauncher.kt:162-168`          | Three empty catch blocks (intentional but should use `_`)   | LOW (already uses `_`) |
| `InstalledAppsCatalog.kt:111`     | Silently returns 0 for timestamp on `NameNotFoundException` | MEDIUM                 |
| `UrlHandlerResolver.kt:120-122`   | Logs but returns null; caller may not know why              | MEDIUM                 |
| `PinnedFileAvailability.kt:41-42` | Catches generic `Exception` with policy fallback            | MEDIUM                 |

### 5.2 Redundant CancellationException Re-throw

| File:Line                        | Issue                                                       | Severity |
| -------------------------------- | ----------------------------------------------------------- | -------- |
| `FilesRepositoryImpl.kt:153-154` | `catch (e: CancellationException) { throw e }` is redundant | LOW      |

`CancellationException` is never caught by generic `catch (e: Exception)` in Kotlin. Same pattern at lines 238-239, 298-299, 365-366, 425-426.

---

## 6. Naming Conventions

### 6.1 Inconsistent Naming

| Issue                                                                       | Files                                 | Severity |
| --------------------------------------------------------------------------- | ------------------------------------- | -------- |
| `ReorderRejectReason` vs `RejectReason` — similar concepts, different names | `ReorderPlan.kt`, `DropDecision.kt`   | LOW      |
| `HomeItem` factory methods: `fromX` vs `create`                             | `HomeItem.kt:133,179,226,277,313,448` | LOW      |
| `ProviderId` uses `const val` instead of enum                               | `PrefixConfig.kt:135-150`             | LOW      |

### 6.2 Magic Numbers

| Constant                 | File:Line                        | Value  | Issue                                         |
| ------------------------ | -------------------------------- | ------ | --------------------------------------------- |
| `EXACT_MATCH`            | `AppQueryRanker.kt:264`          | 10_000 | No explanation of weight                      |
| `PREFIX_MATCH`           | `AppQueryRanker.kt:265`          | 9_000  | Why gap of 1000?                              |
| `MAX_SHORTCUTS_PER_APP`  | `AppContextDataCache.kt:38`      | 4      | Also hardcoded in `AppLauncher.kt`            |
| `MAX_RECENT_APPS`        | `AppStorageSchema.kt:22`         | 8      | Also hardcoded as `.take(8)` in 2 other files |
| `MAX_ENTRIES`            | `ShortcutIconMemoryCache.kt:14`  | 160    | No reasoning documented                       |
| `HOST_ID`                | `WidgetHostManager.kt:69`        | 100    | Comment explains but still magic              |
| `DEFAULT_BITMAP_SIZE_PX` | `AppIconDiskSnapshotStore.kt:24` | 192    | No comment explaining why                     |
| `MIN_PHONE_DIGITS`       | `ContactsSearchProvider.kt:52`   | 3      | No comment explaining threshold               |
| `maxRows` default        | `HomeGridOccupancyPolicy.kt:69`  | 100    | Arbitrary                                     |

---

## 7. Comment Quality

### 7.1 Excessive Comments

| File                        | Issue                                                                    | Severity |
| --------------------------- | ------------------------------------------------------------------------ | -------- |
| `FileFilterConfig.kt:55-67` | "EDUCATIONAL NOTE FOR NEW ANDROID DEVELOPERS" explains what `setOf()` is | MEDIUM   |
| `SearchProviderRegistry.kt` | ASCII art diagram and extensive Javadoc                                  | LOW      |
| DI modules                  | 60%+ comments in `SearchModule.kt`                                       | LOW      |

**Recommendation:** Move educational content and detailed documentation to `docs/` directory.

### 7.3 Missing KDoc

| File                 | Issue            | Severity |
| -------------------- | ---------------- | -------- |
| `AsyncSnapshotCache` | No KDoc on class | LOW      |
| `GridReorderEngine`  | No KDoc at all   | LOW      |
| `DropTargetRegistry` | No KDoc at all   | LOW      |

---

## 8. Collection Operation Inefficiency

| File:Line                                    | Issue                                                                     | Severity |
| -------------------------------------------- | ------------------------------------------------------------------------- | -------- |
| `AppQueryRanker.kt:25-43`                    | `recentApps.withIndex().associate { ... }` creates full map even if empty | MEDIUM   |
| `ContactsQueryLayer.kt:138`                  | IN clause not chunked; could exceed SQLite's 999 variable limit           | MEDIUM   |
| `HomeGridOccupancyPolicy.kt:19-33`           | Full occupancy map rebuilt on every call during drag                      | MEDIUM   |
| `SearchProviderRegistry.kt:204-206`          | `sortedWith` creates comparator on every provider change                  | LOW      |
| `AppQueryRanker.kt:233-260`                  | Levenshtein allocates IntArrays per call                                  | MEDIUM   |
| `FilesRepositoryImpl.kt:348`                 | New `mutableListOf` for every cursor row                                  | LOW      |
| `SettingsSearchSourceStorageCodec.kt:79-114` | Two-pass normalization and deduplication                                  | LOW      |
| `normalizeAndValidateSearchSources`          | Could combine into single pass                                            | LOW      |

---

## 11. Test Coverage Gaps

### 11.1 Untested Critical Paths (40+ files)

The following files have **no tests**:

| Category       | Files                                                                                                                                     |
| -------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| URL/Validation | `UrlValidator.kt`, `MimeTypeUtil.kt`                                                                                                      |
| Availability   | `PinnedFileAvailability.kt`                                                                                                               |
| Icon Caching   | `AppIconMemoryCache.kt`, `AppIconDiskSnapshotStore.kt`, `ShortcutIconLoader.kt`                                                           |
| Widget         | `WidgetHostManager.kt`, `WidgetPickerCatalogStore.kt`                                                                                     |
| URL Handling   | `UrlHandlerResolver.kt`                                                                                                                   |
| Search         | `SuggestionResolver.kt`, `SuggestionPatternMatcher.kt`, `QueryTextMatcher.kt`, `FilterAppsUseCase.kt`, `ConfigurableUrlSearchProvider.kt` |
| Home Grid      | `HomeGridOccupancyPolicy.kt`, `HomeSnapshotStore.kt`, `HomeItemSerializer.kt`                                                             |
| Contacts       | `ContactsQueryLayer.kt`, `ContactsMappingLayer.kt`                                                                                        |
| Files          | `FilesRepositoryImpl.kt`                                                                                                                  |
| Backup         | `LauncherBackupRepositoryImpl.kt`                                                                                                         |
| Settings       | `SettingsRepositoryImpl.kt`, `ActionShortcutRepositoryImpl.kt`                                                                            |
| Recent Storage | `RecentAppsStore.kt`, `ContactsRecentStorage.kt`, `FilesRecentStorage.kt`                                                                 |
| Apps           | `AppLabelCache.kt`, `InstalledAppsCatalog.kt`, `PackageChangeMonitor.kt`                                                                  |
| Permission     | `PermissionHandler.kt`, `PermissionUtil.kt`, `PermissionAccessStateStore.kt`                                                              |
| Domain         | `HomeGraph.kt`, `OccupancyBuilder.kt`, `parseSearchQuery`                                                                                 |
| Launcher       | `LauncherDefaultState.kt`, `LauncherDefaultRequest.kt`                                                                                    |
| Intent         | `FileOpener.kt`, `UrlOpener.kt`                                                                                                           |

### 11.2 Existing Test Quality

| Test File                                       | Coverage                | Issue              |
| ----------------------------------------------- | ----------------------- | ------------------ |
| `SettingsMutationStorePrefixConflictTest.kt`    | Only prefix conflicts   | Missing CRUD tests |
| `ContactsSearchProviderPermissionPromptTest.kt` | Only permission prompts | Narrow scope       |
| `FilesSearchProviderPermissionPromptTest.kt`    | Only permission prompts | Narrow scope       |
| `ExampleInstrumentedTest.kt`                    | Default template only   | No real UI tests   |

### 11.3 Instrumented Tests

Only 1 instrumented test exists — the default template. No real UI or integration tests.
