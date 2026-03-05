# App Drawer

This document explains the new homescreen app drawer feature and how it reuses the launcher’s existing gesture/menu/drag patterns.

## Feature Summary

The launcher now supports a full-screen app drawer overlay with the following behavior:

- Swipe up on homescreen opens the drawer **only** when `SwipeUpAction` is set to `OPEN_APP_DRAWER`.
- Drawer open and close use Material bottom-sheet motion/gesture behavior.
- Drawer shows all installed launcher activities from `AppRepository`.
- Drawer can be sorted from a dropdown menu using:
  - `Alphabetical (A → Z)`
  - `Alphabetical (Z → A)`
  - `Last update date (Newest first)`
- When sort mode changes, drawer immediately snaps back to the top row so the new ordering appears without delay.
- Long-pressing an app icon in drawer shows the same action menu pattern as search/home app items.
- If drag starts from a drawer icon:
  - The icon menu closes (existing `AppGridItem` behavior).
  - Drawer closes immediately.
  - External drag payload continues to homescreen drop targets.
- Drawer closes on swipe down **only when the drawer grid is currently at top**.

---

## Architectural Placement

### Presentation

- `AppDrawerViewModel` (`presentation/drawer/AppDrawerViewModel.kt`)
  - Collects installed apps reactively from `AppRepository.observeInstalledApps()`.
  - Holds selected sort mode.
  - Exposes sorted list via `AppDrawerUiState`.

### UI

- `AppDrawerOverlay` (`ui/components/AppDrawerOverlay.kt`)
  - Full-screen drawer composable.
  - Header with sort dropdown (checkmark indicates active mode).
  - `LazyVerticalGrid` with adaptive columns (`GridCells.Adaptive`) that adjusts
    column count for phones, foldables, and tablets.
  - Applies `statusBarsPadding()` and `navigationBarsPadding()` so content never
    renders behind system UI chrome.

- `LauncherScreen`
  - Presents drawer in a full-screen `ModalBottomSheet` (`skipPartiallyExpanded = true`, `sheetMaxWidth = Dp.Unspecified`).
  - Uses native bottom-sheet drag-to-dismiss behavior instead of custom close gesture code.
  - Programmatic drawer closes (for example, drag start from drawer icon) call sheet hide animation before removing visibility state.

### Host Integration

- `MainActivity`
  - Owns `isAppDrawerOpen` visibility flag.
  - Injects `AppDrawerViewModel`.
  - Collects `SettingsRepository.settings` and applies swipe-up setting.
  - Includes layered dismissal behavior:
    - Back press closes drawer before search.
    - Home press closes drawer first.
    - `onStop` closes drawer.

- `LauncherScreen`
  - Detects upward swipe on homescreen root.
  - Calls host callback; host decides whether drawer should open based on settings.
  - Renders `AppDrawerOverlay` above homescreen grid.

---

## Reused Interaction Patterns

The implementation intentionally reuses existing interaction contracts already used by folders/search/home:

1. **Long-press menu + drag arbitration**
   - Reused from `AppListItem` / `AppGridItem`.
   - Menu is non-focusable while gesture is active.
   - Menu dismisses before drag starts.

2. **External drag payload contract**
   - Reused `startExternalAppDrag` path.
   - Drag source is still a normal app payload (same payload codec path used by search/folder).

3. **Homescreen drop handling**
   - Reused existing `DraggablePinnedItemsGrid` external drop overlay + `HomeViewModel.pinOrMoveHomeItemToPosition`.

This avoids introducing a new drag/drop mechanism and keeps behavior consistent across launcher surfaces.

---

## Sorting Data Source

`AppInfo` now includes:

- `lastUpdatedTimestamp: Long`

`AppRepositoryImpl` populates this using `PackageManager.getPackageInfo(...).lastUpdateTime` via a compatibility helper (`getPackageInfoCompat`) so it works across API levels.

The drawer ViewModel sorts against this field for “Last update date (Newest first)”.

### Performance notes for sorting

- Drawer sort computation runs on a background dispatcher to avoid blocking the main thread during mode changes.
- Alphabetical descending uses `asReversed()` (cheap view) instead of a full resort.
- Re-selecting the currently active sort mode is ignored to prevent unnecessary recomputation.

### Performance notes for app loading

- Installed app discovery now deduplicates icon preloads per package when multiple launcher activities exist.
- Last-update timestamps are cached per package during discovery to avoid repeated `PackageManager.getPackageInfo(...)` calls.

### Reactive app list updates

Both the app drawer and the search dialog automatically reflect package changes
(installs, uninstalls, updates) without requiring a manual refresh or app restart.

**How it works:**

1. `AppRepositoryImpl` registers a `BroadcastReceiver` on the `Application` context
   for `ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_REMOVED`, `ACTION_PACKAGE_REPLACED`,
   and `ACTION_PACKAGE_CHANGED`.
2. Each broadcast fires a signal into a `MutableSharedFlow<Unit>` with
   `DROP_OLDEST` overflow — rapid signals coalesce naturally.
3. `observeInstalledApps()` merges an initial `flowOf(Unit)` (for first-collection
   data) with the broadcast signal flow, then uses `mapLatest` to call
   `getInstalledApps()`. If a new broadcast arrives while a reload is in-flight,
   the stale reload is cancelled and only the latest one completes.
4. `AppDrawerViewModel` collects `observeInstalledApps()` to keep the drawer grid
   current.
5. `SearchViewModel` collects `observeInstalledApps()` to keep search results
   current (alongside its existing one-shot `loadInstalledApps()` for fast startup).

---

## Gesture Rules

### Open (homescreen swipe up)

Open is attempted only when:

- Search dialog is not visible.
- Folder popup is not open.
- Homescreen context menu is not open.
- Drawer is not already open.

The gesture detector checks for a predominantly vertical upward movement past a lower threshold derived from `Spacing.mediumLarge`.
Gesture sampling uses the initial pointer pass and ignore-consumed deltas so opening remains responsive even when child composables are handling touch input.

### Close (drawer swipe down)

Close is attempted only when:

- User drags down the full-screen bottom sheet.
- Material sheet behavior handles thresholds/animation internally.

---

## Design-System Compliance

Drawer UI uses existing theme primitives and spacing tokens:

- Layout spacing uses `Spacing` constants only.
- No hardcoded `dp` values were introduced.
- Existing menu/item components are reused instead of creating parallel styling systems.

---

## Notes for Future Enhancements

Potential future improvements (out of current scope):

- Persist drawer sort mode in `SettingsRepository`.
- Add optional section headers (A/B/C…) for large app lists.
- Add search inside drawer if product direction requires it.
- Fine-tune drawer open/close animation timings after broader UX testing.
