# Home Surface Interactions

This document describes the cleaned-up interaction model for the homescreen.

## Goals

- Keep one source of truth for transient homescreen interaction state.
- Keep drag/drop routing separate from gesture conflict policy.
- Make swipe-up stable today and make swipe-down easy to add later.
- Reduce inline boolean coupling across the grid layers.

## Current structure

### 1. `HomeSurfaceInteractionController`

File:

- `app/src/main/java/com/milki/launcher/ui/components/HomeSurfaceInteractionController.kt`

Responsibilities:

- Tracks context-menu visibility.
- Tracks whether the long-press gesture is still active.
- Tracks widget resize mode.
- Tracks external drag hover state.
- Delegates internal drag lifecycle to `AppDragDropController`.

Why this matters:

- `DraggablePinnedItemsGrid` no longer owns a cluster of unrelated mutable flags.
- Layer composables read one interaction controller instead of threading state by hand.

### 2. `HomeBackgroundGestureBindings`

File:

- `app/src/main/java/com/milki/launcher/ui/components/grid/HomeBackgroundGesturePolicy.kt`

Responsibilities:

- Describes which background gestures are currently enabled.
- Keeps callbacks for:
  - empty-area long press
  - swipe up
  - swipe down

Why this matters:

- The grid API no longer needs separate `onHomeSwipeUp` + boolean gating params.
- Swipe-down can be introduced by wiring one additional callback, without redesigning the detector boundary again.

### 3. Pure policy from interaction snapshot

`HomeSurfaceInteractionSnapshot.toBackgroundGesturePolicy(...)` decides whether background gestures may start.

Current blockers:

- internal drag active
- external drag active
- widget resize active
- any homescreen context menu open

This keeps conflict rules explicit and testable instead of scattering them across the render tree.

## Layer responsibilities

### `DraggablePinnedItemsGrid`

- Owns layout metrics and drag/highlight wiring.
- Creates one `HomeSurfaceInteractionController`.
- Passes the controller into rendering/routing layers.

### `InternalGridDragLayer`

- Renders pinned items and widget views.
- Starts, updates, and ends internal drags through the controller.
- Uses the controller-derived background gesture policy.

### `ExternalDropRoutingLayer`

- Owns external drag/drop callbacks only.
- Updates hover/drop state through the controller.
- Keeps routing decisions in `ExternalHomeDropDispatcher`.

### `DropHighlightLayer`

- Reads drag state and renders visuals only.
- Does not decide whether gestures are allowed.

## Swipe-down plan

The detector and bindings now support swipe-down structurally, but product behavior is intentionally not wired yet.

Recommended next step:

1. Add a homescreen swipe-down setting alongside swipe-up.
2. Expose a host callback for swipe-down in the launcher action model.
3. Pass that callback through `HomeBackgroundGestureBindings(onSwipeDown = ...)`.
4. Keep the same `HomeSurfaceInteractionSnapshot` gating rules.

Good first candidates for swipe-down behavior:

- Open search
- Open notifications/quick settings proxy action
- Do nothing

## Extension rules

- Add new homescreen gesture features through `HomeBackgroundGestureBindings` first.
- Add new conflict rules through `HomeSurfaceInteractionSnapshot` policy conversion.
- Keep visual layers free of policy branching whenever possible.
- Keep data writes in dispatchers/ViewModels, not in gesture state holders.
