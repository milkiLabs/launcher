# P2 Modernization and Dependency Opportunities

## Deprecated / old patterns

### 1) Deprecated activity result callback still used
- Severity: Medium
- Confidence: Confirmed and likely justified
- Evidence:
  - `app/src/main/java/com/milki/launcher/app/activity/MainActivity.kt:101`
- Notes:
  - The deprecation is explicitly documented for widget configuration compatibility. Keep with clear rationale unless platform support improves.

### 2) Broad external storage model
- Severity: High
- Confidence: Confirmed
- Evidence:
  - `app/src/main/AndroidManifest.xml:18`
  - `app/src/main/java/com/milki/launcher/data/repository/FilesRepositoryImpl.kt:30`
- Notes:
  - `MANAGE_EXTERNAL_STORAGE` is high-friction and policy-sensitive.
- Recommendation:
  - Re-evaluate whether scoped-storage plus SAF can cover most flows.

### 3) Backup posture should be explicitly reviewed
- Severity: Medium
- Confidence: Confirmed
- Evidence:
  - `app/src/main/AndroidManifest.xml:43`
- Recommendation:
  - Audit data extraction and backup rules for sensitive stores.

## Library opportunities (prioritized)

### A) Add kotlinx immutable collections
- Priority: High
- Why:
  - Helps enforce immutable flow snapshots and reduce accidental mutation.
- Suggested usage:
  - Repository flows and UI state lists.

### B) Add Timber for logging standardization
- Priority: Medium
- Why:
  - Unified logging policy and simpler log management by build type.

### C) Add Truth for tests
- Priority: Medium
- Why:
  - Improves assertion readability and failure diagnostics with low migration cost.

### D) Keep current search implementation unless scale changes
- Priority: Low
- Why:
  - Current search logic appears custom but appropriate for launcher scale.
  - Revisit Lucene/fuzzy libs only if data size/ranking complexity grows substantially.

### E) Keep current permissions orchestrator design
- Priority: Low
- Why:
  - Existing reducer/orchestrator approach is testable and explicit.

## Standardization proposals
1. Error model standard (typed errors by layer).
2. Naming taxonomy (Coordinator vs Handler vs Manager).
3. DI boundaries and package rules enforced via lint/detekt.
4. Threading policy doc: who owns dispatcher decisions.
5. Required tests checklist for each feature module.
