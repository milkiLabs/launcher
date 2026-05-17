# Compose UI & Performance Review

> Analysis of Jetpack Compose best practices, recomposition optimization, theming consistency, accessibility, and UI performance.

---

## 1. Recomposition Optimization

### 1.1 Stability Annotations Missing

| File | Issue | Severity |
|------|-------|----------|
| `domain/model/*.kt` | Domain model data classes lack `@Immutable` or `@Stable` annotations | MEDIUM |
| `presentation/search/SearchUiState.kt` | UI state data classes not annotated | MEDIUM |
| `presentation/drawer/DrawerAdapterItem.kt` | Adapter item classes not annotated | LOW |

Compose can skip recomposition for stable types. Without `@Immutable` on data classes, Compose must assume they may change, causing unnecessary recompositions.

### 1.2 Lambda Stability Issues

| File:Line | Issue | Severity |
|-----------|-------|----------|
| `PinnedItem.kt:118-123` | `combinedClickable` lambdas created inline every recomposition | MEDIUM |
| `DraggablePinnedItemsGrid.kt` | Drag gesture lambdas not remembered | MEDIUM |
| `LauncherScreen.kt` | Multiple inline lambdas passed to child composables | LOW |

**Example from `PinnedItem.kt:118-123`:**
```kotlin
.combinedClickable(
    onClick = onClick,
    onLongClick = {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        isMenuVisible = true
    }
)
```
The `onLongClick` lambda is created inline every recomposition. Should be:
```kotlin
val onLongClick = remember {
    {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        isMenuVisible = true
    }
}
```

### 1.3 Missing `derivedStateOf` Usage

| File | Issue | Severity |
|------|-------|----------|
| `HomeViewModel.kt` | `pinnedItems` StateFlow combined with other state without `derivedStateOf` | MEDIUM |
| `SearchViewModelStateHolder.kt` | Complex combine chains could benefit from `derivedStateOf` for sub-computations | LOW |

---

## 2. Composition Scope and Structure

### 2.1 Large Composition Scopes

| File:Line | Issue | Severity |
|-----------|-------|----------|
| `LauncherScreen.kt:106-179` | Entire home screen in single composable | MEDIUM |
| `SettingsScreen.kt` | All settings sections in one large composable | LOW |
| `DraggablePinnedItemsGrid.kt:44-231` | Complex layered grid in single composable | LOW (well-structured) |

### 2.2 State Hoisting

**Good patterns:**
- `PinnedItem` hoists `isMenuVisible` state
- `ItemActionMenu` receives `expanded` state from parent

**Issues:**
| File:Line | Issue | Severity |
|-----------|-------|----------|
| `LauncherScreen.kt:83-84` | `homescreenMenuAnchorPx` and `homeItemBoundsById` are `remember`-based, reset on config change | MEDIUM |
| `ActionShortcutManagerSheet.kt:71-72` | `editingShortcut` and `isCreating` reset on rotation | LOW |

---

## 3. Theming Consistency

### 3.1 Hardcoded Colors

| File:Line | Hardcoded Value | Issue | Severity |
|-----------|----------------|-------|----------|
| `PinnedItem.kt:183` | `Color.White` for label | Unreadable on light wallpapers | HIGH |
| `ItemActionMenu.kt:142-144` | `Color(0xFF2F323A)` background, `Color.White` text | Breaks in light mode | HIGH |
| `PinnedItem.kt:408` | `fileTypeVisual.backgroundColor.copy(alpha = 0.2f)` | Alpha-based theming | LOW |
| `PinnedItem.kt:367` | `MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)` | Acceptable but could use dedicated color | LOW |

**`PinnedItem.kt:183` Detail:** White text on transparent background over the home screen wallpaper. Contrast is entirely dependent on wallpaper brightness and will frequently fail WCAG requirements.

**`ItemActionMenu.kt:142-144` Detail:** Hardcoded dark background with white text looks correct in dark mode but will be invisible or low-contrast in light mode.

### 3.2 Incomplete Color Scheme Customization

`Theme.kt:78-120` — Only `primary`, `secondary`, `tertiary` are customized. `surface`, `background`, `error` etc. use Material defaults, which may not match the intended design.

### 3.3 Icon Size Inconsistency

| Usage | Size | Source |
|-------|------|--------|
| App grid | 72dp | `Spacing.kt:198` |
| Home compact | 56dp | `Spacing.kt:191` |
| App list | 40dp | `Spacing.kt:179` |
| Search leading | 24dp | `Spacing.kt:165` |
| Small trailing | 20dp | `Spacing.kt:158` |
| Extra small | 16dp | `Spacing.kt:151` |

The range is reasonable but some sizes (40dp, 24dp, 20dp, 16dp) fall below the 48dp minimum touch target.

---

## 4. Performance Bottlenecks

### 4.1 List/Grid Performance

| File | Issue | Severity |
|------|-------|----------|
| `AppDrawerOverlay.kt:347-352` | Uses `LazyVerticalGrid` with `GridCells.Fixed(4)` — good | GOOD |
| `DraggablePinnedItemsGrid.kt` | Custom grid layout — no LazyList, all items composed | MEDIUM |
| `WidgetPickerBottomSheet.kt` | Widget catalog uses LazyColumn — good | GOOD |

**`DraggablePinnedItemsGrid.kt` Detail:** The custom grid composes ALL visible items at once rather than using a lazy approach. For a home screen with 20-50 items, this is acceptable, but could become a bottleneck with many items.

### 4.2 Icon Loading

| File | Issue | Severity |
|------|-------|----------|
| `AppIconMemoryCache.kt` | Three-tier cache (memory, disk, PackageManager) — well-designed | GOOD |
| `ShortcutIconMemoryCache.kt` | LRU cache with constant state — good | GOOD |
| `AppIcon.kt` | No Coil integration (commented out in build.gradle) | MEDIUM |

**Note:** Coil is commented out in dependencies. The app uses custom icon loading with `PackageManager.getApplicationIcon()`. This works but Coil would provide better caching, crossfade animations, and placeholder support.

### 4.3 Search Performance

| File | Issue | Severity |
|------|-------|----------|
| `AppQueryRanker.kt` | Levenshtein distance allocates IntArrays per call | MEDIUM |
| `AppQueryRanker.kt:264-282` | 19 magic-number scoring constants | LOW |
| `SearchViewModelPipelineCoordinator.kt` | Generation counter for cancellation — correct pattern | GOOD |

**`AppQueryRanker.kt` Detail:** `levenshteinDistance` at line 233-260 creates two `IntArray` objects per call. For a ranking function called on every app during every keystroke, this generates significant GC pressure. Consider using a pooled buffer or limiting edit distance to short strings.

---

## 5. Modifier Ordering

### 5.1 Issues Found

| File:Line | Issue | Severity |
|-----------|-------|----------|
| `PinnedItem.kt:116-117` | `fillMaxWidth()` before `combinedClickable()` — correct order | GOOD |
| Various | Some composables lack `Modifier` parameter for external customization | LOW |

### 5.2 Missing Modifiers

Several composables don't accept a `Modifier` parameter:
- `PinnedItemView` — has `modifier` but it's not documented
- `ContactIcon` — has `modifier` — good
- `FileIcon` — has `modifier` — good
- `ActionShortcutIcon` — has `modifier` — good

---

## 6. Unnecessary Nested Layouts

| File:Line | Issue | Severity |
|-----------|-------|----------|
| `PinnedItem.kt:398-422` | `FileIcon` has 4 nested `Box`/`Surface` levels | MEDIUM |
| `PinnedItem.kt:354-378` | `ContactIcon` has 3 nested levels | LOW |
| `PinnedItem.kt:330-348` | Fallback action shortcut icon has 3 nested levels | LOW |

**`FileIcon` Detail:**
```kotlin
Box {                    // Level 1
    Surface {            // Level 2
        Box {            // Level 3
            Icon         // Level 4
        }
    }
}
```
This could be simplified by using `Surface`'s content parameter and reducing nesting.

---

## 7. Memory Leaks in Compose

### 7.1 LaunchedEffect / DisposableEffect

| File | Issue | Severity |
|------|-------|----------|
| `SearchDialogLifecycle.kt` | Lifecycle-aware search dialog — uses proper patterns | GOOD |
| `AppSearchDialog.kt` | Uses `Dialog` composable — platform handles lifecycle | GOOD |

### 7.2 Side Effects

| File:Line | Issue | Severity |
|-----------|-------|----------|
| `PinnedItem.kt:288-295` | `ActionShortcutIcon` resolves package name via `runCatching` during composition | MEDIUM |

**Detail:** `ActionShortcutIcon` performs a `PackageManager.resolveActivity()` call during composition. This is a blocking call that should be moved to a `LaunchedEffect` or done outside composition.

---

## 8. Preview Coverage

### 8.1 Missing Previews

The following composables have NO `@Preview` annotations:
- `PinnedItem` / `PinnedItemView`
- `PinnedItemIcon` / `FileIcon` / `ContactIcon` / `ActionShortcutIcon`
- `ItemActionMenu`
- `DraggablePinnedItemsGrid`
- `LauncherScreen`
- `FolderIcon` / `FolderPopupDialog`
- `WidgetPickerBottomSheet`
- Most search result item composables
- Most settings components

### 8.2 Existing Previews

Very few composables have previews. This makes it difficult to:
- Visually verify changes
- Test different configurations (dark mode, font scaling)
- Onboard new contributors

---

## 9. Animation Quality

### 9.1 Good Animations

| File | Animation | Quality |
|------|-----------|---------|
| `FolderPopupDialog.kt:277-293` | Folder open/close with `FastOutSlowInEasing` | GOOD |
| `LauncherSheet.kt:60-62` | Sheet spring animation (damping 0.8, stiffness 300) | GOOD |
| `WidgetPickerBottomSheet.kt:379-382` | Expand/collapse rotation with `LinearOutSlowInEasing` | GOOD |

### 9.2 Missing Animations

| Component | Missing Animation | Impact |
|-----------|------------------|--------|
| `LauncherScreen` | No transition between surfaces (search, drawer) | Feels abrupt |
| `SurfaceStateCoordinator` | State changes are instant | No visual continuity |
| `AppSearchDialog` | Uses platform default Dialog animation | Acceptable but not branded |

---

## 10. Priority Summary

| Priority | Finding | File | Impact |
|----------|---------|------|--------|
| P0 | Hardcoded white label color fails on light wallpapers | `PinnedItem.kt:183` | Accessibility, UX |
| P0 | Hardcoded menu colors break in light mode | `ItemActionMenu.kt:142-144` | Theming |
| P1 | `ActionShortcutIcon` resolves package during composition | `PinnedItem.kt:288-295` | Performance, jank |
| P1 | Domain models lack `@Immutable` annotations | `domain/model/*.kt` | Recomposition |
| P2 | Nested Box/Surface levels in icon composables | `PinnedItem.kt` | Performance |
| P2 | No `@Preview` annotations on most composables | Multiple | DX, visual testing |
| P3 | Missing transition animations between surfaces | `LauncherScreen.kt` | UX polish |
| P3 | Levenshtein distance allocates per call | `AppQueryRanker.kt` | GC pressure |
