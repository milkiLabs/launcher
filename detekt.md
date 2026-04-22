# Detekt Cleanup Plan

## Purpose

This document is a working roadmap for future AI agents who continue reducing the Detekt baseline in this repository.

The goal is not to "make the baseline disappear at any cost". The goal is:

1. Remove findings in small, safe batches.
2. Keep behavior unchanged unless a finding clearly points to a bug.
3. Prefer mechanical cleanup first, structural refactors second.
4. Keep `./gradlew app:detekt baselineprofile:detekt` green at all times.
5. Regenerate the baseline only after a cleanup batch is fully verified.

## Current Snapshot

Snapshot taken after the first cleanup pass on 2026-04-22.

- `config/detekt/app-baseline.xml`: 233 findings remaining
- `config/detekt/baselineprofile-baseline.xml`: empty
- `baselineprofile` should now be treated as "clean by default"

## Status Update (2026-04-22)

Completed in this batch:

- `HomeModelWriter.kt` received the first deep structural cleanup pass.
- The refactor targeted the highest-yield findings in that file first:
  - command dispatch complexity
  - `ReturnCount` hotspots across folder/widget mutation helpers
  - `LoopWithTooManyJumpStatements`
  - class-level function pressure via helper extraction

Verification:

- `./gradlew app:detekt` passed on 2026-04-22 after the refactor.
- `./gradlew app:testDebugUnitTest --tests com.milki.launcher.domain.homegraph.HomeModelWriterTest` did not complete because the project currently has unrelated compile errors in `AppSearchDialog.kt`:
  - unresolved `matchParentSize`
  - inaccessible `weight` access

Important:

- The counts below are still the pre-regeneration snapshot.
- Do not regenerate `config/detekt/*baseline.xml` until the unrelated compile issue is resolved and the owner is ready to refresh the baselines.
- Treat `HomeModelWriter.kt` as "done pending baseline regeneration", not as an active starting point for the next batch.

### Remaining Findings By Rule

- `ReturnCount`: 73
- `MagicNumber`: 65
- `LongMethod`: 33
- `MaxLineLength`: 18
- `CyclomaticComplexMethod`: 11
- `TooGenericExceptionCaught`: 7
- `MatchingDeclarationName`: 6
- `TooManyFunctions`: 6
- `SwallowedException`: 4
- `UnusedParameter`: 4
- `ComplexCondition`: 2
- `LoopWithTooManyJumpStatements`: 2
- `ForbiddenComment`: 1
- `VariableNaming`: 1

### Main Hotspot Files

These files give the biggest payoff because they contain multiple findings each:

- `HomeModelWriter.kt`: 15 in the pre-regeneration snapshot, but the cleanup batch landed on 2026-04-22
- `PinnedItem.kt`: 12
- `ExternalHomeDropDispatcher.kt`: 11
- `FolderIcon.kt`: 8
- `AppSearchDialog.kt`: 7
- `SettingsSourceEditorComponents.kt`: 7
- `WidgetOverlayLayer.kt`: 7
- `ContactsQueryLayer.kt`: 6
- `FolderPopupDialogSupport.kt`: 6
- `SearchProviderVisuals.kt`: 6
- `WidgetHostManager.kt`: 6
- `SettingsMutationStore.kt`: 6

## Core Working Rules

Future agents should follow these rules while doing Detekt cleanup:

1. Do not regenerate the baseline before the code changes are verified.
2. Never mix unrelated cleanup and feature work in the same batch.
3. Favor one rule family at a time.
4. Favor one subsystem at a time when refactors are non-trivial.
5. If a refactor changes behavior, stop and narrow the batch.
6. When touching drag/drop, widget, or search flows, rerun targeted tests if any exist in addition to Detekt.
7. Keep commits or patches easy to review: one batch should be explainable in a short paragraph.

## Recommended Order

The best next steps are:

1. Finish the remaining low-risk mechanical findings.
2. Tackle control-flow simplification in leaf/helper functions.
3. Tackle complexity and long methods in UI files by extracting private composables/helpers.
4. Tackle large stateful domain classes only after helper-level cleanup is done.

`HomeModelWriter.kt` already has a dedicated structural cleanup batch in progress history. Keep the same "tightly scoped only" rule for `WidgetHostManager.kt`.

## Phase 1: Low-Risk Mechanical Cleanup

These are the safest remaining categories and should be done first.

### 1. `MatchingDeclarationName` (6)

Files:

- `AppItemContextMenuSupport.kt`
- `FolderPopupLayout.kt`
- `IconLabelCell.kt`
- `LauncherSheetHostPolicy.kt`
- `QueryParser.kt`
- `SearchProviderVisuals.kt`

Preferred approach:

- Rename the file to match the main top-level declaration when the declaration name is already good.
- Only rename the declaration instead of the file if the file name is clearly the better API name and changing references is easy.

Acceptance criteria:

- No behavior changes.
- Imports and references updated cleanly.
- Detekt removes the rule entries with no new findings created.

### 2. `UnusedParameter` (4)

Files:

- `DragVisualEffects.kt`: `cellWidthPx`, `cellHeightPx`
- `PinnedItem.kt`: `onLongClick`
- `SettingsScreen.kt`: `onNavigateBack`

Preferred approach:

- Remove parameters when they are truly unused and not part of a stable API worth preserving.
- If a parameter is intentionally kept for interface symmetry, document it or refactor the surrounding API so the parameter is no longer needed.

Acceptance criteria:

- Public call sites updated.
- No fake reads like `param;` or dummy assignments.

### 3. `VariableNaming` (1)

File:

- `FilesRepositoryImpl.kt`

Likely cause:

- A constant like `PROJECTION` conflicts with the current naming rule for a private variable.

Preferred approach:

- Either convert it into a style the repo already uses consistently, or move it into a companion object/object where constant naming becomes natural.
- Do not weaken Detekt config for a single case.

### 4. `ForbiddenComment` (1)

File:

- `PermissionHandler.kt`

Preferred approach:

- Replace the `TODO` marker with neutral prose or implement the missing behavior if it is small and safe.
- If implementation is not trivial, convert the comment into a non-forbidden note that still preserves intent.

## Phase 2: Straightforward Local Refactors

These are medium risk but still manageable in small batches.

### 5. `MaxLineLength` (18)

Main targets:

- `AppExternalDragDrop.kt`
- `AppSearchDialog.kt`
- `DraggablePinnedItemsGrid.kt`
- `DraggablePinnedItemsGridLayers.kt`
- `ExternalDropRoutingLayer.kt`
- `ExternalHomeDropDispatcher.kt`
- `LauncherActions.kt`
- `PinnedItem.kt`
- `SearchResultContactItem.kt`
- `SettingsSourceEditorComponents.kt`
- `WidgetHostManager.kt`

Preferred patterns:

- Break long lambda types across lines.
- Extract very long string templates into local vals.
- Split long boolean expressions into named locals.
- For composables, move complex modifier chains or content lambdas into helpers.

Avoid:

- Raising max line length.
- Reformatting whole files unnecessarily.

### 6. `MagicNumber` (65)

Main hotspots:

- `PinnedItem.kt`
- `FolderIcon.kt`
- `AppDrawerOverlay.kt`
- `ContactsQueryLayer.kt`
- `SearchProviderVisuals.kt`
- `SettingsSourceEditorComponents.kt`
- `WidgetOverlayLayer.kt`
- `WidgetHostManager.kt`

Preferred patterns:

- Extract UI constants near usage with names that explain intent, not just units.
- Group related constants in `private companion object` or top-level private vals.
- For repeated colors, strongly consider routing through theme or named palette constants.
- For file-size units like `1024`, extract semantic constants such as `BytesPerKilobyte`.

Suggested batching:

- Batch A: visual constants only (`PinnedItem`, `FolderIcon`, `SearchProviderVisuals`, `WidgetOverlayLayer`)
- Batch B: repository/domain numeric constants (`ContactsQueryLayer`, `FilesRepositoryImpl`, `FileDocument`)
- Batch C: remaining single-file leftovers

Avoid:

- Extracting constants so far away that readability gets worse.
- Replacing well-known constants with obscure names that hide meaning.

## Phase 3: Control-Flow Cleanup

This is the biggest volume category and should be handled deliberately.

### 7. `ReturnCount` (73)

This category should be reduced in batches of related files.

Best first targets:

- `ContactsSearchProvider.kt`
- `ClipboardSuggestionResolver.kt`
- `Contact.kt`
- `ContactsMappingLayer.kt`
- `ExternalDragCoordinateMapper.kt`
- `SearchProviderVisuals.kt`
- `PinnedFileAvailability.kt`
- `SettingsSearchSourceStorageCodec.kt`
- `UrlValidator.kt`
- `AppLauncher.kt`

Harder targets to defer:

- `ExternalHomeDropDispatcher.kt`
- `HomeBackgroundGestureDetector.kt`
- `WidgetPlacementCoordinator.kt`
- `LauncherBackupRepositoryImpl.kt`

Preferred refactor patterns:

- Convert early-return trees into `when` expressions that return once.
- Extract guard logic into private predicates.
- Extract "decision" objects or small helper functions.
- For nullable-return helpers, prefer one final return after branching where it stays readable.

Important note:

Do not blindly force single-return style when early returns are clearer. The objective is to get under the configured threshold without harming readability.

Suggested batches:

- Batch 1: pure helper functions and validators
- Batch 2: repository search/codec helpers
- Batch 3: drag/drop decision nodes
- Batch 4: large stateful domain classes

### 8. `LoopWithTooManyJumpStatements` (2)

Files:

- `ContactsQueryLayer.kt`
- `HomeModelWriter.kt`

Preferred patterns:

- Extract loop-body decisions into named functions.
- Replace multiple `continue`/`break` branches with filtered sequences or clearer state transitions where performance is still acceptable.

`HomeModelWriter.kt` was addressed on 2026-04-22. `ContactsQueryLayer.kt` is now the next best target in this rule family.

## Phase 4: Complexity and Long Method Reduction

These findings overlap. Many files will clear both rules with one refactor.

### 9. `LongMethod` (33)

Main targets:

- `AppDrawerOverlay.kt`
- `AppSearchDialog.kt`
- `ContactsQueryLayer.kt`
- `DraggablePinnedItemsGrid.kt`
- `DraggablePinnedItemsGridLayers.kt`
- `DropHighlightLayer.kt`
- `ExternalDragPayloadCodec.kt`
- `FolderPopupDialog.kt`
- `FolderPopupDialogSupport.kt`
- `HomeScreenWidgetView.kt`
- `ItemActionMenu.kt`
- `LauncherRootContent.kt`
- `LauncherScreen.kt`
- `PinnedItem.kt`
- `SettingsCardComponents.kt`
- `SettingsPrefixEditorComponents.kt`
- `SettingsScreen.kt`
- `SettingsSourceEditorComponents.kt`
- `WidgetOverlayLayer.kt`
- `WidgetPickerBottomSheet.kt`

Preferred patterns for composables:

- Extract private composables for visual subtrees.
- Extract state derivation into pure helpers.
- Extract event handler lambdas into named local functions only when it improves readability.
- Move constants and derived styling out of the main body.

Preferred patterns for non-UI methods:

- Extract parsing, mapping, and validation steps.
- Separate "gather inputs", "decide", and "apply effect".

### 10. `CyclomaticComplexMethod` (11)

Main targets:

- `AppDrawerOverlay.kt`
- `AppSearchDialog.kt`
- `DraggablePinnedItemsGridLayers.kt`
- `DropHighlightLayer.kt`
- `ExternalAppDragDropCoordinator.kt`
- `ExternalDragPayloadCodec.kt`
- `FolderPopupDialog.kt`
- `HomeBackgroundGestureDetector.kt`
- `HomeScreenWidgetView.kt`
- `SettingsScreen.kt`

Preferred patterns:

- Replace nested conditionals with named booleans or rule objects.
- Split event handlers by state or mode.
- For drag/drop and gesture code, model explicit states instead of stacking booleans.

High-risk files:

- `HomeBackgroundGestureDetector.kt`
- `HomeScreenWidgetView.kt`

These should each get their own dedicated batch.

## Phase 5: Class/Module Decomposition

### 11. `TooManyFunctions` (6)

Files:

- `ActionExecutor.kt`
- `HomeViewModel.kt`
- `SettingsRepository.kt`
- `SettingsViewModel.kt`
- `WidgetHostManager.kt`

This phase should happen only after the helper- and method-level cleanup above.

Preferred decomposition directions:

- `ActionExecutor.kt`: split action groups by concern, such as app/file/url/contact/permission execution helpers.
- `HomeViewModel.kt`: split widget placement flow from generic home mutations.
- `SettingsViewModel.kt`: split backup/import/export and prefix/source management into helpers.
- `WidgetHostManager.kt`: split bind/configure/delete and preview/measurement responsibilities.

Important:

- Do not introduce abstraction layers just to satisfy the rule.
- The decomposition must produce clearer ownership, not just more files.

## Exception-Handling Cleanup Track

These rules are low-count but important because they may reveal real defects.

### 12. `TooGenericExceptionCaught` (7)

Files:

- `AppSearchDialog.kt`
- `DragGestureDetector.kt`
- `FileOpener.kt`
- `FilesRepositoryImpl.kt`
- `HomeScreenWidgetView.kt`
- `UrlHandlerResolver.kt`
- `WidgetHostManager.kt`

Preferred approach:

- Replace `Exception` with the narrowest known type.
- If multiple exception types are expected, catch them separately.
- If the exact type is unclear, inspect the called APIs before changing.

### 13. `SwallowedException` (4)

Files:

- `AppIconMemoryCache.kt`
- `AppSearchDialog.kt`
- `FileOpener.kt`
- `UrlHandlerResolver.kt`

Preferred approach:

- Log with context.
- Convert silent failure to explicit fallback behavior.
- If swallowing is intentional, document why and emit a useful trace.

This track is important because it can improve reliability, not just style.

## Concrete Batching Recommendation

Future agents should use this batch order unless new context suggests a better one:

### Batch 1

- `MatchingDeclarationName`
- `UnusedParameter`
- `VariableNaming`
- `ForbiddenComment`

Expected difficulty: low

### Batch 2

- `MaxLineLength`
- first 15-20 easiest `MagicNumber` findings in UI files

Expected difficulty: low to medium

### Batch 3

- easiest `ReturnCount` files only:
  - `ContactsSearchProvider.kt`
  - `ClipboardSuggestionResolver.kt`
  - `Contact.kt`
  - `ContactsMappingLayer.kt`
  - `ExternalDragCoordinateMapper.kt`
  - `SearchProviderVisuals.kt`
  - `PinnedFileAvailability.kt`

Expected difficulty: medium

### Batch 4

- `TooGenericExceptionCaught`
- `SwallowedException`

Expected difficulty: medium

### Batch 5

- composable extraction pass:
  - `AppSearchDialog.kt`
  - `SettingsScreen.kt`
  - `AppDrawerOverlay.kt`
  - `DropHighlightLayer.kt`

Expected difficulty: medium

### Batch 6+

- deep structural files:
  - `WidgetHostManager.kt`
  - `ExternalHomeDropDispatcher.kt`
  - `HomeScreenWidgetView.kt`

Expected difficulty: high

## Verification Workflow

For every cleanup batch:

1. Make the smallest coherent patch.
2. Run:
   - `./gradlew app:detekt baselineprofile:detekt --console=plain`
3. If touched code has unit tests, run the most relevant targeted tests.
4. Only after Detekt is green, regenerate baselines:
   - `./gradlew app:detektBaseline baselineprofile:detektBaseline --console=plain`
5. Confirm the baseline shrank rather than shifted sideways.

## What Future Agents Should Avoid

- Do not relax Detekt config to remove findings unless the rule is clearly wrong for this codebase.
- Do not "fix" findings by introducing dummy reads, pointless wrappers, or no-op helper methods.
- Do not do a giant repo-wide formatting or refactor pass.
- Do not combine feature work with Detekt cleanup.
- Do not regenerate the baseline before code cleanup, because that hides progress.
- Do not start with the hardest files when easier wins are still available.

## Definition Of Done

A cleanup batch is done when:

- The code compiles.
- `./gradlew app:detekt baselineprofile:detekt` passes.
- The baseline is regenerated.
- The total finding count decreases.
- The changes are understandable without reading the whole codebase.

## Short Version For The Next Agent

If you only have time for one safe batch, do this:

1. Clear the six `MatchingDeclarationName` findings.
2. Clear the four `UnusedParameter` findings.
3. Fix the single `VariableNaming` and `ForbiddenComment` findings.
4. Run Detekt.
5. Regenerate the baseline.

That is the highest-signal, lowest-risk next step.
