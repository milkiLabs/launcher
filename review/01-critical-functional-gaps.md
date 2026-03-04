# P0/P1 Functional Gaps

## P0 — Persisted settings are not applied at runtime

## 1) Search behavior settings are mostly no-op (**P0**)
### Evidence
- `LauncherSettings` defines `maxSearchResults`, `showRecentApps`, `maxRecentApps`, `searchResultLayout`, `defaultSearchEngine`, `closeSearchOnLaunch`, provider enable flags, etc.
- `SettingsViewModel` updates these values.
- Search pipeline and UI behavior do not consume most of them.

### Impact
- Users change settings and see no effect.
- UI communicates configurability that does not actually exist.
- This is a product trust regression.

### Fix
Create a `SearchRuntimeConfig` flow (derived from settings) consumed by `SearchViewModel`, `SearchResultsList`, `ActionExecutor`, and provider registry filtering.

---

## 2) `maxSearchResults` ignored (**P0**)
### Evidence
- `SearchViewModel.executeSearch()` hard-codes `take(8)` for app results.

### Impact
- Slider in settings is misleading.

### Fix
- Pass runtime-configured max count into app result mapping.
- Add unit tests for boundaries (3..20).

---

## 3) Provider enable toggles ignored (**P0**)
### Evidence
- `webSearchEnabled`, `contactsSearchEnabled`, `youtubeSearchEnabled`, `filesSearchEnabled` are stored but provider registry still contains all providers.

### Impact
- Disabled providers remain active by prefix.

### Fix
- Build active provider set from settings and rebuild registry mappings accordingly.
- If disabled provider prefix is typed, parse as plain app query or show a disabled-provider hint.

---

## 4) `defaultSearchEngine` ignored (**P0**)
### Evidence
- `WebSearchProvider` always returns Google + DuckDuckGo fixed list.

### Impact
- Settings option has no product effect.

### Fix
- Drive web provider output from selected engine or engine-order preference.
- Keep optional secondary engines if desired, but selected engine must be primary and explicit.

---

## 5) `autoFocusKeyboard` ignored (**P1**)
### Evidence
- `AppSearchDialog` always requests focus and shows keyboard on window focus.

### Impact
- User cannot disable keyboard auto-popup despite setting.

### Fix
- Make `AppSearchDialog` focus behavior configurable via runtime settings.

---

## 6) `closeSearchOnLaunch` ignored (**P1**)
### Evidence
- `SearchResultAction.shouldCloseSearch()` returns hardcoded values.
- `ActionExecutor` closes search based on that, not settings.

### Impact
- Setting does not control close behavior after launching/opening items.

### Fix
- Move close policy to `ActionExecutor` runtime config, not static extension.

---

## 7) `showRecentApps` / `maxRecentApps` ignored (**P1**)
### Evidence
- Empty query path returns full recent list from repository; no setting gate/cap applied in search pipeline.

### Impact
- Recent apps behavior diverges from user preference.

### Fix
- Apply settings during app filtering when query is blank.

---

## 8) `showHomescreenHint` ignored (**P1**)
### Evidence
- `DraggablePinnedItemsGrid` always renders "Tap to search" when grid is empty.

### Impact
- Toggle has no visible effect.

### Fix
- Feed settings into home UI state and conditionally render hint.

---

## 9) `homeTapAction` / `swipeUpAction` / `homeButtonClearsQuery` partially or fully ignored (**P1**)
### Evidence
- Main home/onNewIntent behavior is mostly hardcoded in `MainActivity`.
- No clear runtime branching on these settings.

### Impact
- Home interactions do not match configured behavior.

### Fix
- Centralize home gesture/tap behavior in a settings-driven policy object.