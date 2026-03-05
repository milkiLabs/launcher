# Deprecated APIs, Dead Code, and Modernization

## 1) Settings fields and methods currently dead from runtime perspective (**P0/P1 dead behavior**)
### Evidence
- Numerous settings are stored/edited but not consumed by runtime logic.

### Recommendation
- Either wire them now (preferred) or remove from UI until implemented.

---

## 2) Placeholder/dead action path: `createOpenWithAction()` (**P1**)
### Evidence
- Returns `SearchResultAction.RequestPermission("", "")` and appears unused.

### Risk
- If invoked later, it triggers invalid action behavior.

### Fix
- Remove until implemented or replace with proper `OpenWith(file)` action type.

---

## 3) TODO-driven unfinished shortcut flow (**P2**)
### Evidence
- Shortcut launching and shortcut icon handling are TODO/placeholder implementations.

### Fix
- Implement real shortcut launch path via `LauncherApps` APIs.
- If out of scope, mark feature as experimental and hide unavailable options.

---

## 4) Deprecated API compatibility usage is generally acceptable but should be isolated (**P2**)
### Evidence
- Version-gated compatibility methods exist (`queryIntentActivitiesCompat`, resolver checks) with suppression on old APIs.

### Improvement
- Keep all compatibility shims in one `platform/compat` package for maintainability.

---

## 5) `collectAsState` in `SettingsActivity` not lifecycle-aware (**P2 modernization**)
### Evidence
- Uses `collectAsState()` instead of `collectAsStateWithLifecycle()`.

### Impact
- Potential unnecessary collection when not in active lifecycle states.

### Fix
- Adopt `collectAsStateWithLifecycle()` consistently across activities/screens.

---

## 6) Dependency modernization needed (**P2**)
### Evidence
- Core stack versions lag behind current date baseline.

### Fix
- Upgrade in small batches with changelog review and smoke tests:
  1. Compose + Activity/Lifecycle
  2. Koin
  3. Kotlinx serialization

---

## 7) Manifest and permission model review required (**P2/P3**)
### Evidence
- Broad storage permissions (`MANAGE_EXTERNAL_STORAGE`) used.

### Considerations
- Ensure this aligns with distribution requirements and user trust messaging.
- If feasible, reduce to scoped/document-picker based flow for lower policy friction.