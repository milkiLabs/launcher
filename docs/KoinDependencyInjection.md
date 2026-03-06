# Dependency Injection with Koin

## Overview

Milki Launcher uses **Koin** for dependency injection. Koin is a lightweight dependency injection framework for Kotlin that uses a DSL (Domain Specific Language) to define dependencies, without the need for annotation processing or complex build steps.

This document explains how Koin is configured and used in the launcher app.

---

## Table of Contents

1. [Why Koin?](#why-koin)
2. [Adding Koin to the Project](#adding-koin-to-the-project)
3. [Koin Module Definition](#koin-module-definition)
4. [Initializing Koin](#initializing-koin)
5. [Injecting Dependencies](#injecting-dependencies)
6. [Dependency Types](#dependency-types)
7. [How It Works](#how-it-works)
8. [Comparison with Manual DI](#comparison-with-manual-di)
9. [Testing with Koin](#testing-with-koin)
10. [Common Patterns](#common-patterns)

---

## Why Koin?

### Before Koin: Manual DI

Previously, the app used manual dependency injection through an `AppContainer` class:

```kotlin
// Old approach - Manual DI
class AppContainer(private val application: Application) {
    private val appRepository: AppRepository by lazy {
        AppRepositoryImpl(application)
    }
    
    val searchViewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(appRepository, ...) as T
        }
    }
}

// In Activity
private val viewModel: SearchViewModel by viewModels {
    (application as LauncherApplication).container.searchViewModelFactory
}
```

**Problems with Manual DI**:
- Boilerplate code for each ViewModel factory
- Manual dependency wiring (easy to make mistakes)
- Harder to test (need to mock the container)
- Tight coupling to Application class

### After Koin: Declarative DI

```kotlin
// New approach - Koin
val appModule = module {
    single<AppRepository> { AppRepositoryImpl(get()) }
    viewModel { SearchViewModel(get(), get(), ...) }
}

// In Activity
private val viewModel: SearchViewModel by viewModel()
```

**Benefits of Koin**:
- Declarative dependency definitions
- Automatic dependency resolution
- Easy testing with module overrides
- No annotation processing (faster builds)
- Clean, readable code

---

## Adding Koin to the Project

### 1. Version Catalog (libs.versions.toml)

```toml
[versions]
koinBom = "4.1.1"

[libraries]
koin-bom = { module = "io.insert-koin:koin-bom", version.ref = "koinBom" }
koin-core = { module = "io.insert-koin:koin-core" }
koin-android = { module = "io.insert-koin:koin-android" }
koin-androidx-compose = { module = "io.insert-koin:koin-androidx-compose" }
```

**What is BOM?**

BOM (Bill of Materials) ensures all Koin libraries use compatible versions. When you update the BOM version, all Koin dependencies are automatically updated to compatible versions.

### 2. Build Gradle (app/build.gradle.kts)

```kotlin
dependencies {
    // Koin BOM - manages all Koin library versions
    implementation(platform(libs.koin.bom))
    
    // Koin Core - core DI functionality
    implementation(libs.koin.core)
    
    // Koin Android - Android-specific extensions
    implementation(libs.koin.android)
    
    // Koin Compose - Jetpack Compose integration
    implementation(libs.koin.androidx.compose)
}
```

---

## Koin Module Definition

### Location: `di/` package

Dependencies are split across **feature-specific modules** rather than defined in a single monolithic module.
Each module groups the dependencies that belong to one feature, and `AppModule.kt` aggregates them
into an `allModules` list that the Application class loads at startup.

### Module Structure

```
di/
├── AppModule.kt       ← Aggregator: combines all feature modules into allModules
├── CoreModule.kt      ← Shared repositories (AppRepository, SettingsRepository)
├── SearchModule.kt    ← Search feature (ContactsRepo, FilesRepo, providers, use cases, SearchVM)
├── HomeModule.kt      ← Home screen feature (HomeRepository, HomeViewModel)
├── WidgetModule.kt    ← Widget infrastructure (WidgetHostManager)
├── SettingsModule.kt  ← Settings feature (SettingsViewModel)
└── DrawerModule.kt    ← App drawer feature (AppDrawerViewModel)
```

### Dependency Direction Rules

```
Feature modules ──→ coreModule   (allowed: features read shared repos)
coreModule ──→ feature modules   (NEVER: would create circular dependency)
Feature ──→ another feature      (NEVER: use coreModule as intermediary)
```

All modules follow the architecture rule: **presentation → domain → data**.

### Example: CoreModule (shared dependencies)

```kotlin
// di/CoreModule.kt
val coreModule = module {
    // Shared repositories used by 2+ features
    single<AppRepository> { AppRepositoryImpl(get()) }      // used by search + drawer
    single<SettingsRepository> { SettingsRepositoryImpl(get()) } // used by search + settings
}
```

### Example: SearchModule (feature-specific dependencies)

```kotlin
// di/SearchModule.kt
val searchModule = module {
    // Search-only repositories
    single<ContactsRepository> { ContactsRepositoryImpl(get()) }
    single<FilesRepository> { FilesRepositoryImpl(get()) }

    // Search providers
    single { ContactsSearchProvider(get()) }
    single { FilesSearchProvider(get()) }
    single { SearchProviderRegistry(initialProviders = listOf(get<ContactsSearchProvider>(), get<FilesSearchProvider>())) }

    // Use cases
    single { FilterAppsUseCase() }
    single { UrlHandlerResolver(get()) }
    single { ClipboardSuggestionResolver(context = get(), urlHandlerResolver = get()) }

    // ViewModel (gets AppRepository + SettingsRepository from coreModule via get())
    viewModel { SearchViewModel(appRepository = get(), contactsRepository = get(), settingsRepository = get(), ...) }
}
```

### Aggregator: AppModule.kt

```kotlin
// di/AppModule.kt
val allModules = listOf(
    coreModule,      // shared repositories (foundation)
    searchModule,    // search feature
    homeModule,      // home screen feature
    widgetModule,    // widget infrastructure
    settingsModule,  // settings feature
    drawerModule     // app drawer feature
)
```

---

## Initializing Koin

### Location: `LauncherApplication.kt`

Koin is initialized in the Application class before any Activity is created:

```kotlin
class LauncherApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        initializeKoin()
    }
    
    private fun initializeKoin() {
        startKoin {
            // Provide the Application context to Koin
            // This allows dependencies to request Context via get()
            androidContext(this@LauncherApplication)
            
            // Enable Koin's Android logger for debugging
            androidLogger(Level.ERROR)
            
            // Load all feature modules (aggregated in AppModule.kt)
            // allModules = listOf(coreModule, searchModule, homeModule, widgetModule, settingsModule, drawerModule)
            modules(allModules)
        }
    }
}
```

### What `startKoin` Does

1. **Creates Koin's internal container**: Where all dependencies are stored
2. **Registers the Android context**: So dependencies can request Context
3. **Loads modules**: All dependencies from all feature modules become available
4. **Sets up logging**: Shows dependency resolution in Logcat

---

## Injecting Dependencies

### In Activities

Use Koin's delegates to inject dependencies:

```kotlin
class MainActivity : ComponentActivity() {
    
    // Inject a ViewModel
    // Koin handles creation, lifecycle scoping, and dependency resolution
    private val searchViewModel: SearchViewModel by viewModel()
    
    // Inject a singleton
    // Koin returns the same instance every time
    private val contactsRepository: ContactsRepository by inject()
    
    // ...
}
```

### In Composables

Use Koin's Compose functions:

```kotlin
@Composable
fun SomeScreen() {
    // Get a ViewModel scoped to the composable's lifecycle
    val viewModel: SearchViewModel = koinViewModel()
    
    // Or get a singleton
    val repository: AppRepository = koinInject()
    
    // ...
}
```

---

## Dependency Types

### Singleton (`single`)

Creates ONE instance for the entire app:

```kotlin
single<AppRepository> { AppRepositoryImpl(get()) }
```

**Use for**:
- Repositories (hold data/state)
- Data sources
- Use cases (stateless business logic)
- Any service that should persist across the app

**Lifecycle**: Created on first request, lives until app termination.

### Factory (`factory`)

Creates a NEW instance every time:

```kotlin
factory { SomeUseCase(get()) }
```

**Use for**:
- Objects that shouldn't be shared
- Stateful objects that need fresh instances
- When you need different instances for different consumers

**Lifecycle**: Created fresh on every request.

### ViewModel (`viewModel`)

Creates a ViewModel scoped to the lifecycle owner (Activity/Fragment):

```kotlin
viewModel { SearchViewModel(get(), get(), ...) }
```

**Use for**:
- ViewModels (always!)
- Presentation logic that needs lifecycle awareness

**Lifecycle**: 
- Created when first requested by a lifecycle owner
- Survives configuration changes (screen rotation)
- Cleared when the lifecycle owner is destroyed

---

## How It Works

### Dependency Resolution with `get()`

When you use `get()` in a module definition, Koin automatically resolves the dependency:

```kotlin
// Definition
single { ContactsSearchProvider(get()) }

// What Koin does:
// 1. Sees get() parameter
// 2. Looks up ContactsRepository in the container
// 3. Returns the ContactsRepository singleton
// 4. Passes it to ContactsSearchProvider constructor
```

### Dependency Graph

Koin builds a dependency graph at startup:

```
AppRepositoryImpl
    └── Context (from androidContext)
    
ContactsRepositoryImpl
    └── Context (from androidContext)

ContactsSearchProvider
    └── ContactsRepository (singleton)
    
SearchProviderRegistry
    ├── WebSearchProvider (singleton)
    ├── ContactsSearchProvider (singleton)
    ├── FilesSearchProvider (singleton)
    └── YouTubeSearchProvider (singleton)

SearchViewModel
    ├── AppRepository (singleton)
    ├── ContactsRepository (singleton)
    ├── SearchProviderRegistry (singleton)
    ├── FilterAppsUseCase (singleton)
    └── UrlHandlerResolver (singleton)
```

When you request `SearchViewModel`, Koin automatically resolves the entire chain.

---

## Comparison with Manual DI

| Aspect | Manual DI (AppContainer) | Koin |
|--------|--------------------------|------|
| **Boilerplate** | High (factory classes) | Low (DSL declarations) |
| **Type Safety** | Manual (runtime errors) | Compile-time |
| **Testing** | Manual mocking | Easy module overrides |
| **Readability** | Scattered across files | Centralized in module |
| **Dependency Resolution** | Manual | Automatic (get()) |
| **Build Speed** | Fast (no processing) | Fast (no annotation processing) |
| **Learning Curve** | Low | Medium |

---

## Testing with Koin

### Overriding Dependencies for Tests

```kotlin
class SearchViewModelTest {
    
    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(
            module {
                // Override real repository with mock
                single<AppRepository> { MockAppRepository() }
                // Use real module for other dependencies
            } + allModules  // allModules aggregates all feature modules
        )
    }
    
    @Test
    fun testSearch() {
        val viewModel: SearchViewModel by viewModel()
        // Test with mock repository
    }
}
```

### Testing a Single Feature Module

Because dependencies are split by feature, you can load only the modules you need:

```kotlin
// Test only the search feature — load coreModule + searchModule
class SearchFeatureTest {
    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(coreModule, searchModule)
    }
}
```

### Test-Specific Module

```kotlin
val testModule = module {
    // Test doubles
    single<AppRepository> { FakeAppRepository() }
    single<ContactsRepository> { FakeContactsRepository() }
}

// In test
startKoin {
    modules(testModule)
}
```

---

## Common Patterns

### Interface to Implementation Binding

```kotlin
// Define interface
interface AppRepository {
    suspend fun getInstalledApps(): List<AppInfo>
}

// Implement
class AppRepositoryImpl(context: Context) : AppRepository {
    // ...
}

// Bind in Koin
single<AppRepository> { AppRepositoryImpl(get()) }
```

### Multiple Implementations

When you have multiple implementations of an interface:

```kotlin
// Use named definitions
single(named("local")) { LocalDataSource(get()) }
single(named("remote")) { RemoteDataSource(get()) }

// Inject with name
val local: DataSource by inject(named("local"))
val remote: DataSource by inject(named("remote"))
```

### Scoped Dependencies

For dependencies that should be scoped to a specific lifecycle:

```kotlin
// Define a scope
scope<MainActivity> {
    scoped { SomeScopedDependency() }
}

// Use in Activity
val dependency: SomeScopedDependency by inject()
```

---

## Module Organization

The launcher splits dependencies by **feature** rather than by **layer**. Each feature module
contains all layers (data, domain, presentation) for that feature, while shared/cross-cutting
dependencies live in `coreModule`.

### Current Module Layout

| Module | Contents | Depends On |
|--------|----------|------------|
| `coreModule` | AppRepository, SettingsRepository | — (foundation) |
| `searchModule` | ContactsRepo, FilesRepo, search providers, registry, use cases, SearchVM | coreModule |
| `homeModule` | HomeRepository, HomeViewModel | — (standalone) |
| `widgetModule` | WidgetHostManager | — (standalone) |
| `settingsModule` | SettingsViewModel | coreModule |
| `drawerModule` | AppDrawerViewModel | coreModule |

### Adding a New Feature Module

To add a new feature module:

1. Create a new file in `di/` (e.g., `NotificationsModule.kt`).
2. Define the module's repositories, use cases, and ViewModels inside a `module { }` block.
3. Add the new module to `allModules` in `AppModule.kt`.

```kotlin
// di/NotificationsModule.kt
val notificationsModule = module {
    single<NotificationsRepository> { NotificationsRepositoryImpl(get()) }
    viewModel { NotificationsViewModel(notificationsRepository = get()) }
}

// di/AppModule.kt — add to the list
val allModules = listOf(
    coreModule,
    searchModule,
    homeModule,
    widgetModule,
    settingsModule,
    drawerModule,
    notificationsModule  // ← new module
)
```

---

## Summary

| Concept | Description |
|---------|-------------|
| **Module** | A collection of dependency definitions |
| **single** | One instance for the entire app (singleton) |
| **factory** | New instance every time |
| **viewModel** | ViewModel scoped to lifecycle |
| **get()** | Automatic dependency resolution |
| **inject()** | Lazy property delegate for injection |
| **viewModel()** | Property delegate for ViewModel injection |
| **startKoin** | Initialize Koin in Application class |
| **androidContext** | Provide Android Context to Koin |

Koin makes dependency injection in Android simple, readable, and testable. The launcher splits dependencies across feature-specific modules (`coreModule`, `searchModule`, `homeModule`, `widgetModule`, `settingsModule`, `drawerModule`) that are aggregated into `allModules` in `AppModule.kt`. Each feature module owns its own dependencies, while shared repositories live in `coreModule`. This keeps the dependency graph organized, testable, and ready for future growth.
