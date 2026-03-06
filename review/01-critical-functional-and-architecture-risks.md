# Critical Functional and Architecture Risks

## P0 Findings

### 1) Widget placement flow can be overwritten by a second placement start
- Evidence: `app/src/main/java/com/milki/launcher/presentation/home/HomeViewModel.kt:645`, `app/src/main/java/com/milki/launcher/presentation/home/HomeViewModel.kt:673`, `app/src/main/java/com/milki/launcher/presentation/home/HomeViewModel.kt:708`
- Problem: Widget placement state is held in a single mutable `pendingWidget` slot. If another placement starts before the first flow completes (bind/configure result), the first state is replaced.
- Risk: Wrong widget result handling, leaked/deallocated wrong widget IDs, unexpected placement behavior.
- Recommendation:
1. Introduce `WidgetPlacementSessionId` and map pending sessions by ID.
2. Include session ID in bind/configure intents and validate on result.
3. Reject stale results and always clean up abandoned IDs with deterministic logs.

### 2) Home data corruption is silently swallowed
- Evidence: `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:880`, `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:888`, `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:893`
- Problem: `deserializeItems` uses `mapNotNull` and drops malformed lines without surfacing error state.
- Risk: Silent item loss, impossible support debugging, hidden data integrity regression.
- Recommendation:
1. Add structured corruption reporting (count + sample item IDs/hash).
2. Persist a recovery flag and surface one-time user notification with reset/backup options.
3. Add migration versioning so malformed legacy entries are transformed rather than dropped.


### 6) Permission denial UX intentionally incomplete
- Evidence: `app/src/main/java/com/milki/launcher/handlers/PermissionHandler.kt:264`
- Problem: There is an explicit TODO for permanently denied permission handling.
- Risk: Repeated failed actions and confusing user loop.
- Recommendation:
1. Detect `shouldShowRequestPermissionRationale == false` after denial.
2. Show rationale sheet with `Open Settings` action.
3. Instrument denied-permission events for UX tuning.
