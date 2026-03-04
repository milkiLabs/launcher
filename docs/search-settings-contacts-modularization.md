# Search, Settings, and Contacts Modularization (March 2026)

## Why this refactor was needed

Several files had grown to hundreds of lines and mixed multiple responsibilities:

- Search result UI rendering logic for many result types in one file.
- Settings UI sections, cards, dialogs, and prefix editor controls in one file.
- SearchViewModel state ownership, pipeline orchestration, and settings observation in one file.
- Contacts repository query logic, cursor-to-domain mapping, and recent-contact persistence in one file.

This made it difficult to reason about behavior, increased merge conflicts, and made safe changes slower.

---

## Refactor goals

This refactor follows **feature-concern boundaries**, not arbitrary line splitting:

1. **Search result UI** → one file per result type + shared primitives.
2. **Settings UI** → section header, cards/inputs, prefix editor, and dialog separated.
3. **SearchViewModel** → state holder + pipeline coordinator + settings adapter.
4. **Contacts repository** → query layer + mapping layer + recent-contact storage.

---

## Search result UI split

### Before

- `ui/components/SearchResultItems.kt` contained many composables in one monolithic file.

### After

- `ui/components/SearchResultWebItem.kt`
- `ui/components/SearchResultYouTubeItem.kt`
- `ui/components/SearchResultUrlItem.kt`
- `ui/components/SearchResultContactItem.kt`
- `ui/components/SearchResultFileItem.kt`
- `ui/components/SearchResultPermissionItem.kt`
- `ui/components/SearchResultsEmptyState.kt`
- Shared row primitive remains in `ui/components/SearchResultListItem.kt`

### Result

- Each result type is isolated and easier to change safely.
- File ownership is clearer when multiple contributors work in parallel.

---

## Settings UI split

### Before

- `ui/components/settings/SettingsComponents.kt` mixed multiple concerns.

### After

- `ui/components/settings/SettingsSectionComponents.kt`
  - `SettingsCategory` section header.
- `ui/components/settings/SettingsCardComponents.kt`
  - `SwitchSettingItem`, `DropdownSettingItem`, `SliderSettingItem`, `ActionSettingItem`.
- `ui/components/settings/SettingsPrefixEditorComponents.kt`
  - `PrefixSettingItem`, `PrefixChip`.
- `ui/components/settings/SettingsPrefixDialog.kt`
  - `AddPrefixDialog`.

### Design-system note

When touching these files, spacing and corner values should come from `ui/theme/Spacing.kt` and related theme constants.

---

## SearchViewModel split

### Before

- `presentation/search/SearchViewModel.kt` owned all state, pipeline orchestration, settings observation, and helper search logic.

### After

- `presentation/search/SearchViewModel.kt`
  - Public API for UI interactions.
  - Lifecycle wiring for collaborators.
- `presentation/search/SearchViewModelStateHolder.kt`
  - Mutable state flows and derived state flows.
- `presentation/search/SearchViewModelPipelineCoordinator.kt`
  - Asynchronous search pipeline (`combine` + `mapLatest`) and loading/result output handling.
- `presentation/search/SearchViewModelSettingsAdapter.kt`
  - Settings flow observation and prefix-registry synchronization.
- `presentation/search/SearchViewModelModels.kt`
  - Small internal data models used across the split.

### Result

- `SearchViewModel.kt` is now substantially smaller and easier to inspect.
- Pipeline and settings behavior remain the same but live in focused classes.

---

## Contacts repository split

### Before

- `data/repository/ContactsRepositoryImpl.kt` contained query, mapping, and DataStore logic together.

### After

- `data/repository/ContactsRepositoryImpl.kt`
  - Thin facade implementing domain interface and permission gating.
- `data/repository/ContactsQueryLayer.kt`
  - ContentResolver query logic and cursor traversal.
- `data/repository/ContactsMappingLayer.kt`
  - Mapping helpers, mutable aggregate model, and relevance sorting.
- `data/repository/ContactsRecentStorage.kt`
  - DataStore read/write for recent contact phone numbers.

### Result

- Query behavior is isolated from mapping and persistence.
- Future changes (e.g., scoring rules or storage format) can be made in one layer without touching the others.

---

## Current hotspot status

The original high-cognitive-load files were reduced as follows:

- `SearchResultItems.kt` monolith removed and replaced by per-type files.
- `SettingsComponents.kt` monolith removed and replaced by concern-focused files.
- `SearchViewModel.kt` reduced to a coordination-focused surface.
- `ContactsRepositoryImpl.kt` reduced to a facade with delegated responsibilities.

---

## How to extend this structure

### Adding a new search result type

1. Create a dedicated file under `ui/components/` (e.g., `SearchResultRedditItem.kt`).
2. Use `SearchResultListItem` for common row structure unless unique UI is required.
3. Wire the new type in the result-list dispatch logic.

### Adding new search pipeline behavior

1. Add state to `SearchViewModelStateHolder` if needed.
2. Update `SearchViewModelPipelineCoordinator` pipeline inputs/logic.
3. Keep `SearchViewModel` focused on public UI methods and lifecycle wiring.

### Changing contacts ranking or mapping

1. Update `ContactsMappingLayer` for ranking/mapping logic.
2. Keep query SQL/projection logic in `ContactsQueryLayer`.
3. Keep recent-history persistence in `ContactsRecentStorage`.

---

## Migration safety principles used

- Public method signatures remained stable for existing callers.
- Behavioral changes were avoided; this was a structural refactor.
- Functions were moved by concern to reduce risk during future edits.
