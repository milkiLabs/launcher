# P3 UX/UI and Standards Findings

## UX/UI improvements

## 1) Accessibility consistency gaps
- Severity: High
- Confidence: Medium (spot-checked + subagent scan)
- Examples:
  - Decorative and functional icon semantics are mixed; establish a strict contentDescription rulebook.
  - Dynamic state changes (sheet open/close, pagination, load completion) should emit accessibility announcements.
- Suggested standard:
  - Define a reusable accessibility helper policy and review all interactive components against it.

## 2) Error and loading state consistency
- Severity: High
- Confidence: Medium
- Problem:
  - Loading/error UX patterns are implemented differently across screens.
- Suggested standard:
  - Shared loading/error composables and a single screen-state contract with loading/empty/error/success branches.

## 3) Touch target and focus visibility review
- Severity: Medium
- Confidence: Medium
- Problem:
  - Some controls likely depend on implicit sizing/padding rather than explicit minimum touch targets and visible focus states.
- Suggested standard:
  - Introduce shared tokens for min tap size and focus ring style.

## 4) User messaging channel fragmentation
- Severity: Medium
- Confidence: Confirmed by broad scan
- Evidence pattern:
  - Multiple direct `Toast` usages (for example `ActionExecutor` and `FileOpener`).
- Suggested standard:
  - Move to a unified snackbar/message-host strategy integrated with Compose scaffolds and state.

## Engineering standards to enforce in CI
1. No broad `Exception` catches without typed mapping and logging rationale.
2. No presentation imports from core/data packages.
3. No duplicate top-level behavior utilities without ownership package.
4. No placeholder/empty production utility objects.
5. Every new feature flow requires tests for success, cancellation, and error paths.
