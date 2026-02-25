# Home Screen Shortcuts

This document describes the home screen shortcuts feature that allows users to pin apps and files to the launcher home screen for quick access.

## Overview

The launcher supports pinning items to the home screen, similar to how traditional Android launchers work. Users can:

1. **Pin apps** - Long-press any app in search results and select "Pin to home"
2. **Pin files** - Long-press any file in search results and select "Pin to home"
3. **Remove items** - Long-press any pinned item on the home screen and select "Remove"

## Architecture

### Unified Action System

All user actions (tap, long-press menu actions) flow through a single unified `SearchResultAction` system:

```
┌─────────────────┐
│ UI Component    │
│ (emits action)  │
└────────┬────────┘
         │ SearchResultAction
         ▼
┌─────────────────┐
│ ActionExecutor  │
│ (handles all)   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Repository/     │
│ System Intent   │
└─────────────────┘
```

This unified approach provides:
- Consistent action handling across all UI components
- Single source of truth for side effects
- Easy to add new actions
- Better testability

### Action Types

```kotlin
sealed class SearchResultAction {
    // Tap actions
    data class Tap(val result: SearchResult) : SearchResultAction()
    data class DialContact(val contact: Contact, val phoneNumber: String) : SearchResultAction()
    data class OpenUrlInBrowser(val url: String) : SearchResultAction()
    
    // Pin actions
    data class PinApp(val appInfo: AppInfo) : SearchResultAction()
    data class PinFile(val file: FileDocument) : SearchResultAction()
    data class UnpinItem(val itemId: String) : SearchResultAction()
    
    // App actions
    data class OpenAppInfo(val packageName: String) : SearchResultAction()
    
    // Permission actions
    data class RequestPermission(val permission: String, val providerPrefix: String) : SearchResultAction()
}
```

### Data Model

Items that can be pinned are represented by the `HomeItem` sealed class:

```kotlin
sealed class HomeItem {
    abstract val id: String
    
    data class PinnedApp(...) : HomeItem()
    data class PinnedFile(...) : HomeItem()
    data class AppShortcut(...) : HomeItem()
}
```

### Key Files

| File | Purpose |
|------|---------|
| `SearchResultAction.kt` | Unified action types for all user interactions |
| `ActionExecutor.kt` | Central handler for all actions |
| `HomeItem.kt` | Data model for pinned items |
| `HomeRepository.kt` | Repository interface |
| `HomeRepositoryImpl.kt` | DataStore persistence |
| `HomeViewModel.kt` | Home screen state management |
| `ItemActionMenu.kt` | Dropdown menu component |

## User Flow

### Pinning an App

```
1. User opens search dialog (press home button)
2. User searches for an app
3. User long-presses the app in results
4. Action menu appears with "Pin to home" and "App info"
5. User taps "Pin to home"
6. SearchResultAction.PinApp is emitted
7. ActionExecutor handles the action
8. App appears on home screen grid
```

## Refactoring Notes

### What Changed

1. **Unified Action System**: Previously had two separate action systems:
   - `SearchResultAction` for search result clicks
   - `LocalPinAction` for pinning items
   
   Now all actions use `SearchResultAction`.

2. **Simplified ItemActionMenu**: 
   - Changed from callback-based to action-based
   - Uses `MenuAction` with `SearchResultAction` instead of callbacks
   - Removed duplicate `createAppActions`/`createFileActions` functions
   - Single `createPinAction` function for all item types

3. **Removed LocalPinAction**: No longer needed since ActionExecutor handles pin actions.

4. **Consolidated in ActionExecutor**: All side effects (pinning, launching, opening settings) are handled in one place.

### Benefits

- **Less code duplication**: Single action handling pattern
- **Easier to maintain**: Add new actions in one place
- **Better testability**: ActionExecutor can be tested independently
- **Consistent UX**: Same behavior across all components
