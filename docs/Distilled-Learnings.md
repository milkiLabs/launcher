# Distilled Learnings

This file captures high-signal lessons from prior refactors and bug fixes.

## 1. Repository Design

### Apps repository split

- Keep `AppRepositoryImpl` thin and orchestrational.
- Delegate app scanning and preload to installed-app catalog collaborator.
- Delegate recent-app persistence/mapping/pruning to recent-store collaborator.
- Delegate package change observation to monitor collaborator.

Outcome:

- Smaller units, clearer ownership, easier testing.

### Home repository split

- Keep home storage concerns in dedicated collaborators:
  - snapshot store
  - serializer/schema
  - occupancy policy
- Keep HomeRepository interface minimal and contract-first.

Outcome:

- Fewer accidental API leaks and safer mutation pathways.

## 2. Home Mutation Safety

- Home persistence uses Proto DataStore with typed schema.
- Use single-writer queue for all home mutations.
- Never mutate from stale UI state; read latest persisted snapshot in write path.
- For folder creation/merge, validate live targets at mutation time to avoid ghost resurrection.
- Use bulk remove mutation (`RemoveItemsById`) for availability cleanup efficiency and consistency.

## 3. Grid and Drag/Drop

- Occupancy must be span-aware; top-left-only checks are incorrect for widgets/spanning items.
- Use one shared occupant lookup helper for drop routing and highlights.
- Compose pointer callbacks can capture stale state; use updated-state patterns for commit-time correctness.

## 4. Widget Placement Flow

- Use command-based placement from ViewModel to activity launchers.
- Keep pending widget placement state in ViewModel, not callback chains.
- Start bind/configure only after successful home-grid drop callback.
- Guard launcher calls because provider-specific flows can throw.
- Always cleanup pending widget IDs on canceled/failure paths.
- Folder operations must reject widget children.

## 5. Files and Permission Edge Cases

- Pinned file cleanup should be observer-driven and conservative.
- Remove pins only on definitive missing data, not transient provider/security failures.

## 6. Flow/Trigger Semantics

- For repeatable trigger events (for example `Unit` refresh), prefer shared hot stream semantics over conflated state semantics.
- Use one shared refresh loop where possible to avoid duplicate expensive work per collector.
- Emit immutable list snapshots from caches.

## 7. Package Layout Lessons

Refactor outcomes that should be preserved:

- App entrypoints under `app.*`.
- Cross-cutting helpers under `core.*`.
- Feature-scoped UI under `ui.components.<feature>`.
- Interaction primitives under `ui.interaction.*`.
- Launcher coordinators under `presentation.launcher`.

Do not reintroduce old flat package patterns.

## 8. Practical Rules for Future Work

1. If behavior is launcher-specific, keep it in launcher packages.
2. If code manipulates persistent home state, route through serialized mutation path.
3. If code touches drag/drop occupancy, ensure span-aware logic.
4. If code launches widget bind/configure intents, treat all failures as cancelable and cleanup-required.
5. If adding stream triggers, verify semantics for repeated equal emissions.
