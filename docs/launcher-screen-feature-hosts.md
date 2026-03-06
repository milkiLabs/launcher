# LauncherScreen Feature Hosts and Grouped Actions

This document explains the `LauncherScreen` boundary refactor that addresses callback explosion and weak feature boundaries.

## Why This Refactor Was Needed

Before the refactor, `LauncherScreen` exposed a very long flat parameter list with callbacks spanning multiple unrelated domains:

- Home grid interactions
- Folder lifecycle actions
- Widget picker and widget grid actions
- App drawer lifecycle actions
- Search dialog actions
- Homescreen context menu actions

That design made the screen hard to reason about, hard to test in isolation, and easy to break when adding a new feature callback.

## What Changed

The `LauncherScreen` API now accepts one grouped action contract:

- `LauncherActions`

`LauncherActions` contains feature-scoped groups:

- `home: HomeActions`
- `folder: FolderActions`
- `widget: WidgetActions`
- `drawer: DrawerActions`
- `search: SearchActions`
- `menu: MenuActions`

Each group has default no-op lambdas so the boundary can be integrated incrementally and used safely in test scaffolding.

## New Host Structure Inside LauncherScreen

The screen is now split into dedicated host composables. This keeps each domain in one focused block:

- `HomeSurface`
- `FolderOverlayHost`
- `DrawerHost`
- `WidgetPickerHost`

### `HomeSurface`

Owns:

- `DraggablePinnedItemsGrid`
- Routing of home, folder-drag, and widget-drag callbacks emitted from the grid
- Empty-area long-press menu anchor capture

Key behavior:

- When external items are dropped to home, it triggers `home.onItemDroppedToHome` and closes search via `search.onDismissSearch`.

### `FolderOverlayHost`

Owns:

- `FolderPopupDialog` lifecycle and folder-specific event routing

Key behavior:

- Uses `key(folder.id)` to ensure a fresh composition state when switching opened folders.

### `DrawerHost`

Owns:

- `ModalBottomSheet` wrapper for `AppDrawerOverlay`
- Drawer visibility and dismiss routing through `DrawerActions`

Key behavior:

- Keeps drawer-sheet coroutine and hide flow localized to drawer host concerns.

### `WidgetPickerHost`

Owns:

- `WidgetPickerBottomSheet` lifecycle and dismissal routing

Key behavior:

- Closes picker immediately on external widget drag start so home grid drop targets are visible.

## MainActivity Integration Pattern

`MainActivity` now wires a single `LauncherActions(...)` object when calling `LauncherScreen`.

Benefits:

- Callback wiring is grouped by feature, which makes future edits safer.
- Feature teams can work on one action group without touching unrelated groups.
- The callsite is easier to scan because behavior is clustered by domain.

## Tests Added

Unit contract tests were added at:

- `app/src/test/java/com/milki/launcher/ui/screens/LauncherActionsContractTest.kt`

These tests verify:

- Default action groups are safe no-ops.
- Home action payloads are forwarded without mutation.
- Feature groups remain isolated (calling folder callbacks does not trigger search callbacks).

## Extension Guidance

When adding a new launcher interaction:

1. Put the callback in the correct feature group (`HomeActions`, `FolderActions`, etc.).
2. Route it inside the corresponding host composable.
3. Wire it from `MainActivity` in the matching `LauncherActions` section.
4. Add or update a `LauncherActionsContractTest` case for the new contract.

This keeps feature boundaries explicit and avoids returning to flat callback sprawl.
