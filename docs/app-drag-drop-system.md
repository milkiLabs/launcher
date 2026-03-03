# App Drag Drop System (Reusable API Guide)

This document focuses on the reusable API exposed in `ui/components/dragdrop`.

## Files

- `app/src/main/java/com/milki/launcher/ui/components/dragdrop/AppDragDropContract.kt`
- `app/src/main/java/com/milki/launcher/ui/components/dragdrop/AppDragDropModifiers.kt`
- `app/src/main/java/com/milki/launcher/ui/components/dragdrop/AppExternalDragDrop.kt`
- `app/src/main/java/com/milki/launcher/ui/components/dragdrop/ExternalDragPayloadCodec.kt`
- `app/src/main/java/com/milki/launcher/ui/components/dragdrop/ExternalDragCoordinateMapper.kt`
- `app/src/main/java/com/milki/launcher/ui/components/dragdrop/ExternalAppDragDropCoordinator.kt`

## Quick start

```kotlin
val controller = rememberAppDragDropController<MyItem>(config)

val metrics = AppDragDropLayoutMetrics(
    cellWidthPx = cellWidth,
    cellHeightPx = cellHeight,
    columns = columns,
    rows = rows
)

Modifier.appDragDropGestures(
    key = "${item.id}-${item.position.row}-${item.position.column}",
    dragThresholdPx = config.dragThresholdPx,
    callbacks = AppDragDropGestureCallbacks(
        onTap = { ... },
        onLongPress = { ... },
        onDragStart = { controller.startDrag(item, item.id, item.position) },
        onDrag = { change, dragAmount ->
            change.consume()
            controller.updateDrag(dragAmount, metrics)
        },
        onDragEnd = {
            when (val result = controller.endDrag(metrics)) {
                is AppDragDropResult.Moved -> persistMove(result.itemId, result.to)
                is AppDragDropResult.Unchanged -> Unit
                AppDragDropResult.Cancelled -> Unit
            }
        },
        onDragCancel = { controller.cancelDrag() }
    )
)
```

## API details

### `AppDragDropLayoutMetrics`

Use this as the single source of grid math for a surface.

- `clamp(position)` prevents out-of-bounds cells
- `calculateTarget(start, offset)` computes nearest target cell
- `clampOffset(start, rawOffset)` keeps dragged preview in valid area
- `cellToPixel(position)` helps render drag preview and target highlights

### `AppDragDropSession<T>`

Represents an active drag:

- `item`, `itemId`
- `startPosition`
- `currentOffset`
- `hasExceededThreshold`

### `AppDragDropController<T>`

Controller lifecycle methods:

- `startDrag(...)`
- `updateDrag(...)`
- `endDrag(...)`
- `cancelDrag()`

Helper methods:

- `isDraggingItem(itemId)`
- `resolveBasePosition(itemId, currentPosition)`

### `AppDragDropResult<T>`

- `Moved` means persistence should happen
- `Unchanged` means user dropped into same cell
- `Cancelled` means no-op

### `appDragDropGestures(...)`

Use this on any draggable composable node.

- requires stable `key`
- supports unified callbacks via `AppDragDropGestureCallbacks`

## Behavior guarantees

- End/cancel are safe to call repeatedly
- Target resolution is always clamped to valid grid bounds
- Movement result is explicit and easy to branch on
- Controller remains independent from repository/data-layer writes

## Home persistence policy (current)

For the home surface, persistence is coordinated by `HomeViewModel` as a single
serialized mutation gateway for:

- internal drag moves,
- external app drops,
- pin from action menus,
- unpin from action menus.

Repository behavior for placement is now:

- target cell occupied by another item => reject move/drop,
- target cell empty => apply move/drop,
- external drop pin-or-move uses one atomic transaction (`pinOrMoveItemToPosition`).

This eliminates race windows caused by split write paths and avoids transient
"pin in first available slot then move" intermediate states.

## Pattern consistency

Home grid icons and search app items now both wire gestures through
`appDragDropGestures(...)` with the same long-press + drag callback pattern.
This keeps interaction behavior aligned across launcher surfaces.

## External app payload bridge

`AppExternalDragDrop.kt` provides the platform drag-and-drop bridge for moving
app payloads from search UI surfaces into the home grid.

- `startExternalAppDrag(...)` starts platform drag transfer using JSON clip payload.
- `startExternalAppDrag(...)` uses global drag flags (with fallback) so drags can
    move from dialog windows onto launcher home surfaces.
- `AppExternalDropTargetOverlay(...)` handles external drag lifecycle on home surfaces.
- Payload contract is intentionally minimal: `name`, `packageName`, `activityName`.
- Payload decoding prefers `DragEvent.localState` (same-process) and falls back to
    ClipData JSON decoding for robustness across devices.

### External drop callback contract

`AppExternalDropTargetOverlay` callbacks expose payload-aware lifecycle:

- `onDragStarted()` is invoked when a likely launcher app drag session starts.
- `onDragMoved(localOffset, appInfo?)` emits hover updates; payload may be null until lazy decode resolves.
- `onAppDropped(appInfo, localOffset)` emits the payload and final drop event.
- `onDragEnded()` signals end of active payload drag lifecycle.

This contract allows target surfaces to render highlights immediately while
keeping payload parsing and drag listener lifecycle inside the reusable coordinator.

### Recomposition safety (critical)

`DefaultExternalAppDragDropCoordinator` is the single source of truth for
platform drag listener lifecycle and payload session state. This prevents
surface-specific drift and repeated listener bugs.

The Compose overlay adapter avoids stale callback references by:

1. **`rememberUpdatedState`** for every callback — the listener closure always
   calls the latest version without needing new `remember` keys.
2. **Keyless `remember { }`** for coordinator/listener instances — they are created once.

### Placement strategy for robust UX

For dialog-to-home external drags, prefer this target selection order:

1. last hovered target cell from `onDragMoved`
2. fallback: convert `onAppDropped` local offset to cell

Using hovered target as primary source keeps final pin position aligned with the
highlight users saw before they released the drag.

This keeps cross-surface drag robust while avoiding non-serializable fields.

## Implementation notes for contributors

1. Keep drag-drop state changes inside controller methods only.
2. Keep cell/offset math inside `AppDragDropLayoutMetrics`.
3. Keep repository writes outside the controller (usually ViewModel callbacks).
4. Keep gesture setup in `appDragDropGestures` for consistency across surfaces.

Following these rules keeps drag-and-drop predictable and reusable across the launcher.
