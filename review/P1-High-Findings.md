# P1 High Findings

## 1) Possible over-recomposition pressure in draggable grid calculations
- Severity: High
- Confidence: Medium (needs compose tracing)
- Evidence:
  - `app/src/main/java/com/milki/launcher/ui/components/launcher/DraggablePinnedItemsGrid.kt:93`
- Impact:
  - Reorder plan is recomputed whenever `items` reference changes, potentially expensive with frequent drag updates.
- Fix:
  - Stabilize item inputs and profile with Compose tracing tools.

## 2) Test surface is small relative to production codebase
- Severity: High
- Confidence: Confirmed
- Evidence:
  - Approximate counts before this pass: `15` test files vs `185` main Kotlin files.
- Impact:
  - Regression risk in stateful and integration-heavy launcher flows.
- Fix:
  - Prioritize tests for search pipeline, home mutation coordinator, permission flow, widget placement, and failure paths.
