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
