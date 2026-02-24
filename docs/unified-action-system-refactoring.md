# Unified Action System Refactoring

This document describes the refactoring from callback-based action handling to a unified action system using CompositionLocal.

## Overview

The launcher has been refactored to use a **Unified Permission-Aware Action System** that eliminates callback prop drilling and provides a cleaner architecture for handling user actions.

## Before: Callback Propagation

Previously, callbacks were passed through multiple UI layers:

```
MainActivity → LauncherScreen → AppSearchDialog → SearchResultsList → MixedResultsList → ContactSearchResultItem
```

Each layer needed to:
- Accept callbacks as parameters
- Pass them to child components
- Handle nullable cases

**Problems:**
- Tight coupling between all layers
- Adding new actions required modifying 5+ files
- Lambda recreation on every recomposition
- Inconsistent permission handling patterns

## After: CompositionLocal with ActionExecutor

Now, actions are handled through:
1. `SearchResultAction` - A sealed class representing all possible actions
2. `ActionExecutor` - A centralized executor that handles actions and permissions
3. `LocalSearchActionHandler` - A CompositionLocal that provides the action handler to all composables

```
┌─────────────────┐
│ UI Component    │
│ (emits action)  │
└────────┬────────┘
         │ SearchResultAction
         ▼
┌─────────────────┐
│ ActionExecutor  │
│ (handles action)│
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ System/Action   │
│ (execute intent)│
└─────────────────┘
```

## Key Files

### New Files

| File | Purpose |
|------|---------|
| `SearchResultAction.kt` | Sealed class for all search result actions |
| `ActionExecutor.kt` | Executes actions with permission handling |
| `LocalSearchActionHandler.kt` | CompositionLocal for action handling |

### Modified Files

| File | Changes |
|------|---------|
| `MainActivity.kt` | Creates ActionExecutor, provides via CompositionLocal |
| `LauncherScreen.kt` | Removed callback parameters |
| `AppSearchDialog.kt` | Uses LocalSearchActionHandler |
| `SearchResultsList.kt` | Uses LocalSearchActionHandler |
| `PermissionHandler.kt` | Added hasPermission(), onCallPermissionResult callback |

### Files to Remove (Deprecated)

| File | Status |
|------|--------|
| `SearchAction.kt` | Replaced by SearchResultAction.kt |
| `ActionHandler.kt` | Replaced by ActionExecutor.kt |

## Action Types

```kotlin
sealed class SearchResultAction {
    // Tap the main area of a result
    data class Tap(val result: SearchResult) : SearchResultAction()
    
    // Tap the dial icon on a contact (direct call)
    data class DialContact(val contact: Contact, val phoneNumber: String) : SearchResultAction()
    
    // Open URL in browser (bypass deep link)
    data class OpenUrlInBrowser(val url: String) : SearchResultAction()
    
    // Request permission
    data class RequestPermission(val permission: String, val providerPrefix: String) : SearchResultAction()
}
```

## Permission Handling Flow

```
1. UI emits SearchResultAction.DialContact
2. ActionExecutor.execute() checks permission
3. If granted: execute immediately
4. If not granted:
   a. Store pending action
   b. Request permission via callback
   c. MainActivity calls PermissionHandler.requestCallPermission()
5. Permission result:
   a. PermissionHandler callback fires
   b. MainActivity calls ActionExecutor.onPermissionResult()
   c. If granted, execute pending action
```

## Usage Example

### In MainActivity

```kotlin
setContent {
    CompositionLocalProvider(
        LocalSearchActionHandler provides { action: SearchResultAction ->
            actionExecutor.execute(action, permissionHandler::hasPermission)
        }
    ) {
        LauncherTheme {
            LauncherScreen(uiState, ...)
        }
    }
}
```

### In UI Components

```kotlin
@Composable
fun ContactSearchResultItem(result: ContactSearchResult, ...) {
    val actionHandler = LocalSearchActionHandler.current
    
    // On item click
    onClick = { actionHandler(SearchResultAction.Tap(result)) }
    
    // On dial icon click
    onDialClick = {
        val phone = result.contact.phoneNumbers.firstOrNull()
        if (phone != null) {
            actionHandler(SearchResultAction.DialContact(result.contact, phone))
        }
    }
}
```

## Benefits

| Aspect | Before | After |
|--------|--------|-------|
| Callback parameters | 5+ per component | 0-1 parameters |
| Permission patterns | 2 different patterns | 1 unified pattern |
| Adding new action | Modify 5+ files | Modify 1-2 files |
| Lambda recreation | Every recomposition | Once per action |
| Testability | Hard (mock many callbacks) | Easy (single executor) |

## Migration Checklist

- [x] Create `SearchResultAction.kt`
- [x] Create `ActionExecutor.kt`
- [x] Create `LocalSearchActionHandler.kt`
- [x] Add CompositionLocalProvider in MainActivity
- [x] Migrate `ContactSearchResultItem`
- [x] Migrate all other result items
- [x] Remove callback parameters from components
- [x] Remove callback parameters from `SearchResultsList`
- [x] Remove callback parameters from `AppSearchDialog`
- [x] Remove callback parameters from `LauncherScreen`
- [ ] Remove old `SearchAction.kt` (keep for reference)
- [ ] Remove old `ActionHandler.kt` (keep for reference)
- [ ] Clean up `SearchViewModel.kt` (remove action flow)
- [ ] Update documentation
