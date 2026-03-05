# Dead Code and Standardization Audit

## Dead Code / Obsolete Surface

### 1) `PinnedItemsGrid` appears unused
- Evidence:
- Declaration: `app/src/main/java/com/milki/launcher/ui/components/PinnedItemsGrid.kt:58`
- Active usage points use `DraggablePinnedItemsGrid`: `app/src/main/java/com/milki/launcher/ui/screens/LauncherScreen.kt:310`
- Search shows no real callsites for `PinnedItemsGrid`.
- Recommendation:
1. Remove file if no planned reuse.
2. If kept for fallback, move to `ui/components/legacy/` and document owner/use-case.

## TODO Debt (User-visible or Core-flow)

1. Permanently denied permission handling missing: `app/src/main/java/com/milki/launcher/handlers/PermissionHandler.kt:264`.
2. App shortcut launch implementation marked TODO: `app/src/main/java/com/milki/launcher/util/AppLauncher.kt:155`.
3. Shortcut icon loading marked TODO: `app/src/main/java/com/milki/launcher/ui/components/PinnedItem.kt:524`.

## Standardization Proposals

### A) UI Standards

1. `Spacing` only for all size/padding/radius values.
2. `AutoMirrored` icons by default where available.
3. Screen-level composables should expose grouped action contracts, not 20+ callback parameters.

### B) Error and Logging Standards

1. No broad `catch (Exception)` without contextual log metadata.
2. Every fallback path should include structured log fields (feature, action, target, cause).
3. No silent data drops in repository parsing.

### C) Feature Boundary Standards

1. Each feature owns its state machine and UI host:
- `search/*`
- `home/*`
- `folder/*`
- `widget/*`
- `drawer/*`
- `settings/*`
2. Cross-feature interactions go through explicit contracts/events, not direct shared mutable state.
3. Domain interfaces should stay behavior-focused and avoid UI concerns.

### D) File and Complexity Standards

1. Soft limit: 350 lines for normal source files.
2. Hard review trigger: >600 lines requires split plan in same PR.
3. Every large feature package gets a short `README.md` with responsibilities and invariants.

## Suggested Cleanup PR Sequence

1. Remove or archive unused `PinnedItemsGrid`.
2. Create lint/static checks for `dp` hardcoding and icon mirroring policy.
3. Convert TODOs with user-facing impact into tracked issues with acceptance criteria.
4. Add architecture gate checklist to PR template.
