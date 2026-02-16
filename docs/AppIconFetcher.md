# AppIconFetcher.kt - Detailed Documentation

## Overview

`AppIconFetcher.kt` is a custom Coil component that teaches the Coil image loading library how to load Android app icons. Since Coil doesn't know about Android's `PackageManager` or app icons by default, we create a custom `Fetcher` that handles `AppIconRequest` objects and returns app icons.

This file demonstrates advanced Android development concepts:
- Custom image loading
- Integration with Android system services
- Coil's component architecture
- Kotlin coroutines in image loading

---

## Table of Contents

1. [What is Coil?](#what-is-coil)
2. [Why Do We Need a Custom Fetcher?](#why-do-we-need-a-custom-fetcher)
3. [File Structure](#file-structure)
4. [AppIconRequest Data Class](#appiconrequest-data-class)
5. [Fetcher Interface](#fetcher-interface)
6. [AppIconFetcher Class](#appiconfetcher-class)
7. [The fetch() Method](#the-fetch-method)
8. [Factory Class](#factory-class)
9. [How Coil Uses Our Fetcher](#how-coil-uses-our-fetcher)
10. [Error Handling](#error-handling)
11. [Performance Considerations](#performance-considerations)

---

## What is Coil?

### Image Loading in Android

Loading images in Android requires handling:
- Memory management (caching, recycling)
- Threading (don't block UI thread)
- Different image formats (JPEG, PNG, WebP, etc.)
- Transformations (resize, crop, filters)
- Placeholders and error states

**The Hard Way (Without a Library)**:
```kotlin
// This is simplified - real code would be much longer!
fun loadImage(url: String, imageView: ImageView) {
    Thread {
        val connection = URL(url).openConnection()
        val inputStream = connection.getInputStream()
        val bitmap = BitmapFactory.decodeStream(inputStream)
        
        // Must update UI on main thread
        imageView.post {
            imageView.setImageBitmap(bitmap)
        }
    }.start()
}
```

Problems with this approach:
- No caching (downloads image every time)
- No error handling
- Memory leaks possible
- No image transformations
- Blocking thread manually

### Enter Coil

Coil is Kotlin Coroutines-based image loading for Android:

**The Easy Way (With Coil)**:
```kotlin
AsyncImage(
    model = "https://example.com/image.jpg",
    contentDescription = null
)
```

Coil automatically handles:
- Loading on background thread
- Memory and disk caching
- Image decoding
- Placeholders and errors
- Lifecycle awareness
- Bitmap pooling (memory efficiency)

---

## Why Do We Need a Custom Fetcher?

### The Problem

Coil knows how to load images from common sources:
- URLs (`https://...`)
- File paths (`/storage/...`)
- Resource IDs (`R.drawable...`)
- Byte arrays

But it doesn't know about Android app icons:
```kotlin
// This won't work - Coil doesn't understand package names
AsyncImage(
    model = "com.google.android.youtube",
    contentDescription = null
)
```

### The Solution

We teach Coil how to load app icons by creating a custom `Fetcher`:

```kotlin
// This works with our custom Fetcher!
AsyncImage(
    model = AppIconRequest("com.google.android.youtube"),
    contentDescription = null
)
```

### How It Works

```
Compose displays Image
    ↓
Coil receives AppIconRequest
    ↓
Coil asks registered Fetchers: "Can you handle this?"
    ↓
AppIconFetcher.Factory says: "Yes! I handle AppIconRequest"
    ↓
Coil creates AppIconFetcher instance
    ↓
fetch() loads icon from PackageManager
    ↓
Icon is displayed
```

---

## File Structure

```kotlin
AppIconFetcher.kt
├── AppIconRequest              // Data class to identify requests
│   └── packageName: String     // Which app to load icon for
│
├── AppIconFetcher              // Main fetcher class
│   ├── fetch()                 // Loads the icon
│   └── Factory                 // Creates fetcher instances
│
└── Factory                     // Inner class
    └── create()                // Determines if it can handle request
```

---

## AppIconRequest Data Class

```kotlin
data class AppIconRequest(val packageName: String)
```

### What is it?

A simple data class that wraps a package name. It acts as a "key" that tells our Fetcher which app's icon to load.

### Why Create a Custom Type?

**Option 1: Use String (Bad)**:
```kotlin
AsyncImage(
    model = "com.google.android.youtube",  // Is this a URL? File path?
    contentDescription = null
)
```
- Ambiguous - could be anything
- Other fetchers might try to handle it
- Type-unsafe

**Option 2: Use Custom Type (Good)**:
```kotlin
AsyncImage(
    model = AppIconRequest("com.google.android.youtube"),
    contentDescription = null
)
```
- Clear intent - this is an app icon request
- Type-safe
- Only our Fetcher handles it

### Data Class Benefits

```kotlin
data class AppIconRequest(val packageName: String)
```

Kotlin automatically generates:
- `equals()` - for comparing requests
- `hashCode()` - for using in hash-based collections
- `toString()` - for debugging
- `copy()` - for creating modified copies
- Component functions - for destructuring

---

## Fetcher Interface

### What is a Fetcher?

A Fetcher is a Coil component that knows how to load a specific type of data into an image.

**Coil's Component Architecture**:
```
Image Request
    ↓
Mapper (transform request)
    ↓
Fetcher (load data) ← We are here!
    ↓
Decoder (decode to bitmap)
    ↓
Transformer (apply effects)
    ↓
Display
```

### Fetcher Interface (Simplified)

```kotlin
interface Fetcher {
    suspend fun fetch(): FetchResult
}
```

**suspend**: The fetch operation is asynchronous (uses coroutines).

**FetchResult**: The result of the fetch operation, containing the image data.

---

## AppIconFetcher Class

```kotlin
class AppIconFetcher(
    private val data: AppIconRequest,
    private val options: Options
) : Fetcher {
```

### Constructor Parameters

**data: AppIconRequest**
- The request containing the package name
- Set when the Fetcher is created
- Used in fetch() to know which icon to load

**options: Options**
- Configuration options from Coil
- Contains Context, size requirements, etc.
- We use it to get the PackageManager

### Why These Parameters?

Coil's Factory pattern provides these when creating the Fetcher:
- `data`: The actual request object
- `options`: Metadata about how to fetch

---

## The fetch() Method

```kotlin
override suspend fun fetch(): FetchResult {
    val pm = options.context.packageManager
    val drawable = try {
        pm.getApplicationIcon(data.packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        // Return default icon if app not found
        pm.defaultActivityIcon
    }

    return DrawableResult(
        drawable = drawable,
        isSampled = false,
        dataSource = DataSource.MEMORY
    )
}
```

### Step-by-Step Breakdown

#### Step 1: Get PackageManager

```kotlin
val pm = options.context.packageManager
```

**options.context**: The Android Context provided by Coil.

**packageManager**: System service for querying app information.

**Why from options?**:
- Coil manages the Context lifecycle
- Always valid, never null
- Correct type (Application context)

#### Step 2: Load the Icon

```kotlin
val drawable = try {
    pm.getApplicationIcon(data.packageName)
} catch (e: PackageManager.NameNotFoundException) {
    pm.defaultActivityIcon
}
```

**getApplicationIcon(packageName)**:
- Retrieves the app's icon as a Drawable
- Fast operation (icon is cached by system)
- May throw exception if app not found

**NameNotFoundException**:
- Thrown when package doesn't exist
- Can happen if app was uninstalled
- We catch it and return default icon

**defaultActivityIcon**:
- Generic Android robot icon
- Used as fallback
- Always available

#### Step 3: Return Result

```kotlin
return DrawableResult(
    drawable = drawable,
    isSampled = false,
    dataSource = DataSource.MEMORY
)
```

**DrawableResult**: Tells Coil we have a Drawable (not a Bitmap or InputStream).

**Parameters**:
- `drawable`: The loaded icon
- `isSampled`: Whether image was downsampled (resized)
- `dataSource`: Where the image came from

**DataSource.MEMORY**:
- Indicates icon came from memory (PackageManager cache)
- Not from disk or network
- Helps Coil manage cache correctly

### suspend Keyword

```kotlin
override suspend fun fetch(): FetchResult
```

**What is suspend?**:
- Function can be paused and resumed
- Runs in a coroutine
- Doesn't block the thread

**Why suspend?**:
- getApplicationIcon() is fast, but suspend allows Coil to:
  - Run on background thread
  - Cancel if needed
  - Compose with other async operations

**Calling suspend functions**:
```kotlin
// Must be called from coroutine or another suspend function
val result = fetch()

// Or launch a coroutine
lifecycleScope.launch {
    val result = fetch()
}
```

---

## Factory Class

```kotlin
class Factory : Fetcher.Factory<AppIconRequest> {
    override fun create(
        data: AppIconRequest,
        options: Options,
        imageLoader: ImageLoader
    ): Fetcher {
        return AppIconFetcher(data, options)
    }
}
```

### What is the Factory?

The Factory is responsible for:
1. Deciding if it can handle a request
2. Creating Fetcher instances

### Fetcher.Factory Interface

```kotlin
interface Factory<T : Any> {
    fun create(data: T, options: Options, imageLoader: ImageLoader): Fetcher?
}
```

**Generic Type <T>**: The type of data this factory handles.

We use `AppIconRequest` as our type parameter.

### The create() Method

```kotlin
override fun create(
    data: AppIconRequest,
    options: Options,
    imageLoader: ImageLoader
): Fetcher {
    return AppIconFetcher(data, options)
}
```

**When is this called?**:
- Every time Coil needs to load an image
- Factory checks if data is AppIconRequest
- If yes, create() is called

**Parameters**:
- `data`: The request (guaranteed to be AppIconRequest)
- `options`: Configuration options
- `imageLoader`: The ImageLoader instance

**Returns**: New AppIconFetcher instance configured for this request.

### How Coil Decides Which Factory to Use

```
Image request comes in
    ↓
Coil iterates through registered factories
    ↓
Checks: can Factory handle this data type?
    ↓
AppIconFetcher.Factory supports AppIconRequest
    ↓
If data is AppIconRequest → Use this factory
    ↓
create() called, Fetcher instance returned
```

---

## How Coil Uses Our Fetcher

### Registration

In `LauncherApplication.kt`:
```kotlin
ImageLoader.Builder(this)
    .components {
        add(AppIconFetcher.Factory())  // Register our factory
    }
    .build()
```

### Usage in Compose

In `MainActivity.kt`:
```kotlin
val painter = rememberAsyncImagePainter(
    model = AppIconRequest(appInfo.packageName)
)

Image(
    painter = painter,
    contentDescription = null,
    modifier = Modifier.size(40.dp)
)
```

### Complete Flow

```
1. Compose calls Image() with AppIconRequest
                    ↓
2. Coil receives request
                    ↓
3. Coil checks registered factories
   - HttpUriFetcher.Factory? No (not a URL)
   - FileFetcher.Factory? No (not a file)
   - AppIconFetcher.Factory? YES! (is AppIconRequest)
                    ↓
4. AppIconFetcher.Factory.create() called
                    ↓
5. New AppIconFetcher instance created
                    ↓
6. Coil calls fetch() on background thread
                    ↓
7. PackageManager.getApplicationIcon() called
                    ↓
8. Drawable returned in DrawableResult
                    ↓
9. Coil caches and displays the icon
```

---

## Error Handling

### PackageManager.NameNotFoundException

```kotlin
try {
    pm.getApplicationIcon(data.packageName)
} catch (e: PackageManager.NameNotFoundException) {
    pm.defaultActivityIcon
}
```

**When it happens**:
- App was uninstalled since we loaded the list
- App is disabled
- Package name is invalid

**Our handling**:
- Gracefully return default icon
- No crash, no error to user
- UI still works

### Other Exceptions

We don't catch other exceptions because:
- `SecurityException`: Shouldn't happen (we're querying our own apps)
- `NullPointerException`: Shouldn't happen with valid Context
- Let Coil handle unexpected errors

### Improving Error Handling

We could add logging:
```kotlin
try {
    pm.getApplicationIcon(data.packageName)
} catch (e: PackageManager.NameNotFoundException) {
    Log.w("AppIconFetcher", "App not found: ${data.packageName}")
    pm.defaultActivityIcon
}
```

---

## Performance Considerations

### Fast Icon Loading

**Why is it fast?**:
- Icons are cached by PackageManager
- No network request
- No disk I/O
- Just a memory lookup

**Timing**:
```
Typical load time: 1-5ms
Memory cache hit: <1ms
```

### No Subsampling

```kotlin
DrawableResult(
    drawable = drawable,
    isSampled = false,  // ← Not downsampled
    ...
)
```

App icons are already optimized (48dp-512dp). No need to resize.

### Memory Efficiency

- Icons loaded as Drawables (system-managed)
- Coil memory cache stores references
- No duplication of bitmap data

### Thread Safety

PackageManager operations are thread-safe:
```kotlin
// Safe to call from any thread
pm.getApplicationIcon(packageName)
```

Coil handles threading:
```kotlin
override suspend fun fetch()  // Runs on background thread
```

---

## Testing the Fetcher

### Unit Test Example

```kotlin
@Test
fun testFetchReturnsIcon() = runTest {
    // Create mock context and PackageManager
    val context = mockk<Context>()
    val packageManager = mockk<PackageManager>()
    val drawable = mockk<Drawable>()
    
    every { context.packageManager } returns packageManager
    every { packageManager.getApplicationIcon("com.test.app") } returns drawable
    
    // Create fetcher
    val request = AppIconRequest("com.test.app")
    val options = Options(context, Size.ORIGINAL)
    val fetcher = AppIconFetcher(request, options)
    
    // Test
    val result = fetcher.fetch()
    
    // Verify
    assertTrue(result is DrawableResult)
    assertEquals(drawable, (result as DrawableResult).drawable)
}
```

### Integration Test

```kotlin
@Test
fun testIconDisplaysInUI() {
    composeTestRule.setContent {
        val painter = rememberAsyncImagePainter(
            model = AppIconRequest("com.android.settings")
        )
        Image(
            painter = painter,
            contentDescription = "Settings icon"
        )
    }
    
    // Verify image is displayed
    composeTestRule.onNodeWithContentDescription("Settings icon")
        .assertIsDisplayed()
}
```

---

## Common Issues and Solutions

### Icons Not Loading

**Check**:
1. Factory registered in ImageLoader.Builder?
2. AppIconRequest used (not plain String)?
3. Package name is correct?
4. App is installed?

### Wrong Icons Displayed

**Possible causes**:
- Package name mismatch
- Cache issues (try clearing app data)
- Multiple apps with same name

### Memory Issues

**If using too much memory**:
- Reduce Coil memory cache size
- Check for memory leaks
- Verify icons aren't being duplicated

---

## Advanced Concepts

### Supporting Multiple Icon Sources

We could extend to support icon packs:
```kotlin
data class AppIconRequest(
    val packageName: String,
    val iconPack: String? = null  // Optional icon pack
)
```

Then in fetch():
```kotlin
if (data.iconPack != null) {
    loadFromIconPack(data.iconPack, data.packageName)
} else {
    loadFromPackageManager(data.packageName)
}
```

### Caching Strategies

We could implement our own caching:
```kotlin
// Check custom cache first
val cached = iconCache.get(data.packageName)
if (cached != null) {
    return DrawableResult(cached, ...)
}

// Load from PackageManager
val icon = pm.getApplicationIcon(data.packageName)
iconCache.put(data.packageName, icon)

return DrawableResult(icon, ...)
```

### Animated Icons

Some apps have adaptive icons with animations:
```kotlin
// Could return AnimatedImageDrawable
val drawable = pm.getApplicationIcon(packageName)
if (drawable is AnimatedImageDrawable) {
    drawable.start()
}
```

---

## Key Takeaways

1. **Fetchers teach Coil how to load custom data types**
2. **AppIconRequest is a type-safe wrapper for package names**
3. **Factory pattern creates Fetcher instances on demand**
4. **suspend functions enable async loading without blocking**
5. **PackageManager provides fast access to app icons**
6. **Always handle NameNotFoundException gracefully**
7. **Register Factory in ImageLoader configuration**

The AppIconFetcher is a small but critical component that bridges Coil's generic image loading with Android's app icon system. It demonstrates how to extend third-party libraries to work with your specific use case.

---

## Architecture Integration

### Layer Placement

The AppIconFetcher sits in the **Data Layer** (infrastructure) while `AppIconRequest` resides in the **Domain Layer**:

```
UI Layer (AppGridItem.kt)
    ↓ imports (correct direction!)
Domain Layer (AppIconRequest.kt)
    ↑ used by (dependency points inward)
Data Layer (AppIconFetcher.kt)
```

**Why this matters**: The UI depends on the domain model (`AppIconRequest`), not on data layer implementation details. This follows Clean Architecture's Dependency Rule: dependencies point inward.

### SOLID Compliance

| Principle | Status | Notes |
|-----------|--------|-------|
| **SRP** | ✅ | Only handles app icon fetching |
| **OCP** | ✅ | Can be extended without modification |
| **LSP** | ✅ | Implements Fetcher interface correctly |
| **ISP** | ✅ | Only implements necessary methods |
| **DIP** | ✅ | Depends on abstractions (Fetcher interface) |

### Registration Flow

```
App starts
    ↓
LauncherApplication.onCreate()
    ↓
ImageLoader.Builder()
    ↓
.components { add(AppIconFetcher.Factory()) }
    ↓
Factory registered with Coil
    ↓
ImageLoader built and cached
```

### Usage Flow

```
Compose calls Image() with AppIconRequest
    ↓
Coil receives request
    ↓
Coil asks registered Factories: "Can you handle this?"
    ↓
AppIconFetcher.Factory says: "Yes! It's AppIconRequest"
    ↓
Factory.create() returns AppIconFetcher instance
    ↓
Coil calls fetch() on background thread
    ↓
PackageManager.getApplicationIcon() loads icon
    ↓
Icon returned in DrawableResult
    ↓
Coil caches and displays the icon
```

### Configuration in LauncherApplication

```kotlin
override fun newImageLoader(): ImageLoader {
    return ImageLoader.Builder(this)
        .components {
            add(AppIconFetcher.Factory())  // Register our fetcher
        }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.15)      // 15% of memory for cache
                .build()
        }
        .diskCache(null)                   // No disk cache (icons in PackageManager)
        .allowHardware(false)              // Safer compatibility mode
        .respectCacheHeaders(false)        // Not loading from network
        .build()
}
```

### Why No Disk Cache?

For a launcher app:

1. **Icons Are Already Cached**: Android's PackageManager caches app icons
2. **Icons Don't Change Often**: Only change when app updates
3. **Save Storage Space**: 200 apps × 100KB = 20MB saved
4. **Simpler Architecture**: One less thing to manage

### File Location Strategy

**Current**: Root package for simplicity
```
com/milki/launcher/
├── AppIconFetcher.kt          # Infrastructure
└── domain/model/
    └── AppIconRequest.kt      # Domain model
```

**Alternative for larger apps**: 
```
data/
  fetcher/
    AppIconFetcher.kt
```

The current approach keeps it simple for educational purposes.

---

## Key Takeaways

1. **Fetchers teach Coil how to load custom data types** - We extend Coil to understand app icons
2. **AppIconRequest is type-safe** - Clear intent vs ambiguous strings
3. **Factory pattern creates instances on demand** - Coil calls create() when needed
4. **suspend enables async loading** - Non-blocking image loading
5. **PackageManager provides fast access** - Icons loaded from system cache
6. **Always handle exceptions gracefully** - NameNotFoundException returns default icon
7. **Register in Application class** - Must declare in ImageLoader configuration

The AppIconFetcher demonstrates how to integrate third-party libraries with Android-specific functionality while maintaining clean architecture principles.
