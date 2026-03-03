# LauncherApplication.kt - Detailed Documentation

## Overview

`LauncherApplication.kt` is the app's custom `Application` class.

Current responsibilities are intentionally focused and minimal:
1. Initialize Koin dependency injection exactly once at process start.
2. Provide an app-wide startup point for future one-time initialization.

The class no longer configures Coil for app icons. App icon loading now uses a launcher-specific memory cache pipeline documented in [app-icon-memory-cache.md](app-icon-memory-cache.md).

---

## Why a Custom Application Class Exists

Android creates the `Application` instance before any `Activity`.
That makes it the safest place for startup work that must be available globally.

For this launcher, that startup work is Koin initialization.

If Koin were initialized later (for example inside `MainActivity`), dependency requests from other components could race and fail.

---

## Runtime Order

At app launch, the order is:

1. Android creates `LauncherApplication`.
2. `LauncherApplication.onCreate()` runs.
3. `initializeKoin()` starts Koin and registers modules.
4. Android creates `MainActivity` (or another entry activity).
5. Activities/ViewModels request dependencies from Koin.

This guarantees the DI container is ready before UI logic runs.

---

## Source Walkthrough

### Class Declaration

`class LauncherApplication : Application()`

- Extends Android `Application`.
- Does not implement additional startup interfaces right now.

### `onCreate()`

`onCreate()` performs:
- `super.onCreate()` first (required lifecycle contract).
- `initializeKoin()` next.

### `initializeKoin()`

The method calls:

- `startKoin { ... }` to create Koin container.
- `androidContext(this@LauncherApplication)` to provide Application context.
- `androidLogger(Level.ERROR)` to keep logs minimal by default.
- `modules(appModule)` to register dependencies.

---

## Why This Is Simpler and Better

- Single responsibility: startup + DI only.
- No launcher icon pipeline logic in Application.
- Easier mental model for beginners.
- Easier future maintenance (fewer framework hooks in one file).

---

## Related Files

- `app/src/main/java/com/milki/launcher/LauncherApplication.kt`
- `app/src/main/java/com/milki/launcher/di/appModule` (Koin module definition entry)
- `docs/KoinDependencyInjection.md`
- `docs/app-icon-memory-cache.md`

---

## Notes for New Contributors

If you need to add new one-time startup initialization, place it carefully:

- Keep it short and deterministic.
- Avoid blocking the main thread.
- Keep unrelated feature setup out unless it truly must run process-wide.

This keeps launch time and startup complexity controlled.
