# MainActivity Architecture

This document explains the architecture of MainActivity and its handler classes.

## Overview

MainActivity is now a lean host that delegates behavior policy and cross-component wiring to dedicated classes. This follows the **Single Responsibility Principle** and reduces Activity-level business logic.

## File Structure

```
app/src/main/java/com/milki/launcher/
├── MainActivity.kt                 - Lifecycle host + Compose composition
├── handlers/
│   └── PermissionHandler.kt        - Permission launchers + state updates
└── presentation/main/
    ├── HomeButtonPolicy.kt         - Pure home-button decision logic
    ├── PermissionOrchestrator.kt   - Internal permission state machine + reducer
    ├── SearchSessionController.kt  - Applies search/menu transitions
    └── PermissionRequestCoordinator.kt - Wires ActionExecutor <-> PermissionHandler
```

## Class Responsibilities

### MainActivity

**What it does:**
- UI composition (setting up Compose content)
- Lifecycle management (onCreate, onResume, onStop)
- Home intent detection and delegation to policy/controller
- Delegating permission callback wiring to coordinator

**What it does NOT do:**
- Home-button decision logic (delegated to HomeButtonPolicy)
- Search/menu transition logic (delegated to SearchSessionController)
- Permission callback orchestration (delegated to PermissionRequestCoordinator)

### PermissionHandler

**What it does:**
- Register permission launchers (contacts, files, manage storage)
- Check permission states
- Request permissions from user
- Update ViewModel with permission states

**Why separate it?**
- Permission logic is complex (different for Android 11+)
- Can be reused by Settings Activity
- Easier to test independently
- Settings can affect permission requirements

### HomeButtonPolicy

**What it does:**
- Accepts simple input state and resolves one home-button decision.
- Keeps behavior deterministic with explicit priority ordering.

**Why separate it?**
- Pure Kotlin logic can be tested without Android runtime.
- Prevents policy drift across Activity methods.

### SearchSessionController

**What it does:**
- Applies decisions from HomeButtonPolicy.
- Executes search/menu transitions (open search, clear query, hide search, close menu).

**Why separate it?**
- Centralizes transition side effects.
- Keeps MainActivity from directly mutating search/menu state in multiple places.

### PermissionRequestCoordinator

**What it does:**
- Wires `ActionExecutor` callbacks to `PermissionHandler` request APIs.
- Delegates request/result sequencing to `PermissionOrchestrator`.
- Routes completed permission results to interested consumers (`ActionExecutor` for call replay).
- Wires action side effects that touch `SearchViewModel` (close search, save recent app).

**Why separate it?**
- Removes callback orchestration noise from Activity.
- Keeps inter-object wiring in one explicit place.

### PermissionOrchestrator

**What it does:**
- Implements permission flow as a small state machine.
- Serializes active requests and keeps at most one queued request.
- Ignores stale/out-of-order results safely.
- Emits explicit effects: `RequestPermission` and `DeliverResult`.

**Why separate it?**
- Makes behavior deterministic and testable in pure Kotlin.
- Reduces edge-case regressions caused by ad-hoc callback ordering.
- Enables future extension to more permissions without rewriting coordinator logic.

## Data Flow (Home Button)

```
Home Intent (ACTION_MAIN + CATEGORY_HOME)
    │
    ▼
┌─────────────────┐
│  MainActivity   │ collects current UI/menu state
└────────┬────────┘
         │ InputState
         ▼
┌─────────────────┐
│ HomeButtonPolicy│ resolves Decision
└────────┬────────┘
         │ Decision
         ▼
┌───────────────────────┐
│ SearchSessionController│ applies transition
└────────┬──────────────┘
         │
         ▼
 SearchViewModel + menu-state callback
```

## Data Flow (Permission Request Wiring)

```
Search UI action
    │
    ▼
ActionExecutor.onRequestPermission(permission)
    │
    ▼
PermissionRequestCoordinator
    │
    ▼
PermissionOrchestrator (state machine)
    │
    ├── emits RequestPermission effect
    │      ├──> PermissionHandler.requestContactsPermission()
    │      ├──> PermissionHandler.requestCallPermission()
    │      └──> PermissionHandler.requestFilesPermission()
    │
    └── receives PermissionHandler.onPermissionResult(permission, granted)
           │
           ├── emits DeliverResult effect for CALL_PHONE
           │      └──> ActionExecutor.onPermissionResult(granted)
           │
           └── ignores stale/out-of-order results safely
```

## Home Button Detection

The launcher still uses lifecycle state tracking for detecting whether user is already on homescreen:

1. `onStop()` sets `wasAlreadyOnHomescreen = false` (user left home screen)
2. User presses home → `onNewIntent()` fires BEFORE `onResume()`
3. At this point, flag is still `false` → We know user is returning from an app
4. `onResume()` sets `wasAlreadyOnHomescreen = true`
5. User presses home again → `onNewIntent()` fires
6. Flag is `true` → We know user was already on home screen

**Why not use `FLAG_ACTIVITY_BROUGHT_TO_FRONT`?**

That flag has the opposite meaning of what you'd expect:
- Set when activity is brought to front from **background** (returning from app)
- NOT set when activity is already in foreground (pressing home while on home)

## Why This Is Cleaner

- MainActivity is now an orchestrator, not a policy engine.
- Home-button behavior is pure/testable.
- Transition side effects are centralized in one controller.
- Permission callback wiring is explicit and isolated.

## Testing Strategy

These classes can be tested independently with small unit tests:

```kotlin
// Example: test pure policy
@Test
fun `home press closes menu before opening search`() {
    val policy = HomeButtonPolicy()
    val decision = policy.resolve(
        HomeButtonPolicy.InputState(
            isAlreadyOnHomescreen = true,
            isHomescreenMenuOpen = true,
            isSearchVisible = false,
            hasSearchQuery = false
        )
    )

    assertEquals(HomeButtonPolicy.Decision.CLOSE_MENU, decision)
}
```
