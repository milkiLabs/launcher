# Conventions

This document defines placement, naming, and behavioral conventions for the codebase.

## 1. Package Placement Rules

When adding new code, place files by ownership and responsibility:

- App startup/activity host logic -> `app.*`.
- Shared cross-feature helpers -> `core.*`.
- Persistence/system integration -> `data.*`.
- Pure business logic/contracts -> `domain.*`.
- ViewModel/runtime orchestration -> `presentation.*`.
- Compose rendering -> `ui.screens.*` or `ui.components.*`.
- Gesture/drag/grid mechanics -> `ui.interaction.*`.

Do not add new files to retired namespaces:

- `com.milki.launcher.presentation.main`
- flat `com.milki.launcher.ui.components` (without feature subpackage)
- old `com.milki.launcher.ui.components.dragdrop` / `grid` / `folder` / `widget`

## 2. Feature-Scoped UI Ownership

- Launcher UI belongs under `ui.components.launcher.*`.
- Search UI belongs under `ui.components.search`.
- Shared app row/grid icon components belong under `ui.components.common`.
- Settings screens/contracts belong under `ui.screens.settings`.
- Reusable settings widgets remain under `ui.components.settings`.

Rule of thumb:

- If a composable is launcher-specific (home surface, folder popup, widget overlay), keep it in launcher packages.
- If it represents generic app-display primitives (icon/list/grid row), keep it in common.

## 3. Naming and API Shape

- Prefer explicit type names over abbreviations.
- Keep function names verb-first for actions (`startWidgetTransform`, `updateFilesPermissionState`).
- Keep data contracts small and strongly typed.
- Avoid adding broad facade APIs when a narrow contract is enough.

## 4. State and Mutation Safety

- Serialize home mutations via the existing single-writer queue.
- Never write home state from stale UI snapshots.
- At mutation time, resolve fresh persisted state before applying changes.
- For drag/drop handlers in Compose, use latest state references where callbacks can outlive composition snapshots.

## 5. Grid and Occupancy Rules

- Use shared occupancy helpers for all placement checks.
- Never use top-left-only comparisons for span-aware items.
- Keep grid column defaults in one shared constant path (no duplicated literals across layers).

## 6. Repository Conventions

- Keep repository implementations as orchestrators with focused collaborators.
- Prefer immutable snapshots when emitting list flows.
- For trigger streams that can fire same-value events repeatedly (for example `Unit`), use hot shared flows instead of state conflation when repetition matters.

## 7. Error Handling

- Treat widget bind/configure launcher failures as canceled flow and clean pending state.
- For file cleanup, avoid destructive actions on transient provider/security errors.
- Guard platform intents that can throw on OEM/provider variance.

## 8. Documentation Convention

Any significant structural or behavioral refactor should update:

- `docs/Architecture.md` if package/layout changed.
- `docs/Conventions.md` if rules changed.
- `docs/Distilled-Learnings.md` if a new hard-earned guardrail emerged.
