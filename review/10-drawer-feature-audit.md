# Drawer Feature Audit

Date: 2026-03-06
Scope: app drawer data loading, sorting, overlay UX, and interaction behavior.

## Findings

### P2) Drawer lacks in-surface quick filter for large app sets
- Evidence: drawer currently presents full adaptive grid with sort only at `app/src/main/java/com/milki/launcher/ui/components/AppDrawerOverlay.kt:77`
- Problem: sort-only UX does not scale well for large app catalogs.
- Risk: poor retrieval time for users with many installed apps.
- Recommendation:
1. Add lightweight drawer-local query box (optional).
2. Add alphabet fast-scroll/section jump if keeping full-grid model.

### P2) Sort-mode keyed subtree reset is effective but coarse
- Evidence: `app/src/main/java/com/milki/launcher/ui/components/AppDrawerOverlay.kt:96`
- Problem: keying by sort mode recreates subtree to force top-reset (intentional), but also resets local UI state broadly.
- Risk: future feature additions inside overlay may experience unintended resets.
- Recommendation:
1. Keep explicit top-scroll reset behavior but isolate keying to list state rather than entire surface when adding more stateful controls.

## What Is Good

1. Drawer feature is nicely isolated in dedicated ViewModel and overlay composable.
2. Sort behavior and loading state are clear and deterministic.
3. External drag start from drawer apps is integrated with close-on-drag UX.
