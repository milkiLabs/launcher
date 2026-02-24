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

### Location: `di/AppModule.kt`

The Koin module defines all dependencies in one place:

```kotlin
val appModule = module {
    
    // Repositories (singletons)
    single<AppRepository> { AppRepositoryImpl(get()) }
    single<ContactsRepository> { ContactsRepositoryImpl(get()) }
    single<FilesRepository> { FilesRepositoryImpl(get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    
    // Search Providers (singletons)
    single { WebSearchProvider() }
    single { YouTubeSearchProvider() }
    single { ContactsSearchProvider(get()) }
    single { FilesSearchProvider(get()) }
    
    // Registry (singleton)
    single {
        SearchProviderRegistry(
            initialProviders = listOf(
                get<WebSearchProvider>(),
                get<ContactsSearchProvider>(),
                get<FilesSearchProvider>(),
                get<YouTubeSearchProvider>()
            )
        )
    }
    
    // Use Cases (singletons)
    single { FilterAppsUseCase() }
    single { UrlHandlerResolver(get()) }
    
    // ViewModels (scoped to lifecycle)
    viewModel {
        SearchViewModel(
            appRepository = get(),
            contactsRepository = get(),
            providerRegistry = get(),
            filterAppsUseCase = get(),
            urlHandlerResolver = get()
        )
    }
    
    viewModel {
        SettingsViewModel(settingsRepository = get())
    }
}
```

---

## Initializing Koin

### Location: `LauncherApplication.kt`

Koin is initialized in the Application class before any Activity is created:

```kotlin
class LauncherApplication : Application(), ImageLoaderFactory {
    
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
            
            // Load our dependency modules
            modules(appModule)
        }
    }
    
    // ... Coil configuration
}
```

### What `startKoin` Does

1. **Creates Koin's internal container**: Where all dependencies are stored
2. **Registers the Android context**: So dependencies can request Context
3. **Loads modules**: All dependencies defined in `appModule` become available
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
            } + appModule
        )
    }
    
    @Test
    fun testSearch() {
        val viewModel: SearchViewModel by viewModel()
        // Test with mock repository
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

For larger projects, split modules by feature:

```kotlin
// Core module (repositories, data sources)
val coreModule = module {
    single<AppRepository> { AppRepositoryImpl(get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
}

// Search module (search providers, use cases)
val searchModule = module {
    single { WebSearchProvider() }
    single { SearchProviderRegistry(getAll()) }
    single { FilterAppsUseCase() }
}

// Presentation module (ViewModels)
val presentationModule = module {
    viewModel { SearchViewModel(get(), get(), ...) }
    viewModel { SettingsViewModel(get()) }
}

// Load all modules
startKoin {
    modules(coreModule, searchModule, presentationModule)
}
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

Koin makes dependency injection in Android simple, readable, and testable. The launcher uses a single module (`appModule`) that defines all repositories, providers, use cases, and ViewModels in one place, making the dependency graph easy to understand and maintain.
