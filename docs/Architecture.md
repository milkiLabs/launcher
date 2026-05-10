# Architecture

This project follows a layered, feature-oriented structure:

- App layer: Android entrypoints and host activities.
- Core layer: cross-cutting infrastructure and dependency wiring.
- Data layer: repository implementations, storage, platform integration.
- Domain layer: models, policies, use cases, and contracts.
- Presentation layer: state coordinators and ViewModel-facing orchestration.
- UI layer: Compose screens/components and interaction primitives.

## Package Map

### App

- `com.milki.launcher.app`
- `com.milki.launcher.app.activity`

Responsibilities:

- Bootstrapping app process and dependency graph.
- Hosting launcher/settings activity boundaries.
- Delegating business behavior to presentation and domain layers.

### Core

- `com.milki.launcher.core.di`
- `com.milki.launcher.core.permission`
- `com.milki.launcher.core.intent`
- `com.milki.launcher.core.file`
- `com.milki.launcher.core.url`

Responsibilities:

- Dependency injection modules.
- Shared platform helpers with no feature ownership.

### Data

- `com.milki.launcher.data.repository.apps`
- `com.milki.launcher.data.repository.home`
- `com.milki.launcher.data.repository.settings`
- `com.milki.launcher.data.search`
- `com.milki.launcher.data.widget`
- `com.milki.launcher.data.icon`

Responsibilities:

- Storage, package manager queries, content resolver, widget host integration.
- Repository split by concern (apps/home/settings).

### Domain

- `com.milki.launcher.domain.model`
- `com.milki.launcher.domain.repository`
- `com.milki.launcher.domain.search`
- `com.milki.launcher.domain.drag.drop`
- `com.milki.launcher.domain.drag.reorder`
- `com.milki.launcher.domain.homegraph`
- `com.milki.launcher.domain.homegraph.writer`
- `com.milki.launcher.domain.drawer`
- `com.milki.launcher.domain.widget`

Responsibilities:

- Pure business logic and contracts.
- Mutation and placement policies independent from Android UI primitives.

### Presentation

- `com.milki.launcher.presentation.launcher`
- `com.milki.launcher.presentation.launcher.host`
- `com.milki.launcher.presentation.home`
- `com.milki.launcher.presentation.home.mutation`
- `com.milki.launcher.presentation.home.prune`
- `com.milki.launcher.presentation.home.widget`
- `com.milki.launcher.presentation.search`
- `com.milki.launcher.presentation.drawer`
- `com.milki.launcher.presentation.settings`

Responsibilities:

- State coordination and flow orchestration.
- Runtime policy around launcher host lifecycle and side effects.

### UI

- `com.milki.launcher.ui.screens.launcher`
- `com.milki.launcher.ui.screens.settings`
- `com.milki.launcher.ui.components.common`
- `com.milki.launcher.ui.components.search`
- `com.milki.launcher.ui.components.launcher`
- `com.milki.launcher.ui.components.launcher.folder`
- `com.milki.launcher.ui.components.launcher.widget`
- `com.milki.launcher.ui.components.settings`
- `com.milki.launcher.ui.interaction.dragdrop`
- `com.milki.launcher.ui.interaction.grid`
- `com.milki.launcher.ui.theme`

Responsibilities:

- Composable rendering and UI-only state.
- Shared interaction primitives (gesture detection, drag payloads, grid math) under `ui.interaction.*`.

## Dependency Direction

Follow this direction only:

- UI -> Presentation -> Domain -> Data -> Core/Platform

Allowed:

- Feature packages depending on core/shared utilities.
- UI components depending on `ui.interaction.*`.

Not allowed:

- Data depending on UI or Presentation.
- Cross-feature shortcuts that bypass domain contracts.
- New files under legacy flat namespaces that were intentionally retired.

## Runtime Flow (Launcher)

1. Activity creates host runtime and action factory.
2. Presentation coordinators expose state and callbacks.
3. UI renders launcher surface/search/drawer/widget overlays.
4. Actions route through presentation/domain writers.
5. Repositories persist snapshots and emit flows.
6. ViewModels collect fresh state and update UI.

## Storage and State

- Home data uses Preferences DataStore (`home_items`) with a typed domain model (`HomeItem`) serialized as newline-delimited JSON rows.
- Home writes are serialized through a single-writer queue to avoid races.
- Widget placement is command-based, with pending state kept in ViewModel.
- Import/export uses a storage-agnostic backup snapshot schema so backup files remain stable even if internal persistence changes later.

## Test Layout

Tests mirror production package ownership:

- Launcher presentation tests in `app/src/test/java/com/milki/launcher/presentation/launcher`.
- Launcher UI tests in `app/src/test/java/com/milki/launcher/ui/components/launcher`.
- Interaction policy tests in `app/src/test/java/com/milki/launcher/ui/interaction/grid`.
