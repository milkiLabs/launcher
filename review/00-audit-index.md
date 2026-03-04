# Launcher Codebase Audit (March 2026)

This audit reviews architecture, software design, bugs, performance, UX/UI, dead code, deprecated APIs, and standardization opportunities.

## Priority Legend
- **P0 (Critical):** User-facing functionality broken or settings do not work as advertised.
- **P1 (High):** Significant architecture/design debt, reliability risk, or major UX inconsistency.
- **P2 (Medium):** Performance/scalability concerns, maintainability issues, moderate UX gaps.
- **P3 (Low):** Cleanup, polish, optional modernization.

## Audit Files
1. `01-critical-functional-gaps.md` — highest-impact behavior bugs and misleading settings.
2. `02-architecture-and-design.md` — architecture smells, separation-of-concerns issues, overengineering.
3. `03-performance-reliability.md` — runtime performance and reliability risks.
4. `04-ui-ux-and-design-system.md` — UX friction and design-system violations.
5. `05-deprecated-dead-code-and-modernization.md` — dead code, deprecated usage, modernization targets.
6. `06-library-opportunities-and-standards.md` — libraries/dependencies and coding standards proposal.

## Executive Summary
The biggest issue is **feature integrity**: many settings are persisted and editable but are not actually applied at runtime. This creates a trust problem in UX and makes the app feel unstable/inconsistent.

Top priorities:
1. Fix P0 settings-to-runtime wiring first.
2. Establish architectural boundaries (domain should not depend on Compose UI primitives).
3. Split oversized files and unify action/gesture/menu patterns.
4. Enforce design-system tokens (`Spacing`, icon policy, color tokens) consistently.
5. Remove dead/placeholder paths and align with modern APIs and dependency versions.

## Suggested Delivery Phases
- **Phase 1 (P0, 2-4 days):** Runtime settings correctness (search behavior, provider enablement, close behavior, result limits).
- **Phase 2 (P1, 3-5 days):** Architecture boundary fixes + large file decomposition.
- **Phase 3 (P1/P2, 2-4 days):** UX consistency and design-system compliance.
- **Phase 4 (P2/P3, 2-3 days):** Dead code cleanup, modernization, dependency refresh.
