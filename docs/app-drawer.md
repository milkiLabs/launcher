# App Drawer

This document explains the new homescreen app drawer feature and how it reuses the launcher’s existing gesture/menu/drag patterns.

## Feature Summary

The launcher now supports a full-screen app drawer overlay with the following behavior:

- Swipe up on homescreen opens the drawer **only** when `SwipeUpAction` is set to `OPEN_APP_DRAWER`.
- Drawer open and close use Material bottom-sheet motion/gesture behavior.
- Drawer shows all installed launcher activities from `AppRepository`.
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
  - Exposes one stable app list via `AppDrawerUiState`.

### UI

- `AppDrawerOverlay` (`ui/components/AppDrawerOverlay.kt`)
  - Full-screen drawer composable.
  - Header title plus `LazyVerticalGrid` app content.
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
  - Delegates swipe-up action resolution to `SurfaceStateCoordinator`, so `OPEN_SEARCH`, `OPEN_APP_DRAWER`, and `DO_NOTHING` all share one policy owner.

- `LauncherScreen`
  - Detects homescreen gestures and routes them through the host callback contract.
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

## Drawer Data Source

The drawer now uses the repository's pre-sorted alphabetical installed-app stream directly.

### Performance notes for smoothness

- The drawer no longer recomputes sorting in ViewModel state updates.
- Sort menu state and sort-mode state transitions were removed, reducing header recomposition work.
- Package metadata lookups used only for sort-by-update were removed from app-list mapping.
- Drawer open/close and grid scroll performance are now driven by one stable app list stream.

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
3. `AppRepositoryImpl` owns a repository-scope hot trigger stream (`shareIn`) that
   emits once at startup and then on package change broadcasts.
4. The repository refresh loop performs the heavy `getInstalledApps()` scan once,
   updates an immutable cached snapshot, and records the refresh timestamp.
5. `observeInstalledApps()` returns that shared cached snapshot flow, so multiple
   collectors (drawer + search) read the same data without per-collector rescans.

---

## Gesture Rules

### Open (homescreen swipe up)

Open is attempted only when:

- Search dialog is not visible.
- Folder popup is not open.
- Homescreen context menu is not open.
- Drawer is not already open.

The gesture detector checks for a predominantly vertical upward movement past a lower threshold derived from `Spacing.mediumLarge`.
Gesture sampling uses the home-surface detector shared with other background interactions.

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

- Add optional section headers (A/B/C…) for large app lists.
- Add search inside drawer if product direction requires it.
- Fine-tune drawer open/close animation timings after broader UX testing.
