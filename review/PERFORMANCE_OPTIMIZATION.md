# Performance Analysis & Optimization Guide

## Current Performance Issues

### 1. Critical: N+1 Query Problem in ContactsRepository

**File**: `data/repository/ContactsRepositoryImpl.kt:65-121`

**Problem**: For each contact, makes 2 additional queries (phones + emails).

```kotlin
// BAD: N+1 queries
override suspend fun searchContacts(query: String): List<Contact> {
    contentResolver.query(...)?.use { cursor ->
        while (cursor.moveToNext()) {
            val contactId = cursor.getLong(idIndex)
            val phoneNumbers = getPhoneNumbers(contactId)  // Query #2
            val emails = getEmails(contactId)              // Query #3
            // ...
        }
    }
}
```

**Impact**: 
- 100 contacts = 201 database queries
- Each query: ~5-10ms
- Total: 500-1000ms (UI freezes!)

**Optimized with JOINs**:
```kotlin
override suspend fun searchContacts(query: String): List<Contact> {
    if (!hasContactsPermission()) return emptyList()
    
    val contacts = mutableMapOf<Long, ContactBuilder>()
    
    // Query 1: Get matching contacts
    val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
    contentResolver.query(
        ContactsContract.Contacts.CONTENT_URI,
        CONTACT_PROJECTION,
        selection,
        arrayOf("%$query%"),
        null
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIndex)
            contacts[id] = ContactBuilder(
                id = id,
                name = cursor.getString(nameIndex),
                // ...
            )
        }
    }
    
    // Query 2: Get ALL phone numbers for these contacts in ONE query
    if (contacts.isNotEmpty()) {
        val contactIds = contacts.keys.joinToString(",") 
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            PHONE_PROJECTION,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN ($contactIds)",
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(phoneContactIdIndex)
                contacts[contactId]?.phoneNumbers?.add(cursor.getString(numberIndex))
            }
        }
    }
    
    // Query 3: Get ALL emails in ONE query (similar pattern)
    
    return contacts.values.map { it.build() }
}
```

**Impact**: 100 contacts = 3 queries total (~30ms). 33x faster!

---

### 2. High: No Search Cancellation

**File**: `presentation/search/SearchViewModel.kt:267-280`

**Problem**: Old searches can overwrite new results.

```kotlin
// BAD: No cancellation
viewModelScope.launch {
    val results = parsed.provider.search(parsed.query)  // Slow network
    updateState { copy(results = results) }  // Might overwrite newer search!
}
```

**Fix with `async` + `await` pattern**:
```kotlin
private var searchJob: Job? = null

private fun performSearch(query: String) {
    // Cancel previous search
    searchJob?.cancel()
    
    searchJob = viewModelScope.launch {
        try {
            val results = withContext(Dispatchers.IO) {
                parsed.provider.search(parsed.query)
            }
            
            // Only update if not cancelled
            if (isActive) {
                updateState { copy(results = results, isLoading = false) }
            }
        } catch (e: CancellationException) {
            // Expected when cancelled, ignore
        }
    }
}
```

**Or use Flow**:
```kotlin
private val searchQueryFlow = MutableStateFlow("")

init {
    searchQueryFlow
        .debounce(150)
        .distinctUntilChanged()
        .flatMapLatest { query ->  // Auto-cancels previous!
            flow {
                emit(SearchUiState(isLoading = true))
                val results = performSearch(query)
                emit(SearchUiState(results = results, isLoading = false))
            }
        }
        .flowOn(Dispatchers.IO)
        .launchIn(viewModelScope)
}
```

**Benefit**: Old searches auto-cancel, no race conditions.

---

### 3. High: Image Loading Not Optimized

**File**: `AppIconFetcher.kt` + `AppGridItem.kt`

**Current**: Loads icons at full resolution.

```kotlin
// AppGridItem.kt
Image(
    painter = rememberAsyncImagePainter(AppIconRequest(appInfo.packageName)),
    modifier = Modifier.size(56.dp)  // But loads full-res icon!
)
```

**Problem**: Icons loaded at 512×512 but displayed at 56×56dp.

**Fix**: Add size hint to Coil request.

```kotlin
// Updated AppIconRequest
data class AppIconRequest(
    val packageName: String,
    val size: Int = 128  // Request smaller icon
)

// AppIconFetcher.kt
override suspend fun fetch(): FetchResult {
    val drawable = if (data.size > 0) {
        // Use getApplicationIcon with size hint (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationIcon(
                data.packageName,
                data.size,
                false  // don't allow bigger
            )
        } else {
            pm.getApplicationIcon(data.packageName)
        }
    } else {
        pm.getApplicationIcon(data.packageName)
    }
    // ...
}
```

**Alternative**: Use Coil's resize:
```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(AppIconRequest(appInfo.packageName))
        .size(128)  // Resize to 128px
        .build(),
    modifier = Modifier.size(56.dp),
    contentDescription = null
)
```

**Benefit**: ~4x less memory per icon (512² → 128² = 16x pixel reduction).

---

### 4. Medium: Unnecessary Recompositions

**File**: `ui/components/AppSearchDialog.kt`

**Problem**: Entire dialog recomposes on every keystroke.

```kotlin
@Composable
fun AppSearchDialog(uiState: SearchUiState, ...) {
    // This entire function runs on every keystroke!
}
```

**Fix**: Use `derivedStateOf` for expensive calculations.

```kotlin
@Composable
fun AppSearchDialog(uiState: SearchUiState, ...) {
    // Only recalculate when results actually change
    val allAppResults by remember(uiState.results) {
        derivedStateOf {
            uiState.results.all { it is AppSearchResult }
        }
    }
    
    // Only recalculate filtered apps when needed
    val appResults by remember(uiState.results) {
        derivedStateOf {
            uiState.results.filterIsInstance<AppSearchResult>()
        }
    }
    
    // ... rest of composable
}
```

**Better Fix**: Split into smaller composables.

```kotlin
@Composable
fun AppSearchDialog(uiState: SearchUiState, ...) {
    // State hoisting - pass only what's needed
    SearchResults(
        results = uiState.results,
        onResultClick = onResultClick
    )
}

@Composable
private fun SearchResults(
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit
) {
    // This only recomposes when results change, not query
    val allAppResults = results.all { it is AppSearchResult }
    
    if (allAppResults) {
        AppResultsGrid(results, onResultClick)
    } else {
        MixedResultsList(results, onResultClick)
    }
}
```

---

### 5. Medium: No Lazy Loading for App List

**File**: `data/repository/AppRepositoryImpl.kt:90-126`

**Problem**: Loads ALL apps at startup (200+ apps).

```kotlin
// Loads everything at once
override suspend fun getInstalledApps(): List<AppInfo> {
    val resolveInfos = pm.queryIntentActivities(mainIntent, 0)  // 200+ items
    // ... process all
}
```

**Fix**: Paginate or lazy load.

```kotlin
// Return Flow for progressive loading
override fun getInstalledApps(): Flow<List<AppInfo>> = flow {
    val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
    val batchSize = 50
    var batch = mutableListOf<AppInfo>()
    
    resolveInfos.forEachIndexed { index, resolveInfo ->
        batch.add(createAppInfo(resolveInfo))
        
        // Emit every 50 apps
        if (batch.size >= batchSize || index == resolveInfos.lastIndex) {
            emit(batch.toList())
            batch = mutableListOf()
        }
    }
}.flowOn(Dispatchers.IO)

// In ViewModel
init {
    viewModelScope.launch {
        appRepository.getInstalledApps()
            .collect { batch ->
                // UI updates progressively
                updateState { 
                    copy(installedApps = installedApps + batch) 
                }
            }
    }
}
```

**Benefit**: UI shows first 50 apps in 100ms instead of waiting for all 200.

---

## Memory Optimizations

### 1. Icon Memory Cache Configuration

**File**: `LauncherApplication.kt`

**Current**:
```kotlin
.memoryCache {
    MemoryCache.Builder(this)
        .maxSizePercent(0.15)  // 15% of memory
        .build()
}
```

**Better**: Limit by item count, not just size.

```kotlin
.memoryCache {
    MemoryCache.Builder(this)
        .maxSizePercent(0.10)  // Reduce to 10%
        .build()
}

// Or use Coil's default (automatic based on device)
```

**For Low-End Devices**:
```kotlin
val memoryPercent = if (isLowRamDevice()) 0.05 else 0.10

.memoryCache {
    MemoryCache.Builder(this)
        .maxSizePercent(memoryPercent)
        .build()
}
```

---

### 2. Contact Photo Loading

**File**: `ContactsRepositoryImpl.kt`

**Problem**: Photo URIs loaded but not used efficiently.

**Current**: Store full photo URI string for every contact.

```kotlin
data class Contact(
    val photoUri: String?,  // Can be long data URI
    // ...
)
```

**Optimized**: Don't store in Contact, load on-demand.

```kotlin
data class Contact(
    val id: Long,
    // No photoUri here
) {
    // Generate URI when needed
    val photoUri: Uri?
        get() = if (hasPhoto) {
            ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI,
                id
            )
        } else null
}
```

---

## Battery Optimizations

### 1. Debounce Search (Already Recommended)

Saves battery by reducing searches during typing.

**Impact**: Typing "calculator" (10 chars) = 1 search instead of 10.

---

### 2. Throttle Repository Updates

**File**: `AppRepositoryImpl.kt:141-171`

**Problem**: `getRecentApps()` emits on every DataStore change.

```kotlin
override fun getRecentApps(): Flow<List<AppInfo>> {
    return application.dataStore.data  // Emits on every change!
        .map { preferences ->
            // Expensive mapping!
        }
}
```

**Fix**: Throttle updates.

```kotlin
override fun getRecentApps(): Flow<List<AppInfo>> {
    return application.dataStore.data
        .sample(500)  // Only emit every 500ms max
        .map { preferences ->
            // Mapping
        }
        .distinctUntilChanged()  // Don't emit if same
}
```

---

### 3. Batch Recent App Saves

**File**: `AppRepositoryImpl.kt:188-207`

**Current**: Saves to DataStore immediately on every app launch.

```kotlin
override suspend fun saveRecentApp(packageName: String) {
    dataStore.edit { preferences ->
        // ... saves immediately
    }
}
```

**Optimized**: Batch saves.

```kotlin
class AppRepositoryImpl(...) : AppRepository {
    private val saveQueue = Channel<String>(Channel.CONFLATED)
    
    init {
        // Batch processor
        viewModelScope.launch {
            saveQueue.consumeAsFlow()
                .debounce(1000)  // Wait 1 second
                .collect { packageName ->
                    actuallySaveRecentApp(packageName)
                }
        }
    }
    
    override suspend fun saveRecentApp(packageName: String) {
        saveQueue.send(packageName)  // Just queue it
    }
}
```

**Benefit**: Rapid app switching only triggers 1 save, not N.

---

## Profiling Checklist

### Use Android Studio Profiler

1. **CPU Profiler**
   - [ ] Search while typing: Should see debounce in action
   - [ ] Load contacts: Should see only 3 queries, not N+1
   - [ ] Scroll app grid: Should be 60fps

2. **Memory Profiler**
   - [ ] Open search dialog: Memory should stabilize quickly
   - [ ] Scroll apps: Memory shouldn't spike
   - [ ] Close search: Memory should decrease (GC)

3. **Network Profiler**
   - [ ] Web searches: Should see actual network calls
   - [ ] No unexpected calls for app loading

### Performance Targets

| Operation | Target | Current (Est.) |
|-----------|--------|----------------|
| Dialog Open → Show | 100ms | 300ms |
| Search Typing | 16ms (60fps) | 50ms |
| App Launch | 50ms | 80ms |
| Contacts Search | 100ms | 800ms |
| Memory Usage | <50MB | ~80MB |

---

## Quick Wins Summary

1. **Fix N+1 queries** in ContactsRepository (biggest impact)
2. **Add search cancellation** to prevent race conditions
3. **Debounce search** input (150ms)
4. **Throttle DataStore** updates (500ms)
5. **Optimize icon loading** with size hints
6. **Split composables** to reduce recompositions

**Estimated Impact**:
- Contacts search: 800ms → 100ms (8× faster)
- Typing: 50ms → 16ms (3× smoother)
- Memory: 80MB → 50MB (37% less)
