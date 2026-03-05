# Widget Feature Audit

Date: 2026-03-06
Scope: picker, drag-to-place flow, bind/configure callbacks, host lifecycle, rendering and cleanup.

## Findings

### P0) Widget placement session is single-slot and overwrite-prone
- Evidence: `app/src/main/java/com/milki/launcher/presentation/home/HomeViewModel.kt:645`, `app/src/main/java/com/milki/launcher/presentation/home/HomeViewModel.kt:671`, `app/src/main/java/com/milki/launcher/presentation/home/HomeViewModel.kt:708`, `app/src/main/java/com/milki/launcher/presentation/home/HomeViewModel.kt:728`
- Problem: only one `pendingWidget` exists for a multi-step asynchronous flow.
- Risk: stale/incorrect activity-result routing, wrong widget cleanup on rapid interactions.
- Recommendation:
1. Use session IDs and pending-session map keyed by request token.
2. Attach token to intents and validate on bind/configure result.
3. Add stale-session guard logs and deterministic cleanup.

### P1) Broad catch usage around host/listener lifecycle hides root causes
- Evidence: `app/src/main/java/com/milki/launcher/data/widget/WidgetHostManager.kt:126`, `app/src/main/java/com/milki/launcher/data/widget/WidgetHostManager.kt:143`, `app/src/main/java/com/milki/launcher/data/widget/WidgetHostManager.kt:175`, `app/src/main/java/com/milki/launcher/ui/components/widget/HomeScreenWidgetView.kt:268`
- Problem: failures are swallowed to generic logs without structured context.
- Risk: difficult production triage for vendor-specific widget host issues.
- Recommendation:
1. Catch narrower exceptions where possible.
2. Include widget ID/provider component and operation stage in logs.
3. Report recoverable failure states to UI where meaningful.

### P2) Picker/provider loading does full synchronous prep at open time
- Evidence: `app/src/main/java/com/milki/launcher/ui/components/widget/WidgetPickerBottomSheet.kt:148`, `app/src/main/java/com/milki/launcher/ui/components/widget/WidgetPickerBottomSheet.kt:152`
- Problem: all providers are loaded and grouped when sheet opens, including icon/label calls.
- Risk: jank on devices with many widget providers.
- Recommendation:
1. Precompute/cache picker entries in ViewModel or manager layer.
2. Incrementally load previews/icons.
3. Keep bottom-sheet open transition lightweight.

## What Is Good

1. Widget flow separation (allocate/bind/configure/place) is explicit and readable.
2. Widget host listen/unlisten lifecycle is correctly tied to activity visibility.
3. Placement path properly deallocates widget IDs on placement failure.
