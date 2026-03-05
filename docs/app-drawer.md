# App Drawer

This document explains the new homescreen app drawer feature and how it reuses the launcher’s existing gesture/menu/drag patterns.

## Feature Summary

The launcher now supports a full-screen app drawer overlay with the following behavior:

- Swipe up on homescreen opens the drawer **only** when `SwipeUpAction` is set to `OPEN_APP_DRAWER`.
- Drawer open and close use subtle animated transitions (fade + small vertical slide) for smoother UX.
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
  - Loads installed apps from `AppRepository`.
  - Holds selected sort mode.
  - Exposes sorted list via `AppDrawerUiState`.

### UI

- `AppDrawerOverlay` (`ui/components/AppDrawerOverlay.kt`)
  - Full-screen drawer composable.
  - Header with sort dropdown.
  - `LazyVerticalGrid` (4 columns) of `AppGridItem` tiles.
  - Swipe-down close detector gated by grid-at-top state.

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

---

## Gesture Rules

### Open (homescreen swipe up)

Open is attempted only when:

- Search dialog is not visible.
- Folder popup is not open.
- Homescreen context menu is not open.
- Drawer is not already open.

The gesture detector checks for a predominantly vertical upward movement past a higher threshold derived from `Spacing.extraLarge`.

### Close (drawer swipe down)

Close is attempted only when:

- Drawer `LazyVerticalGrid` is exactly at top (`firstVisibleItemIndex == 0` and `firstVisibleItemScrollOffset == 0`).
- Gesture is predominantly vertical downward and passes the threshold.
- Close threshold also uses `Spacing.extraLarge` to reduce accidental rapid dismisses.

Interaction scope:

- The swipe-down close gesture is detected across the full drawer surface (header and grid region), not only over the header.

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
