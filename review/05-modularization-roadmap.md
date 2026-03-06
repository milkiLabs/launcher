# Modularization and Maintainability Roadmap

## Goals

1. Reduce coupling between launcher surfaces and feature logic.
2. Make features self-contained and testable.
3. Stop growth of mega-files and callback-heavy APIs.
4. Establish enforceable architecture and UI standards.

## Target Architecture (Incremental)

- `core/`
- Common contracts, error model, logging abstractions, DI bootstrap.

- `feature/home/`
- Home grid orchestration, home policies, home screen state.

- `feature/folder/`
- Folder popup UI, folder drag/reorder behavior, folder domain service.

- `feature/widget/`
- Widget picker, placement state machine, widget host adapter.

- `feature/search/`
- Search query state, providers, action dispatch.

- `feature/drawer/`
- App drawer state and sorting behavior.

- `feature/settings/`
- Settings UI + repository adapters.

- `data/`
- Repository implementations and persistence details.

## Phase Plan

### Phase 1 (Stabilize and De-risk)

1. Extract widget placement flow from `HomeViewModel` into `WidgetPlacementCoordinator` with session IDs.
2. Reduce `MainActivity` to host shell + coordinator wiring.
3. Replace silent deserialize drop with explicit recovery strategy.
4. Add tests for:
- widget placement session transitions
- folder cleanup invariants
- home button policy behavior

### Phase 2 (Surface Decomposition)

1. Split `LauncherScreen` into feature hosts:
- `HomeSurface`
- `FolderOverlayHost`
- `DrawerHost`
- `WidgetPickerHost`
2. Replace flat callback list with grouped action contracts.
3. Split `DraggablePinnedItemsGrid` into:
- grid layout
- drag controller adapter
- folder routing policy
- widget overlays

### Phase 3 (Data and Domain Cleanup)

1. Move home-item persistence from newline JSON to typed schema (Proto/Data model).
2. Add repository mutation queue for deterministic ordering and easier replay tests.
3. [Done] Share installed-app state in repository via hot trigger + cached snapshot flow (`shareIn` + `MutableStateFlow`) and consume from both search/drawer.

### Phase 4 (Standards and Tooling)

1. Add static checks:
- no hardcoded `dp` in UI layers except spacing tokens
- icon mirroring policy
- file-size threshold review warning
2. Add PR checklist with architecture boundary checks.
3. Add package-level ownership docs.

## File Split Recommendations (First Wave)

1. `app/src/main/java/com/milki/launcher/MainActivity.kt`
- Extract `MainSurfaceStateCoordinator`
- Extract `MainWidgetFlowLauncher`
- Keep only lifecycle + `setContent` host

2. `app/src/main/java/com/milki/launcher/ui/screens/LauncherScreen.kt`
- Move folder overlay block into dedicated composable host
- Move homescreen menu logic into dedicated composable
- Keep top-level route composition only

3. `app/src/main/java/com/milki/launcher/ui/components/DraggablePinnedItemsGrid.kt`
- Extract internal drag highlight renderer
- Extract external drop highlight renderer
- Extract widget context/move/resize overlays

4. `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt`
- Extract serialization codec
- Extract folder mutation engine
- Extract occupancy and widget placement validators

5. `app/src/main/java/com/milki/launcher/data/repository/SettingsRepositoryImpl.kt`
- Extract key-mapping layer
- Extract prefix/source serialization adapters
- Extract key-specific write helpers into utility

## Definition of Done for Maintainability

1. No core feature file > 600 lines.
2. Every feature has unit tests for its state transitions.
3. Cross-feature interactions pass through explicit contracts.
4. Standards checks run on every PR.
5. New contributors can find feature ownership and invariants in package docs.
