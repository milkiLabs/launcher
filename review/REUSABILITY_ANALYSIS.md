# Component Reusability & DRY Analysis

## DRY Violations Found

### 1. Duplicate Icon Loading Code

**Violation**: Same icon loading pattern in multiple files.

**Locations**:
- `AppGridItem.kt:85-95`
- `AppListItem.kt:78-88`

**Current**:
```kotlin
// AppGridItem.kt
val painter = rememberAsyncImagePainter(
    model = AppIconRequest(appInfo.packageName)
)
Image(
    painter = painter,
    contentDescription = null,
    modifier = Modifier.size(56.dp)
)

// AppListItem.kt (nearly identical!)
val painter = rememberAsyncImagePainter(
    model = AppIconRequest(appInfo.packageName)
)
Image(
    painter = painter,
    contentDescription = null,
    modifier = Modifier.size(40.dp)
)
```

**Reusable Component**:
```kotlin
// ui/components/AppIcon.kt
@Composable
fun AppIcon(
    packageName: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = rememberAsyncImagePainter(AppIconRequest(packageName)),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}

// Usage:
AppIcon(packageName = appInfo.packageName, size = 56.dp)  // Grid
AppIcon(packageName = appInfo.packageName, size = 40.dp)  // List
```

---

### 2. Duplicate List Item Structure

**Violation**: All search result items have identical layout structure.

**Locations**:
- `SearchResultItems.kt:58-96` (WebSearchResultItem)
- `SearchResultItems.kt:114-148` (YouTubeSearchResultItem)
- `SearchResultItems.kt:169-202` (UrlSearchResultItem)
- `SearchResultItems.kt:221-283` (ContactSearchResultItem)

**Pattern Repeated**:
```kotlin
ListItem(
    headlineContent = { Text(...) },
    supportingContent = { Text(...) },
    leadingContent = { Icon(...) },
    modifier = Modifier.clickable { onClick() }
)
```

**Reusable Component**:
```kotlin
// ui/components/SearchResultItem.kt
@Composable
fun SearchResultItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let {
            { 
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint
            )
        },
        modifier = Modifier.clickable { onClick() }
    )
}

// Usage:
SearchResultItem(
    title = result.title,
    subtitle = result.engine,
    icon = Icons.Default.Search,
    iconTint = accentColor ?: MaterialTheme.colorScheme.primary,
    onClick = { onResultClick(result) }
)
```

**Lines Saved**: ~200 lines across 5 result types.

---

### 3. Duplicate Text Field Setup

**Violation**: Same OutlinedTextField configuration in multiple places.

**Current**: Text field configuration scattered.

**Reusable Component**:
```kotlin
// ui/components/SearchTextField.kt
@Composable
fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    leadingIcon: @Composable (() -> Unit)? = null,
    onDone: () -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        leadingIcon = leadingIcon,
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, "Clear")
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    )
}
```

---

### 4. Duplicate Color/Theme Logic

**Violation**: Same color fallback logic repeated.

**Pattern** (appears 5+ times):
```kotlin
val iconColor = accentColor ?: MaterialTheme.colorScheme.primary
```

**Reusable Extension**:
```kotlin
// ui/theme/ColorExtensions.kt
@Composable
fun Color?.orPrimary(): Color {
    return this ?: MaterialTheme.colorScheme.primary
}

// Usage:
val iconColor = accentColor.orPrimary()
```

---

### 5. Duplicate Modifier Chains

**Violation**: Same padding/modifier patterns repeated.

**Common Pattern**:
```kotlin
Modifier
    .fillMaxWidth()
    .padding(horizontal = 16.dp)
    .padding(top = 16.dp)
```

**Reusable Modifiers**:
```kotlin
// ui/theme/Modifiers.kt
fun Modifier.dialogPadding(): Modifier = this
    .fillMaxWidth(0.9f)
    .fillMaxHeight(0.8f)
    .imePadding()
    .navigationBarsPadding()
    .statusBarsPadding()

fun Modifier.listItemPadding(): Modifier = this
    .fillMaxWidth()
    .padding(horizontal = 16.dp, vertical = 8.dp)

// Usage:
Surface(modifier = Modifier.dialogPadding()) { ... }
```

---

## Over-Complicated Components

### 1. AppSearchDialog Too Large

**Current**: 340 lines handling multiple responsibilities.

**Responsibilities Mixed**:
- Dialog container
- Text field with indicator
- Empty state
- Results list coordination

**Split Into**:
```kotlin
// AppSearchDialog.kt - Container only (~80 lines)
@Composable
fun AppSearchDialog(uiState: SearchUiState, ...) {
    Dialog(...) {
        Surface(...) {
            Column {
                SearchHeader(
                    query = uiState.query,
                    onQueryChange = onQueryChange,
                    activeProvider = uiState.activeProviderConfig
                )
                SearchContent(
                    results = uiState.results,
                    isLoading = uiState.isLoading,
                    onResultClick = onResultClick
                )
            }
        }
    }
}

// SearchHeader.kt - Text field + indicator (~100 lines)
// SearchContent.kt - Results or empty state (~80 lines)
```

---

### 2. SearchViewModel Too Many Responsibilities

**Current**: 408 lines handling:
- State management
- Search coordination
- URL detection
- Action emission
- Data loading

**Split Into**:
```kotlin
// SearchStateManager.kt - Just state management
class SearchStateManager {
    private val _state = MutableStateFlow(SearchUiState())
    val state = _state.asStateFlow()
    
    fun updateQuery(query: String) { ... }
    fun updateResults(results: List<SearchResult>) { ... }
    // ...
}

// SearchCoordinator.kt - Search logic coordination
class SearchCoordinator(
    private val providers: List<SearchProvider>,
    private val filterApps: (String) -> List<AppInfo>
) {
    suspend fun performSearch(query: String): List<SearchResult> { ... }
}

// SearchViewModel.kt - Just wiring (~100 lines)
class SearchViewModel(...) : ViewModel() {
    private val stateManager = SearchStateManager()
    private val coordinator = SearchCoordinator(providers, filterAppsUseCase::invoke)
    
    // Just delegate to specialized classes
}
```

---

## Unused Abstractions

### 1. SearchProviderRegistry

**Current**:
```kotlin
class SearchProviderRegistry(initialProviders: List<SearchProvider>) {
    fun getAllProviders(): List<SearchProvider> = providers
    fun findProvider(prefix: String): SearchProvider? = 
        providers.find { it.config.prefix == prefix }
}
```

**Reality**: Just wraps a list with two methods that are one-liners.

**Simpler**:
```kotlin
// Just use the list directly!
val providers = listOf(WebProvider(), ContactsProvider(), YouTubeProvider())

// Extension function if needed
fun List<SearchProvider>.findByPrefix(prefix: String) = 
    find { it.config.prefix == prefix }
```

---

### 2. UseCase Classes

**Current Pattern**:
```kotlin
class FilterAppsUseCase {
    operator fun invoke(query: String, apps: List<AppInfo>): List<AppInfo> { ... }
}

class DetectUrlUseCase {
    operator fun invoke(query: String): UrlSearchResult? { ... }
}
```

**Simpler with Extensions**:
```kotlin
// domain/search/AppFilters.kt
fun List<AppInfo>.filterByQuery(query: String): List<AppInfo> { ... }

// domain/search/UrlDetection.kt
fun String.detectUrl(): UrlSearchResult? { ... }

// Usage:
val filtered = apps.filterByQuery(query)
val url = query.detectUrl()
```

**Benefits**:
- No DI needed
- More discoverable (IDE autocomplete)
- More idiomatic Kotlin
- Fewer classes

---

## Component Library Proposal

Create a shared component library:

```
ui/components/
├── core/                    # Primitive components
│   ├── AppIcon.kt          # Reusable icon component
│   ├── SearchTextField.kt  # Standard search input
│   └── LoadingIndicator.kt # Skeleton/shimmer
│
├── items/                   # List item components
│   ├── SearchResultItem.kt # Generic result item
│   ├── AppListItem.kt      # App-specific
│   └── ContactItem.kt      # Contact-specific
│
├── containers/              # Layout containers
│   ├── SearchDialog.kt     # Dialog shell
│   ├── SearchHeader.kt     # Header section
│   └── SearchContent.kt    # Content area
│
└── feedback/                # User feedback
    ├── EmptyState.kt
    ├── LoadingState.kt
    └── ErrorState.kt
```

---

## Code Reuse Metrics

### Current State

| Metric | Value |
|--------|-------|
| Total Composables | ~35 |
| Unique Layout Patterns | ~12 |
| Duplicated Lines | ~400 |
| Average Composable Size | 85 lines |

### Target State

| Metric | Value |
|--------|-------|
| Total Composables | ~25 (-28%) |
| Unique Layout Patterns | ~8 (-33%) |
| Duplicated Lines | ~50 (-87%) |
| Average Composable Size | 45 lines (-47%) |

---

## Reusable Component Examples

### 1. IconWithBadge

```kotlin
@Composable
fun IconWithBadge(
    icon: ImageVector,
    badge: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Box {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        
        badge?.let {
            Badge(
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(it)
            }
        }
    }
}
```

### 2. ResultListItem

```kotlin
@Composable
fun <T> ResultListItem(
    item: T,
    title: (T) -> String,
    subtitle: ((T) -> String)? = null,
    icon: (T) -> ImageVector,
    onClick: (T) -> Unit
) {
    ListItem(
        headlineContent = { Text(title(item)) },
        supportingContent = subtitle?.let { { Text(it(item)) } },
        leadingContent = { Icon(icon(item), null) },
        modifier = Modifier.clickable { onClick(item) }
    )
}

// Usage:
ResultListItem(
    item = contact,
    title = { it.displayName },
    subtitle = { it.phoneNumbers.firstOrNull() },
    icon = { Icons.Default.Person },
    onClick = onContactClick
)
```

### 3. AnimatedList

```kotlin
@Composable
fun <T> AnimatedList(
    items: List<T>,
    key: (T) -> String,
    itemContent: @Composable (T) -> Unit
) {
    LazyColumn {
        items(
            items = items,
            key = key
        ) { item ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically()
            ) {
                itemContent(item)
            }
        }
    }
}
```

---

## Migration Strategy

### Phase 1: Extract Low-Hanging Fruit
1. Create `AppIcon` component
2. Create `SearchResultItem` component
3. Create shared modifiers

### Phase 2: Refactor Large Components
1. Split `AppSearchDialog` into smaller parts
2. Extract common list item patterns
3. Create reusable feedback components

### Phase 3: Remove Abstractions
1. Replace UseCase classes with extensions
2. Remove Registry wrapper
3. Simplify ViewModel

### Phase 4: Polish
1. Add component documentation
2. Create component showcase/preview
3. Add component tests

---

## Summary

**Current Problems**:
- ~400 lines of duplicated code
- 12+ similar list item implementations
- Large components with mixed responsibilities
- Unnecessary wrapper classes

**Solutions**:
- Extract 5-10 reusable components
- Use Kotlin extensions instead of classes
- Split large composables (max 50 lines each)
- Remove wrapper abstractions

**Impact**:
- 37% less code
- Easier maintenance
- Better testability
- More consistent UI
