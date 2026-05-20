# Code Duplication Report - Milki Launcher Android App

**Date:** 2026-05-20
**Tool:** jscpd v4.2.3 (configured to ignore import-only duplicates)
**Scope:** `app/src/main/java/` (235 Kotlin files, ~35,550 lines)
**Status:** ALL 4 REAL DUPLICATES RESOLVED

---

## Executive Summary

jscpd identified **8 clone groups**. After filtering out import-only noise, **4 real code duplications** were found and **all have been refactored**.

| # | Files | Lines Duplicated | Type | Status |
|---|-------|------------------|------|--------|
| 1 | `AppGridItem.kt` / `AppListItem.kt` | 22 | UI component pattern | RESOLVED |
| 2 | `SettingsMutationStore.kt` / `SettingsPreferenceWriter.kt` | 25 | Write functions | RESOLVED |
| 3 | `ActionExecutor.kt` (self) | 19 | Dial handlers | RESOLVED |
| 4 | `AppLauncher.kt` / `AppContextDataCache.kt` | 10 | Shortcut mapping | RESOLVED |

**Lines saved after refactoring: ~66 lines**
**New files created: 2** (`SettingsPreferenceWriteSupport.kt`, extension in `ShortcutInfoComparator.kt`)

---

## Post-Refactor jscpd Results

After refactoring, jscpd reports only **4 remaining clones** -- all are import-only noise (not actionable):

1. `SettingsPrefixEditorComponents.kt` / `SettingsSourceEditorComponents.kt` - imports only
2. `PinnedItem.kt` / `FolderIcon.kt` - imports only
3. `ItemActionMenu.kt` / `PopupWidgetView.kt` - imports only
4. `DraggablePinnedItemsGridLayers.kt` / `DropHighlightLayer.kt` - imports only

**0 real code duplicates remain.**

---

## Resolved Clones

### Clone 1: ItemContextMenu Block (RESOLVED)

**Files:**
- `ui/components/common/AppGridItem.kt`
- `ui/components/common/AppListItem.kt`

**Solution:** Created `AppItemContextMenu` composable in `ItemContextMenuState.kt` that bundles the standard uninstall action and wires up menu state. Both `AppGridItem` and `AppListItem` now use this single composable.

**Before (each file):**
```kotlin
ItemContextMenu(
    packageName = appInfo.packageName,
    appName = appInfo.name,
    expanded = menuState.showMenu,
    onDismiss = menuState::dismiss,
    focusable = menuState.isMenuFocusable,
    onExternalDragStarted = {
        menuState.dismiss()
        onExternalDragStarted()
    },
    extraActions = listOf(
        createUninstallAppAction(
            packageName = appInfo.packageName,
            actionHandler = LocalSearchActionHandler.current
        )
    )
)
```

**After (each file):**
```kotlin
AppItemContextMenu(
    appInfo = appInfo,
    menuState = menuState,
    onExternalDragStarted = onExternalDragStarted
)
```

---

### Clone 2: Write Functions (RESOLVED)

**Files:**
- `data/repository/settings/SettingsMutationStore.kt`
- `data/repository/settings/SettingsPreferenceWriter.kt`

**Solution:** Created `SettingsPreferenceWriteSupport.kt` with extension functions on `MutablePreferences`:
- `MutablePreferences.writePrefixConfigurations(configurations)`
- `MutablePreferences.writeSearchSources(sources)`

Both classes now use these shared extension functions instead of duplicating the write logic.

**New file:** `data/repository/settings/SettingsPreferenceWriteSupport.kt`

---

### Clone 3: Dial Handlers (RESOLVED)

**File:** `presentation/search/ActionExecutor.kt`

**Solution:** Extracted a single `executeDirectCall(phoneNumber: String)` private method. Both `handleDialContact` and `handleDialPhoneNumber` now delegate to it.

**Before:** Two nearly identical 17-line methods
**After:** Two 1-line delegators + one 17-line implementation

---

### Clone 4: Shortcut Mapping (RESOLVED)

**Files:**
- `core/intent/AppLauncher.kt`
- `data/contextmenu/AppContextDataCache.kt`

**Solution:** Added `ShortcutInfo.toAppShortcut()` extension function in `core/shortcut/ShortcutInfoComparator.kt`. Both files now use this extension instead of repeating the mapping pipeline.

**Before:**
```kotlin
.map { shortcut ->
    HomeItem.AppShortcut.fromShortcutInfo(
        packageName = shortcut.`package`,
        shortcutId = shortcut.id,
        shortLabel = shortcut.shortLabel?.toString().orEmpty(),
        longLabel = shortcut.longLabel?.toString() ?: shortcut.shortLabel?.toString().orEmpty()
    )
}
```

**After:**
```kotlin
.map { it.toAppShortcut() }
```

---

## Additional Observations

### Patterns Worth Consolidating (not flagged by jscpd but structurally similar)

1. **Settings mutation patterns** in `SettingsMutationStore.kt`: Multiple methods follow the same read-validate-write pattern. A generic `mutateSearchSources` helper could reduce boilerplate.

2. **Toast + Log error handling** in `ActionExecutor.kt`: Many handlers use the same `try/catch` with `Log.w` + `Toast.makeText` pattern. A helper like `runCatchingWithToast(context, TAG) { ... }` could reduce repetition.

3. **Intent creation pattern**: Multiple places create intents with `Intent.FLAG_ACTIVITY_NEW_TASK` and similar try/catch blocks.

---

## Recommended Refactoring Order

1. **Clone 2 (Settings write functions)** - Highest impact, cleanest extraction
2. **Clone 3 (Dial handlers)** - Simplest, single-file change
3. **Clone 4 (Shortcut mapping)** - Small but improves consistency
4. **Clone 1 (ItemContextMenu)** - Requires careful UI testing

---

## jscpd Configuration Used

```json
{
  "minTokens": 80,
  "minLines": 8,
  "maxLines": 500,
  "maxSize": "50kb",
  "threshold": 30,
  "ignore": [
    "**/build/**", "**/.gradle/**", "**/node_modules/**",
    "**/generated/**", "**/R.java", "**/BuildConfig.java",
    "**/test/**", "**/androidTest/**", "**/*.xml", "**/res/**"
  ]
}
```

Import-only clones were filtered by setting `minTokens: 80` (import blocks typically score below 80 tokens of actual code logic).

---

## Raw jscpd Reports

- JSON: `review/jscpd/jscpd-report.json`
- Markdown: `review/jscpd/jscpd-report.md`
