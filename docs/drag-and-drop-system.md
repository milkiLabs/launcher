# Drag and Drop System

This document describes the current reusable drag-and-drop system used by the launcher.

## Why this redesign was needed

The previous implementation mixed three responsibilities inside `DraggablePinnedItemsGrid`:

1. Gesture lifecycle (`tap`, `long press`, `drag start`, `drag update`, `drag end`)
2. Drag state machine (active item, offset, drop target)
3. Grid math (clamping offsets, resolving target cells)

That made the code hard to reuse in other launcher surfaces and difficult to evolve safely.

The new design extracts these concerns into dedicated reusable primitives under:

- `app/src/main/java/com/milki/launcher/ui/components/dragdrop/`

## Current architecture

```text
UI Surface (example: DraggablePinnedItemsGrid)
        |
        | uses
        v
DragGestureDetector.detectDragGesture(...)
        |
        | dispatches pointer events
        v
AppDragDropController<T>
        |
        | uses layout metrics for all math
        v
AppDragDropLayoutMetrics
        |
        | resolves movement result
        v
AppDragDropResult<T>
        |
        | move/pin intent
        v
HomeViewModel (single mutation coordinator)
        |
        | serialized repository writes
        v
HomeRepository
```

## Core components

### 1) `AppDragDropLayoutMetrics`

**File:** `ui/components/dragdrop/AppDragDropContract.kt`

Purpose:

- Stores runtime grid geometry (`cellWidthPx`, `cellHeightPx`, `columns`, `rows`)
- Converts cells to pixels
- Resolves target cells from drag offsets
- Clamps drag offsets so drag preview always remains inside valid bounds

Important methods:

- `clamp(position: GridPosition): GridPosition`
- `cellToPixel(position: GridPosition): Offset`
- `calculateTarget(startPosition, offset): GridPosition`
- `clampOffset(startPosition, rawOffset): Offset`

### 2) `AppDragDropController<T>`

**File:** `ui/components/dragdrop/AppDragDropContract.kt`

Purpose:

- Owns drag lifecycle and state machine
- Works with any item type (`T`) so it is not home-grid specific
- Produces semantic drag results without touching persistence

Lifecycle:

1. `startDrag(item, itemId, startPosition)`
2. `updateDrag(delta, metrics)` repeated while moving
3. `endDrag(metrics)` or `cancelDrag()`

State exposed:

- `session: AppDragDropSession<T>?`
- `targetPosition: GridPosition?`

Helper methods:

- `isDraggingItem(itemId)`
- `resolveBasePosition(itemId, currentPosition)`

### 3) `AppDragDropResult<T>`

**File:** `ui/components/dragdrop/AppDragDropContract.kt`

Result types:

- `Moved`: drag ended in a new cell
- `Unchanged`: drag ended on original cell
- `Cancelled`: no active drag or cancelled interaction

This keeps the controller purely UI-interaction oriented. Data-layer updates are still performed by callers.

### 4) `detectDragGesture(...)`

**File:** `ui/components/grid/DragGestureDetector.kt`

Purpose:

- Canonical shared modifier that wires pointer input to drag callbacks
- Surfaces define behavior directly using tap / long-press / drag lifecycle callbacks
- Cancellation and pointer-state failures are handled with structured logging for observability

Input:

- stable `key` (typically `id + row + column`)
- `dragThreshold`
- lifecycle callbacks (`onTap`, `onLongPress`, `onDragStart`, `onDrag`, `onDragEnd`, `onDragCancel`)
- this is the single supported public callback style for gesture wiring

### Tap classification fix (March 2026)

`DragGestureDetector` previously used an additional `waitForUpOrCancellation()` probe
when `awaitLongPressOrCancellation()` returned `null`.

In practice, that extra probe could miss an already-finished quick-tap `UP` event,
misclassify the interaction as cancellation, and drop the first tap. The user-visible
effect was a "double tap required" feeling across surfaces using `detectDragGesture`.

Current behavior:

- If long-press was not reached and the original pointer is no longer pressed in the
        current event state, the gesture is classified as `onTap` immediately.
- If the pointer is still pressed, it is treated as cancellation (`onDragCancel`).

Why this is safe for external drag-drop:

- Long-press + drag path is unchanged.
- External drag start still happens only through `onDragStart` after crossing drag threshold.
- The fix only affects the `longPress == null` branch (quick tap / cancellation disambiguation).

## Home screen integration

The home grid (`DraggablePinnedItemsGrid`) now:

1. Creates `dragController` via `rememberAppDragDropController<HomeItem>(config)`
2. Builds `AppDragDropLayoutMetrics` from `BoxWithConstraints`
3. Uses `detectDragGesture(...)` per item
4. On `Moved` result, emits `onItemMove(itemId, to)`

### External payload drops from search dialog

The home grid also acts as a platform drag-and-drop target for launcher payloads.

- Search app rows start drag via `startExternalAppDrag(...)`.
- Contact rows (`c` prefix) start drag via `startExternalContactDrag(...)`.
- File rows (`f` prefix) start drag via `startExternalFileDrag(...)`.
- Home grid uses `AppExternalDropTargetOverlay(...)`.
- External hover positions are converted via `AppDragDropLayoutMetrics.pixelToCell(...)`.
- `LauncherScreen` forwards the resolved `HomeItem` + cell to `onItemDroppedToHome`.

Implementation details for reliability:

- External drag starts from the activity decor view (not the dialog local view).
        This prevents drag cancellation when search dialog is dismissed after drag start.
- The drag shadow is icon-only, using a custom `DragShadowBuilder`, so the user
        sees the app icon ghost instead of a full dialog snapshot.
- Home grid renders a live external drag hover highlight that tracks
        `ACTION_DRAG_LOCATION` events and shows the current target cell.
- External platform listener state machine is centralized in
        `DefaultExternalAppDragDropCoordinator` so target surfaces no longer own
        payload decoding or listener lifecycle details.

This path allows dragging app/file/contact results from the search dialog and
dropping them directly into a target home grid cell.

### External drop reliability rules (current behavior)

The external drop pipeline now uses stricter rules to make drop placement and
highlight feedback stable across devices:

1. **Payload-gated activation**
         - The external drop overlay only activates when the drag payload resolves to
                 a valid launcher payload (`App`, `File`, `Contact`, or `Widget`).
         - Payload resolution prefers same-process `localState` first, then falls back
                 to ClipData JSON decoding.
         - This prevents unrelated system drags from triggering home-grid highlights.

6. **Widget payload fallback is mandatory**
         - Widget drags can lose `localState` on some OEM/global drag paths.
         - Widget payloads therefore include a ClipData JSON fallback carrying:
                 provider package, provider class, and span (`columns`, `rows`).
         - On drop, if `providerInfo` is missing, the grid resolves it from
                 `WidgetHostManager.getInstalledProviders()` using the provider component.
         - This keeps widget hover highlights span-aware and prevents silent drop failures.

7. **Widget span normalization for fixed grid width**
         - Provider-reported widget spans can exceed launcher grid width
                 (example: `7x7` widget on a `4`-column grid).
         - Before collision checks and placement, widget spans are normalized using
                 a launcher-style default heuristic:
                 1) clamp columns to grid width,
                 2) scale rows proportionally when width shrinks,
                 3) cap rows to a practical default (currently 4).
         - Example on 4-column grid: `7x7` is placed as `4x4`.
         - Hover highlight uses the same normalized span so preview matches drop result.

2. **Stable drag host selection**
         - External payload drags are started from Activity decor view when available,
                 with safe fallbacks to root/local host view.
         - This reduces cross-window instability when search dialog is dismissed right
                 after drag begins.
         - Drag start uses `View.DRAG_FLAG_GLOBAL` (with local fallback), so the
                 drag can cross from dialog window to the home-screen window.

3. **Centralized listener lifecycle**
         - `DefaultExternalAppDragDropCoordinator` owns drag session state.
         - `AppExternalDropTargetOverlay` is now a thin adapter that forwards
                 callbacks using `rememberUpdatedState`.
         - This removes duplicated listener logic from surfaces and keeps behavior
                 consistent across all current/future drop targets.

4. **Hover-first drop target resolution**
         - Final drop cell prefers the **last hovered target cell** collected from
                 `ACTION_DRAG_LOCATION`.
         - `ACTION_DROP` coordinates are only used as fallback when no hover sample
                 exists (very quick drop).
         - This guarantees final pin placement matches what users saw highlighted.

5. **Visual parity with internal drag**
         - External target highlight now uses the same item-shaped visual language as
                 internal drag highlight (showing item-style preview when payload is available).
         - The highlight cell also shows a shadow, a background glow tinted with the
                 theme primary color, and a subtle border — making the target cell clearly
                 visible even on busy wallpapers.

Visual behavior remains launcher-like:

- Long-press shows menu immediately (non-focusable popup during gesture)
- If finger lifts without movement: menu becomes focusable and interactive
- If finger moves past threshold: menu closes, drag starts
- Drop target highlight follows resolved cell
- Preview item follows finger
- Haptic feedback is triggered at long press, drag activation, and confirmed drop

## Widget picker interaction contract (March 2026)

Widget cards in `WidgetPickerBottomSheet` are now **drag-only**:

- Tap is intentionally ignored.
- Long-press + drag starts external platform drag.
- The picker closes when drag starts so the home grid is visible.
- Bind/configure flow starts only after a successful drop onto a valid home-grid target.

Why this matters:

- Prevents accidental pre-drop widget placement.
- Ensures widget configuration happens in the correct order (after target cell is chosen).
- Aligns with the app drawer behavior where drag starts external placement intent.

## Widget placement flow ownership (March 2026 cleanup)

Widget placement now uses an explicit command-based state machine in
`HomeViewModel` instead of callback lambdas passed from `MainActivity`.

Why this was changed:

- Previous flow passed `launchBindPermission` / `launchConfigure` callbacks down
  into view-model logic, which spread control flow across layers and made
  behavior harder to follow.
- The new flow keeps all widget state transitions in one place and makes the
  activity a thin command dispatcher.

Current contract:

1. `MainActivity` calls `HomeViewModel.startWidgetPlacement(...)` after a valid
        widget drop.
2. `HomeViewModel` returns one `WidgetPlacementCommand`:
        - `LaunchBindPermission(intent)`
        - `LaunchConfigure(intent)`
        - `NoOp` (flow completed or cancelled)
3. `MainActivity` executes command intents via pre-registered ActivityResult
        launchers.
4. Activity results are routed back into:
        - `HomeViewModel.handleWidgetBindResult(...)`
        - `HomeViewModel.handleWidgetConfigureResult(...)`
5. ViewModel performs final persistence through serialized home mutation path.

Benefits:

- One source of truth for bind/configure/placement transitions.
- No launch callback wiring in business logic.
- Easier maintenance and safer future edits.
- Keeps widget ID cleanup behavior centralized with placement failure handling.

## Widget long-press reliability (March 2026)

Widgets are rendered using `AppWidgetHostView` inside `AndroidView`. On some devices,
`AppWidgetHostView` consumes touch events before parent Compose pointer handlers can
observe them, which makes parent-level long-press detection unreliable.

Current behavior:

- A transparent Compose gesture layer is rendered above each home-screen widget.
- Gesture rules now match normal home icons exactly:
        - Long-press opens widget dropdown menu.
        - Long-press + drag closes menu and starts internal drag.
        - Releasing after long-press without drag keeps menu interactive.
- Widget drag uses the same `detectDragGesture` + `AppDragDropController` path,
        with span-aware occupancy checks on drop.

## Widget drag-size visuals (March 2026)

Widget dragging now shows span-sized visuals in both paths:

- Internal home-grid drag: target highlight and floating preview use the widget span
        (`columns × rows`) instead of a fixed 1×1 cell.
- External picker drag: platform drag shadow uses a lightweight plain box
        (no stretched widget preview image), while homescreen span-highlight shows
        the final target footprint.
- External highlight now waits for resolved payload before rendering, avoiding
        transient 1×1 fallback highlights for widgets.

### Non-focusable popup pattern for long-press + drag coexistence

The `DropdownMenu` creates a popup window. When the popup is focusable (default),
Android routes touch events to the popup — which prevents the underlying gesture
detector from receiving movement events needed for drag detection.

To allow both long-press menus AND drag-after-long-press:

1. On `onLongPress`: show the menu with `focusable = false` (popup is visible but
   doesn't steal touches).
2. On `onLongPressRelease` (finger lifts without drag): switch to `focusable = true`
   so menu items become tappable.
3. On `onDragStart` (finger moves past threshold): close the menu and start drag.

This pattern is used by `AppGridItem`, `AppListItem`, and `DraggablePinnedItemsGrid`.
The `ItemActionMenu` component supports this via its `focusable` parameter.


## Reuse recipe for other launcher surfaces

To add drag-drop to a new surface:

1. Provide your own item type `T` with stable item ID and position
2. Create `val controller = rememberAppDragDropController<T>(config)`
3. Compute `AppDragDropLayoutMetrics` from your measured layout
4. Attach `Modifier.detectDragGesture(...)` to draggable nodes
5. Persist only when result is `AppDragDropResult.Moved`

This ensures the same drag rules and behavior across launcher surfaces.

## Notes about persistence

The controller does not write repository data. This is intentional:

- UI interaction and persistence are decoupled
- `HomeViewModel` is now the single home-mutation coordinator for move, external drop, pin, and unpin writes
- All home writes are serialized through one mutation pipeline to avoid ordering races
- Occupied target cells are now rejected (no swap behavior)
- External payload drop persistence uses one atomic repository operation (`pinOrMoveItemToPosition`) to avoid two-phase placement flicker

## Home item mapping for external drops

The drop target adapter maps external payloads into persisted `HomeItem` types:

- `ExternalDragItem.App` -> `HomeItem.PinnedApp`
- `ExternalDragItem.File` -> `HomeItem.PinnedFile`
- `ExternalDragItem.Contact` -> `HomeItem.PinnedContact`

This keeps the platform drag pipeline generic while preserving the existing home
repository mutation semantics.

## Legacy cleanup

The old grid drag abstractions under `ui/components/grid/` (`DragController`,
`DragState`, `DropTarget`) were removed from the active codebase after the
`ui/components/dragdrop/` system became the canonical implementation.

This reduces dual-system drift and prevents accidental reintroduction of legacy
state flows that bypass current reliability guarantees.

## Future extension points

The new core is designed to support:

- Dock drag-drop
- Folder drag-drop
- Cross-surface drag dispatch (surface-level drop routing)
- External payload drop adapters (for app/file/contact payloads)

When adding those features, reuse the same controller and metrics types to keep behavior consistent.
