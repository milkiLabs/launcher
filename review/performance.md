## 3. Threading Issues

### 3.1 ActionExecutor Uses Custom CoroutineScope

**Severity: MEDIUM**  
**File:** `app/src/main/java/com/milki/launcher/presentation/search/ActionExecutor.kt:50`

**Issue:**
ActionExecutor creates its own `CoroutineScope(SupervisorJob() + Dispatchers.IO)` instead of using structured concurrency.

**Current Code:**

```kotlin
class ActionExecutor(
    // ...
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

**Problems:**

- Scope is not tied to any lifecycle
- Potential for memory leaks if activities are destroyed
- Operations continue even after user leaves the app
- Testing is harder with uncontrolled scope

**Suggested Fix:**
Accept a `CoroutineScope` as a parameter or use `viewModelScope`:

```kotlin
class ActionExecutor(
    private val context: Context,
    private val contactsRepository: ContactsRepository,
    private val homeRepository: HomeRepository,
    private val scope: CoroutineScope // Inject scope
) {
    // Use injected scope
}
```

Or make the methods suspend and let the caller decide the scope:

```kotlin
suspend fun handlePinApp(action: SearchResultAction.PinApp) {
    val pinnedApp = HomeItem.PinnedApp.fromAppInfo(action.appInfo)
    homeRepository.addPinnedItem(pinnedApp)
}
```

---

### 3.2 Missing flowOn in Some Repository Methods

**Severity: MEDIUM**  
**File:** `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:86-96`

**Issue:**
The `pinnedItems` Flow does not specify a dispatcher, relying on DataStore's default behavior.

**Current Code:**

```kotlin
override val pinnedItems: Flow<List<HomeItem>> = context.homeDataStore.data
    .catch { exception ->
        if (exception is IOException) {
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }
    .map { preferences ->
        deserializeItems(preferences)
    }
```

**Note:** DataStore internally handles I/O dispatching correctly, so this is **not a critical issue**. However, being explicit is better for code clarity.

**Suggested Addition:**

```kotlin
.map { preferences ->
    deserializeItems(preferences)
}.flowOn(Dispatchers.IO)
```

---

## 4. Data Structure Inefficiencies

### 4.1 Inefficient Contact Lookup by Phone Number

**Severity: MEDIUM**  
**File:** `app/src/main/java/com/milki/launcher/data/repository/ContactsRepositoryImpl.kt:440-487`

**Issue:**
`getContactByPhoneNumber()` performs two separate queries - one for the contact and one for all phone numbers.

**Current Flow:**

1. Query Phone table for the contact
2. Query Phone table again for all phone numbers

**Impact:**

- Two I/O operations per contact lookup
- Called for each recent contact individually
- Up to 8 queries for the recent contacts display

**Note:** The `getContactsByPhoneNumbers()` batch method already optimizes this - ensure it's used consistently.

---

### 4.2 Grid Position Search Could Use Set Instead of List

**Severity: MEDIUM**  
**File:** `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:141-158`

**Issue:**
Finding available position iterates through all items and creates a Set from positions.

**Current Code:**

```kotlin
private fun findAvailablePositionInList(items: List<HomeItem>, columns: Int): GridPosition {
    val occupiedPositions = items.map { it.position }.toSet()

    for (row in 0..100) {
        for (column in 0 until columns) {
            val position = GridPosition(row, column)
            if (position !in occupiedPositions) {
                return position
            }
        }
    }
    return GridPosition(100, 0)
}
```

**Potential Issue:**

- Creates a new Set on every call
- For a 4-column grid with sparse items, this is fine
- For larger grids or frequent updates, consider caching

**Status:** For typical usage (10-50 items, 4 columns), this is acceptable. No change needed unless grid grows significantly.

---

### 4.3 String Splitting for Recent Contacts

**Severity: LOW**  
**File:** `app/src/main/java/com/milki/launcher/data/repository/ContactsRepositoryImpl.kt:387-399`

**Issue:**
Recent contacts are stored as comma-separated string, requiring split/join operations.

**Current Code:**

```kotlin
val recentPhones = current.split(",")
    .filter { it.isNotEmpty() }
    .toMutableList()
```

**Impact:**

- Creates intermediate lists
- String operations on every read/write
- Limited to 8 items, so impact is minimal

**Suggested Alternative:**
Use `stringSetPreferencesKey` instead:

```kotlin
private val recentContactsKey = stringSetPreferencesKey("recent_contacts")
```

**Note:** This would change the storage format and require migration for existing users.

---

## 5. Caching Opportunities

### 5.1 URL Handler Resolution Not Cached

**Severity: MEDIUM**  
**File:** `app/src/main/java/com/milki/launcher/domain/search/UrlHandlerResolver.kt:96-142`

**Issue:**
`resolveUrlHandler()` is called for every URL typed, even for the same URL multiple times. PackageManager queries are relatively expensive.

**Current Behavior:**

- User types "youtube.com"
- `resolveUrlHandler()` called with "https://youtube.com"
- User adds "/watch?v=xyz"
- `resolveUrlHandler()` called again with the new URL

**Suggested Fix:**
Add an LRU cache for URL handler results:

```kotlin
class UrlHandlerResolver(private val context: Context) {
    private val cache = LruCache<String, UrlHandlerApp?>(50)

    fun resolveUrlHandler(url: String): UrlHandlerApp? {
        cache.get(url)?.let { return it }

        val result = // ... existing resolution logic

        cache.put(url, result)
        return result
    }
}
```

---

### 5.2 Installed Apps Loaded on Every ViewModel Creation

**Severity: MEDIUM**  
**File:** `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModel.kt:111-116`

**Issue:**
`getInstalledApps()` is called in the ViewModel's `init` block. If the ViewModel is recreated (e.g., process death), this expensive operation runs again.

**Current Code:**

```kotlin
init {
    loadInstalledApps()
    observeRecentApps()
}

private fun loadInstalledApps() {
    viewModelScope.launch {
        val apps = appRepository.getInstalledApps()
        updateState { copy(installedApps = apps) }
    }
}
```

**Suggested Fix:**
Cache installed apps in the repository with a Flow:

```kotlin
// In AppRepositoryImpl
private val cachedApps: Flow<List<AppInfo>> = flow {
    emit(queryInstalledApps())
}.shareIn(
    scope = CoroutineScope(Dispatchers.IO),
    started = SharingStarted.WhileSubscribed(5000),
    replay = 1
)
```

---

## 6. Potential Memory Leaks

### 6.1 Activity Context in Singleton Repositories

**Severity: LOW**  
**Files:** All repository implementations

**Issue:**
Repositories are singletons (via Koin) and hold `Context` references. However, they use `Application Context` (provided by Koin's `androidContext()`), which is correct and won't leak.

**Status:** Correctly implemented. Application Context is safe to hold in singletons.

---

## 7. Image Loading Performance

### 7.1 Coil Configuration Not Customized

**Severity: LOW**  
**File:** `app/src/main/java/com/milki/launcher/AppIconFetcher.kt`

**Issue:**
The app uses Coil for image loading with default configuration. For a launcher app that displays many app icons, custom configuration could improve performance.

**Current Code:**

```kotlin
val painter = rememberAsyncImagePainter(
    model = AppIconRequest(packageName)
)
```

**Suggested Optimizations:**

1. Configure a custom ImageLoader with larger memory cache
2. Enable crossfade for smoother icon loading
3. Set appropriate placeholder and error drawables

```kotlin
// In LauncherApplication
override fun onCreate() {
    super.onCreate()

    val imageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25) // 25% of available memory
                .build()
        }
        .components {
            add(AppIconFetcher.Factory())
        }
        .build()

    Coil.setImageLoader(imageLoader)
}
```

---

## 8. Compose-Specific Performance

### 8.1 Missing `remember` for Computed Values

**Severity: LOW**  
**File:** `app/src/main/java/com/milki/launcher/ui/components/SearchResultItems.kt:498-506`

**Issue:**
File icon selection logic runs on every recomposition.

**Current Code:**

```kotlin
@Composable
fun FileDocumentSearchResultItem(...) {
    val fileIcon = when {
        file.isPdf() -> Icons.Outlined.PictureAsPdf
        file.isWordDocument() -> Icons.AutoMirrored.Outlined.Article
        // ...
    }
}
```

**Status:** The `when` expression is cheap and `Icons` are singletons, so this is acceptable. The `ImageVector` objects are pre-allocated.

---

### 8.2 Heavy Computation During Composition

**Severity: MEDIUM**  
**File:** `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModel.kt:277-341`

**Issue:**
The `performSearch()` function performs URL detection and app filtering, but this is correctly offloaded to `Dispatchers.Default`.

**Status:** Correctly implemented. The heavy work runs on a background dispatcher.

---

## 9. Network Performance

### 9.1 No Offline Caching for Web Search

**Severity: LOW**  
**Note:** The launcher doesn't perform network requests directly - it opens URLs in the browser. No caching is needed at the app level.

---

## 10. Action Items Summary

### Immediate (High Priority)

1. Fix `ActionExecutor.kt:147-148` to use version-checked `queryIntentActivities` API
2. Simplify `HomeViewModel` to use `stateIn()` instead of manual state management
3. Reduce recomposition in `DraggablePinnedItemsGrid` by using `key()` and `derivedStateOf`

### Short Term (Medium Priority)

1. Inject `CoroutineScope` into `ActionExecutor` for proper lifecycle management
2. Add LRU cache to `UrlHandlerResolver` for repeated URL lookups
3. Cache installed apps in `AppRepositoryImpl` using a shared Flow
4. Store activity name in `PinnedApp` to avoid `getLaunchIntentForPackage()` calls

### Long Term (Low Priority)

1. Configure custom Coil ImageLoader with optimized memory cache
2. Consider using `stringSetPreferencesKey` for recent contacts storage
3. Add explicit `flowOn(Dispatchers.IO)` to repository Flows for clarity

---

## Appendix: Performance Best Practices Observed

The codebase follows many performance best practices:

1. **Lazy initialization:** `AppInfo.nameLower` and `packageLower` use `by lazy` to avoid repeated lowercase conversions
2. **Batch database queries:** `ContactsRepositoryImpl.searchContacts()` uses a single JOIN query instead of N+1 queries
3. **Coroutines:** All I/O operations are suspend functions or use Flows
4. **Structured concurrency:** ViewModels use `viewModelScope`
5. **State hoisting:** UI components are stateless where appropriate
6. **Cancellation support:** Search operations can be cancelled with `searchJob?.cancel()`
7. **Pagination:** Search results are limited (8 for apps, 50 for contacts) to avoid loading too much data
