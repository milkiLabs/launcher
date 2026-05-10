# P0 Critical Findings

## 1) Exception swallowing is widespread and inconsistent
- Severity: Critical
- Confidence: Confirmed
- Evidence:
  - `24` broad catches currently remain: `catch (e: Exception)` or `catch (_: Exception)` across main source.
  - Representative files:
    - `app/src/main/java/com/milki/launcher/data/widget/WidgetHostManager.kt:145`
    - `app/src/main/java/com/milki/launcher/data/repository/FilesRepositoryImpl.kt:156`
    - `app/src/main/java/com/milki/launcher/domain/search/UrlHandlerResolver.kt:123`
- Progress in this pass:
  - Replaced broad catches in `ActionExecutor` with narrower `ActivityNotFoundException` / `SecurityException` handling and logging.
- Problem:
  - Broad catches hide root causes and produce inconsistent user outcomes (silent fallback, toast-only feedback, no typed error).
- Recommended fix:
  - Introduce a unified error model by layer:
    - Data layer: map to typed repository errors and log once with context.
    - Domain layer: return explicit result types.
    - Presentation: map to user-facing state/snackbar.

## Immediate execution order
1. Introduce error-handling standard and migrate highest-traffic paths (`ActionExecutor`, widget and file paths).
