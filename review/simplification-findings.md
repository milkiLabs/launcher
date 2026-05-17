## 1. Dead Code — Delete Entirely (783 lines)

These files have **zero production references**. They were likely built for a feature that was never completed or was replaced by a different approach.

### 1.1 Dead Domain/Widget Files (171 lines)

| File                                    | Lines | What It Was                           | Why Dead                                             |
| --------------------------------------- | ----- | ------------------------------------- | ---------------------------------------------------- |
| `domain/widget/WidgetTransformFrame.kt` | 78    | Widget frame transformation math      | Widget sizing uses `WidgetHostSizingSupport` instead |
| `domain/widget/WidgetSpanPolicy.kt`     | 50    | Widget span placement recommendations | Not integrated into widget placement flow            |
| `domain/widget/WidgetLayoutPolicy.kt`   | 43    | Inline widget span fitting            | Not integrated into widget placement flow            |

**Action:** Delete all 3 files. Widget placement is handled by `HomeModelWriter` + `WidgetHostSizingSupport`.

### 1.2 Dead Domain/Drag/Drop Files (21 lines)

| File                                     | Lines | What It Was                       | Why Dead                                |
| ---------------------------------------- | ----- | --------------------------------- | --------------------------------------- |
| `domain/drag/drop/DropTargetNode.kt`     | 6     | Generic drop target interface     | Replaced by direct drop action handlers |
| `domain/drag/drop/DropTargetRegistry.kt` | 15    | Registry that dispatches to nodes | No production code uses it              |

**Action:** Delete both files. Drop handling is done directly in `DraggablePinnedItemsGrid` and `ExternalDropRoutingLayer`.

### 1.3 Dead UI Gesture Detector (398 lines)

| File                                         | Lines | What It Was                    | Why Dead                                                                           |
| -------------------------------------------- | ----- | ------------------------------ | ---------------------------------------------------------------------------------- |
| `ui/interaction/grid/DragGestureDetector.kt` | 398   | Reusable drag gesture detector | The app uses Compose's `detectDragGestures` directly in `DraggablePinnedItemsGrid` |

This is the single largest dead file. It was built as a reusable gesture detector with tap/long-press/drag detection, multi-touch safety, and haptic coordination — but the actual drag-and-drop implementation bypasses it entirely and uses Compose Foundation's built-in gesture detection.

**Action:** Delete the file. All drag detection is handled by `DraggablePinnedItemsGrid` using `detectDragGestures`.

### 1.4 Dead Test Files (193 lines)

The dead production files above have corresponding test files that should also be deleted:

| File                                                 | Lines | Tests                  |
| ---------------------------------------------------- | ----- | ---------------------- |
| `test/.../domain/widget/WidgetLayoutPolicyTest.kt`   | 64    | Widget layout policy   |
| `test/.../domain/widget/WidgetSpanPolicyTest.kt`     | 48    | Widget span policy     |
| `test/.../domain/widget/WidgetTransformFrameTest.kt` | 81    | Widget transform frame |

**Action:** Delete all 3 test files.
