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

### 3) Launcher root orchestration is over-concentrated in one Activity
- Evidence: `app/src/main/java/com/milki/launcher/MainActivity.kt:242`, `app/src/main/java/com/milki/launcher/MainActivity.kt:474`, `app/src/main/java/com/milki/launcher/MainActivity.kt:600`
- Problem: `MainActivity` coordinates permissions, home button policy, widget flow, drawer/search/folder lifecycle, and UI wiring.
- Risk: High regression risk from small changes, hard testing, difficult onboarding.
- Recommendation:
1. Extract feature coordinators: `WidgetPlacementCoordinator`, `SurfaceStateCoordinator`, `HomeIntentCoordinator`.
2. Keep `MainActivity` as shell: lifecycle + host only.
3. Define stable interfaces for each coordinator and write unit tests for policy transitions.

## P1 Findings

### 4) `LauncherScreen` has callback explosion and weak feature boundaries
- Evidence: `app/src/main/java/com/milki/launcher/ui/screens/LauncherScreen.kt:133`, `app/src/main/java/com/milki/launcher/ui/screens/LauncherScreen.kt:152`, `app/src/main/java/com/milki/launcher/ui/screens/LauncherScreen.kt:191`, `app/src/main/java/com/milki/launcher/ui/screens/LauncherScreen.kt:208`
- Problem: One composable owns too many cross-feature callbacks (grid/folder/widget/search/drawer/menu).
- Risk: Interface fragility, difficult preview/testing, tight coupling between unrelated feature domains.
- Recommendation:
1. Replace flat callback list with `LauncherActions` grouped by feature: `home`, `folder`, `widget`, `drawer`, `search`.
2. Split screen into feature hosts: `HomeSurface`, `FolderOverlayHost`, `DrawerHost`, `WidgetPickerHost`.
3. Add contract tests around each host boundary.

### 5) Single DI module is becoming a monolith
- Evidence: `app/src/main/java/com/milki/launcher/di/AppModule.kt`
- Problem: All dependencies are defined in one broad module, despite feature growth.
- Risk: Hidden coupling, harder DI graph reasoning, painful future module extraction.
- Recommendation:
1. Split into `coreModule`, `searchModule`, `homeModule`, `widgetModule`, `settingsModule`, `drawerModule`.
2. Keep only cross-feature interfaces in `core`.
3. Add a dependency direction rule: presentation -> domain -> data only.

### 6) Permission denial UX intentionally incomplete
- Evidence: `app/src/main/java/com/milki/launcher/handlers/PermissionHandler.kt:264`
- Problem: There is an explicit TODO for permanently denied permission handling.
- Risk: Repeated failed actions and confusing user loop.
- Recommendation:
1. Detect `shouldShowRequestPermissionRationale == false` after denial.
2. Show rationale sheet with `Open Settings` action.
3. Instrument denied-permission events for UX tuning.
