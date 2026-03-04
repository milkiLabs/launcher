# External Drag & Drop Simplification (Implemented)

## Goal

Use one reusable external drag/drop architecture that is:

1. Easy to reason about.
2. Easy to reuse across launcher surfaces.
3. Strictly split between platform bridge logic and UI rendering logic.

The implementation now supports three payload families through the same pipeline:

- App payloads
- File payloads
- Contact payloads

## Current module layout

- [app/src/main/java/com/milki/launcher/ui/components/dragdrop/ExternalDragPayloadCodec.kt](../app/src/main/java/com/milki/launcher/ui/components/dragdrop/ExternalDragPayloadCodec.kt)
- [app/src/main/java/com/milki/launcher/ui/components/dragdrop/ExternalDragCoordinateMapper.kt](../app/src/main/java/com/milki/launcher/ui/components/dragdrop/ExternalDragCoordinateMapper.kt)
- [app/src/main/java/com/milki/launcher/ui/components/dragdrop/ExternalAppDragDropCoordinator.kt](../app/src/main/java/com/milki/launcher/ui/components/dragdrop/ExternalAppDragDropCoordinator.kt)
- [app/src/main/java/com/milki/launcher/ui/components/dragdrop/AppExternalDragDrop.kt](../app/src/main/java/com/milki/launcher/ui/components/dragdrop/AppExternalDragDrop.kt)

`AppExternalDropTargetOverlay(...)` is a thin Compose adapter over the coordinator.

## Implemented coordinator contract

```kotlin
interface ExternalAppDragDropCoordinator {
    fun createListener(callbacks: TargetCallbacks): View.OnDragListener

    interface TargetCallbacks {
        fun onStarted()
        fun onMoved(localOffset: Offset, item: ExternalDragItem?)
        fun onDropped(item: ExternalDragItem, localOffset: Offset): Boolean
        fun onEnded(result: Boolean)
    }
}
```

### Why this is simple

- Target surfaces receive only what they need.
- Drag listener lifecycle is centralized in one place.
- Payload decode logic is centralized in one codec.
- Surface code stays focused on visual state and offset-to-cell mapping.

## Implemented payload contract

`ExternalDragPayloadCodec` defines a sealed payload model:

```kotlin
sealed class ExternalDragItem {
    data class App(val appInfo: AppInfo)
    data class File(val fileDocument: FileDocument)
    data class Contact(val contact: Contact)
}
```

Codec responsibilities:

- Encode payloads into `ClipData`
- Detect likely launcher payload drags in `ACTION_DRAG_STARTED`
- Decode from `localState` first, then ClipData JSON
- Support legacy app-only clip labels for compatibility

## Implemented start-drag API

`AppExternalDragDrop.kt` provides source helpers:

- `startExternalAppDrag(...)`
- `startExternalFileDrag(...)`
- `startExternalContactDrag(...)`

All source helpers share the same host fallback strategy:

1. Activity decor view (preferred)
2. Root view
3. Source host view

All source helpers attempt global drag first, then local fallback.

## Surface integration pattern

Each target surface should do only this:

1. Track local external drag UI state (`isExternalDragActive`, `hoverCell`, `previewItem`).
2. Convert `localOffset` to cell via local `AppDragDropLayoutMetrics`.
3. Resolve final drop position (prefer hover cell, fallback to drop offset).
4. Map `ExternalDragItem` to the surface item model (`HomeItem`, etc.).
5. Forward to ViewModel/repository mutation callback.

No surface-level payload parsing should be added.

## Home grid mapping (implemented)

`DraggablePinnedItemsGrid` maps external payloads to persisted home items:

- `ExternalDragItem.App` -> `HomeItem.PinnedApp`
- `ExternalDragItem.File` -> `HomeItem.PinnedFile`
- `ExternalDragItem.Contact` -> `HomeItem.PinnedContact`

This keeps one generic drag bridge while preserving home persistence semantics.

## Prefix result integration (implemented)

Search result rows now start external drags using the same pipeline:

- App results -> `startExternalAppDrag(...)`
- File results (`f` prefix) -> `startExternalFileDrag(...)`
- Contact results (`c` prefix) -> `startExternalContactDrag(...)`

All three drop through the same home overlay and callback path.

## Invariants kept by design

1. Accept likely launcher drags early and decode payload lazily.
2. Use one coordinate normalization implementation.
3. Keep drag listener lifecycle centralized.
4. Keep business policy (occupied cell rejection, persistence rules) outside the coordinator.
5. Keep source-host fallback logic centralized in start-drag helpers.

## Resulting mental model

- `AppDragDropController` = internal in-surface dragging.
- `ExternalAppDragDropCoordinator` = platform external drag bridge.
- `ExternalDragPayloadCodec` = payload transport contract.
- Surface layer = visual state + cell mapping + callback forwarding.
- `HomeViewModel` = write policy + serialized persistence.
