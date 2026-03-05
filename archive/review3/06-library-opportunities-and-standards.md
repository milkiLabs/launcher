# Library Opportunities & Engineering Standards

## 2) URL/domain matching correctness (**P2**)
### Current
- Custom resolver + heuristics for browser detection.

### Opportunity
- Consider dedicated URL/domain parsing library (or stricter Android URI utilities) for normalization and edge-case handling.

### Benefits
- Fewer subtle URL parsing bugs and better maintainability.



## 4) Permission orchestration helper (**P2**)
### Current
- Permission flow is handcrafted across handler + executor callbacks.

### Opportunity
- Add a small internal permission state machine helper module.

### Benefits
- Fewer edge-case regressions; easier testing.

---

## Team standards proposal

## A) Runtime settings contract (**P0 standard**)
- Every UI-visible setting must have:
  1. persistence
  2. runtime application path
  3. test (unit/integration) proving effect
- No setting shipped as interactive unless all three exist.

## B) Layer boundary standard (**P1 standard**)
- Domain models/interfaces cannot reference Compose/UI classes (`Color`, `ImageVector`, etc.).
- UI mapping belongs in presentation layer.

## C) Token-only UI standard (**P1 standard**)
- No hardcoded layout dp or feature-local color constants.
- Use `Spacing`, `IconSize`, `CornerRadius`, and theme primitives only.

## D) File-size and decomposition guideline (**P2 standard**)
- Soft cap: ~300-350 LOC per feature file.
- Above cap requires decomposition plan and ownership split.

## E) Error handling standard (**P1/P2 standard**)
- Avoid `catch (Exception)` unless rethrow/log strategy is explicit.
- Define typed error categories and user-facing fallback behavior.