# Drag and Drop System

This document describes the architecture, components, and usage of the drag and drop system used for the home screen grid in the launcher.

## Overview

The drag and drop system enables users to:
- Long-press and drag icons to new positions
- Swap items by dropping on occupied cells
- Receive visual feedback during drag operations
- Get haptic feedback for gesture confirmation

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    DraggablePinnedItemsGrid                      │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  UI Component Layer                                         │ │
│  │  - Renders items at grid positions                          │ │
│  │  - Applies visual effects during drag                       │ │
│  │  - Handles haptic feedback                                  │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  GridConfig          - Configuration data class             │ │
│  │  DragState           - Immutable state representation       │ │
│  │  DragController      - State management and logic           │ │
│  │  GridCalculator      - Coordinate conversion utilities      │ │
│  │  DragGestureDetector - Reusable gesture detection           │ │
│  │  DragVisualEffects   - Animation and visual feedback        │ │
│  │  DropTarget          - Interface for drop handling          │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Data Layer                                 │
│  HomeRepository.updateItemPosition() - Handles swap logic       │
│  HomeViewModel.moveItemToPosition() - ViewModel API             │
└─────────────────────────────────────────────────────────────────┘
```

## Components

### 1. GridConfig

Centralized configuration for the grid layout and drag behavior.

**File:** `ui/components/grid/GridConfig.kt`

```kotlin
data class GridConfig(
    val columns: Int = 4,
    val extraRows: Int = 4,
    val dragThresholdPx: Float = 20f,
    val dragScale: Float = 1.15f,
    val dragAlpha: Float = 0.6f,
    // ... more properties
)
```

**Purpose:**
- Define grid dimensions (columns, rows)
- Configure gesture thresholds
- Set visual effect parameters

**Benefits:**
- Change configuration in one place
- Easy to create variants (tablet, accessibility)
- Testable configuration

### 2. DragState

Immutable sealed class representing drag operation state.

**File:** `ui/components/grid/DragState.kt`

```kotlin
sealed class DragState {
    data object Idle : DragState()
    data class Dragging(...) : DragState()
    data class PendingDrop(...) : DragState()
}
```

**Purpose:**
- Type-safe state representation
- Exhaustive state handling (compiler enforced)
- Clean state machine

**State Transitions:**
```
Idle -> Dragging -> PendingDrop -> Idle
         │
         └──────> Idle (cancelled)
```

### 3. DragController

Stateful controller managing drag operations.

**File:** `ui/components/grid/DragController.kt`

```kotlin
class DragController(
    val config: GridConfig,
    val calculator: GridCalculator
) {
    var state: DragState by mutableStateOf(DragState.Idle)
    
    fun startDrag(item: HomeItem, startPosition: GridPosition)
    fun updateDrag(delta: Offset)
    fun endDrag(): DropResult
    fun cancelDrag()
}
```

**Purpose:**
- Encapsulate drag logic
- Coordinate with DropTarget
- Provide clean API for UI

**Usage:**
```kotlin
val controller = rememberDragController(config, calculator)

// In gesture handler
controller.startDrag(item, position)
controller.updateDrag(delta)
val result = controller.endDrag()
```

### 4. GridCalculator

Stateless utility for coordinate conversions.

**File:** `ui/components/grid/GridCalculator.kt`

```kotlin
data class GridCalculator(
    val cellWidthPx: Float,
    val cellHeightPx: Float,
    val columns: Int,
    val rows: Int
) {
    fun pixelToCell(pixelPosition: Offset): GridPosition
    fun cellToPixel(position: GridPosition): Offset
    fun calculateTargetPosition(startPosition: GridPosition, offset: Offset): GridPosition
    fun isValidPosition(position: GridPosition): Boolean
}
```

**Purpose:**
- Convert between pixel and grid coordinates
- Validate positions
- Calculate drop targets

### 5. DragGestureDetector

Reusable gesture detection for drag operations.

**File:** `ui/components/grid/DragGestureDetector.kt`

```kotlin
suspend fun PointerInputScope.detectDragOrTapGesture(
    dragThreshold: Float = 20f,
    onTap: () -> Unit,
    onLongPress: (Offset) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (change, dragAmount) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
)
```

**Purpose:**
- Distinguish tap vs long-press vs drag
- Handle gesture lifecycle
- Provide extension for Modifier

**Gesture Flow:**
```
Touch Down
    │
    ├── Released before long-press → TAP
    │
    └── Long-press detected → Show Menu
            │
            ├── Released without movement → Menu stays
            │
            └── Movement beyond threshold → DRAG
                    │
                    ├── Continue moving → UPDATE DRAG
                    ├── Released → END DRAG
                    └── Cancelled → CANCEL DRAG
```

### 6. DragVisualEffects

Composable functions for drag animations.

**File:** `ui/components/grid/DragVisualEffects.kt`

```kotlin
@Composable
fun animateDragVisuals(
    isDragging: Boolean,
    config: GridConfig
): DragVisualValues

fun Modifier.dragVisualEffects(values: DragVisualValues): Modifier
fun Modifier.previewVisualEffects(config: GridConfig): Modifier
```

**Purpose:**
- Animate scale/alpha during drag
- Provide preview effects
- Define animation specs

### 7. DropTarget

Interface for components that can receive dropped items.

**File:** `ui/components/grid/DropTarget.kt`

```kotlin
interface DropTarget {
    fun canDrop(item: HomeItem, position: GridPosition): Boolean
    fun previewDrop(item: HomeItem, position: GridPosition)
    fun onDrop(item: HomeItem, position: GridPosition): DropResult
    fun onDragCancelled()
}

sealed class DropResult {
    data class Success(...) : DropResult()
    data class Swap(...) : DropResult()
    data class Rejected(...) : DropResult()
    data object Cancelled : DropResult()
}
```

**Purpose:**
- Abstract drop handling
- Enable multiple drop targets (grid, dock, folder)
- Provide result feedback

## Bugs Fixed

### 1. PointerInput Key Instability
**Issue:** Using `items` list as key restarted gesture detector on every position update.

**Fix:** Changed to use `item.id` which is stable and unique.

```kotlin
// Before
.pointerInput(item, items, columns, ...)

// After
.detectDragGesture(key = item.id, ...)
```

### 2. Preview Position Calculation
**Issue:** Redundant calculation `- half + half` was confusing dead code.

**Fix:** Simplified to direct positioning.

```kotlin
// Before
x = previewX.roundToInt() - (cellWidthPx / 2).roundToInt() + cellWidthPx.roundToInt() / 2

// After
x = previewX.roundToInt()
```

### 3. Race Condition in Drag End/Cancel
**Issue:** `onDragEnd` and `onDragCancel` could conflict during concurrent calls.

**Fix:** Added `isDragEnding` flag to prevent race.

```kotlin
var isDragEnding by remember { mutableStateOf(false) }

onDragEnd = {
    isDragEnding = true
    // ... handle drop
    isDragEnding = false
}

onDragCancel = {
    if (!isDragEnding) {
        // ... handle cancel
    }
}
```

### 4. Exception Swallowing
**Issue:** All exceptions caught silently, making debugging impossible.

**Fix:** Added logging before catching.

```kotlin
} catch (e: Exception) {
    android.util.Log.w("DragGestureDetector", "Gesture cancelled: ${e.message}")
    // ... handle
}
```

## Configuration

### Default Configuration
```kotlin
GridConfig.Default // 4 columns, standard thresholds
```

### Tablet Configuration
```kotlin
GridConfig.Tablet // 6 columns, larger thresholds
```

### Accessibility Configuration
```kotlin
GridConfig.Accessibility // 3 columns, larger touch targets
```

### Custom Configuration
```kotlin
val customConfig = GridConfig(
    columns = 5,
    dragThresholdPx = 30f,
    dragScale = 1.2f
)
```

## Extending the System

### Adding a New Drop Target (e.g., Dock)

```kotlin
class DockDropTarget(
    private val onDrop: (HomeItem, Int) -> Unit
) : DropTarget {
    override fun canDrop(item: HomeItem, position: GridPosition): Boolean {
        return position.row == 0 // Only allow drops in row 0
    }
    
    override fun onDrop(item: HomeItem, position: GridPosition): DropResult {
        onDrop(item, position.column)
        return DropResult.Success(position)
    }
}
```

### Adding Multi-Page Support

1. Create `PageDropTarget` implementing `DropTarget`
2. Use `DragController` with page-aware calculator
3. Handle page transitions during drag preview

## Testing

### Unit Testing GridCalculator
```kotlin
@Test
fun testPixelToCellConversion() {
    val calculator = GridCalculator(cellWidthPx = 100f, cellHeightPx = 100f)
    
    val position = calculator.pixelToCell(Offset(250f, 150f))
    
    assertEquals(GridPosition(row = 1, column = 2), position)
}
```

### Unit Testing DragState
```kotlin
@Test
fun testDragStateTransitions() {
    val item = HomeItem.PinnedApp(...)
    val startPos = GridPosition(0, 0)
    
    var state: DragState = DragState.Idle
    assertEquals(null, state.draggedItem)
    
    state = DragState.startDrag(item, startPos)
    assertTrue(state is DragState.Dragging)
    assertEquals(item, state.draggedItem)
}
```

## Performance Considerations

1. **State Stability:** Use `remember` for calculators and controllers
2. **Key Stability:** Only use stable values as `pointerInput` keys
3. **Animation:** Use `Animatable` for smooth 60fps animations
4. **Recomposition:** `DragVisualValues` is a data class for stability

## Future Improvements

1. **Swap Animation:** Add smooth animation when items swap positions
2. **Multi-Touch Guard:** Better handling of simultaneous touches
3. **Drag to Remove:** Add drop zone for uninstalling/unpinning
4. **Folder Support:** Allow dropping items into folders
5. **Cross-Page Drag:** Enable dragging items between pages
