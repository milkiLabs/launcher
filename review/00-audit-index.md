# Launcher Audit Index (2026-03-06)

This audit reviews the current codebase across architecture, design quality, reliability, performance, UX/UI consistency, and maintainability.

## Priority Summary

- P0: Fix now (functional risk / data integrity / major architectural blocker)
- P1: Fix soon (high impact maintainability, UX consistency, perf under real usage)
- P2: Plan and execute in roadmap (modularization and long-term standardization)

## Report Files

1. `review/01-critical-functional-and-architecture-risks.md`
- P0/P1 findings that can cause user-facing bugs, state corruption, or critical scalability pain.

2. `review/02-performance-and-reliability.md`
- Performance bottlenecks, duplicate expensive flows, and failure handling quality.

3. `review/03-ui-ux-and-design-system.md`
- UX and interaction review, design-system drift, accessibility/usability gaps.

4. `review/04-dead-code-and-standardization.md`
- Dead code, TODO debt, and concrete coding standards to unify implementation style.

5. `review/05-modularization-roadmap.md`
- Step-by-step plan to split large files, self-contain features, and move toward a maintainable modular architecture.

6. `review/06-settings-configuration-audit.md`
- Focused audit of settings/configuration models, persistence, runtime application, and scalability/performance patterns.

## Top 5 Immediate Actions

1. Replace single `pendingWidget` state with a robust widget-placement session model to prevent overwrites and wrong-result routing (`app/src/main/java/com/milki/launcher/presentation/home/HomeViewModel.kt:645`).
2. Remove duplicate initial app-loading paths and centralize installed-app stream sharing to prevent repeated heavy `PackageManager` scans (`app/src/main/java/com/milki/launcher/presentation/search/SearchViewModel.kt:123`, `app/src/main/java/com/milki/launcher/presentation/drawer/AppDrawerViewModel.kt:140`).
3. Stop silently dropping malformed home items on deserialize; add corruption telemetry and recovery UX (`app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:880`, `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:888`).
4. Close permission UX gap for permanently denied states (`app/src/main/java/com/milki/launcher/handlers/PermissionHandler.kt:264`).
5. Enforce UI design-system rules (Spacing + AutoMirrored icons) and remove current violations (`app/src/main/java/com/milki/launcher/ui/components/FolderPopupDialog.kt:371`, `app/src/main/java/com/milki/launcher/ui/screens/LauncherScreen.kt:355`).
