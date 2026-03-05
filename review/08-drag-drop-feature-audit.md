# Drag and Drop Feature Audit (Internal + External)

Date: 2026-03-06
Scope: internal grid drag, external payload drops, folder drag-out, drop routing and occupancy semantics.

## Findings

### P1) `DraggablePinnedItemsGrid` is carrying too many responsibilities
- Evidence: `app/src/main/java/com/milki/launcher/ui/components/DraggablePinnedItemsGrid.kt:126`, `app/src/main/java/com/milki/launcher/ui/components/DraggablePinnedItemsGrid.kt:888`, `app/src/main/java/com/milki/launcher/ui/components/DraggablePinnedItemsGrid.kt:1100`
- Problem: One composable owns internal drag, external drag target overlay, folder routing, widget routing, highlights, widget move/resize overlays, and context menus.
- Risk: Very high change coupling and bug surface.
- Recommendation:
1. Split into `InternalGridDragLayer`, `ExternalDropRoutingLayer`, `WidgetOverlayLayer`, `DropHighlightLayer`.
2. Keep only composition/wiring in the top-level grid composable.

### P1) External drop occupant detection is inconsistent with span-aware occupancy rules
- Evidence: `app/src/main/java/com/milki/launcher/ui/components/DraggablePinnedItemsGrid.kt:926`, `app/src/main/java/com/milki/launcher/ui/components/DraggablePinnedItemsGrid.kt:1077`, `app/src/main/java/com/milki/launcher/ui/components/DraggablePinnedItemsGrid.kt:1081`
- Problem: some external-drop routing uses `items.find { it.position == dropPosition }` (top-left match), while widget occupancy in repository is span-aware.
- Risk: UX mismatch where visually occupied widget cells can be treated as empty during route selection, then rejected later by repository.
- Recommendation:
1. Introduce one shared span-aware `findOccupantAt(position)` helper for all drop paths.
2. Use the same occupancy map logic as repository and drag highlight layer.

### P2) Cross-window drag bridge is robust but hard to reason about without tests
- Evidence: `app/src/main/java/com/milki/launcher/ui/components/dragdrop/AppExternalDragDrop.kt:276`, `app/src/main/java/com/milki/launcher/ui/components/dragdrop/AppExternalDragDrop.kt:311`, `app/src/main/java/com/milki/launcher/ui/components/dragdrop/AppExternalDragDrop.kt:339`
- Problem: fallback host selection and drag-start flag combinations are advanced and OEM-sensitive, but currently not guarded by integration tests.
- Risk: regressions on specific devices/windows.
- Recommendation:
1. Add instrumentation tests for startDrag success/fallback host selection.
2. Add minimal debug logging around host candidate selection and flag path taken.

## What Is Good

1. External payload codec and localState-first decode pattern is a strong design.
2. Folder-child external drag route correctly handles same-folder, target-folder, occupied non-folder, and empty-cell cases.
3. Widget drop includes span normalization and collision checks before placement flow.
