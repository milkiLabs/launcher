# Future Refactoring: Unified Permission-Aware Action System

This document outlines a significant architectural improvement to simplify callback propagation and permission handling across the app.

## Current Problems

### 1. Callback Propagation (Prop Drilling)

The `onDialClick` callback passes through 5 UI layers:

```
MainActivity → LauncherScreen → AppSearchDialog → SearchResultsList → MixedResultsList → ContactSearchResultItem
```

Each layer must:
- Accept the callback as a parameter
- Pass it to the next layer
- Handle nullable cases

**Problems:**
- Tight coupling between all layers
- Adding new actions requires modifying all layers
- Easy to forget a layer when refactoring
- Lambdas recreated on every recomposition

### 2. Multiple Permission Patterns

Two different patterns exist:

| Permission | Pattern | Location |
|------------|---------|----------|
| `READ_CONTACTS` | `PermissionRequestResult` in search results | Data layer |
| `CALL_PHONE` | Pending action + permission request | ViewModel layer |

**Problems:**
- Inconsistent behavior
- Hard to understand for new developers
- Each permission adds complexity in different places

### 3. Lambda Recreation

The dial click lambda is recreated on every recomposition:

```kotlin
onDialClick = onDialClick?.let { callback ->
    {
        val phone = result.contact.phoneNumbers.firstOrNull()
        if (phone != null) {
            callback(result.contact, phone)
        }
    }
}
```

**Problems:**
- Unnecessary allocations
- Potential for subtle bugs
- Not idiomatic Compose

---

## Proposed Solution

### Phase 1: Single Action Handler Pattern

Replace multiple callbacks with a single `SearchResultAction` sealed class:

```kotlin
// SearchResultAction.kt

/**
 * Sealed class representing all possible actions from search results.
 * 
 * This unified approach eliminates callback prop drilling by using
 * a single action handler that processes all result interactions.
 */
sealed class SearchResultAction {
    /**
     * User tapped the main area of a search result.
     */
    data class Tap(val result: SearchResult) : SearchResultAction()
    
    /**
     * User tapped the dial icon on a contact result.
     * Makes a direct call (requires CALL_PHONE permission).
     */
    data class DialContact(val contact: Contact, val phoneNumber: String) : SearchResultAction()
    
    /**
     * User tapped a secondary action on a URL result.
     * Opens in browser instead of handler app.
     */
    data class OpenInBrowser(val url: String) : SearchResultAction()
}
```

### Phase 2: Permission-Aware Action System

Create a base class for actions that require permissions:

```kotlin
// PermissionAwareAction.kt

/**
 * Base class for actions that require runtime permissions.
 * 
 * This unifies permission handling across all actions that need it,
 * providing a consistent pattern for:
 * - Checking permissions
 * - Storing pending actions
 * - Executing after permission grant
 */
sealed class PermissionAwareAction {
    /**
     * The Android permission required to execute this action.
     * Null if no permission is required.
     */
    abstract val requiredPermission: String?
    
    /**
     * Whether this action can proceed without the permission.
     * If false, the action will be cancelled if permission is denied.
     */
    open val canProceedWithoutPermission: Boolean = false
    
    /**
     * Execute this action. Called when permissions are granted
     * or if canProceedWithoutPermission is true.
     */
    abstract suspend fun execute(context: Context)
    
    /**
     * Execute a fallback action if permission is denied
     * and canProceedWithoutPermission is false.
     */
    open suspend fun onPermissionDenied(context: Context) {}
}
```

### Phase 3: Unified Action Executor

Create a single executor that handles all actions:

```kotlin
// ActionExecutor.kt

/**
 * Executes search result actions with permission handling.
 * 
 * This class centralizes all action execution logic:
 * - Permission checking
 * - Pending action storage
 * - Action execution
 * - Fallback handling
 */
class ActionExecutor(
    private val context: Context,
    private val permissionHandler: PermissionHandler,
    private val searchViewModel: SearchViewModel
) {
    /**
     * Pending action waiting for permission.
     * Stored when user triggers an action that requires permission.
     */
    private var pendingAction: PermissionAwareAction? = null
    
    /**
     * Execute an action, handling permissions if needed.
     */
    fun execute(action: SearchResultAction) {
        when (action) {
            is SearchResultAction.Tap -> handleTap(action.result)
            is SearchResultAction.DialContact -> handleDialContact(action)
            is SearchResultAction.OpenInBrowser -> handleOpenInBrowser(action.url)
        }
    }
    
    private fun handleDialContact(action: SearchResultAction.DialContact) {
        val permissionAware = DialContactAction(action.contact, action.phoneNumber)
        
        if (permissionHandler.hasPermission(permissionAware.requiredPermission!!)) {
            // Permission granted, execute immediately
            executePermissionAware(permissionAware)
        } else {
            // Store pending and request permission
            pendingAction = permissionAware
            permissionHandler.requestPermission(permissionAware.requiredPermission)
        }
    }
    
    /**
     * Called when a permission result is received.
     */
    fun onPermissionResult(granted: Boolean) {
        val action = pendingAction
        pendingAction = null
        
        if (granted && action != null) {
            executePermissionAware(action)
        } else if (action != null && action.canProceedWithoutPermission) {
            // Try fallback
            action.onPermissionDenied(context)
        }
    }
    
    private fun executePermissionAware(action: PermissionAwareAction) {
        // Use coroutine scope to execute
        CoroutineScope(Dispatchers.Main).launch {
            action.execute(context)
        }
    }
}
```

### Phase 4: CompositionLocal for Action Handler

Avoid prop drilling entirely using CompositionLocal:

```kotlin
// LocalSearchActionHandler.kt

/**
 * CompositionLocal for search action handling.
 * 
 * Provides the action handler to all composables without prop drilling.
 * Any composable can call LocalSearchActionHandler.current to get
 * the handler and execute actions.
 */
val LocalSearchActionHandler = compositionLocalOf<((SearchResultAction) -> Unit)> {
    error("No SearchActionHandler provided")
}

// Usage in MainActivity
setContent {
    LauncherTheme {
        CompositionLocalProvider(LocalSearchActionHandler provides actionExecutor::execute) {
            LauncherScreen(
                uiState = uiState,
                onShowSearch = { searchViewModel.showSearch() },
                onQueryChange = { searchViewModel.onQueryChange(it) },
                onDismissSearch = { searchViewModel.hideSearch() }
                // No onResultClick or onDialClick needed!
            )
        }
    }
}

// Usage in ContactSearchResultItem
@Composable
fun ContactSearchResultItem(result: ContactSearchResult, accentColor: Color?) {
    val actionHandler = LocalSearchActionHandler.current
    
    // ...
    Icon(
        imageVector = Icons.Default.Call,
        modifier = Modifier.clickable {
            val phone = result.contact.phoneNumbers.firstOrNull()
            if (phone != null) {
                actionHandler(SearchResultAction.DialContact(result.contact, phone))
            }
        }
    )
}
```

---

## Implementation Order

### Step 1: Create Action Types (Low Risk)
1. Create `SearchResultAction.kt` sealed class
2. Create `PermissionAwareAction.kt` base class
3. No changes to existing code yet

### Step 2: Create Action Executor (Medium Risk)
1. Create `ActionExecutor.kt`
2. Implement permission handling
3. Write unit tests for executor

### Step 3: Create CompositionLocal (Low Risk)
1. Create `LocalSearchActionHandler.kt`
2. Add provider in MainActivity
3. No changes to children yet

### Step 4: Migrate Components One by One (Medium Risk)
1. Start with `ContactSearchResultItem`
2. Remove `onDialClick` parameter
3. Use `LocalSearchActionHandler.current` instead
4. Test thoroughly
5. Repeat for other components

### Step 5: Clean Up (Low Risk)
1. Remove unused callback parameters
2. Remove `pendingDirectCall` from `SearchUiState`
3. Update documentation

---

## Benefits

| Aspect | Before | After |
|--------|--------|-------|
| Callback parameters | 5+ parameters per component | 0-1 parameters |
| Permission patterns | 2 different patterns | 1 unified pattern |
| Adding new action | Modify 5+ files | Modify 1-2 files |
| Lambda recreation | Every recomposition | Once per action |
| Testability | Hard (mock many callbacks) | Easy (single executor) |
| Code clarity | Prop drilling | Clear action flow |

---

## Migration Checklist

- [ ] Create `SearchResultAction.kt`
- [ ] Create `PermissionAwareAction.kt`
- [ ] Create `ActionExecutor.kt`
- [ ] Create `LocalSearchActionHandler.kt`
- [ ] Add CompositionLocalProvider in MainActivity
- [ ] Migrate `ContactSearchResultItem`
- [ ] Migrate `UrlSearchResultItem` (for browser fallback)
- [ ] Migrate `PermissionRequestItem`
- [ ] Migrate all other result items
- [ ] Remove callback parameters from components
- [ ] Remove callback parameters from `SearchResultsList`
- [ ] Remove callback parameters from `AppSearchDialog`
- [ ] Remove callback parameters from `LauncherScreen`
- [ ] Remove `pendingDirectCall` from `SearchUiState`
- [ ] Remove `onDialClick` from `SearchViewModel`
- [ ] Update tests
- [ ] Update documentation

---

## Estimated Effort

| Phase | Time | Risk |
|-------|------|------|
| Step 1: Create Action Types | 1-2 hours | Low |
| Step 2: Create Action Executor | 3-4 hours | Medium |
| Step 3: Create CompositionLocal | 30 min | Low |
| Step 4: Migrate Components | 4-6 hours | Medium |
| Step 5: Clean Up | 1-2 hours | Low |
| **Total** | **10-15 hours** | **Medium** |

---

## Notes

- This refactoring should be done in a separate branch
- Each step should be tested before moving to the next
- Consider writing migration tests to ensure behavior is preserved
- Document any edge cases discovered during migration
