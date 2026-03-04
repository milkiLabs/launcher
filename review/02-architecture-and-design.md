# Architecture & Software Design Audit

## P1 — Layer boundary violations

## 1) Domain layer depends on Compose UI types (**P1**)
### Evidence
- `SearchProviderConfig` contains `Color` and `ImageVector`.
- `SearchProvider` (domain interface) effectively couples to UI representation.

### Why this is a problem
- Domain becomes Android/Compose-aware.
- Testing and reuse become harder.
- Violates clean separation between domain and presentation.

### Suggested design
- Domain config should contain only semantic identifiers (`providerId`, `name`, `description`, `defaultPrefix`).
- Presentation layer maps providerId -> icon/color using theme tokens.

---

## 2) Overgrown files and high cognitive load (**P1**)
### Hotspots
- `SearchResultItems.kt` (~700+ lines)
- `SettingsComponents.kt` (~700+ lines)
- `SearchViewModel.kt` (~650+ lines)
- `ContactsRepositoryImpl.kt` (~650+ lines)

### Impact
- Hard to reason about behavior and regressions.
- Large merge conflicts and fragile refactors.

### Refactor direction
- Split by feature concern, not by arbitrary size:
  - Search result UI: one file per result type + common primitives.
  - Settings UI: cards/inputs/prefix-editor/dialog separated.
  - Search VM: state holder + pipeline coordinator + settings adapter.
  - Contacts repo: query layer + mapping layer + recent-contact storage.

---

## 3) Business logic in `Activity` remains high (**P1**)
### Evidence
- `MainActivity` orchestrates permission wiring, action executor callbacks, home button behavior, and search toggling.

### Impact
- Lifecycle/UI container does policy decisions.
- Harder to test behavior without instrumented tests.

### Suggested design
- Introduce coordinator/use-case classes:
  - `HomeButtonPolicy`
  - `PermissionRequestCoordinator`
  - `SearchSessionController`

---

## 4) Dual drag/gesture abstractions may be overengineered (**P2**)
### Evidence
- Generic drag contract + additional modifier wrappers + separate detector layers.

### Impact
- More abstraction than current app scope needs.
- Steeper onboarding for contributors.

### Recommendation
- Keep reusable core, but reduce indirection:
  - Single canonical gesture API.
  - Remove duplicate wrappers unless they enforce real invariants.

---

## 5) Excessive broad exception swallowing (**P2**)
### Evidence
- Multiple `catch (Exception)` blocks in core flows and repositories.

### Impact
- Silent failures and degraded observability.
- Hard to debug user-reported issues.

### Recommendation
- Catch specific exception types where expected.
- Emit structured logs/events for non-fatal failures.
- Avoid returning empty results for all failures without surfacing reason.

---

## 6) Documentation verbosity inside production files hurts scanability (**P2**)
### Evidence
- Very long explanatory comments in most classes.

### Impact
- Important logic can be buried in narrative.

### Recommendation
- Keep high-value comments near tricky code.
- Move educational deep-dives to docs and link from code headers.
- Preserve educational mission, but optimize on-file signal-to-noise for maintainers.