# Milki Launcher - Architecture Plan

## Overview
This document outlines a scalable, maintainable architecture for the Milki Launcher Android app using **Clean Architecture** principles combined with **MVVM** presentation pattern.

## Architecture Principles

### 1. Clean Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  (UI, ViewModels, State Management, Navigation)             │
├─────────────────────────────────────────────────────────────┤
│                      Domain Layer                            │
│  (Use Cases, Repository Interfaces, Domain Models)          │
├─────────────────────────────────────────────────────────────┤
│                       Data Layer                             │
│  (Repositories, Data Sources, Mappers, DTOs)                │
└─────────────────────────────────────────────────────────────┘
```

### 2. Dependency Rule
- **Inner layers know nothing about outer layers**
- Domain layer is independent of Android framework
- Dependencies point inward only

## Proposed Package Structure

```
com.milki.launcher/
├── app/                          # Application-level components
│   ├── LauncherApplication.kt
│   ├── di/                       # App-level dependency injection
│   │   ├── AppModule.kt
│   │   └── DataStoreModule.kt
│   └── navigation/               # Navigation graph & routes
│       ├── LauncherNavHost.kt
│       └── Routes.kt
│
├── domain/                       # Domain layer (no Android deps)
│   ├── model/                    # Domain models
│   │   ├── AppInfo.kt
│   │   ├── AppCategory.kt
│   │   └── SearchResult.kt
│   ├── repository/               # Repository interfaces
│   │   ├── AppRepository.kt
│   │   ├── RecentAppsRepository.kt
│   │   └── SettingsRepository.kt
│   └── usecase/                  # Use cases (single responsibility)
│       ├── GetInstalledAppsUseCase.kt
│       ├── GetRecentAppsUseCase.kt
│       ├── SaveRecentAppUseCase.kt
│       ├── SearchAppsUseCase.kt
│       └── LaunchAppUseCase.kt
│
├── data/                         # Data layer
│   ├── local/                    # Local data sources
│   │   ├── datastore/            # DataStore preferences
│   │   │   ├── LauncherDataStore.kt
│   │   │   └── RecentAppsDataSource.kt
│   │   └── database/             # Room database (future)
│   │       ├── AppDatabase.kt
│   │       └── dao/
│   ├── remote/                   # Remote data sources (future)
│   │   └── api/
│   ├── repository/               # Repository implementations
│   │   ├── AppRepositoryImpl.kt
│   │   ├── RecentAppsRepositoryImpl.kt
│   │   └── SettingsRepositoryImpl.kt
│   └── mapper/                   # Data-to-domain mappers
│       ├── AppInfoMapper.kt
│       └── AppCategoryMapper.kt
│
├── presentation/                 # Presentation layer
│   ├── common/                   # Shared UI components
│   │   ├── components/
│   │   │   ├── AppListItem.kt
│   │   │   ├── AppIcon.kt
│   │   │   └── SearchBar.kt
│   │   ├── theme/
│   │   │   ├── Theme.kt
│   │   │   ├── Color.kt
│   │   │   └── Type.kt
│   │   └── util/
│   │       └── Extensions.kt
│   ├── home/                     # Home screen feature
│   │   ├── HomeScreen.kt
│   │   ├── HomeViewModel.kt
│   │   └── HomeUiState.kt
│   ├── search/                   # Search feature
│   │   ├── SearchScreen.kt
│   │   ├── SearchViewModel.kt
│   │   ├── SearchUiState.kt
│   │   └── components/
│   │       ├── SearchDialog.kt
│   │       └── FilteredAppList.kt
│   └── settings/                 # Settings feature (future)
│       ├── SettingsScreen.kt
│       └── SettingsViewModel.kt
│
└── core/                         # Core utilities (cross-cutting)
    ├── utils/
    │   ├── AppIconFetcher.kt     # Icon loading utility
    │   └── CoroutineUtils.kt
    └── constants/
        └── AppConstants.kt
```

## Detailed Component Design

### 1. Domain Layer

#### Domain Models
```kotlin
// domain/model/AppInfo.kt
data class AppInfo(
    val name: String,
    val packageName: String,
    val category: AppCategory,
    val isSystemApp: Boolean,
    val lastUsed: Long? = null
)

enum class AppCategory {
    PRODUCTIVITY, SOCIAL, ENTERTAINMENT, SYSTEM, OTHER
}
```

#### Repository Interfaces
```kotlin
// domain/repository/AppRepository.kt
interface AppRepository {
    suspend fun getInstalledApps(): List<AppInfo>
    suspend fun getAppByPackageName(packageName: String): AppInfo?
    fun observeInstalledApps(): Flow<List<AppInfo>>
}

// domain/repository/RecentAppsRepository.kt
interface RecentAppsRepository {
    suspend fun getRecentApps(): List<AppInfo>
    suspend fun saveRecentApp(packageName: String)
    suspend fun clearRecentApps()
}
```

#### Use Cases
```kotlin
// domain/usecase/SearchAppsUseCase.kt
class SearchAppsUseCase(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(query: String): SearchResult {
        val apps = appRepository.getInstalledApps()
        return SearchAlgorithm.search(apps, query)
    }
}

// domain/usecase/GetRecentAppsUseCase.kt
class GetRecentAppsUseCase(
    private val recentAppsRepository: RecentAppsRepository
) {
    suspend operator fun invoke(): List<AppInfo> {
        return recentAppsRepository.getRecentApps()
    }
}
```

### 2. Data Layer

#### Repository Implementations
```kotlin
// data/repository/AppRepositoryImpl.kt
class AppRepositoryImpl(
    private val packageManager: PackageManager,
    private val appInfoMapper: AppInfoMapper
) : AppRepository {
    
    override suspend fun getInstalledApps(): List<AppInfo> = 
        withContext(Dispatchers.IO) {
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            packageManager.queryIntentActivities(mainIntent, 0)
                .map { appInfoMapper.mapToDomain(it) }
                .sortedBy { it.name.lowercase() }
        }
    
    override fun observeInstalledApps(): Flow<List<AppInfo>> = 
        callbackFlow { /* observe package changes */ }
}
```

#### Data Sources
```kotlin
// data/local/datastore/RecentAppsDataSource.kt
class RecentAppsDataSource(
    private val dataStore: DataStore<Preferences>
) {
    private val recentAppsKey = stringPreferencesKey("recent_apps")
    
    suspend fun getRecentPackageNames(): List<String> {
        return dataStore.data
            .map { it[recentAppsKey] ?: "" }
            .first()
            .split(",")
            .filter { it.isNotEmpty() }
    }
    
    suspend fun saveRecentPackage(packageName: String) {
        dataStore.edit { preferences ->
            // Implementation
        }
    }
}
```

### 3. Presentation Layer

#### State Management
```kotlin
// presentation/search/SearchUiState.kt
data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val recentApps: List<AppInfo> = emptyList(),
    val searchResults: List<AppInfo> = emptyList(),
    val error: String? = null
)

// presentation/search/SearchViewModel.kt
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchAppsUseCase: SearchAppsUseCase,
    private val getRecentAppsUseCase: GetRecentAppsUseCase,
    private val saveRecentAppUseCase: SaveRecentAppUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        if (query.isBlank()) {
            loadRecentApps()
        } else {
            searchApps(query)
        }
    }
    
    private fun searchApps(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val results = searchAppsUseCase(query)
            _uiState.update { 
                it.copy(isLoading = false, searchResults = results.apps) 
            }
        }
    }
}
```

### 4. Dependency Injection (Hilt)

```kotlin
// app/di/AppModule.kt
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun providePackageManager(@ApplicationContext context: Context): PackageManager {
        return context.packageManager
    }
    
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}

// app/di/RepositoryModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    abstract fun bindAppRepository(
        impl: AppRepositoryImpl
    ): AppRepository
    
    @Binds
    abstract fun bindRecentAppsRepository(
        impl: RecentAppsRepositoryImpl
    ): RecentAppsRepository
}

// app/di/UseCaseModule.kt
@Module
@InstallIn(ViewModelComponent::class)
object UseCaseModule {
    
    @Provides
    fun provideSearchAppsUseCase(
        appRepository: AppRepository
    ): SearchAppsUseCase = SearchAppsUseCase(appRepository)
}
```

## Navigation Architecture

### Type-Safe Navigation (Compose Navigation 2.8+)
```kotlin
// app/navigation/Routes.kt
sealed class Route {
    @Serializable
    data object Home : Route()
    
    @Serializable
    data class Search(val initialQuery: String = "") : Route()
    
    @Serializable
    data object Settings : Route()
}

// app/navigation/LauncherNavHost.kt
@Composable
fun LauncherNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Route.Home,
        modifier = modifier
    ) {
        composable<Route.Home> {
            HomeScreen(
                onSearchClick = { navController.navigate(Route.Search()) }
            )
        }
        
        composable<Route.Search> { backStackEntry ->
            val search = backStackEntry.toRoute<Route.Search>()
            SearchScreen(
                initialQuery = search.initialQuery,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

## Testing Strategy

### 1. Unit Tests (Domain & Data Layers)
```kotlin
// Example: Use case test
class SearchAppsUseCaseTest {
    private lateinit var useCase: SearchAppsUseCase
    private val appRepository: AppRepository = mockk()
    
    @Test
    fun `search returns exact matches first`() = runTest {
        // Arrange
        val apps = listOf(
            AppInfo("Chrome", "com.android.chrome", AppCategory.PRODUCTIVITY),
            AppInfo("Chrome Beta", "com.chrome.beta", AppCategory.PRODUCTIVITY)
        )
        coEvery { appRepository.getInstalledApps() } returns apps
        
        // Act
        val result = useCase("chrome")
        
        // Assert
        assertEquals("Chrome", result.apps.first().name)
    }
}
```

### 2. Integration Tests (Repository Tests)
```kotlin
@Test
fun `repository loads apps from package manager`() = runTest {
    // Test with real/fake package manager
}
```

### 3. UI Tests (Presentation Layer)
```kotlin
@HiltAndroidTest
class SearchScreenTest {
    @Test
    fun searchDisplaysResults() {
        composeTestRule.setContent {
            SearchScreen(/*...*/)
        }
        
        composeTestRule
            .onNodeWithContentDescription("Search apps...")
            .performTextInput("chrome")
        
        composeTestRule
            .onNodeWithText("Chrome")
            .assertIsDisplayed()
    }
}
```

## Implementation Phases

### Phase 1: Foundation (Week 1-2)
- [ ] Set up Hilt for dependency injection
- [ ] Create domain layer (models, interfaces)
- [ ] Migrate existing code to new package structure
- [ ] Extract AppInfo data class to domain

### Phase 2: Data Layer (Week 2-3)
- [ ] Implement AppRepository with package manager
- [ ] Implement RecentAppsRepository with DataStore
- [ ] Create mappers for data transformation
- [ ] Add repository unit tests

### Phase 3: Use Cases (Week 3)
- [ ] Create search algorithm use case
- [ ] Create recent apps management use cases
- [ ] Add use case unit tests
- [ ] Optimize performance with coroutines

### Phase 4: Presentation Refactor (Week 3-4)
- [ ] Refactor ViewModels to use use cases
- [ ] Extract UI components to presentation/common
- [ ] Implement proper state management
- [ ] Add ViewModel tests

### Phase 5: Navigation (Week 4)
- [ ] Implement type-safe navigation
- [ ] Create navigation graph
- [ ] Handle deep links
- [ ] Add navigation tests

### Phase 6: Polish & Features (Week 5)
- [ ] Add settings screen
- [ ] Implement app categories
- [ ] Add animations and transitions
- [ ] Performance optimization
- [ ] Accessibility improvements

## Additional Dependencies to Add

```toml
[versions]
hilt = "2.55"
hiltNavigationCompose = "1.2.0"
navigationCompose = "2.8.7"
room = "2.6.1"
turbine = "1.2.0"
mockk = "1.13.16"
coroutinesTest = "1.10.1"

[libraries]
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }

[plugins]
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version = "2.0.21-1.0.27" }
```

## Benefits of This Architecture

1. **Testability**: Domain layer is pure Kotlin, easy to unit test
2. **Maintainability**: Clear separation of concerns
3. **Scalability**: Easy to add new features without breaking existing code
4. **Flexibility**: Can swap data sources (e.g., add Room database) without changing UI
5. **Team Collaboration**: Clear boundaries enable parallel development
6. **Testability**: Each layer can be tested independently with mocks

## Guidelines

### Do's ✅
- Keep domain layer free from Android dependencies
- Use immutable data classes for state
- Prefer Flow/StateFlow for reactive data
- Use type-safe navigation
- Write unit tests for use cases and repositories
- Follow single responsibility principle

### Don'ts ❌
- Don't import Android classes in domain layer
- Don't put business logic in ViewModels
- Don't expose data layer models to UI
- Don't use `GlobalScope` for coroutines
- Don't access context in ViewModels directly

## Future Enhancements

- **Room Database**: Cache app info for faster loading
- **WorkManager**: Background sync for app updates
- **App Widgets**: Add launcher widgets
- **Shortcuts**: Support for app shortcuts
- **Search Providers**: Integrate with system search
- **Gesture Navigation**: Custom gesture controls
- **Themes**: User-customizable themes
- **App Lock**: Privacy features
