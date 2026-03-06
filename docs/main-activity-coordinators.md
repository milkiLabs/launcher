# MainActivity Coordinator Extraction

This document explains the coordinator extraction that reduces orchestration concentration in `MainActivity`.

## Problem This Addresses

`MainActivity` previously handled too many unrelated orchestration responsibilities in one class:

- Widget bind/configure launcher wiring
- Layered surface visibility and close ordering
- HOME-button intent orchestration and policy execution

This increased regression risk and made policy testing difficult.

## New Coordinator Boundaries

Three coordinator interfaces were introduced under `presentation/main`:

- `SurfaceStateCoordinatorContract`
- `HomeIntentCoordinatorContract`
- `WidgetPlacementCoordinator`

Each has a concrete default implementation.

## 1) SurfaceStateCoordinator

File:

- `app/src/main/java/com/milki/launcher/presentation/main/SurfaceStateCoordinator.kt`

Responsibilities:

- Owns Compose-observed surface flags:
  - homescreen menu
  - app drawer
  - widget picker
- Owns layered close ordering for:
  - back press
  - HOME press pre-consumption
- Owns swipe-up drawer transition side effects:
  - close menu
  - hide search
  - close folder
  - open drawer

Why this matters:

- Ordering rules are now centralized and unit-tested.
- `MainActivity` no longer manually mutates multiple flags in multiple branches.

## 2) HomeIntentCoordinator

File:

- `app/src/main/java/com/milki/launcher/presentation/main/HomeIntentCoordinator.kt`

Responsibilities:

- Tracks lifecycle marker (`wasAlreadyOnHomescreen`) via `onResume()` / `onStop()`.
- Runs HOME-button flow orchestration:
  1. ask surface coordinator to consume layered surfaces first
  2. build `HomeButtonPolicy.InputState`
  3. resolve decision with `HomeButtonPolicy`
  4. delegate side-effect execution to decision applier

Why this matters:

- HOME-button policy flow is now explicit and testable without Activity internals.

## 3) WidgetPlacementCoordinator

File:

- `app/src/main/java/com/milki/launcher/presentation/main/WidgetPlacementCoordinator.kt`

Responsibilities:

- Registers bind/configure `ActivityResult` launchers.
- Routes bind/configure results back to `HomeViewModel` command flow.
- Executes `WidgetPlacementCommand` launch actions.

Why this matters:

- Keeps launcher registration and widget command dispatch out of `MainActivity`.
- Preserves existing ViewModel command-based architecture.

## MainActivity After Extraction

`MainActivity` now:

- Initializes coordinators during `onCreate`.
- Delegates back-press layered behavior to `SurfaceStateCoordinator`.
- Delegates HOME-button orchestration to `HomeIntentCoordinator`.
- Delegates widget command execution to `WidgetPlacementCoordinator`.
- Primarily acts as lifecycle host + Compose host.

## Unit Tests Added

- `app/src/test/java/com/milki/launcher/presentation/main/HomeIntentCoordinatorTest.kt`
- `app/src/test/java/com/milki/launcher/presentation/main/SurfaceStateCoordinatorTest.kt`

Coverage includes:

- HOME layered-surface short-circuiting
- Home policy decision transitions
- Back/HOME close ordering
- Swipe-up drawer transition behavior
- Stop lifecycle transient-surface cleanup

## Extension Guidance

When adding new cross-surface behavior:

1. Add state transitions in `SurfaceStateCoordinator` first.
2. Keep `HomeIntentCoordinator` focused on policy orchestration, not UI details.
3. Keep widget launcher concerns in `WidgetPlacementCoordinator` only.
4. Add/adjust unit tests in coordinator test files before touching `MainActivity`.

This keeps the shell/coordinator boundary stable as features evolve.
