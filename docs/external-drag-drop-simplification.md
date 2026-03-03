# External Drag & Drop Simplification Plan

## Goal

Replace the current multi-path external drag/drop behavior with one small reusable abstraction that is:

1. Easy to reason about.
2. Easy to reuse on any launcher surface (home grid, dock, folders, future pages).
3. Strictly split between platform bridge logic and UI rendering logic.

## Current status (implemented)

This simplification has been implemented with the following modules:

- [app/src/main/java/com/milki/launcher/ui/components/dragdrop/ExternalDragPayloadCodec.kt](../app/src/main/java/com/milki/launcher/ui/components/dragdrop/ExternalDragPayloadCodec.kt)
- [app/src/main/java/com/milki/launcher/ui/components/dragdrop/ExternalDragCoordinateMapper.kt](../app/src/main/java/com/milki/launcher/ui/components/dragdrop/ExternalDragCoordinateMapper.kt)
- [app/src/main/java/com/milki/launcher/ui/components/dragdrop/ExternalAppDragDropCoordinator.kt](../app/src/main/java/com/milki/launcher/ui/components/dragdrop/ExternalAppDragDropCoordinator.kt)

`AppExternalDropTargetOverlay` in [app/src/main/java/com/milki/launcher/ui/components/dragdrop/AppExternalDragDrop.kt](../app/src/main/java/com/milki/launcher/ui/components/dragdrop/AppExternalDragDrop.kt) now acts as a thin adapter over the coordinator.

---

## Core simplification idea

Use a **single coordinator contract** with only 3 responsibilities:

- **Start drag** from any source item.
- **Track drag location** over any target surface.
- **Commit drop** with one resolved target cell.

Everything else (highlight drawing, policy checks like occupied-cell rejection) stays outside the coordinator.

---

## Proposed minimal API

```kotlin
interface ExternalAppDragDropCoordinator {
    fun startDrag(hostView: View, appInfo: AppInfo, shadowSize: Dp): Boolean

    fun attachTarget(
        targetView: View,
        callbacks: TargetCallbacks
    )

    fun detachTarget(targetView: View)

    interface TargetCallbacks {
        fun onStarted()
        fun onMoved(localOffset: Offset)
        fun onDropped(appInfo: AppInfo, localOffset: Offset): Boolean
        fun onEnded(result: Boolean)
    }
}
```

### Why this is simpler

- Target callbacks carry only what the target surface needs.
- App payload decode happens in one place, one way.
- No surface-level awareness of drag host fallback strategy.
- Reusable by any composable that can host an Android `View` overlay.

---

## Strict internal rules (coordinator implementation)

1. **Accept drag at started if likely app payload** (do not reject too early).
2. **Decode payload lazily once**, cache it for the session.
3. **Normalize coordinates once** with one utility function.
4. **Always emit started/ended callbacks** when this target accepted the drag session.
5. **Never own business policy** (occupied cell, clamping policy, pin/move policy) — only bridge events.

This keeps the coordinator small and avoids feature creep.

---

## Surface integration pattern (home grid and future surfaces)

Each target surface does exactly this:

1. Keep tiny local UI state:
   - `isExternalDragActive`
   - `hoverCell`
2. On `onMoved(localOffset)`:
   - convert pixel -> cell via local metrics.
   - update `hoverCell`.
3. On `onDropped(appInfo, localOffset)`:
   - resolve final cell (prefer hover cell, else localOffset conversion).
   - call ViewModel callback.
4. On `onEnded(...)`:
   - clear local drag state.

No payload decoding, no host fallback logic, no drag listener lifecycle complexity in each surface.

---

## File structure to reduce cognitive load

### Keep

- [app/src/main/java/com/milki/launcher/ui/components/dragdrop/AppDragDropContract.kt](../app/src/main/java/com/milki/launcher/ui/components/dragdrop/AppDragDropContract.kt)
- [app/src/main/java/com/milki/launcher/ui/components/dragdrop/AppDragDropModifiers.kt](../app/src/main/java/com/milki/launcher/ui/components/dragdrop/AppDragDropModifiers.kt)

### Split current external bridge into 3 small files

1. `ExternalDragPayloadCodec.kt`
   - ClipData creation + decode + likely-payload check.
2. `ExternalDragCoordinateMapper.kt`
   - local/window/screen normalization utility.
3. `ExternalAppDragDropCoordinator.kt`
   - startDrag + target listener lifecycle + callback dispatch.

Then keep a tiny Compose adapter:

4. `ExternalDropTargetOverlay.kt`
   - wraps AndroidView and wires coordinator attach/detach.

This is dramatically easier to scan and test than one long multipurpose file.

---

## What to remove from target surfaces

After migration, surfaces should not contain:

- payload parsing logic.
- drag started acceptance heuristics.
- coordinate-system fallback heuristics.
- listener lifecycle details.

Surfaces only convert offsets to cells and render highlights.

---

## Reusable policy layer (optional but clean)

Define one tiny policy interface for placement behaviors:

```kotlin
interface ExternalDropPlacementPolicy {
    fun resolveDropCell(hoverCell: GridPosition?, dropOffset: Offset, metrics: AppDragDropLayoutMetrics): GridPosition
}
```

Implement once for the current behavior:

- prefer hover cell
- fallback to drop offset
- clamp in metrics

Any future surface can reuse this policy unchanged.

---

## Migration steps (safe + incremental)

1. Extract codec + coordinate mapper from current external file.
2. Introduce coordinator with same behavior.
3. Migrate [app/src/main/java/com/milki/launcher/ui/components/DraggablePinnedItemsGrid.kt](../app/src/main/java/com/milki/launcher/ui/components/DraggablePinnedItemsGrid.kt) to the coordinator callbacks.
4. Verify no behavior regression with existing logs.
5. Remove now-redundant logic from old external file.
6. Keep only concise debug logs behind one tag in coordinator.

---

## Resulting mental model

- **Controller (`AppDragDropController`)** = internal in-surface drag.
- **Coordinator (`ExternalAppDragDropCoordinator`)** = platform cross-surface drag bridge.
- **Surface (`DraggablePinnedItemsGrid`)** = rendering + offset->cell + callback to ViewModel.
- **ViewModel (`HomeViewModel`)** = placement persistence and business policy.

Each layer does one thing, and the same coordinator can be reused anywhere in the app.
