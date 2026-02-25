# UX/UI Audit Report - Milki Launcher

**Date:** 2026-02-25  
**Auditor:** AI Code Review  
**Scope:** All UI/Compose files in the codebase

---

### 23. Contact Without Phone Number Still Shows Call Icon

**File:** `SearchResultItems.kt:358-370`  
**Severity:** Medium

**Description:**  
Contacts without phone numbers show a non-clickable call icon, which is confusing.

**User Impact:**  
Users see a call icon but cannot tap it. This is visually misleading.

**Suggested Improvement:**
Hide the call icon entirely for contacts without phone numbers.

### 7. Missing Haptic Feedback

**Files:**

- `DraggablePinnedItemsGrid.kt`
- `PinnedItem.kt`
- `ItemActionMenu.kt`

**Severity:** High

**Description:**  
No haptic feedback is provided for:

- Long press to show menu
- Starting drag operation
- Dropping item in new position
- Menu item selection

**User Impact:**  
Users lack tactile confirmation of actions, making the interface feel less responsive and polished.

**Suggested Improvement:**

```kotlin
val hapticFeedback = LocalHapticFeedback.current

// On long press
hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

// On drag start
hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

// On drop
hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
```

### 10. Search Dialog Keyboard Focus Timing

**File:** `AppSearchDialog.kt:182-185`  
**Severity:** High

**Description:**  
The 10ms delay for focus request is a workaround that may not work reliably on all devices. Some devices may have slower layout passes.

**User Impact:**  
On some devices, the keyboard may not appear automatically when opening search.

**Suggested Improvement:**
Use `LaunchedEffect` with `focusRequester.requestFocus()` and handle the `FocusRequester` lifecycle properly. Consider using `DisposableEffect` to ensure cleanup.

### 15. Dropdown Menu Doesn't Dismiss on Outside Tap

**File:** `SettingsComponents.kt:238-290`  
**Severity:** High

**Description:**  
The dropdown setting item uses an inline expanded list instead of a `DropdownMenu`. Tapping outside the expanded options doesn't close the dropdown.

**User Impact:**  
Users expect tapping outside to dismiss dropdowns. The current behavior is unexpected.

**Suggested Improvement:**
Use `DropdownMenu` composable which handles outside tap dismissal automatically, or add a transparent scrim layer.

### 2. Missing Error State Handling

**File:** `SearchUiState.kt`  
**Severity:** Critical

**Description:**  
There is no error state in `SearchUiState`. Network failures, permission denials, and other errors have no UI representation. The codebase catches exceptions but only shows Toast messages.

**User Impact:**  
Users receive no meaningful feedback when operations fail. Error messages in Toasts disappear quickly and cannot be reviewed.

**Suggested Improvement:**
Add error state to `SearchUiState`:

```kotlin
data class SearchUiState(
    // ... existing properties
    val error: String? = null,
    val errorType: ErrorType? = null
)

enum class ErrorType {
    NETWORK, PERMISSION, NOT_FOUND, GENERIC
}
```

And display errors inline in the search dialog with retry options.

---

# TODO:

### 4. Hardcoded Strings (No Internationalization)

**Files:** Multiple (see below)  
**Severity:** High

**Description:**  
Nearly all UI strings are hardcoded instead of using `stringResource()`. This prevents internationalization and makes text changes difficult.

**Locations:**

- `LauncherScreen.kt:110` - "Press home to search"
- `LauncherScreen.kt:172` - "App not found: ${item.label}"
- `LauncherScreen.kt:187` - "Open ${item.name}"
- `LauncherScreen.kt:194` - "No app found to open ${item.name}"
- `SettingsScreen.kt:88` - "Settings"
- `SettingsScreen.kt:117` - "Search Behavior"
- `SettingsScreen.kt:120-260` - All settings titles and subtitles
- `AppSearchDialog.kt:82-87` - Placeholder texts
- `SearchResultItems.kt:620-646` - Empty state messages
- `ItemActionMenu.kt:108,131,137,158,174` - Menu item labels

**User Impact:**  
App cannot be translated to other languages. Users who don't speak English cannot use the app effectively.

**Suggested Improvement:**
Move all strings to `res/values/strings.xml` and use `stringResource(R.string.key)` in composables.

---

## TODO:

### 5. Missing Content Descriptions for Accessibility

**Files:** Multiple  
**Severity:** High

**Description:**  
Several interactive elements lack content descriptions for screen readers.

**Locations:**

- `AppSearchDialog.kt:273-276` - Close button has "Clear search" but could be more descriptive
- `PinnedItemsGrid.kt:102-103` - Empty state has no semantic properties
- `DraggablePinnedItemsGrid.kt:184-188` - Empty state has no semantic properties
- `SettingsComponents.kt:280` - "Selected" check icon uses generic description
- `SearchResultItems.kt:350` - Call icon button should describe which contact

**User Impact:**  
Screen reader users cannot understand the purpose of some UI elements.

**Suggested Improvement:**

```kotlin
// Add contentDescription with context
Icon(
    imageVector = Icons.Default.Call,
    contentDescription = "Call ${result.contact.displayName}",
    // ...
)
```

---

### 6. Touch Target Size Issues

**Files:**

- `SearchResultItems.kt:350-356` - Call icon button
- `AppSearchDialog.kt:272-278` - Clear button

**Severity:** High

**Description:**  
The call icon button in contact results uses a touch target of `Spacing.extraLarge` (32dp), which is below Material Design's recommended 48dp minimum. The clear button is a standard IconButton which is acceptable, but could be improved.

**User Impact:**  
Users with motor impairments or larger fingers may struggle to tap these buttons accurately.

**Suggested Improvement:**

```kotlin
// Increase touch target to 48dp minimum
Box(
    modifier = Modifier
        .size(48.dp)  // Minimum recommended touch target (use spacing don't hardcode)
        .clickable { onDialClick() },
    contentAlignment = Alignment.Center
) {
    Icon(
        modifier = Modifier.size(IconSize.standard),
        // ...
    )
}
```

---

### 9. Settings Reset Has No Success Feedback

**File:** `SettingsScreen.kt:271-293`  
**Severity:** High

**Description:**  
After resetting settings to defaults, there's no feedback confirming the action succeeded. The dialog just closes.

**User Impact:**  
Users may be uncertain whether the reset actually occurred.

**Suggested Improvement:**

```kotlin
// Add snackbar after reset
scope.launch {
    snackbarHostState.showSnackbar("Settings restored to defaults")
}
```

---

### 11. No Empty State for App Info Launch Failure

**File:** `LauncherScreen.kt:166-174`  
**Severity:** High

**Description:**  
When `getLaunchIntentForPackage` returns null, only a Toast is shown. The app doesn't offer any alternative action.

**User Impact:**  
Users see a brief toast that disappears quickly, with no way to resolve the issue.

**Suggested Improvement:**
Show an alert dialog with options:

- "App not found. It may have been uninstalled."
- Action: "Remove from home screen" / "Search in Play Store"

---

### 12. Drag Threshold Too Small

**File:** `DraggablePinnedItemsGrid.kt:472`  
**Severity:** High

**Description:**  
The drag threshold is hardcoded to `20f` pixels. This is very small and may cause accidental drag starts when the user just wanted to long-press for the menu.

**User Impact:**  
Users may accidentally enter drag mode when trying to access the menu, causing frustration.

**Suggested Improvement:**
Use a larger threshold (40-48dp) or make it configurable:

```kotlin
val dragThreshold = with(LocalDensity.current) { 48.dp.toPx() }
```

---

### 13. URL Result Doesn't Show "Open in Browser" Option

**File:** `SearchResultItems.kt:256-264`  
**Severity:** High

**Description:**  
The TODO comment at line 257 acknowledges this issue. When a URL has a handler app, users cannot choose to open it in a browser instead.

**User Impact:**  
Users lose control over how URLs are opened. For example, a YouTube link always opens in the YouTube app even if the user wants to view it in a browser.

**Suggested Improvement:**
Add a long-press menu with "Open in browser" option, or add a secondary action button.

---

---

### 17. Hardcoded Corner Radius in Theme

**File:** `Spacing.kt`  
**Severity:** Medium

**Description:**  
`CornerRadius` is defined in `Spacing.kt` but could logically belong with theme-related values. The naming is also inconsistent with Material Design conventions.

**User Impact:**  
Minor organization issue. Developers may not find corner radius values easily.

**Suggested Improvement:**
Move to a separate `Shape.kt` file or rename to align with Material Design naming (e.g., `small`, `medium`, `large`).

---

### 18. Missing Animation on Dialog Appear/Disappear

**File:** `AppSearchDialog.kt:102-108`  
**Severity:** Medium

**Description:**  
The search dialog appears instantly without animation. Dialogs in Material Design typically have enter/exit animations.

**User Impact:**  
The sudden appearance can be jarring. Animations provide visual continuity.

**Suggested Improvement:**
Use `AnimatedVisibility` or platform dialog animations:

```kotlin
Dialog(
    properties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = true
    ),
    // Add enter/exit transitions
)
```

---

### 19. Provider Info Text Not Clickable

**File:** `AppSearchDialog.kt:309-329`  
**Severity:** Medium

**Description:**  
The provider info row shows helpful text like "YouTube: Search YouTube videos" but is not clickable. It could link to settings to enable/disable providers.

**User Impact:**  
Users who want to configure search providers must navigate to settings manually.

**Suggested Improvement:**
Make the provider info row clickable to navigate to search provider settings.

---

### 20. No Debounce on Search Input

**File:** `SearchViewModel.kt` (inferred from usage)  
**Severity:** Medium

**Description:**  
Based on the code flow, every keystroke triggers a search. For providers like web search, this could cause excessive network requests.

**User Impact:**  
Poor performance on slow networks. Wasted bandwidth and battery.

**Suggested Improvement:**
Implement debouncing with a 300-500ms delay for non-local search providers.

---

### 21. Recent Apps Not Pinned Indication

**File:** `SearchResultsList.kt:238-242`  
**Severity:** Medium

**Description:**  
Recent apps in search results look identical to other apps. Users cannot tell which apps are recently used vs. all installed apps.

**User Impact:**  
Users lose context about why certain apps appear first.

**Suggested Improvement:**
Add a small "Recent" badge or different styling for recent apps.

---

### 22. File Size Display Format

**File:** `SearchResultItems.kt:508-516`  
**Severity:** Medium

**Description:**  
File sizes are displayed without locale-aware formatting. The code calls `file.formattedSize()` but the implementation should use `DecimalFormat` with locale support.

**User Impact:**  
File sizes may display with incorrect decimal separators in some locales (e.g., "1,5 MB" vs "1.5 MB").

**Suggested Improvement:**
Use `android.icu.text.DecimalFormat` or `String.format()` with locale.

---

---

### 24. Permission Request Card Uses Error Color

**File:** `SearchResultItems.kt:443-448`  
**Severity:** Medium

**Description:**  
The permission request card uses `MaterialTheme.colorScheme.error` for the warning icon. While attention-grabbing, it may imply an error rather than a request for permission.

**User Impact:**  
Users may feel alarmed by the error-colored icon when it's just a permission request.

**Suggested Improvement:**
Use a neutral or primary color for the icon, or use a specific "info" color.

---

### 25. Settings Switch Animation Missing

**File:** `SettingsComponents.kt:154-158`  
**Severity:** Medium

**Description:**  
The switch in `SwitchSettingItem` uses the default Material Switch which animates, but the rest of the row doesn't animate state changes.

**User Impact:**  
Minor visual inconsistency. The switch animates but surrounding elements snap.

**Suggested Improvement:**
Use `animateContentSize()` on the row for smoother transitions.

---

### 26. Slider Missing Value Preview on Drag

**File:** `SettingsComponents.kt:365-373`  
**Severity:** Medium

**Description:**  
The slider shows the current value in a badge, but this doesn't update smoothly during drag or show a tooltip at the thumb position.

**User Impact:**  
Users must look away from the slider to see the current value.

**Suggested Improvement:**
Add a thumb label that follows the slider position during drag.

---

### 27. Settings Category Headers Not Sticky

**File:** `SettingsScreen.kt:108-268`  
**Severity:** Medium

**Description:**  
When scrolling through settings, category headers scroll away. In long settings lists, users lose context about which section they're viewing.

**User Impact:**  
Users may forget which settings category they're scrolling through.

**Suggested Improvement:**
Use `stickyHeader` in `LazyColumn` for category headers, or use a `LazyVerticalGrid` with pinned headers.

---

### 28. Drag Preview Uses Different Alpha Than Base Item

**File:** `DraggablePinnedItemsGrid.kt:420-426`  
**Severity:** Medium

**Description:**  
The drag preview item uses `alpha = 0.9f` and `scale = 1.2f`, while the original item uses `alpha = 0.6f` and `scale = 1.15f`. This creates a confusing visual where the dragged item appears in two places with different appearances.

**User Impact:**  
The dual appearance during drag can be visually confusing.

**Suggested Improvement:**
Make the original item disappear entirely (alpha = 0) during drag, or use consistent styling.

---

### 29. Empty Home State Not Actionable

**File:** `LauncherScreen.kt:108-113` and `DraggablePinnedItemsGrid.kt:179-191`  
**Severity:** Medium

**Description:**  
The "Press home to search" / "Tap to search" text is not clickable. Users cannot tap it to open search.

**User Impact:**  
Users must press the home button to open search, even though the hint suggests tapping.

**Suggested Improvement:**
Make the empty state clickable to open search:

```kotlin
Text(
    text = "Tap to search",
    modifier = Modifier.clickable { searchViewModel.showSearch() }
)
```

---

### 30. No Visual Feedback for Pinned Items

**File:** `AppGridItem.kt`, `AppListItem.kt`  
**Severity:** Medium

**Description:**  
Apps that are already pinned to the home screen look identical to unpinned apps. There's no visual indicator of pinned status.

**User Impact:**  
Users may try to pin an app that's already pinned, leading to confusion.

**Suggested Improvement:**
Add a small pin indicator or badge for already-pinned apps.

---

### 31. Contact Result Truncation

**File:** `SearchResultItems.kt:306`  
**Severity:** Medium

**Description:**  
Only the first phone number is shown for contacts. Contacts with multiple numbers may have important numbers hidden.

**User Impact:**  
Users cannot see all available phone numbers without opening the contact.

**Suggested Improvement:**
Show a count indicator for additional numbers (e.g., "+2 more") or expandable list.

---

### 32. No Way to Cancel Ongoing Search

**File:** `AppSearchDialog.kt`  
**Severity:** Medium

**Description:**  
For long-running searches (web, files), there's no way to cancel the operation once started.

**User Impact:**  
Users must wait for searches to complete or close the entire dialog.

**Suggested Improvement:**
Add a cancel button or make the loading indicator clickable to cancel.

---

### 33. Back Button Behavior Not Communicated

**File:** `AppSearchDialog.kt:93`  
**Severity:** Medium

**Description:**  
The `BackHandler` intercepts the back button to close the dialog, but there's no visual indication that back will close search.

**User Impact:**  
Users may not realize pressing back will close the search dialog.

**Suggested Improvement:**
This is acceptable behavior, but consider adding a hint or ensure consistent behavior with system navigation.

---

### 34. File Search Results Show Path Without Context

**File:** `SearchResultItems.kt:508-516`  
**Severity:** Medium

**Description:**  
The folder path is shown but not labeled. Users see "Downloads/Files/Document" without knowing what it represents.

**User Impact:**  
Users may not understand what the supporting text represents.

**Suggested Improvement:**
Add context: "In: Downloads/Files" or use a folder icon prefix.

---

## Low Severity Issues

### 35. Inconsistent Typography Usage

**File:** `SettingsComponents.kt:73-74`  
**Severity:** Low

**Description:**  
`SettingsCategory` manually specifies `letterSpacing = 1.2.sp` instead of using the typography system.

**User Impact:**  
Minor inconsistency. Changes to typography won't affect category headers.

**Suggested Improvement:**
Define a `labelMediumUppercase` style in `Type.kt` for category headers.

---

### 36. Magic Numbers in Drag Calculations

**File:** `DraggablePinnedItemsGrid.kt:173, 233-235`  
**Severity:** Low

**Description:**  
The number of extra rows (`+ 4`) and scale values (`1.15f`, `1.2f`) are magic numbers without explanation.

**User Impact:**  
Minor maintainability issue.

**Suggested Improvement:**
Extract to named constants:

```kotlin
private const val EXTRA_ROWS_FOR_VISUAL_PADDING = 4
private const val DRAGGED_ITEM_SCALE = 1.15f
private const val DRAG_PREVIEW_SCALE = 1.2f
```

---

### 37. No Indication of Grid Columns Being Configurable

**File:** `DraggablePinnedItemsGrid.kt:111` and `LauncherScreen.kt:126`  
**Severity:** Low

**Description:**  
The grid uses 4 columns hardcoded. The `columns` parameter exists but isn't exposed in settings.

**User Impact:**  
Users cannot customize grid density to their preference.

**Suggested Improvement:**
Add a settings option for grid columns (3, 4, 5).

---

### 38. Search Field Placeholder Doesn't Update Dynamically

**File:** `SearchUiState.kt:80-87`  
**Severity:** Low

**Description:**  
The placeholder text is computed but doesn't provide contextual suggestions based on installed apps or user patterns.

**User Impact:**  
Minor: Placeholders could be more helpful.

**Suggested Improvement:**
Consider showing recently searched terms or suggestions.

---

### 39. Duplicate Empty State Composables

**Files:**

- `PinnedItemsGrid.kt:95-107`
- `DraggablePinnedItemsGrid.kt:179-191`
- `SearchResultItems.kt:564-672`

**Severity:** Low

**Description:**  
There are three separate empty state implementations with different messages. This could be consolidated.

**User Impact:**  
Minor inconsistency in empty state appearance.

**Suggested Improvement:**
Create a shared `EmptyState` composable with configurable message and icon.

---

### 40. No Accessibility Semantics for Drag Operations

**File:** `DraggablePinnedItemsGrid.kt`  
**Severity:** Low

**Description:**  
Drag and drop operations have no accessibility semantics. Screen reader users cannot rearrange items.

**User Impact:**  
Users with accessibility needs cannot use drag-to-rearrange functionality.

**Suggested Improvement:**
Add semantics for draggable items and provide alternative rearrangement method (e.g., long-press menu with "Move" option).

---

### 41. Long App Names Truncated Without Tooltip

**File:** `PinnedItem.kt:201-211`  
**Severity:** Low

**Description:**  
Long app names are truncated with ellipsis, but there's no way to see the full name.

**User Impact:**  
Users cannot see full app names for similarly-named apps.

**Suggested Improvement:**
Add a tooltip on long press or show full name in the action menu.

---

### 42. No Search History

**File:** `SearchViewModel.kt` (inferred)  
**Severity:** Low

**Description:**  
There's no search history feature. Users must retype previous searches.

**User Impact:**  
Users spend more time typing repeated searches.

**Suggested Improvement:**
Store recent searches and show them when the search field is focused.

---

### 43. File Type Icons Not Consistent with System

**File:** `PinnedItem.kt:367-413`  
**Severity:** Low

**Description:**  
File type icons are custom Material icons, not matching the system's file association icons.

**User Impact:**  
Minor visual inconsistency with system file managers.

**Suggested Improvement:**
Consider loading file type icons from the system, or use Material icons consistently.

---

### 44. Settings Values No Validation

**File:** `SettingsScreen.kt:133-149`  
**Severity:** Low

**Description:**  
Slider settings accept any value in the range without validation of sensible combinations (e.g., `maxRecentApps` > `maxSearchResults`).

**User Impact:**  
Users may set confusing configurations.

**Suggested Improvement:**
Add validation and show warnings for unusual configurations.

---

### 45. App Icon Loading Has No Placeholder

**File:** `AppIcon.kt:106-108`  
**Severity:** Low

**Description:**  
The `rememberAsyncImagePainter` is used without a placeholder or error state. If icon loading fails or is slow, users see nothing.

**User Impact:**  
Brief or permanent blank space where app icons should be.

**Suggested Improvement:**

```kotlin
val painter = rememberAsyncImagePainter(
    model = AppIconRequest(packageName),
    placeholder = painterResource(R.drawable.ic_app_placeholder),
    error = painterResource(R.drawable.ic_app_error)
)
```

---

### 46. No Haptic Feedback on Settings Changes

**File:** `SettingsComponents.kt`  
**Severity:** Low

**Description:**  
Settings toggles and sliders don't provide haptic feedback on value changes.

**User Impact:**  
Minor: Settings feel less tactile.

**Suggested Improvement:**
Add light haptic feedback on switch toggles and slider value changes.

---

### 47. URL Search Result Title Format

**File:** `SearchResultItems.kt:248-254`  
**Severity:** Low

**Description:**  
URL search results show the handler app name in the title, but the format is not clearly documented. The `result.title` property determines what's shown.

**User Impact:**  
Minor: Inconsistent title formatting for different URL types.

**Suggested Improvement:**
Document the expected title format in `UrlSearchResult` and ensure consistency.

---

## Summary Table

| Category  | Count  |
| --------- | ------ |
| Critical  | 3      |
| High      | 12     |
| Medium    | 19     |
| Low       | 13     |
| **Total** | **47** |

### Issues by Type

| Type                         | Count                  |
| ---------------------------- | ---------------------- |
| Accessibility                | 6                      |
| Hardcoded Strings            | 1 (multiple instances) |
| Missing Loading/Error States | 4                      |
| Dark Mode Issues             | 1                      |
| Touch Target Issues          | 2                      |
| Missing User Feedback        | 5                      |
| Missing Animations           | 3                      |
| UX/Flow Issues               | 12                     |
| Internationalization         | 1                      |
| Minor Polish                 | 12                     |

---

## Recommendations Priority

1. **Immediate (Critical):**
   - Add loading state UI to search dialog
   - Add error state handling with retry options
   - Fix Color.White usage for wallpaper compatibility

2. **Short-term (High):**
   - Move all strings to resources
   - Add content descriptions
   - Add haptic feedback
   - Add confirmation for destructive actions
   - Fix touch target sizes

3. **Medium-term (Medium):**
   - Add dialog animations
   - Add scroll position restoration
   - Improve drag-and-drop feedback
   - Add pin status indicators

4. **Low Priority (Polish):**
   - Add tooltips for truncated text
   - Add search history
   - Add grid customization settings
   - Consolidate empty state components

---

## Conclusion

The Milki Launcher codebase demonstrates solid architecture and good separation of concerns. The design system with centralized spacing, icons, and corner radius values is well-implemented. The main areas for improvement are:

1. **Accessibility** - Content descriptions, touch targets, and haptic feedback
2. **Internationalization** - String resources
3. **Error Handling** - Loading and error states
4. **Dark Mode Support** - Hardcoded colors on home screen
5. **User Feedback** - Confirmations, undo actions, and visual feedback

Addressing these issues will significantly improve the user experience and make the app more accessible to a wider audience.
