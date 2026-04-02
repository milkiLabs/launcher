# Contributing

Welcome. This guide is optimized for fast, safe contributions.

## 1. First-Time Setup

1. Ensure JDK 11+ is installed.
2. Sync Gradle project.
3. Build once:

```bash
./gradlew :app:compileDebugKotlin
```

4. Run a focused test smoke pass:

```bash
./gradlew :app:compileDebugUnitTestKotlin :app:testDebugUnitTest --tests com.milki.launcher.domain.homegraph.HomeModelWriterTest
```

## 2. Before You Start a Change

1. Read `docs/Architecture.md` for package ownership.
2. Read `docs/Conventions.md` for placement and mutation rules.
3. Search for existing helpers before adding new ones.
4. Keep changes feature-scoped.

## 3. Where to Put New Code

- New launcher coordinator/policy: `presentation.launcher`.
- New launcher UI component: `ui.components.launcher`.
- New drag/grid primitive: `ui.interaction.dragdrop` or `ui.interaction.grid`.
- New reusable app visual primitive: `ui.components.common`.
- New search UI: `ui.components.search`.
- New settings screen contract: `ui.screens.settings`.

If uncertain, prefer feature package over generic shared package.

## 4. Development Workflow

1. Make the smallest coherent change.
2. Compile:

```bash
./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
```

3. Run impacted tests only.
4. If the change affects launcher interaction or home graph, include these tests:

```bash
./gradlew :app:testDebugUnitTest \
  --tests com.milki.launcher.domain.homegraph.HomeModelWriterTest \
  --tests com.milki.launcher.ui.components.launcher.HomeSurfaceInteractionControllerTest \
  --tests com.milki.launcher.ui.components.launcher.ItemActionMenuPlacementTest
```

5. Update docs when architectural rules or package layout change.

## 5. Review Checklist

- Layer direction preserved (UI -> Presentation -> Domain -> Data).
- No new files in retired packages.
- State mutations are serialized and use fresh source-of-truth state.
- Drag/drop and occupancy logic are span-aware.
- Widget flow failures are guarded and cleanup-safe.
- Tests cover the changed behavior.

## 6. PR Guidance

PR description should include:

- What changed.
- Why this placement/package was chosen.
- Which tests were run.
- Any migration impact (imports, package rename, behavior changes).

## 7. New Contributor Safety Tips

- Do not trust stale UI snapshots for persistence writes.
- Do not duplicate grid constants; use shared defaults.
- Do not bypass repository/domain contracts from UI.
- Prefer extending existing focused collaborators over adding monolithic utility classes.
